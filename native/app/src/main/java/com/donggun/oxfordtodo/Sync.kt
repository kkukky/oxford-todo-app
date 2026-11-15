package com.donggun.oxfordtodo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 데이터 보관: 앱 안의 SQLite(빠른 작업용) + 폰 폴더(Documents/OXFORDToDoNotepad) + 구글 드라이브(OXFORDToDoNotepad 폴더).
 * 세 곳은 같은 JSON 파일 하나로 맞춘다. 레코드마다 updated_at이 더 늦은 쪽이 이긴다.
 */
object Sync {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    class SyncError(msg: String) : Exception(msg)

    private val ITEM_COLS = listOf("id", "title", "description", "bucket", "star", "urgent", "done", "completed_at", "sort_order", "created_at", "updated_at", "deleted_at", "archived_at")
    private val STEP_COLS = listOf("id", "item_id", "title", "done", "done_at", "position", "updated_at", "deleted_at")
    private val LOG_COLS = listOf("id", "item_id", "type", "text", "created_at", "updated_at", "deleted_at")
    private val BOOLS = setOf("star", "urgent", "done")
    private data class T(val name: String, val cols: List<String>)
    private val TABLES = listOf(T("items", ITEM_COLS), T("steps", STEP_COLS), T("logs", LOG_COLS))

    fun folderOn(db: Db) = db.getSetting("fs_tree", "").isNotEmpty()
    fun driveOn(db: Db) = db.getSetting("gd_on", "0") == "1"
    fun enabled(db: Db) = folderOn(db) || driveOn(db)
    fun email(db: Db) = db.getSetting("gd_email", "")

    fun authRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private fun driveToken(ctx: Context): String {
        val r = try {
            Tasks.await(Identity.getAuthorizationClient(ctx.applicationContext).authorize(authRequest()), 30, TimeUnit.SECONDS)
        } catch (e: Exception) { throw SyncError("구글 연결 확인 실패: " + (e.cause?.message ?: e.message)) }
        if (r.hasResolution()) throw SyncError("구글 드라이브 연결을 다시 해 주세요 (설정 → 구글 드라이브 연결)")
        return r.accessToken ?: throw SyncError("구글 접근 토큰을 받지 못했어요")
    }

    // ---------- 내보내기 / 합치기 ----------
    fun export(raw: SQLiteDatabase): String {
        val root = JSONObject().put("app", "oxford-todo").put("version", 1)
        for (t in TABLES) {
            val arr = JSONArray()
            raw.rawQuery("SELECT ${t.cols.joinToString(",")} FROM ${t.name} ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    t.cols.forEachIndexed { i, col ->
                        if (c.isNull(i)) o.put(col, JSONObject.NULL)
                        else when {
                            col == "sort_order" -> o.put(col, c.getDouble(i))
                            c.getType(i) == Cursor.FIELD_TYPE_INTEGER -> o.put(col, c.getLong(i))
                            else -> o.put(col, c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            root.put(t.name, arr)
        }
        return root.toString(1)
    }

    /** 파일 내용을 앱 DB에 합친다. 반환: 바뀐 레코드 수 */
    fun merge(raw: SQLiteDatabase, text: String): Int {
        val root = try { JSONObject(text) } catch (e: Exception) { throw SyncError("데이터 파일을 읽을 수 없어요") }
        var n = 0
        raw.beginTransaction()
        try {
            for (t in TABLES) {
                val arr = root.optJSONArray(t.name) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id"); val ru = o.optLong("updated_at", 0)
                    val lu = raw.rawQuery("SELECT updated_at FROM ${t.name} WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
                    if (ru <= lu) continue
                    val v = ContentValues()
                    t.cols.forEach { col ->
                        if (o.isNull(col)) v.putNull(col)
                        else when {
                            col in BOOLS -> v.put(col, if (o.get(col) == true || o.optInt(col) == 1) 1 else 0)
                            col == "sort_order" -> v.put(col, o.getDouble(col))
                            col.endsWith("_at") || col == "position" -> v.put(col, o.getLong(col))
                            else -> v.put(col, o.getString(col))
                        }
                    }
                    v.put("synced_at", ru)
                    raw.insertWithOnConflict(t.name, null, v, SQLiteDatabase.CONFLICT_REPLACE)
                    n++
                }
            }
            raw.setTransactionSuccessful()
        } finally { raw.endTransaction() }
        return n
    }

    // ---------- 구글 드라이브 ----------
    private fun gd(method: String, url: String, tok: String, body: ByteArray? = null, ctype: String? = null, override: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer $tok")
        if (override != null) c.setRequestProperty("X-HTTP-Method-Override", override)
        if (ctype != null) c.setRequestProperty("Content-Type", ctype)
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body) } }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return code to txt
    }

    private fun gdErr(txt: String): String = try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { txt.take(120) }
    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun firstId(tok: String, query: String): String {
        val (code, txt) = gd("GET", "https://www.googleapis.com/drive/v3/files?q=${q(query)}&fields=files(id)&spaces=drive&pageSize=1", tok)
        if (code !in 200..299) throw SyncError("드라이브 조회 실패: " + gdErr(txt))
        val a = JSONObject(txt).optJSONArray("files")
        return if (a != null && a.length() > 0) a.getJSONObject(0).getString("id") else ""
    }

    private fun ensureFolder(tok: String): String {
        val id = firstId(tok, "name='${Store.DIR}' and mimeType='$FOLDER_MIME' and trashed=false")
        if (id.isNotEmpty()) return id
        val meta = JSONObject().put("name", Store.DIR).put("mimeType", FOLDER_MIME).toString().toByteArray()
        val (code, txt) = gd("POST", "https://www.googleapis.com/drive/v3/files?fields=id", tok, meta, "application/json; charset=UTF-8")
        if (code !in 200..299) throw SyncError("드라이브 폴더 만들기 실패: " + gdErr(txt))
        return JSONObject(txt).getString("id")
    }

    private fun upload(tok: String, folderId: String, fileId: String, text: String) {
        if (fileId.isEmpty()) {
            val b = "oxfordBoundary7d29"
            val meta = JSONObject().put("name", Store.FILE).put("parents", JSONArray().put(folderId)).toString()
            val body = ("--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$text\r\n--$b--").toByteArray(Charsets.UTF_8)
            val (code, txt) = gd("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", tok, body, "multipart/related; boundary=$b")
            if (code !in 200..299) throw SyncError("드라이브 저장 실패: " + gdErr(txt))
        } else {
            val (code, txt) = gd("POST", "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media", tok, text.toByteArray(Charsets.UTF_8), "application/json; charset=UTF-8", "PATCH")
            if (code !in 200..299) throw SyncError("드라이브 저장 실패: " + gdErr(txt))
        }
    }

    /** 폰 폴더 · 드라이브에서 읽어 합치고, 합친 결과를 다시 쓴다. 반환: (저장한 곳 수, 가져온 레코드 수) */
    fun run(ctx: Context): Pair<Int, Int> {
        val db = Db.get(ctx)
        val raw = db.writableDatabase
        val useFs = folderOn(db); val useGd = driveOn(db)
        var saved = 0; var got = 0

        val fsText = if (useFs) Store.read(ctx) else null
        if (fsText != null) got += merge(raw, fsText)

        var tok = ""; var folderId = ""; var fileId = ""; var gdText: String? = null
        if (useGd) {
            tok = driveToken(ctx)
            folderId = ensureFolder(tok)
            fileId = firstId(tok, "'$folderId' in parents and name='${Store.FILE}' and trashed=false")
            if (fileId.isNotEmpty()) {
                val (code, txt) = gd("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", tok)
                if (code !in 200..299) throw SyncError("드라이브 읽기 실패: " + gdErr(txt))
                gdText = txt
                got += merge(raw, txt)
            }
        }

        val out = export(raw)
        if (useFs && fsText != out) {
            if (!Store.write(ctx, out)) throw SyncError("폰 폴더에 저장하지 못했어요. 설정에서 폴더를 다시 선택해 주세요")
            saved++
        }
        if (useGd && gdText != out) { upload(tok, folderId, fileId, out); saved++ }
        db.setSetting("last_sync", System.currentTimeMillis().toString())
        return saved to got
    }
}

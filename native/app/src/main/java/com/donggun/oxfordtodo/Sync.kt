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
    private val AI_COLS = listOf("id", "item_id", "who", "label", "text", "applied", "created_at", "updated_at", "deleted_at")
    private val BOOLS = setOf("star", "urgent", "done")
    private data class T(val name: String, val cols: List<String>)
    private val TABLES = listOf(T("items", ITEM_COLS), T("steps", STEP_COLS), T("logs", LOG_COLS), T("ai_msgs", AI_COLS))

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

    /** (파일 id, 내용 해시) — 없으면 ("", "") */
    private fun firstFile(tok: String, query: String): Pair<String, String> {
        val (code, txt) = gd("GET", "https://www.googleapis.com/drive/v3/files?q=${q(query)}&fields=files(id,md5Checksum)&spaces=drive&pageSize=1", tok)
        if (code !in 200..299) throw SyncError("드라이브 조회 실패: " + gdErr(txt))
        val a = JSONObject(txt).optJSONArray("files")
        return if (a != null && a.length() > 0) a.getJSONObject(0).getString("id") to a.getJSONObject(0).optString("md5Checksum", "") else "" to ""
    }
    private fun firstId(tok: String, query: String): String = firstFile(tok, query).first

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

    // ---------- 마지막으로 동기화한 내용 사본 (앱 저장소) ----------
    private fun snapFile(ctx: Context, name: String) = java.io.File(java.io.File(ctx.filesDir, "sync").also { it.mkdirs() }, name)
    private fun readSnap(ctx: Context, name: String): String? = try { snapFile(ctx, name).takeIf { it.exists() }?.readText(Charsets.UTF_8) } catch (e: Exception) { null }
    private fun writeSnap(ctx: Context, name: String, text: String) { try { snapFile(ctx, name).writeText(text, Charsets.UTF_8) } catch (e: Exception) {} }
    private fun md5(text: String): String = java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    class Result(val saved: Int, val got: Int, val errors: List<String>, val notes: List<String>)

    /**
     * 마지막으로 동기화한 사본(구글 드라이브용·폰 폴더용)과 비교해서 달라진 쪽만 읽거나 쓴다.
     *  - 드라이브 파일의 해시가 지난번과 같으면 내려받지 않고, 앱 내용이 사본과 같으면 올리지 않는다.
     *  - 폰 폴더 파일이 사본과 같으면 합치지 않고, 앱 내용과 같으면 다시 쓰지 않는다.
     * 폰 폴더와 드라이브는 서로 독립이다: 한쪽이 실패해도 다른 쪽은 계속 진행하고, 실패 이유는 모아서 돌려준다.
     * 같은 시각에 두 번 돌지 않도록 한 번에 하나만 실행한다.
     */
    @Synchronized
    fun run(ctx: Context): Result {
        val db = Db.get(ctx)
        val raw = db.writableDatabase
        val useFs = folderOn(db); val useGd = driveOn(db)
        var saved = 0; var got = 0
        val errors = ArrayList<String>(); val notes = ArrayList<String>()

        // ① 읽어서 합치기 (실패해도 다른 쪽은 계속)
        var fsText: String? = null; var fsReadOk = useFs
        if (useFs) {
            try {
                fsText = Store.read(ctx)
                val snapFs = readSnap(ctx, "last_phone.json")
                if (fsText != null && fsText != snapFs) {
                    try { got += merge(raw, fsText) }
                    catch (e: SyncError) { notes.add("폰 폴더 파일이 깨져 있어서 건너뛰고 새로 저장해요"); fsText = null }
                }
            } catch (e: Exception) { fsReadOk = false; errors.add("폰 폴더: " + (e.message ?: e.javaClass.simpleName)) }
        }

        var tok = ""; var folderId = ""; var fileId = ""; var remoteMd5 = ""; var gdReadOk = useGd
        if (useGd) {
            try {
                tok = driveToken(ctx)
                folderId = ensureFolder(tok)
                val f = firstFile(tok, "'$folderId' in parents and name='${Store.FILE}' and trashed=false")
                fileId = f.first; remoteMd5 = f.second
                val lastMd5 = db.getSetting("gd_md5", "")
                if (fileId.isNotEmpty() && (remoteMd5.isEmpty() || remoteMd5 != lastMd5)) {
                    val (code, txt) = gd("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", tok)
                    if (code !in 200..299) throw SyncError("드라이브 읽기 실패: " + gdErr(txt))
                    got += merge(raw, txt)
                }
            } catch (e: Exception) { gdReadOk = false; errors.add("구글 드라이브: " + (e.message ?: e.javaClass.simpleName)) }
        }

        // ② 내보내서 저장하기. 읽기에 실패한 쪽은 상대 내용을 못 본 채 덮어쓸 수 있어서 쓰지 않는다.
        val out = export(raw)
        if (useFs && fsReadOk) {
            try {
                if (fsText == null || fsText != out) {
                    if (!Store.write(ctx, out)) throw SyncError("폰 폴더에 저장하지 못했어요. 설정에서 폴더를 다시 선택해 주세요")
                    saved++
                }
                writeSnap(ctx, "last_phone.json", out)
            } catch (e: Exception) { errors.add("폰 폴더: " + (e.message ?: e.javaClass.simpleName)) }
        }
        if (useGd && gdReadOk) {
            try {
                val snapGd = readSnap(ctx, "last_drive.json")
                val outMd5 = md5(out)
                if (fileId.isEmpty() || out != snapGd || remoteMd5 != outMd5) { upload(tok, folderId, fileId, out); saved++ }
                writeSnap(ctx, "last_drive.json", out)
                db.setSetting("gd_md5", outMd5)
            } catch (e: Exception) { errors.add("구글 드라이브: " + (e.message ?: e.javaClass.simpleName)) }
        }

        val now = System.currentTimeMillis().toString()
        db.setSetting("last_check", now)
        if (saved > 0 || got > 0) db.setSetting("last_change", now)
        if (errors.isEmpty()) { db.setSetting("sync_err", ""); db.setSetting("last_ok", now) }
        else { db.setSetting("sync_err", errors.joinToString(" / ")); db.setSetting("sync_err_at", now) }
        return Result(saved, got, errors, notes)
    }
}

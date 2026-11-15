package com.donggun.oxfordtodo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Supabase 이메일 로그인 + 변경분 동기화. 서버 주소와 공개(publishable) 키는 앱에 넣어도 안전한 값이다. */
object Sync {
    const val URL_BASE = "https://ggesrslhgqtiwdxuyvnh.supabase.co"
    const val KEY = "sb_publishable_Xtpyq_Xp_8caJgit_jaE-w_i76ZdhKg"

    class SyncError(msg: String) : Exception(msg)

    private fun http(method: String, path: String, body: String?, token: String?, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(URL_BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Authorization", "Bearer " + (token ?: KEY))
        c.setRequestProperty("Content-Type", "application/json")
        extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to txt
    }

    private fun errMsg(txt: String): String = try {
        val j = JSONObject(txt); j.optString("msg", j.optString("error_description", j.optString("message", txt)))
    } catch (e: Exception) { txt }

    fun email(db: Db) = db.getSetting("sb_email", "")
    fun loggedIn(db: Db) = db.getSetting("sb_refresh", "").isNotEmpty()

    private fun saveSession(db: Db, j: JSONObject) {
        db.setSetting("sb_access", j.getString("access_token"))
        db.setSetting("sb_refresh", j.getString("refresh_token"))
        db.setSetting("sb_expires", (System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60000).toString())
        j.optJSONObject("user")?.optString("email")?.let { if (it.isNotEmpty()) db.setSetting("sb_email", it) }
        j.optJSONObject("user")?.optString("id")?.let { if (it.isNotEmpty()) db.setSetting("sb_uid", it) }
    }

    /** 가입 후 확인 메일이 필요하면 true를 돌려준다 */
    fun signUp(db: Db, email: String, pw: String): Boolean {
        val b = JSONObject().put("email", email).put("password", pw).toString()
        val (code, txt) = http("POST", "/auth/v1/signup", b, null)
        if (code !in 200..299) throw SyncError(errMsg(txt))
        val j = JSONObject(txt)
        return if (j.has("access_token")) { saveSession(db, j); false } else true
    }

    fun signIn(db: Db, email: String, pw: String) {
        val b = JSONObject().put("email", email).put("password", pw).toString()
        val (code, txt) = http("POST", "/auth/v1/token?grant_type=password", b, null)
        if (code !in 200..299) throw SyncError(errMsg(txt))
        saveSession(db, JSONObject(txt))
    }

    fun signOut(db: Db) {
        listOf("sb_access", "sb_refresh", "sb_expires", "sb_email", "sb_uid").forEach { db.setSetting(it, "") }
    }

    private fun token(db: Db): String {
        val exp = db.getSetting("sb_expires", "0").toLongOrNull() ?: 0
        val acc = db.getSetting("sb_access", "")
        if (acc.isNotEmpty() && System.currentTimeMillis() < exp) return acc
        val rf = db.getSetting("sb_refresh", "")
        if (rf.isEmpty()) throw SyncError("로그인이 필요해요")
        val (code, txt) = http("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", rf).toString(), null)
        if (code !in 200..299) throw SyncError("다시 로그인해 주세요")
        val j = JSONObject(txt); saveSession(db, j); return j.getString("access_token")
    }

    private val ITEM_COLS = listOf("id", "title", "description", "bucket", "star", "urgent", "done", "completed_at", "sort_order", "created_at", "updated_at", "deleted_at", "archived_at")
    private val STEP_COLS = listOf("id", "item_id", "title", "done", "done_at", "position", "updated_at", "deleted_at")
    private val LOG_COLS = listOf("id", "item_id", "type", "text", "created_at", "updated_at", "deleted_at")
    private val BOOLS = setOf("star", "urgent", "done")

    private data class T(val name: String, val cols: List<String>)
    private val TABLES = listOf(T("items", ITEM_COLS), T("steps", STEP_COLS), T("logs", LOG_COLS))

    /** 변경분 올리기 + 받기. 반환: (올린 수, 받은 수) */
    fun run(ctx: Context): Pair<Int, Int> {
        val db = Db.get(ctx)
        val tok = token(db)
        val raw: SQLiteDatabase = db.writableDatabase
        var up = 0; var down = 0
        // 1) 올리기: updated_at > synced_at
        for (t in TABLES) {
            val rows = ArrayList<JSONObject>()
            raw.rawQuery("SELECT ${t.cols.joinToString(",")} FROM ${t.name} WHERE updated_at > synced_at", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    t.cols.forEachIndexed { i, col ->
                        if (c.isNull(i)) o.put(col, JSONObject.NULL)
                        else when {
                            col in BOOLS -> o.put(col, c.getInt(i) == 1)
                            col == "sort_order" -> o.put(col, c.getDouble(i))
                            c.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER -> o.put(col, c.getLong(i))
                            else -> o.put(col, c.getString(i))
                        }
                    }
                    rows.add(o)
                }
            }
            rows.chunked(200).forEach { chunk ->
                val (code, txt) = http("POST", "/rest/v1/${t.name}?on_conflict=id", JSONArray(chunk).toString(), tok,
                    mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
                if (code !in 200..299) throw SyncError("올리기 실패: " + errMsg(txt))
                chunk.forEach { o ->
                    val v = ContentValues().apply { put("synced_at", o.getLong("updated_at")) }
                    raw.update(t.name, v, "id=? AND updated_at=?", arrayOf(o.getString("id"), o.getLong("updated_at").toString()))
                }
                up += chunk.size
            }
        }
        // 2) 받기: 서버의 updated_at이 로컬보다 새로우면 덮어쓰기(나중에 고친 쪽이 이김)
        for (t in TABLES) {
            val key = "pull_" + t.name
            var since = db.getSetting(key, "0").toLongOrNull() ?: 0
            while (true) {
                val (code, txt) = http("GET", "/rest/v1/${t.name}?select=${t.cols.joinToString(",")}&updated_at=gt.$since&order=updated_at.asc&limit=500", null, tok)
                if (code !in 200..299) throw SyncError("받기 실패: " + errMsg(txt))
                val arr = JSONArray(txt); if (arr.length() == 0) break
                raw.beginTransaction()
                try {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.getString("id"); val ru = o.getLong("updated_at")
                        val lu = raw.rawQuery("SELECT updated_at FROM ${t.name} WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
                        if (ru > lu) {
                            val v = ContentValues()
                            t.cols.forEach { col ->
                                if (o.isNull(col)) v.putNull(col)
                                else when {
                                    col in BOOLS -> v.put(col, if (o.getBoolean(col)) 1 else 0)
                                    col == "sort_order" -> v.put(col, o.getDouble(col))
                                    col.endsWith("_at") || col == "position" -> v.put(col, o.getLong(col))
                                    else -> v.put(col, o.getString(col))
                                }
                            }
                            v.put("synced_at", ru)
                            raw.insertWithOnConflict(t.name, null, v, SQLiteDatabase.CONFLICT_REPLACE)
                            down++
                        }
                        since = maxOf(since, ru)
                    }
                    raw.setTransactionSuccessful()
                } finally { raw.endTransaction() }
                if (arr.length() < 500) break
            }
            db.setSetting(key, since.toString())
        }
        db.setSetting("last_sync", System.currentTimeMillis().toString())
        return up to down
    }
}

package com.donggun.oxfordwriter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 아이보리 바탕 + 우드 색 */
object C {
    val Ivory = Color(0xFFFBF8F1)
    val Paper2 = Color(0xFFF1E9DB)
    val Card = Color(0xFFFFFEFA)
    val Ink = Color(0xFF3A2A1C)
    val Ink2 = Color(0xFF7A6650)
    val Line = Color(0xFFE6DCCB)
    val Wood = Color(0xFF8A6139)
    val WoodLight = Color(0xFFA37548)
    val Accent = Color(0xFF9A622B)
    val Bad = Color(0xFFA8402F)
}

object Pref {
    private fun sp(c: Context) = c.getSharedPreferences("writer", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = ""): String = sp(c).getString(k, d) ?: d
    fun set(c: Context, k: String, v: String) { sp(c).edit().putString(k, v).apply() }
}

class Memo(val id: String, val title: String, val desc: String, val steps: List<String>)

/** 메모 앱이 폰의 Documents/OXFORDToDoNotepad 폴더에 저장하는 데이터 파일을 읽는다. (읽기 전용: 메모 파일은 바꾸지 않는다) */
object Memos {
    const val DIR = "OXFORDToDoNotepad"
    const val FILE = "oxford-todo-data.json"
    const val OUT = "oxford-writing-data.json"
    const val TAG = "#글쓰기"

    fun setTree(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {}
        Pref.set(ctx, "tree", uri.toString())
    }

    fun hasTree(ctx: Context) = Pref.get(ctx, "tree").isNotEmpty()

    /** 선택한 폴더 안의 OXFORDToDoNotepad 폴더. 선택한 폴더 자체가 그 이름이면 그대로 쓴다. */
    fun dir(ctx: Context, create: Boolean = false): DocumentFile? {
        val t = Pref.get(ctx, "tree")
        if (t.isEmpty()) return null
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(t)) ?: return null
        if (!root.exists()) return null
        if (root.name == DIR) return root
        val d = root.findFile(DIR)?.takeIf { it.isDirectory }
        return d ?: if (create && root.canWrite()) root.createDirectory(DIR) else null
    }

    fun label(ctx: Context): String {
        val t = Pref.get(ctx, "tree")
        if (t.isEmpty()) return ""
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(t)) ?: return ""
        return if (root.name == DIR) DIR else (root.name ?: "") + "/" + DIR
    }

    /** #글쓰기 표시가 있는 메모만. 삭제·보관한 메모는 제외한다. */
    fun load(ctx: Context): Result<List<Memo>> {
        if (!hasTree(ctx)) return Result.failure(Exception("NOFOLDER"))
        val d = dir(ctx) ?: return Result.failure(Exception("폴더를 열 수 없어요. 폴더를 다시 선택해 주세요."))
        val f = d.findFile(FILE) ?: return Result.failure(Exception("폴더 안에 메모 데이터 파일($FILE)이 없어요. 메모 앱에서 폰 폴더를 연결했는지 확인해 주세요."))
        return try {
            val text = ctx.contentResolver.openInputStream(f.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val root = JSONObject(text)
            val items = root.optJSONArray("items") ?: JSONArray()
            val stepsArr = root.optJSONArray("steps") ?: JSONArray()
            val stepMap = HashMap<String, ArrayList<Pair<Long, String>>>()
            for (i in 0 until stepsArr.length()) {
                val o = stepsArr.getJSONObject(i)
                if (!o.isNull("deleted_at")) continue
                stepMap.getOrPut(o.optString("item_id")) { ArrayList() }.add(o.optLong("position") to o.optString("title"))
            }
            val tmp = ArrayList<Pair<Long, Memo>>()
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                if (!o.isNull("deleted_at") || !o.isNull("archived_at")) continue
                val title = o.optString("title"); val desc = o.optString("description")
                if (!title.contains(TAG) && !desc.contains(TAG)) continue
                val id = o.optString("id")
                val steps = (stepMap[id] ?: ArrayList()).sortedBy { it.first }.map { it.second }
                tmp.add(o.optLong("created_at") to Memo(id, title, desc, steps))
            }
            Result.success(tmp.sortedByDescending { it.first }.map { it.second })
        } catch (e: Exception) {
            Result.failure(Exception("메모 데이터 파일을 읽지 못했어요"))
        }
    }
}

class Draft(
    val id: String, val kind: String, val title: String, val body: String, val draft: String,
    val srcHash: String, val read: Boolean, val edited: Boolean, val created: Long,
)

/** 쓴 글은 앱 안에 저장하고, 같은 폴더에 oxford-writing-data.json으로도 한 부 내보낸다(다른 앱에서 읽을 수 있게). */
object Drafts {
    private fun file(ctx: Context) = File(ctx.filesDir, "writings.json")

    fun load(ctx: Context): Map<String, Draft> {
        val f = file(ctx)
        if (!f.exists()) return emptyMap()
        return try {
            val arr = JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("writings") ?: JSONArray()
            val m = LinkedHashMap<String, Draft>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                m[o.getString("id")] = Draft(
                    o.getString("id"), o.optString("kind", "memo"), o.optString("title"), o.optString("body"), o.optString("draft"),
                    o.optString("src_hash"), o.optBoolean("read"), o.optBoolean("edited"), o.optLong("created_at"),
                )
            }
            m
        } catch (_: Exception) { emptyMap() }
    }

    private fun toJson(all: Collection<Draft>): String {
        val arr = JSONArray()
        all.forEach {
            arr.put(JSONObject().put("id", it.id).put("kind", it.kind).put("title", it.title).put("body", it.body).put("draft", it.draft)
                .put("src_hash", it.srcHash).put("read", it.read).put("edited", it.edited).put("created_at", it.created))
        }
        return JSONObject().put("app", "oxford-writer").put("version", 1).put("writings", arr).toString()
    }

    fun put(ctx: Context, d: Draft) {
        val m = LinkedHashMap(load(ctx)); m[d.id] = d; write(ctx, m.values)
    }

    fun remove(ctx: Context, id: String) {
        val m = LinkedHashMap(load(ctx)); m.remove(id); write(ctx, m.values)
    }

    private fun write(ctx: Context, all: Collection<Draft>) {
        val text = toJson(all)
        file(ctx).writeText(text, Charsets.UTF_8)
        val app = ctx.applicationContext
        Thread {
            try {
                val dir = Memos.dir(app, true) ?: return@Thread
                val f = dir.findFile(Memos.OUT) ?: dir.createFile("application/json", Memos.OUT) ?: return@Thread
                app.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } catch (_: Exception) {}
        }.start()
    }
}

/** 내 Anthropic API 키로 직접 호출. 키는 이 폰의 앱 저장소에만 둔다. */
object Ai {
    class Msg(val role: String, val text: String)

    fun key(c: Context) = Pref.get(c, "ai_key").trim()
    fun hasKey(c: Context) = key(c).isNotEmpty()
    fun model(c: Context) = Pref.get(c, "ai_model", "claude-sonnet-5-5")
    fun dayLimit(c: Context) = (Pref.get(c, "ai_limit", "30").toIntOrNull() ?: 30).coerceIn(1, 500)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
    fun todayCount(c: Context): Int = if (Pref.get(c, "ai_day") == today()) Pref.get(c, "ai_cnt", "0").toIntOrNull() ?: 0 else 0
    private fun bump(c: Context) { Pref.set(c, "ai_day", today()); Pref.set(c, "ai_cnt", (todayCount(c) + 1).toString()) }
    fun cut(s: String, n: Int) = if (s.length > n) s.take(n) + "…" else s

    suspend fun call(ctx: Context, system: String, messages: List<Msg>, maxTokens: Int, hardCap: Int): Result<String> = withContext(Dispatchers.IO) {
        val k = key(ctx)
        if (k.isEmpty()) return@withContext Result.failure(Exception("설정(⚙)에서 API 키를 먼저 입력해 주세요"))
        if (todayCount(ctx) >= dayLimit(ctx)) return@withContext Result.failure(Exception("오늘 호출 한도(${dayLimit(ctx)}회)를 다 썼어요. 설정에서 한도를 바꿀 수 있어요"))
        try {
            val body = JSONObject().apply {
                put("model", model(ctx)); put("max_tokens", maxTokens); put("system", system)
                put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) } })
            }
            val c = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 90000; doOutput = true
                setRequestProperty("x-api-key", k); setRequestProperty("anthropic-version", "2023-06-01"); setRequestProperty("content-type", "application/json")
            }
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val m = try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
                return@withContext Result.failure(Exception(when (code) {
                    401 -> "API 키가 올바르지 않아요"
                    403 -> "이 키로는 사용할 수 없어요"
                    429 -> "요청이 많아요. 잠시 뒤에 다시 해 주세요"
                    529, 503 -> "AI 서버가 바빠요. 잠시 뒤에 다시 해 주세요"
                    else -> "오류($code) ${cut(m, 80)}"
                }))
            }
            bump(ctx)
            val arr = JSONObject(txt).getJSONArray("content")
            val out = StringBuilder()
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("type") == "text") out.append(o.getString("text")) }
            Result.success(cut(out.toString().trim(), hardCap))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("인터넷에 연결되어 있지 않아요"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("응답이 너무 늦어요. 다시 해 주세요"))
        } catch (e: Exception) {
            Result.failure(Exception("호출에 실패했어요: ${cut(e.message ?: "", 60)}"))
        }
    }
}

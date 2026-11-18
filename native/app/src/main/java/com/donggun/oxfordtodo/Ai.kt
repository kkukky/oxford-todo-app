package com.donggun.oxfordtodo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 본인 Anthropic API 키로 직접 호출하는 AI 도우미.
 * 키는 폰 안(앱 전용 저장소)에만 저장되고, 동기화(드라이브·폰 폴더)에는 포함되지 않는다.
 * 토큰이 마음대로 쓰이지 않도록: 응답 글자 한도(=max_tokens), 입력 길이 제한, 하루 호출 한도, 사용자가 누를 때만 호출.
 */
object Ai {
    class Msg(val role: String, val text: String)

    const val CAP_IN_TITLE = 200
    const val CAP_IN_DESC = 500
    const val CAP_IN_STEP = 80
    const val CAP_IN_MSG = 500
    const val MAX_STEPS_IN = 10
    const val KEEP_MSGS = 6 // 이어가기 때 함께 보내는 최근 메시지 수

    fun key(db: Db) = db.getSetting("ai_key", "").trim()
    fun hasKey(db: Db) = key(db).isNotEmpty()
    fun lenCap(db: Db) = (db.getSetting("ai_len", "600").toIntOrNull() ?: 600).coerceIn(100, 1000)
    fun dayLimit(db: Db) = (db.getSetting("ai_limit", "30").toIntOrNull() ?: 30).coerceIn(1, 500)
    fun model(db: Db) = db.getSetting("ai_model", "claude-haiku-5-5")

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
    fun todayCount(db: Db): Int = if (db.getSetting("ai_day", "") == today()) db.getSetting("ai_cnt", "0").toIntOrNull() ?: 0 else 0
    private fun bump(db: Db) { val t = today(); val n = todayCount(db) + 1; db.setSetting("ai_day", t); db.setSetting("ai_cnt", n.toString()) }

    fun cut(s: String, n: Int) = if (s.length > n) s.take(n) + "…" else s

    /** 할 일 하나를 짧은 글로 (입력 길이 제한 적용) */
    fun context(db: Db, item: Item, desc: String): String {
        val sb = StringBuilder()
        sb.append("할 일: ").append(cut(item.title, CAP_IN_TITLE)).append('\n')
        if (desc.isNotBlank()) sb.append("설명: ").append(cut(desc, CAP_IN_DESC)).append('\n')
        val steps = db.steps(item.id)
        if (steps.isNotEmpty()) {
            sb.append("절차:\n")
            steps.take(MAX_STEPS_IN).forEachIndexed { i, s -> sb.append("${i + 1}. ").append(if (s.done) "(완료) " else "").append(cut(s.title, CAP_IN_STEP)).append('\n') }
        }
        return sb.toString().trim()
    }

    /** 성공하면 글, 실패하면 사용자에게 보일 한국어 오류 */
    suspend fun call(db: Db, system: String, messages: List<Msg>, maxTokens: Int, hardCap: Int): Result<String> = withContext(Dispatchers.IO) {
        val k = key(db)
        if (k.isEmpty()) return@withContext Result.failure(Exception("설정에서 API 키를 먼저 입력해 주세요"))
        if (todayCount(db) >= dayLimit(db)) return@withContext Result.failure(Exception("오늘 호출 한도(${dayLimit(db)}회)를 다 썼어요. 설정에서 한도를 바꿀 수 있어요"))
        try {
            val body = JSONObject().apply {
                put("model", model(db)); put("max_tokens", maxTokens); put("system", system)
                put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) } })
            }
            val c = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 60000; doOutput = true
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
            bump(db)
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

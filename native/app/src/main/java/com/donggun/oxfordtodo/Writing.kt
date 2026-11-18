package com.donggun.oxfordtodo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 글쓰기 자동화: 메모 중 #글쓰기 표시가 있는 것만 모아 목록으로 보여주고, 펼치면 AI가 쓴 글을 보여준다.
 * 표시가 없는 메모는 건드리지 않는다. 앱 안에서 직접 글쓰기를 요청할 수도 있다.
 * AI 호출은 설정에 저장한 내 API 키·하루 한도를 그대로 쓴다. 글은 이 폰의 앱 저장소에 저장된다.
 */
object Writing {
    const val TAG = "#글쓰기"
    private val TAG_RE = Regex(Regex.escape(TAG))

    class Ent(val id: String, val title: String, val body: String, val steps: List<String>, val local: Boolean, val w: WRow?) {
        val draft: String get() = w?.draft ?: ""
        val hash: String get() = (body + "|" + steps.joinToString("|")).hashCode().toString()
        val stale: Boolean get() = !local && draft.isNotBlank() && w?.srcHash != hash
    }

    private fun strip(s: String) = s.replace(TAG_RE, "").replace(Regex("[ \t]+"), " ").trim()

    fun load(db: Db): List<Ent> {
        val ws = db.writings().associateBy { it.id }
        val out = ArrayList<Ent>()
        ws.values.filter { it.kind == "local" }.sortedByDescending { it.createdAt }.forEach {
            out.add(Ent(it.id, it.title, it.body, emptyList(), true, it))
        }
        db.tagged(TAG).forEach { item ->
            val lines = item.title.split('\n').map { strip(it) }.filter { it.isNotBlank() }
            val desc = item.description.split('\n').map { strip(it) }.filter { it.isNotBlank() }
            val body = (lines + desc).joinToString("\n")
            val steps = db.steps(item.id).map { strip(it.title) }.filter { it.isNotBlank() }
            out.add(Ent(item.id, lines.firstOrNull() ?: "(제목 없음)", body, steps, false, ws[item.id]))
        }
        return out
    }

    fun save(db: Db, e: Ent, draft: String, edited: Boolean) {
        val old = e.w
        db.putWriting(
            WRow(
                e.id, if (e.local) "local" else "memo", e.title, e.body, draft, e.hash,
                if (edited) (old?.read ?: false) else false, edited, old?.createdAt ?: System.currentTimeMillis(),
            )
        )
    }

    suspend fun generate(db: Db, e: Ent, instr: String): Result<String> {
        val prev = e.draft
        val rw = when {
            prev.isNotBlank() && instr.isNotBlank() -> "\n\n[이전 글]\n$prev\n\n[수정 요청사항] $instr\n이전 글을 바탕으로 요청사항을 반영해 다시 쓴다."
            prev.isNotBlank() -> "\n\n이전에 쓴 글이 있다. 같은 메모로 이전과 다른 구성과 표현으로 새로 쓴다."
            instr.isNotBlank() -> "\n\n[요청사항] $instr"
            else -> ""
        }
        val steps = if (e.steps.isEmpty()) "" else "\n[세부 항목]\n" + e.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        val prompt = "아래 메모는 글쓴이의 생각의 씨앗입니다. 이 생각을 확장해 글로 완성해 주세요.\n\n[메모]\n" +
            Ai.cut(e.body, 1500) + steps +
            "\n\n[작성 형식] 첫 줄은 제목 한 줄. 이어서 도입-전개-마무리 3~4문단. 글쓴이의 1인칭 시점, 담백하고 진솔한 존댓말. 분량 600~800자." + rw +
            "\n\n규칙: 메모에 담긴 생각과 관점을 중심으로 쓴다. 근거 없는 통계, 인용, 실제 사례, 일화를 지어내지 않는다. " +
            "메모에 사람 이름이 나오면 그 사람에 대한 사실을 지어내지 않는다. 메모가 성경 구절이면 구절은 그대로 두고 해설은 단정하지 않는다. " +
            "마크다운 기호(#, *, -)는 쓰지 않고 순수 텍스트로만 쓴다. 글 본문만 출력한다."
        return Ai.call(db, "당신은 글쓰기 비서입니다.", listOf(Ai.Msg("user", prompt)), 2000, 2400)
    }
}

@Composable
private fun WBtn(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(6.dp)
    Text(
        label, color = if (primary) pal.bg else pal.ink, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f).clip(shape)
            .then(if (primary) Modifier.background(pal.ink) else Modifier.border(1.dp, pal.rule, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
fun WritingPage(tick: Int) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val ents = remember(tick, rev) { Writing.load(db) }
    var openId by remember { mutableStateOf("") }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val errs = remember { mutableStateMapOf<String, String>() }
    val instr = remember { mutableStateMapOf<String, String>() }
    var newText by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var stopReq by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Writing.Ent?>(null) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    suspend fun doOne(e: Writing.Ent, ins: String): Boolean {
        busy[e.id] = true; errs.remove(e.id)
        val r = Writing.generate(db, e, ins.trim())
        busy.remove(e.id)
        return r.fold(
            onSuccess = { txt -> Writing.save(db, e, txt, false); instr.remove(e.id); rev++; true },
            onFailure = { err -> errs[e.id] = err.message ?: "작성에 실패했어요"; false },
        )
    }

    fun runOne(e: Writing.Ent) {
        if (busy[e.id] == true || running) return
        scope.launch { doOne(e, instr[e.id] ?: "") }
    }

    fun runAll() {
        val todo = ents.filter { it.draft.isBlank() && busy[it.id] != true }
        if (todo.isEmpty() || running) return
        running = true; stopReq = false
        scope.launch {
            var n = 0
            for (e in todo) {
                if (stopReq) break
                prog = "작성 중 ${n + 1}/${todo.size}"
                if (!doOne(e, "")) { prog = "중간에 멈췄어요. 아래 표시를 확인해 주세요."; break }
                n++
            }
            if (!prog.startsWith("중간")) prog = if (stopReq) "중지했어요 (${n}건 작성)" else "${n}건 작성했어요"
            running = false
        }
    }

    fun addLocal() {
        val t = newText.trim()
        if (t.isEmpty()) { toast("주제를 입력해 주세요"); return }
        val id = "a-" + UUID.randomUUID().toString()
        val title = t.lines().first().trim().take(40)
        db.putWriting(WRow(id, "local", title, t, "", "", false, false, System.currentTimeMillis()))
        newText = ""; openId = id; rev++
        val e = Writing.Ent(id, title, t, emptyList(), true, null)
        scope.launch { doOne(e, "") }
    }

    fun copy(t: String) {
        if (t.isBlank()) { toast("복사할 내용이 없어요"); return }
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("writing", t))
        toast("복사했어요")
    }

    fun share(t: String) {
        if (t.isBlank()) { toast("공유할 내용이 없어요"); return }
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }, "글 공유"))
    }

    val pending = ents.count { it.draft.isBlank() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it.take(1000) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("글로 쓰고 싶은 생각이나 주제를 적어 주세요", fontSize = 14.sp) },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "메모에 ${Writing.TAG}를 붙이면 여기에 모여요", color = pal.sub, fontSize = 11.sp, modifier = Modifier.weight(1f),
                    )
                    WBtn("글쓰기 요청", primary = true, enabled = newText.isNotBlank()) { addLocal() }
                }
                if (!Ai.hasKey(db)) Text("설정(⚙)에서 API 키를 입력하면 AI가 글을 써요.", color = pal.urgentBg, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (running) prog else if (prog.isNotEmpty()) prog else if (pending > 0) "미작성 ${pending}건" else if (ents.isEmpty()) "글쓰기 대상이 없어요" else "모두 작성돼 있어요",
                        color = pal.sub, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    if (running) WBtn("중지") { stopReq = true }
                    else WBtn("모두 쓰기", primary = true, enabled = pending > 0) { confirmAll = true }
                }
            }
        }
        items(ents, key = { it.id }) { e ->
            val open = openId == e.id
            val w = e.w
            val unread = e.draft.isNotBlank() && w?.read != true
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(pal.panel)
                    .border(1.dp, if (open) pal.accent else pal.rule, RoundedCornerShape(10.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { openId = if (open) "" else e.id }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (unread) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(RoundedCornerShape(50)).background(pal.accent))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = pal.ink, fontSize = 15.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                        if (e.local) Text("직접 입력", color = pal.accent, fontSize = 10.sp)
                    }
                    Text(
                        if (busy[e.id] == true) "작성 중" else if (e.draft.isBlank()) "미작성" else if (w?.read == true) "읽음" else "새 글",
                        color = pal.sub, fontSize = 11.sp,
                    )
                }
                if (open) {
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        if (e.draft.isNotBlank()) {
                            Text(
                                e.draft, color = pal.ink, fontSize = 15.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(pal.bg).padding(12.dp),
                            )
                            val cnt = e.draft.filter { !it.isWhitespace() }.length
                            Text(
                                "${cnt}자" + (if (w?.edited == true) " · 직접 수정함" else "") + (if (e.stale) " · 메모가 바뀌었어요" else ""),
                                color = if (e.stale) pal.accent else pal.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        } else if (busy[e.id] != true) {
                            Text("아직 글이 없어요. 아래 '글쓰기'를 눌러 보세요.", color = pal.sub, fontSize = 13.sp)
                        }
                        errs[e.id]?.let { Text(it, color = pal.urgentBg, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        if (busy[e.id] == true) Text("AI가 쓰는 중…", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = instr[e.id] ?: "", onValueChange = { instr[e.id] = it.take(200) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("요청사항 (예: 더 짧게, 따뜻한 말투로) — 비워도 돼요", fontSize = 12.sp) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (e.draft.isNotBlank()) {
                                    WBtn(if (w?.read == true) "읽지 않음" else "읽음") {
                                        w?.let { db.putWriting(it.copy(read = !it.read)); rev++ }
                                    }
                                    WBtn("수정") { editing = e }
                                    WBtn("복사") { copy(e.draft) }
                                    WBtn("공유") { share(e.draft) }
                                }
                                if (e.local) WBtn("삭제") { db.deleteWriting(e.id); if (openId == e.id) openId = ""; rev++ }
                            }
                            WBtn(if (e.draft.isBlank()) "글쓰기" else "다시 쓰기", primary = true, enabled = busy[e.id] != true && !running) { runOne(e) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("모두 쓰기") },
        text = {
            Text("아직 작성하지 않은 글 ${pending}건을 AI가 차례로 모두 작성합니다. AI 호출이 ${pending}회 사용돼요(오늘 ${Ai.todayCount(db)}회 사용, 하루 한도 ${Ai.dayLimit(db)}회). 진행할까요?")
        },
        confirmButton = { TextButton({ confirmAll = false; runAll() }) { Text("모두 쓰기") } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("취소") } },
    )

    editing?.let { e -> WritingEditor(e, { editing = null }) { txt -> Writing.save(db, e, txt, true); editing = null; rev++; toast("저장했어요") } }
}

@Composable
private fun WritingEditor(e: Writing.Ent, onClose: () -> Unit, onSave: (String) -> Unit) {
    val initial = e.draft
    var text by remember { mutableStateOf(initial) }
    var askClose by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    fun tryClose() { if (text != initial) askClose = true else onClose() }
    Dialog(
        onDismissRequest = { tryClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navSafe().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ tryClose() }) { Text("취소") }
                Text(e.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button({ onSave(text.trim()) }, enabled = text != initial && text.isNotBlank()) { Text("저장") }
            }
            HorizontalDivider()
            BasicTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(color = cs.onBackground, fontSize = 16.sp, lineHeight = 28.sp, fontFamily = FontFamily.Serif),
                cursorBrush = SolidColor(cs.primary),
            )
            HorizontalDivider()
            Text("${text.filter { !it.isWhitespace() }.length}자", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.6f))
        }
    }
    if (askClose) AlertDialog(
        onDismissRequest = { askClose = false },
        title = { Text("저장하지 않고 닫을까요?") },
        confirmButton = { TextButton({ askClose = false; onClose() }) { Text("저장 안 함") } },
        dismissButton = { TextButton({ askClose = false }) { Text("계속 편집") } },
    )
}

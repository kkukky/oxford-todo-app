package com.donggun.oxfordwriter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class Ent(val id: String, val title: String, val body: String, val steps: List<String>, val local: Boolean, val w: Draft?) {
    val draft: String get() = w?.draft ?: ""
    val hash: String get() = (body + "|" + steps.joinToString("|")).hashCode().toString()
    val stale: Boolean get() = !local && draft.isNotBlank() && w?.srcHash != hash
}

private val TAG_RE = Regex(Regex.escape(Memos.TAG))
private fun strip(s: String) = s.replace(TAG_RE, "").replace(Regex("[ \t]+"), " ").trim()

private fun buildEnts(memos: List<Memo>, drafts: Map<String, Draft>): List<Ent> {
    val out = ArrayList<Ent>()
    drafts.values.filter { it.kind == "local" }.sortedByDescending { it.created }.forEach {
        out.add(Ent(it.id, it.title, it.body, emptyList(), true, it))
    }
    memos.forEach { m ->
        val lines = m.title.split('\n').map { strip(it) }.filter { it.isNotBlank() }
        val desc = m.desc.split('\n').map { strip(it) }.filter { it.isNotBlank() }
        out.add(Ent(m.id, lines.firstOrNull() ?: "(제목 없음)", (lines + desc).joinToString("\n"), m.steps.map { strip(it) }.filter { it.isNotBlank() }, false, drafts[m.id]))
    }
    return out
}

private fun saveDraft(ctx: Context, e: Ent, text: String, edited: Boolean) {
    val old = e.w
    Drafts.put(
        ctx,
        Draft(e.id, if (e.local) "local" else "memo", e.title, e.body, text, e.hash, if (edited) (old?.read ?: false) else false, edited, old?.created ?: System.currentTimeMillis()),
    )
}

private suspend fun generate(ctx: Context, e: Ent, instr: String): Result<String> {
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
    return Ai.call(ctx, "당신은 글쓰기 비서입니다.", listOf(Ai.Msg("user", prompt)), 2000, 2400)
}

@Composable
private fun WBtn(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        label, color = if (primary) C.Ivory else C.Ink, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f).clip(shape)
            .then(if (primary) Modifier.background(C.Wood) else Modifier.border(1.dp, C.Line, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp),
    )
}

@Composable
fun WriterApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var memos by remember { mutableStateOf<List<Memo>>(emptyList()) }
    var loadErr by remember { mutableStateOf("") }
    var rev by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var folderLabel by remember { mutableStateOf(Memos.label(ctx)) }
    var syncMsg by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            val r = withContext(Dispatchers.IO) { Memos.load(ctx) }
            r.fold(
                onSuccess = { memos = it; loadErr = ""; rev++ },
                onFailure = { memos = emptyList(); loadErr = it.message ?: ""; rev++ },
            )
            syncMsg = if (r.isSuccess) "메모 ${memos.size}건을 불러왔어요" else ""
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Memos.setTree(ctx, uri); folderLabel = Memos.label(ctx); reload() }
    }
    val docs = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reload() }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }

    Column(Modifier.fillMaxSize().background(C.Ivory).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(C.WoodLight, C.Wood))).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("메모 글쓰기", color = C.Ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text(if (syncMsg.isNotEmpty()) syncMsg else "${Memos.TAG} 메모를 AI가 글로 써요", color = C.Ivory.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            Text("새로고침", color = C.Ivory, fontSize = 13.sp, modifier = Modifier.clickable { reload() }.padding(8.dp))
            Text("⚙", color = C.Ivory, fontSize = 20.sp, modifier = Modifier.clickable { showSettings = true }.padding(8.dp))
        }
        if (!Memos.hasTree(ctx)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("메모 앱의 데이터를 연결해 주세요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(
                    "메모 앱(옥스포드 투두노트패드)이 폰의 Documents/OXFORDToDoNotepad 폴더에 저장한 파일을 읽어요. " +
                        "'Documents' 폴더를 고른 뒤 '이 폴더 사용'을 눌러 주세요. 메모 파일은 읽기만 하고 바꾸지 않아요.",
                    fontSize = 14.sp, color = C.Ink2, lineHeight = 22.sp,
                )
                WBtn("메모 폴더 선택", primary = true) { pick.launch(docs) }
            }
        } else {
            WritingList(memos, loadErr, rev, ::reload)
        }
    }

    if (showSettings) SettingsDialog(folderLabel, { showSettings = false }, { pick.launch(docs) })
}

@Composable
private fun WritingList(memos: List<Memo>, loadErr: String, rev0: Int, onReload: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    val ents = remember(memos, rev, rev0) { buildEnts(memos, Drafts.load(ctx)) }
    var openId by remember { mutableStateOf("") }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val errs = remember { mutableStateMapOf<String, String>() }
    val instr = remember { mutableStateMapOf<String, String>() }
    var newText by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var stopReq by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Ent?>(null) }
    val listState = rememberLazyListState()

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    suspend fun doOne(e: Ent, ins: String): Boolean {
        busy[e.id] = true; errs.remove(e.id)
        val r = generate(ctx, e, ins.trim())
        busy.remove(e.id)
        return r.fold(
            onSuccess = { txt -> saveDraft(ctx, e, txt, false); instr.remove(e.id); rev++; true },
            onFailure = { err -> errs[e.id] = err.message ?: "작성에 실패했어요"; false },
        )
    }

    fun runOne(e: Ent) {
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
        Drafts.put(ctx, Draft(id, "local", title, t, "", "", false, false, System.currentTimeMillis()))
        newText = ""; openId = id; rev++
        val e = Ent(id, title, t, emptyList(), true, null)
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

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it.take(1000) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("글로 쓰고 싶은 생각이나 주제를 직접 적어도 돼요", fontSize = 14.sp) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WBtn("글쓰기 요청", primary = true, enabled = newText.isNotBlank()) { addLocal() }
                }
                if (!Ai.hasKey(ctx)) Text("설정(⚙)에서 API 키를 입력하면 AI가 글을 써요.", color = C.Bad, fontSize = 12.sp)
                if (loadErr.isNotEmpty() && loadErr != "NOFOLDER") Text(loadErr, color = C.Bad, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (running) prog else if (prog.isNotEmpty()) prog else if (pending > 0) "미작성 ${pending}건" else if (ents.isEmpty()) "${Memos.TAG} 표시가 된 메모가 없어요" else "모두 작성돼 있어요",
                        color = C.Ink2, fontSize = 12.sp, modifier = Modifier.weight(1f),
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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Card)
                    .border(1.dp, if (open) C.Wood else C.Line, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        openId = if (open) "" else e.id
                        if (!open) { val ix = ents.indexOfFirst { it.id == e.id } + 1; scope.launch { listState.animateScrollToItem(ix) } }
                    }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (unread) Box(Modifier.padding(end = 8.dp).size(8.dp).clip(RoundedCornerShape(50)).background(C.Accent))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = C.Ink, fontSize = 15.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                        if (e.local) Text("직접 입력", color = C.Accent, fontSize = 10.sp)
                    }
                    Text(
                        if (busy[e.id] == true) "작성 중" else if (e.draft.isBlank()) "미작성" else if (w?.read == true) "읽음" else "새 글",
                        color = C.Ink2, fontSize = 11.sp,
                    )
                }
                if (open) {
                    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        if (e.draft.isNotBlank()) {
                            Text(
                                e.draft, color = C.Ink, fontSize = 15.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.Ivory).padding(12.dp),
                            )
                            val cnt = e.draft.filter { !it.isWhitespace() }.length
                            Text(
                                "${cnt}자" + (if (w?.edited == true) " · 직접 수정함" else "") + (if (e.stale) " · 메모가 바뀌었어요" else ""),
                                color = if (e.stale) C.Accent else C.Ink2, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        } else if (busy[e.id] != true) {
                            Text("아직 글이 없어요. 아래 '글쓰기'를 눌러 보세요.", color = C.Ink2, fontSize = 13.sp)
                        }
                        errs[e.id]?.let { Text(it, color = C.Bad, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        if (busy[e.id] == true) Text("AI가 쓰는 중…", color = C.Ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                                        w?.let { Drafts.put(ctx, Draft(it.id, it.kind, it.title, it.body, it.draft, it.srcHash, !it.read, it.edited, it.created)); rev++ }
                                    }
                                    WBtn("수정") { editing = e }
                                    WBtn("복사") { copy(e.draft) }
                                    WBtn("공유") { share(e.draft) }
                                }
                                if (e.local) WBtn("삭제") { Drafts.remove(ctx, e.id); if (openId == e.id) openId = ""; rev++ }
                            }
                            WBtn(if (e.draft.isBlank()) "글쓰기" else "다시 쓰기", primary = true, enabled = busy[e.id] != true && !running) { runOne(e) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("모두 쓰기") },
        text = {
            Text("아직 작성하지 않은 글 ${pending}건을 AI가 차례로 모두 작성합니다. AI 호출이 ${pending}회 사용돼요(오늘 ${Ai.todayCount(ctx)}회 사용, 하루 한도 ${Ai.dayLimit(ctx)}회). 진행할까요?")
        },
        confirmButton = { TextButton({ confirmAll = false; runAll() }) { Text("모두 쓰기") } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("취소") } },
    )

    editing?.let { e -> Editor(e, { editing = null }) { txt -> saveDraft(ctx, e, txt, true); editing = null; rev++; toast("저장했어요") } }
}

@Composable
private fun Editor(e: Ent, onClose: () -> Unit, onSave: (String) -> Unit) {
    val initial = e.draft
    var text by remember { mutableStateOf(initial) }
    var askClose by remember { mutableStateOf(false) }
    fun tryClose() { if (text != initial) askClose = true else onClose() }
    Dialog(
        onDismissRequest = { tryClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(C.Ivory).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ tryClose() }) { Text("취소") }
                Text(e.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center)
                Button({ onSave(text.trim()) }, enabled = text != initial && text.isNotBlank()) { Text("저장") }
            }
            HorizontalDivider()
            BasicTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                textStyle = TextStyle(color = C.Ink, fontSize = 17.sp, lineHeight = 30.sp, fontFamily = FontFamily.Serif),
                cursorBrush = SolidColor(C.Wood),
            )
            HorizontalDivider()
            Text("${text.filter { !it.isWhitespace() }.length}자", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = C.Ink2)
        }
    }
    if (askClose) AlertDialog(
        onDismissRequest = { askClose = false },
        title = { Text("저장하지 않고 닫을까요?") },
        confirmButton = { TextButton({ askClose = false; onClose() }) { Text("저장 안 함") } },
        dismissButton = { TextButton({ askClose = false }) { Text("계속 편집") } },
    )
}

@Composable
private fun SettingsDialog(folder: String, onClose: () -> Unit, onPickFolder: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Ai.key(ctx)) }
    var keyIn by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(Ai.model(ctx)) }
    var limit by remember { mutableStateOf(Pref.get(ctx, "ai_limit", "30")) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(C.Ivory).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClose) { Text("✕ 닫기") }
                Text("설정", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.width(72.dp))
            }
            HorizontalDivider()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("메모 데이터 폴더", fontWeight = FontWeight.Bold, color = C.Ink)
                Text(if (folder.isEmpty()) "아직 선택하지 않았어요" else "사용 중: $folder", color = C.Ink2, fontSize = 13.sp)
                WBtn(if (folder.isEmpty()) "폴더 선택" else "폴더 바꾸기") { onPickFolder() }
                HorizontalDivider()
                Text("AI (내 API 키)", fontWeight = FontWeight.Bold, color = C.Ink)
                Text(
                    "내 Anthropic API 키를 이 폰에만 저장해서 직접 호출해요. 사용한 만큼 키 계정에 요금이 나가므로 아래 하루 한도로 제한해요.",
                    color = C.Ink2, fontSize = 12.sp, lineHeight = 18.sp,
                )
                Text(if (key.isBlank()) "키가 저장되어 있지 않아요" else "키 저장됨 (…${key.takeLast(4)})", color = C.Ink, fontSize = 13.sp)
                OutlinedTextField(
                    value = keyIn, onValueChange = { keyIn = it }, label = { Text("API 키 붙여넣기") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WBtn("키 저장", primary = true, enabled = keyIn.isNotBlank()) { val k = keyIn.trim(); Pref.set(ctx, "ai_key", k); key = k; keyIn = "" }
                    WBtn("키 지우기", enabled = key.isNotBlank()) { Pref.set(ctx, "ai_key", ""); key = "" }
                }
                Text("모델", fontWeight = FontWeight.SemiBold, color = C.Ink, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("claude-haiku-5-5" to "Haiku (저렴)", "claude-sonnet-5-5" to "Sonnet (똑똑)").forEach { (v, l) ->
                        WBtn(l, primary = model == v) { model = v; Pref.set(ctx, "ai_model", v) }
                    }
                }
                Text("하루 호출 한도", fontWeight = FontWeight.SemiBold, color = C.Ink, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("10" to "10회", "30" to "30회", "100" to "100회").forEach { (v, l) ->
                        WBtn(l, primary = limit == v) { limit = v; Pref.set(ctx, "ai_limit", v) }
                    }
                }
                Text("오늘 ${Ai.todayCount(ctx)}회 사용", color = C.Ink2, fontSize = 12.sp)
            }
        }
    }
}

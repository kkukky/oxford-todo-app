package com.donggun.oxfordtodo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pal(
    val bg: Color, val ink: Color, val pen: Color, val sub: Color, val rule: Color, val margin: Color,
    val panel: Color, val bar: Color, val barInk: Color, val accent: Color, val gold: Color, val urgentBg: Color,
)

val LightPal = Pal(
    bg = Color(0xFFFBF6E0), ink = Color(0xFF1F3A5F), pen = Color(0xFF1F3A8A), sub = Color(0xFF8A8A8A),
    rule = Color(0xFF9FB5D6), margin = Color(0xFFE05A5A), panel = Color(0xFFF1EBD3),
    bar = Color(0xFF1F3A5F), barInk = Color.White, accent = Color(0xFFD9822B), gold = Color(0xFFD99A1E), urgentBg = Color(0xFFD9534F),
)
val DarkPal = Pal(
    bg = Color.Black, ink = Color(0xFFA8A8A8), pen = Color(0xFFA8A8A8), sub = Color(0xFF707070),
    rule = Color(0xFF2A2A2A), margin = Color(0xFF3B2323), panel = Color(0xFF0A0A0A),
    bar = Color.Black, barInk = Color(0xFFA8A8A8), accent = Color(0xFFA8A8A8), gold = Color(0xFFA8A8A8), urgentBg = Color(0xFF5A2A2A),
)
val LocalPal = staticCompositionLocalOf { LightPal }

val Gaegu: FontFamily get() = FontPref.family

/** 어절이 아니라 글자 단위로 줄바꿈: 글자 사이마다 폭 없는 공백(U+200B)을 끼워 넣는다 */
private fun brk(t: String): String {
    val sb = StringBuilder()
    t.forEachIndexed { i, c -> sb.append(c); if (!Character.isHighSurrogate(c)) sb.append('\u200B') }
    return sb.toString()
}
private val TAG_RE = Regex("#[^\\s#]+")
private val TAG_COLOR = Color(0xFF1F7A74)
private fun cleanTag(t: String) = t.trimEnd('.', ',', '!', '?', ';', ':', ')', ']').lowercase()
private fun tagsOf(s: String): List<String> = TAG_RE.findAll(s).map { cleanTag(it.value) }.filter { it.length > 1 }.toList()
/** 글자 단위 줄바꿈 + #키워드는 색을 입혀 보여준다 */
private fun tagged(text: String, tagColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    TAG_RE.findAll(text).forEach { m ->
        if (m.range.first > last) append(brk(text.substring(last, m.range.first)))
        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold)) { append(brk(m.value)) }
        last = m.range.last + 1
    }
    if (last < text.length) append(brk(text.substring(last)))
}
private val CharBreak = androidx.compose.ui.text.input.VisualTransformation { t ->
    val sb = StringBuilder(); val o2t = IntArray(t.text.length + 1); val t2o = ArrayList<Int>()
    t.text.forEachIndexed { i, c ->
        o2t[i] = sb.length; t2o.add(i); sb.append(c)
        if (!Character.isHighSurrogate(c)) { sb.append('\u200B'); t2o.add(i + 1) }
    }
    o2t[t.text.length] = sb.length; t2o.add(t.text.length)
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(sb.toString()),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, t.text.length)]
            override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, t2o.size - 1)]
        },
    )
}
private val INPUT_LH = 24.sp
@Composable private fun tagColor() = if (LocalPal.current === DarkPal) Color(0xFF6FC2BD) else TAG_COLOR
private val ROW = 50.dp
private val MARGIN = 50.dp
private val TS = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
private fun fmt(ts: Long) = TS.format(Date(ts))
private val LI = mapOf("start" to "●", "memo" to "✎", "step" to "✓", "undo" to "↺", "done" to "✔", "reopen" to "↺", "move" to "⇄")

private fun rank(i: Item) = (if (i.done) 4 else 0) + (if (i.urgent) 0 else 2) + (if (i.star) 0 else 1)

fun visibleItems(db: Db, bucket: String, filter: String): List<Item> {
    val base = db.list(bucket).filter {
        when (filter) {
            "left" -> !it.done
            "urgent" -> it.urgent && !it.done
            "star" -> it.star
            "done" -> it.done
            else -> true
        }
    }
    return base.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
}

/** 노트지 한 줄: 아래 괘선(rows>1이면 줄마다) + 왼쪽 빨간 이중 여백선 */
@Composable
private fun Modifier.ruled(rows: Int = 1): Modifier {
    val p = LocalPal.current
    return this.drawBehind {
        val m = MARGIN.toPx()
        drawLine(p.margin, Offset(m, 0f), Offset(m, size.height), 1.5f)
        drawLine(p.margin, Offset(m + 4.dp.toPx(), 0f), Offset(m + 4.dp.toPx(), size.height), 1f)
        for (k in 1..rows) {
            val y = if (k == rows) size.height - 1f else ROW.toPx() * k
            drawLine(p.rule, Offset(0f, y), Offset(size.width, y), 1f)
        }
    }
}

private class DeleteReq(val ids: List<String>, val title: String, val stepCount: Int)

@Composable
fun NotePadApp(lock: Boolean = false, onOpenApp: () -> Unit = {}, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var dark by remember { mutableStateOf(db.getSetting("dark", "0") == "1") }
    var showSettings by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var tickLocal by remember { mutableIntStateOf(0) }
    val tick = tickLocal + SyncState.rev.intValue
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var pendingDelete by remember { mutableStateOf<DeleteReq?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { if (lock) 1 else 4 }
    val fm = LocalFocusManager.current
    remember { FontPref.load(ctx) }
    LaunchedEffect(pager.currentPage) { fm.clearFocus() }
    val pal = if (dark) DarkPal else LightPal
    val titles = listOf("지금 할 일", "나중 할 일", "보관함", "AI 분석")

    val lifeOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifeOwner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME || e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) SyncState.sync(ctx)
        }
        lifeOwner.lifecycle.addObserver(ob); onDispose { lifeOwner.lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(tickLocal) { if (tickLocal > 0) { kotlinx.coroutines.delay(2500); SyncState.sync(ctx) } }

    BackHandler(enabled = showSettings || searching) { if (searching) searching = false else showSettings = false }

    fun askDelete(list: List<Item>, title: String) {
        if (list.isEmpty()) return
        pendingDelete = DeleteReq(list.map { it.id }, title, list.sumOf { db.steps(it.id).size })
    }

    CompositionLocalProvider(LocalPal provides pal) {
        Column(Modifier.fillMaxSize().background(pal.bg)) {
            Band(dark, { dark = !dark; db.setSetting("dark", if (dark) "1" else "0") }, { fm.clearFocus(); showSettings = !showSettings }, lock, onOpenApp, onClose, searching) { fm.clearFocus(); searching = !searching; showSettings = false }
            val page = pager.currentPage
            val listPage = !showSettings && page < 2
            val bucket = if (page == 1) "later" else "now"
            val foldList = if (listPage) remember(tick, bucket, filter) { visibleItems(db, bucket, filter) } else emptyList()
            val anyOpen = foldList.any { open[it.id] == true }
            if (searching && !lock) {
                SearchPane(tick, Modifier.weight(1f)) { pg, id ->
                    searching = false
                    if (pg < 2) open[id] = true
                    scope.launch { pager.scrollToPage(pg) }
                }
            } else {
                HeadRow(
                    title = if (showSettings) "설정" else titles[page], showFilters = listPage, filter = filter, onFilter = { filter = it },
                    showFold = listPage, anyOpen = anyOpen, onFold = { foldList.forEach { open[it.id] = !anyOpen } },
                )
                if (showSettings) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) { SettingsPane() }
                } else {
                    HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !lock) { p ->
                        when (p) {
                            0 -> ListPage("now", filter, tick, open, { tickLocal++ }) { askDelete(it, it[0].title) }
                            1 -> ListPage("later", filter, tick, open, { tickLocal++ }) { askDelete(it, it[0].title) }
                            2 -> ArchivePage(tick) { tickLocal++ }
                            else -> Placeholder("AI 분석 — 3단계에서 만듭니다")
                        }
                    }
                    val (total, done) = remember(tick) { db.counts() }
                    val arcN = remember(tick) { db.archived().size }
                    Footer(page, total, done, arcN, if (lock) 0 else 4) {
                        val doneItems = (db.list("now") + db.list("later")).filter { it.done }
                        askDelete(doneItems, "완료한 ${doneItems.size}개")
                    }
                }
            }
        }
        pendingDelete?.let { req ->
            val close = { pendingDelete = null }
            AlertDialog(
                onDismissRequest = close,
                containerColor = pal.bg,
                title = { Text("삭제하기 전에", color = pal.ink, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "‘${req.title}’을(를) 지웁니다. 적어 둔 절차" + (if (req.stepCount > 0) " ${req.stepCount}개" else "") +
                            "와 함께 기록으로 남겨둘까요? 남겨두면 보관함에서 다시 볼 수 있어요.",
                        color = pal.ink, fontSize = 14.sp,
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton({ req.ids.forEach { db.archive(it) }; close(); tickLocal++ }) { Text("기록 저장하고 삭제", color = pal.ink, fontWeight = FontWeight.Bold) }
                        TextButton({ req.ids.forEach { db.deleteItem(it) }; close(); tickLocal++ }) { Text("저장하지 않고 삭제", color = pal.urgentBg) }
                        TextButton(close) { Text("취소", color = pal.sub) }
                    }
                },
            )
        }
    }
}

@Composable
private fun Band(dark: Boolean, onDark: () -> Unit, onSettings: () -> Unit, lock: Boolean = false, onOpenApp: () -> Unit = {}, onClose: () -> Unit = {}, searchOn: Boolean = false, onSearch: () -> Unit = {}) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().background(pal.bar).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("OXFORD", color = pal.barInk, fontSize = 15.6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.28.em)
            Text("TO DO NOTEPAD", color = pal.barInk.copy(alpha = 0.7f), fontSize = 8.sp, letterSpacing = 0.12.em)
        }
        Spacer(Modifier.weight(1f))
        if (lock) {
            Text("앱 열기", color = pal.barInk, fontSize = 12.sp, modifier = Modifier.clickable { onOpenApp() }.padding(horizontal = 8.dp, vertical = 8.dp))
            Text("닫기", color = pal.barInk, fontSize = 12.sp, modifier = Modifier.clickable { onClose() }.padding(horizontal = 8.dp, vertical = 8.dp))
        } else Text("⚙", color = pal.barInk, fontSize = 18.sp, modifier = Modifier.clickable { onSettings() }.padding(horizontal = 8.dp, vertical = 8.dp))
        if (!lock) Box(Modifier.size(36.dp).clickable { onSearch() }, Alignment.Center) { SearchIcon(pal.barInk.copy(alpha = if (searchOn) 0.6f else 1f)) }
        Box(Modifier.size(32.dp).clickable { onDark() }, Alignment.Center) {
            Box(Modifier.size(15.dp).clip(CircleShape).background(if (dark) pal.barInk else Color.Transparent).border(1.8.dp, pal.barInk, CircleShape))
        }
    }
}

@Composable
private fun SearchIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        drawCircle(color, radius = w * 0.31f, center = Offset(w * 0.43f, w * 0.43f), style = Stroke(width = 2.dp.toPx()))
        drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.92f, w * 0.92f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

private class Memo(val id: String, val title: String, val desc: String, val steps: List<String>, val where: String, val page: Int) {
    val text = (listOf(title, desc) + steps).joinToString(" ")
}

/** 돋보기 화면: #키워드 버튼 + 검색어로 메모 찾기 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPane(tick: Int, modifier: Modifier, onPick: (Int, String) -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var q by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    val memos = remember(tick) {
        val a = (db.list("now") + db.list("later")).map { Memo(it.id, it.title, it.description, db.steps(it.id).map { s -> s.title }, if (it.bucket == "now") "지금 할 일" else "나중 할 일", if (it.bucket == "now") 0 else 1) }
        val b = db.archived().map { Memo(it.id, it.title, it.description, db.steps(it.id).map { s -> s.title }, "보관함", 2) }
        a + b
    }
    val counts = remember(memos) {
        val m = LinkedHashMap<String, Int>()
        memos.forEach { mm -> tagsOf(mm.text).toSet().forEach { t -> m[t] = (m[t] ?: 0) + 1 } }
        m.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    val toks = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val hits = if (tag == null && toks.isEmpty()) emptyList() else memos.filter { m ->
        val tx = m.text.lowercase()
        (tag == null || tagsOf(tx).contains(tag)) && toks.all { tx.contains(it) }
    }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(50)).border(1.dp, pal.rule, RoundedCornerShape(50)).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(pal.sub)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.weight(1f).height(42.dp).focusRequester(fr), singleLine = true,
                textStyle = TextStyle(color = pal.pen, fontSize = 17.sp, fontFamily = Gaegu), cursorBrush = SolidColor(pal.pen),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), Alignment.CenterStart) { if (q.isEmpty()) Text("메모 검색 · #키워드", color = pal.sub, fontSize = 17.sp, fontFamily = Gaegu); inner() } },
            )
            if (q.isNotEmpty() || tag != null) Text("✕", color = pal.sub, fontSize = 14.sp, modifier = Modifier.clickable { q = ""; tag = null }.padding(10.dp))
        }
        FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.forEach { (t, n) ->
                val on = tag == t
                Text(
                    "$t  $n", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) pal.bg else pal.ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) pal.ink else Color.Transparent)
                        .border(1.dp, pal.rule, RoundedCornerShape(50)).clickable { tag = if (on) null else t }.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        if (tag == null && toks.isEmpty()) {
            Text(if (counts.isEmpty()) "메모에 #키워드를 적어 두면 여기에 모여요" else "키워드를 누르거나 검색어를 적어 보세요", color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
        } else if (hits.isEmpty()) {
            Text("찾은 메모가 없어요", color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
        } else LazyColumn(Modifier.weight(1f)) {
            items(hits, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().clickable { onPick(m.page, m.id) }.drawBehind {
                    drawLine(pal.rule, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                    Text(tagged(m.title, tagColor()), color = pal.pen, fontSize = 18.sp, fontFamily = Gaegu, lineHeight = 24.sp)
                    Text(m.where + (if (m.desc.isNotEmpty()) " · ${m.desc}" else "") + (if (m.steps.isNotEmpty()) " · 절차 ${m.steps.size}" else ""), color = pal.sub, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HeadRow(
    title: String, showFilters: Boolean, filter: String, onFilter: (String) -> Unit,
    showFold: Boolean, anyOpen: Boolean, onFold: () -> Unit,
) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = pal.ink, fontSize = 28.5.sp, fontWeight = FontWeight.Bold, fontFamily = Gaegu, maxLines = 1)
        if (showFilters) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("all" to "전체", "left" to "남은 일", "urgent" to "긴급", "star" to "중요", "done" to "완료").forEach { (k, l) ->
                    val on = filter == k
                    Text(
                        l, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (on) pal.bg else pal.ink, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) pal.ink else Color.Transparent)
                            .border(1.dp, pal.rule, RoundedCornerShape(50)).clickable { onFilter(k) }.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        } else Spacer(Modifier.weight(1f))
        if (showFold) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.2.dp, pal.rule, CircleShape).clickable { onFold() },
                Alignment.Center,
            ) { Text(if (anyOpen) "▴▴" else "▾▾", color = pal.ink, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun Footer(page: Int, total: Int, done: Int, archived: Int, dots: Int, onClear: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxWidth().background(pal.panel).navigationBarsPadding().height(52.dp)) {
        Text(
            if (page >= 2) (if (page == 2) (if (archived > 0) "보관 ${archived}개" else "보관한 기록 없음") else "AI 분석")
            else if (total > 0) "전체 ${total}개 중 ${done}개 완료" else "할 일을 적어 보세요",
            color = pal.sub, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
        )
        Row(Modifier.align(Alignment.Center)) {
            repeat(dots) { i ->
                Box(
                    Modifier.padding(4.dp).size(10.dp).clip(CircleShape)
                        .then(if (i == page) Modifier.background(pal.ink) else Modifier.border(1.8.dp, pal.rule, CircleShape))
                )
            }
        }
        if (page < 2) {
            Text(
                "완료 항목 지우기", color = pal.ink, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).clip(RoundedCornerShape(6.dp))
                    .border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable { onClear() }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Placeholder(msg: String) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(msg, color = pal.sub, fontSize = 14.sp) }
}

/** 눌러서 쓰면 5줄 높이로 커지는 입력칸. Enter를 누르면 onSubmit */
/** 입력창에서 저장하지 않고 벗어났을 때 저장할지 묻는다 */
@Composable
private fun SaveAsk(text: String, onSave: () -> Unit, onDiscard: () -> Unit, onBack: () -> Unit) {
    val short = text.replace(Regex("\\s+"), " ").trim().let { if (it.length > 24) it.take(24) + "…" else it }
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("저장할까요?") },
        text = { Text("‘$short’ 내용을 저장하지 않고 벗어났어요.") },
        confirmButton = {
            Row {
                TextButton(onClick = onBack) { Text("계속 쓰기") }
                TextButton(onClick = onDiscard) { Text("저장 안 함") }
                TextButton(onClick = onSave) { Text("저장") }
            }
        },
    )
}

@Composable
private fun NoteInput(
    value: String, onValue: (String) -> Unit, hint: String, maxLen: Int, onSubmit: () -> Unit,
    modifier: Modifier = Modifier, fontSize: Float = 17f, onFocus: (Boolean) -> Unit = {}, fit: Boolean = false,
    enterSubmits: Boolean = false, focusRequester: FocusRequester? = null,
) {
    val pal = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    var lines by remember { mutableIntStateOf(1) }
    // fit: 입력을 마친 뒤에도 여러 줄을 노트줄 배수 높이로 전부 보여준다
    val multi = fit && !focused && lines > 1
    val fitRows = kotlin.math.ceil((lines * 24 + (ROW.value - 24)) / ROW.value).toInt().coerceAtLeast(1)
    BasicTextField(
        value = value,
        onValueChange = { v -> if (enterSubmits && v.contains('\n')) onSubmit() else onValue(v.take(maxLen)) },
        modifier = modifier.height(if (focused) ROW * 5 else if (multi) ROW * fitRows else ROW)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        textStyle = TextStyle(color = pal.pen, fontSize = fontSize.sp, fontFamily = Gaegu, lineHeight = INPUT_LH),
        cursorBrush = SolidColor(pal.pen),
        visualTransformation = CharBreak,
        onTextLayout = { lines = it.lineCount },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(top = if (focused || multi) 13.dp else 0.dp), if (focused || multi) Alignment.TopStart else Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = pal.sub.copy(alpha = 0.8f), fontSize = fontSize.sp, fontFamily = Gaegu, lineHeight = INPUT_LH)
                inner()
            }
        },
    )
}

@Composable
fun ListPage(
    bucket: String, filter: String, tick: Int, open: SnapshotStateMap<String, Boolean>,
    onChange: () -> Unit, onDelete: (List<Item>) -> Unit,
) {
    val db = Db.get(LocalContext.current)
    val order = remember(tick, bucket, filter) { mutableStateListOf<Item>().apply { addAll(visibleItems(db, bucket, filter)) } }
    val listState = rememberLazyListState()
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }

    fun dragMove(dy: Float) {
        val id = dragId ?: return
        dragDy += dy
        val idx = order.indexOfFirst { it.id == id }; if (idx < 0) return
        val infos = listState.layoutInfo.visibleItemsInfo
        val me = infos.firstOrNull { it.key == id } ?: return
        val center = me.offset + dragDy + me.size / 2f
        val next = order.getOrNull(idx + 1)?.takeIf { rank(it) == rank(order[idx]) }
        val prev = order.getOrNull(idx - 1)?.takeIf { rank(it) == rank(order[idx]) }
        val ni = next?.let { n -> infos.firstOrNull { it.key == n.id } }
        val pi = prev?.let { p -> infos.firstOrNull { it.key == p.id } }
        if (ni != null && center > ni.offset + ni.size / 2f) { order.add(idx + 1, order.removeAt(idx)); dragDy -= ni.size }
        else if (pi != null && center < pi.offset + pi.size / 2f) { order.add(idx - 1, order.removeAt(idx)); dragDy += pi.size }
    }
    fun dragEnd(save: Boolean) {
        if (save && dragId != null) db.reorderWithin(order.map { it.id })
        dragId = null; dragDy = 0f; onChange()
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), state = listState) {
        if (order.isEmpty()) item { Box(Modifier.fillMaxWidth().height(ROW).ruled(), Alignment.CenterStart) { Text(if (filter == "all" || filter == "left") "비어 있어요" else "해당하는 일이 없어요", color = LocalPal.current.sub, fontSize = 13.sp, modifier = Modifier.padding(start = MARGIN + 12.dp)) } }
        items(order, key = { it.id }) { r ->
            val dragging = dragId == r.id
            Box(if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragDy } else Modifier.animateItem()) {
                ItemBlock(
                    r, bucket, open[r.id] == true, dragging,
                    onToggle = { open[r.id] = !(open[r.id] ?: false) }, onChange = onChange, onDelete = { onDelete(listOf(r)) },
                    onDragStart = { dragId = r.id; dragDy = 0f }, onDrag = { dragMove(it) }, onDragEnd = { dragEnd(true) }, onDragCancel = { dragEnd(false) },
                )
            }
        }
        item { AddRow(bucket, onChange) }
        items(10) { Box(Modifier.fillMaxWidth().height(ROW).ruled()) }
    }
}

@Composable
private fun ItemBlock(
    item: Item, bucket: String, open: Boolean, dragging: Boolean,
    onToggle: () -> Unit, onChange: () -> Unit, onDelete: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    val fm = LocalFocusManager.current
    var editing by remember(item.id) { mutableStateOf(false) }
    var lines by remember(item.id, item.title) { mutableIntStateOf(1) }
    val dStart by rememberUpdatedState(onDragStart); val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDragEnd); val dCancel by rememberUpdatedState(onDragCancel)
    val rows = maxOf(1, Math.ceil(((lines * 24 + 6) / 50.0)).toInt())

    Column(Modifier.fillMaxWidth().then(if (dragging) Modifier.background(pal.bg) else Modifier)) {
        if (editing) {
            TitleEditor(item, bucket, onChange, onDone = { editing = false; onChange() })
        } else Row(
            Modifier.fillMaxWidth().height(ROW * rows).ruled().clickable(onClick = { fm.clearFocus(); onToggle() })
                .pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dStart() }, onDrag = { c, a -> c.consume(); dMove(a.y) },
                        onDragEnd = { dEnd() }, onDragCancel = { dCancel() },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(MARGIN), Alignment.Center) {
                if (bucket == "now") {
                    Checkbox(
                        checked = item.done, onCheckedChange = { db.toggleDone(item.id, it); onChange() },
                        colors = CheckboxDefaults.colors(checkedColor = pal.margin, uncheckedColor = pal.pen, checkmarkColor = pal.bg),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("▶", color = if (open) pal.ink else pal.sub, fontSize = 10.sp, modifier = Modifier.rotate(if (open) 90f else 0f).width(14.dp))
            if (item.urgent || item.star) {
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.urgent) Text(
                        "급", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(-3f).clip(RoundedCornerShape(3.dp)).background(pal.urgentBg).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (item.star) Text("★", color = pal.gold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(
                tagged(item.title, if (item.done) pal.sub else tagColor()), color = if (item.done) pal.sub else pal.pen, fontSize = 18.sp, lineHeight = 24.sp, fontFamily = Gaegu,
                fontWeight = if (item.urgent && !item.done) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                onTextLayout = { lines = it.lineCount }, modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
            if (item.stepsTotal > 0) Text(
                "${item.stepsDone}/${item.stepsTotal}", color = pal.ink, fontSize = 10.sp,
                modifier = Modifier.padding(end = 2.dp).border(1.dp, pal.rule, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
            )
            Box(Modifier.size(width = 44.dp, height = ROW).clickable { editing = true }, Alignment.Center) {
                Text("•••", color = pal.sub, fontSize = 12.sp, letterSpacing = 0.sp)
            }
        }
        if (open) Panel(item, bucket, onChange, onDelete)
    }
}

/** ⋯ 를 누르면 열리는 제목 편집칸: 4줄 높이, 400자, 커서는 맨 뒤(스페이스 한 칸 뒤) */
@Composable
private fun TitleEditor(item: Item, bucket: String, onChange: () -> Unit, onDone: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    val start = item.title + " "
    var tf by remember(item.id) { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val fr = remember { FocusRequester() }
    var finished by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var back by remember { mutableIntStateOf(0) }
    val win = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(back) { if (back > 0) { kotlinx.coroutines.delay(150); fr.requestFocus() } }
    fun finish() { if (finished) return; finished = true; db.setTitle(item.id, tf.text); onDone() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = tf,
            onValueChange = { v -> tf = v.copy(text = v.text.take(400)) },
            modifier = Modifier.fillMaxWidth().height(ROW * 4).background(pal.panel).ruled()
                .padding(start = MARGIN + 10.dp, end = 10.dp, top = 13.dp)
                .focusRequester(fr).onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus && !finished && !ask && win.isWindowFocused) {
                        val v = tf.text.trim()
                        if (v.isEmpty() || v == item.title) finish() else ask = true
                    }
                },
            textStyle = TextStyle(color = pal.pen, fontSize = 18.sp, fontFamily = Gaegu, lineHeight = INPUT_LH),
            cursorBrush = SolidColor(pal.pen),
            visualTransformation = CharBreak,
        )
        // 아래 오른쪽: 중요 · 긴급 · 옮기기 · 마침
        Row(Modifier.fillMaxWidth().height(ROW).background(pal.panel).ruled(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            IconBtn(if (item.star) "★" else "☆", item.star) { db.setFlag(item.id, "star", !item.star); onChange() }
            IconBtn("!", item.urgent) { db.setFlag(item.id, "urgent", !item.urgent); onChange() }
            IconBtn(if (bucket == "now") "↓" else "↑", false) {
                finish(); db.move(item.id, if (bucket == "now") "later" else "now"); onChange()
            }
            Box(Modifier.size(40.dp).clickable { finish() }, Alignment.Center) {
                Text("✓", color = pal.pen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }
        if (ask) SaveAsk(tf.text, onSave = { ask = false; finish() }, onDiscard = { ask = false; if (!finished) { finished = true; onDone() } }, onBack = { ask = false; back++ })
    }
}

@Composable
private fun Panel(item: Item, bucket: String, onChange: () -> Unit, onDelete: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var desc by remember(item.id) { mutableStateOf(item.description) }
    var savedDesc by remember(item.id) { mutableStateOf(item.description) }
    var descFocused by remember(item.id) { mutableStateOf(false) }
    var askDesc by remember(item.id) { mutableStateOf(false) }
    var descRefocus by remember(item.id) { mutableIntStateOf(0) }
    val descFr = remember(item.id) { FocusRequester() }
    val win = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(descRefocus) { if (descRefocus > 0) { kotlinx.coroutines.delay(150); descFr.requestFocus() } }
    // 패널이 닫힐 때 저장 안 된 설명이 남아 있으면 그대로 저장해 둔다
    val descNow by rememberUpdatedState(desc); val savedNow by rememberUpdatedState(savedDesc)
    DisposableEffect(item.id) { onDispose { if (descNow != savedNow) db.setDesc(item.id, descNow) } }
    var v by remember(item.id) { mutableIntStateOf(0) }
    var logOpen by remember(item.id) { mutableStateOf(false) }
    var newStep by remember(item.id) { mutableStateOf("") }
    val steps = remember(item.id, v) { db.steps(item.id) }
    val logs = remember(item.id, v, item.done) { db.logs(item.id).reversed() }
    val focus = LocalFocusManager.current
    fun refresh() { v++; onChange() }
    val nextId = steps.firstOrNull { !it.done }?.id
    val stepList = remember(item.id, v, steps) { mutableStateListOf<Step>().apply { addAll(steps) } }
    var sDrag by remember(item.id) { mutableStateOf<String?>(null) }
    var sDy by remember(item.id) { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW.toPx() }

    Column(Modifier.fillMaxWidth().background(pal.panel)) {
        // 설명 + ☆ ! ↓
        Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text("설명", color = pal.sub, fontSize = 10.sp) }
            Spacer(Modifier.width(8.dp))
            NoteInput(
                desc, { desc = it }, "설명 적기", 120, {}, Modifier.weight(1f).padding(end = 6.dp), 17f,
                onFocus = { f -> if (!f && descFocused && win.isWindowFocused && desc != savedDesc && !askDesc) askDesc = true; descFocused = f },
                fit = true, focusRequester = descFr,
            )
            if (descFocused) Box(Modifier.size(width = 40.dp, height = ROW).clickable { db.setDesc(item.id, desc); savedDesc = desc; focus.clearFocus() }, Alignment.Center) {
                Text("✓", color = pal.pen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (askDesc) SaveAsk(desc, onSave = { askDesc = false; db.setDesc(item.id, desc); savedDesc = desc }, onDiscard = { askDesc = false; desc = savedDesc }, onBack = { askDesc = false; descRefocus++ })
        // 로그 (자동 기록, 읽기 전용)
        Row(Modifier.fillMaxWidth().height(ROW).ruled().clickable { logOpen = !logOpen }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MARGIN), Alignment.Center) { Text("로그", color = pal.sub, fontSize = 10.sp) }
            Spacer(Modifier.width(10.dp))
            Text("▶", color = pal.sub, fontSize = 10.sp, modifier = Modifier.rotate(if (logOpen) 90f else 0f).width(14.dp))
            if (logOpen) {
                Text("${logs.size}건 · 눌러서 접기", color = pal.sub, fontSize = 11.sp)
            } else if (logs.isNotEmpty()) {
                val l = logs.first()
                Text(fmt(l.ts), color = pal.sub, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                Text((LI[l.type] ?: "·") + " " + l.text, color = pal.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (logs.size > 1) Text("  외 ${logs.size - 1}건", color = pal.sub, fontSize = 10.sp)
            } else Text("기록 없음", color = pal.sub, fontSize = 12.sp)
        }
        if (logOpen) logs.forEach { l ->
            Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(MARGIN + 24.dp))
                Text(fmt(l.ts), color = pal.sub, fontSize = 10.sp, modifier = Modifier.width(72.dp))
                Text((LI[l.type] ?: "·") + " " + l.text, color = pal.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 절차 (길게 눌러 순서 변경)
        stepList.forEachIndexed { i, s ->
            key(s.id) {
                Row(
                    Modifier.fillMaxWidth().height(ROW)
                        .then(if (sDrag == s.id) Modifier.zIndex(1f).graphicsLayer { translationY = sDy }.background(pal.panel) else Modifier)
                        .ruled()
                        .pointerInput(s.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { sDrag = s.id; sDy = 0f },
                                onDrag = { c, a ->
                                    c.consume(); sDy += a.y
                                    val idx = stepList.indexOfFirst { it.id == s.id }
                                    if (sDy > rowPx / 2 && idx >= 0 && idx < stepList.lastIndex) { stepList.add(idx + 1, stepList.removeAt(idx)); sDy -= rowPx }
                                    else if (sDy < -rowPx / 2 && idx > 0) { stepList.add(idx - 1, stepList.removeAt(idx)); sDy += rowPx }
                                },
                                onDragEnd = { db.reorderSteps(stepList.map { it.id }); sDrag = null; sDy = 0f; refresh() },
                                onDragCancel = { sDrag = null; sDy = 0f },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(MARGIN), Alignment.Center) {
                        Checkbox(
                            checked = s.done, onCheckedChange = { db.toggleStep(s.id, item.id, it); refresh() },
                            colors = CheckboxDefaults.colors(checkedColor = pal.margin, uncheckedColor = pal.pen, checkmarkColor = pal.bg),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${i + 1}", color = if (s.id == nextId) pal.accent else pal.sub, fontSize = 11.sp, modifier = Modifier.width(16.dp))
                    Text(
                        tagged(s.title, tagColor()), color = if (s.done) pal.sub else pal.pen, fontSize = 16.sp, fontFamily = Gaegu,
                        fontWeight = if (s.id == nextId) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.id == nextId) Text(
                        "지금 이 단계", color = pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF7E3A8)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text("✕", color = pal.sub, fontSize = 14.sp, modifier = Modifier.clickable { db.deleteStep(s.id); refresh() }.padding(12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text("+", color = pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            NoteInput(
                newStep, { newStep = it }, if (steps.isEmpty()) "첫 번째 절차를 적어 보세요" else "다음 절차 적고 Enter", 80,
                { db.addStep(item.id, newStep); newStep = ""; refresh() }, Modifier.weight(1f), 16f, enterSubmits = true,
            )
        }
        Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(MARGIN + 10.dp))
            Text(
                "삭제", color = pal.ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable { onDelete() }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.weight(1f))
            if (steps.isNotEmpty()) Text("${steps.count { it.done }} / ${steps.size} 단계 완료", color = pal.sub, fontSize = 10.sp, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun IconBtn(label: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.size(40.dp).clickable(onClick = onClick), Alignment.Center) {
        if (on && label == "!") Text(
            "!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(pal.urgentBg).padding(horizontal = 9.dp, vertical = 3.dp),
        ) else Text(label, color = if (on) pal.gold else pal.sub, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddRow(bucket: String, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var t by remember { mutableStateOf("") }
    var star by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var refocus by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    val win = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(refocus) { if (refocus > 0) { kotlinx.coroutines.delay(150); fr.requestFocus() } }
    fun submit() { if (t.isNotBlank()) { db.add(t, bucket, star, urgent); t = ""; star = false; urgent = false; onChange() } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text("+", color = pal.margin, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            NoteInput(
                t, { t = it }, if (bucket == "now") "지금 할 일 적고 ✓" else "나중에 할 일 적고 ✓", 80, { submit() },
                Modifier.weight(1f).padding(end = 10.dp), 18f, focusRequester = fr,
                onFocus = { f -> if (!f && focused && win.isWindowFocused && t.isNotBlank() && !ask) ask = true; focused = f },
            )
        }
        // 입력칸 아래, 오른쪽 정렬: ☆ ! ✓
        if (focused || t.isNotBlank() || star || urgent) {
            Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                IconBtn(if (star) "★" else "☆", star) { star = !star }
                IconBtn("!", urgent) { urgent = !urgent }
                Box(Modifier.size(40.dp).clickable { submit() }, Alignment.Center) {
                    Text("✓", color = pal.pen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        if (ask) SaveAsk(t, onSave = { ask = false; submit() }, onDiscard = { ask = false; t = ""; star = false; urgent = false }, onBack = { ask = false; refocus++ })
    }
}

@Composable
private fun ArchivePage(tick: Int, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    val rows = remember(tick) { db.archived() }
    if (rows.isEmpty()) { Placeholder("저장해 둔 기록이 아직 없어요"); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        rows.forEach { a ->
            val steps = db.steps(a.id)
            Column(Modifier.fillMaxWidth().ruled().padding(start = MARGIN + 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Text(a.title, color = pal.pen, fontSize = 18.sp, fontFamily = Gaegu, fontWeight = FontWeight.Bold)
                if (a.description.isNotEmpty()) Text("설명: ${a.description}", color = pal.sub, fontSize = 12.sp)
                val doneN = steps.count { it.done }
                Text(
                    (if (a.bucket == "now") "지금 할 일" else "나중 할 일") + (if (a.done) " · 완료함" else "") + (if (steps.isNotEmpty()) " · 절차 $doneN/${steps.size}" else ""),
                    color = pal.sub, fontSize = 12.sp,
                )
                steps.forEachIndexed { k, s -> Text((if (s.done) "✓ " else "· ") + "${k + 1}. ${s.title}", color = pal.sub, fontSize = 12.sp) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("되돌리기", color = pal.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { db.restore(a.id); onChange() }.padding(vertical = 6.dp))
                    Text("영구 삭제", color = pal.urgentBg, fontSize = 13.sp, modifier = Modifier.clickable { db.deleteItem(a.id); onChange() }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

/** 저장/동기화 상태(화면 갱신용). 폰 폴더나 구글 드라이브가 켜져 있을 때만 동작한다. */
object SyncState {
    val rev = mutableIntStateOf(0)
    val status = mutableStateOf("")
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    fun sync(ctx: android.content.Context, force: Boolean = false) {
        val db = Db.get(ctx)
        if (!Sync.enabled(db) || !busy.compareAndSet(false, true)) return
        status.value = "동기화 중…"
        Thread {
            try {
                val (up, down) = Sync.run(ctx)
                status.value = "저장 완료 (갱신 ${up}곳 · 가져온 변경 ${down}건)"
                if (down > 0 || force) rev.intValue++
            } catch (e: Exception) { status.value = "동기화 실패: " + (e.message ?: e.javaClass.simpleName) }
            finally { busy.set(false) }
        }.start()
    }
}

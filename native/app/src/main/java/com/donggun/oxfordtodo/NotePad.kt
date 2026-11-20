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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
internal val ROW: Dp get() = 50.dp * FontPref.scale
internal val MARGIN = 50.dp

/** 하단 시스템 영역(3버튼 내비게이션·제스처 영역)만큼 비워서 뒤로가기/홈 버튼과 앱 버튼이 겹치지 않게 한다 */
@androidx.compose.runtime.Composable
internal fun Modifier.navSafe(): Modifier = this.windowInsetsPadding(
    WindowInsets.navigationBars.union(WindowInsets.tappableElement).union(WindowInsets.mandatorySystemGestures)
)

/** 글자 높이(px)로 노트줄 몇 칸이 필요한지 계산 — 글자 크기·시스템 글자 배율과 상관없이 맞는다 */
@Composable
private fun rowsFor(textHeightPx: Int, padEach: Dp): Int {
    val d = LocalDensity.current
    val rowPx = with(d) { ROW.toPx() }
    val padPx = with(d) { padEach.toPx() }
    if (textHeightPx <= 0 || rowPx <= 0f) return 1
    return kotlin.math.ceil((textHeightPx + 2 * padPx) / rowPx - 0.001).toInt().coerceAtLeast(1)
}
private val TS = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
private fun fmt(ts: Long) = TS.format(Date(ts))
private val LI = mapOf("start" to "●", "memo" to "✎", "step" to "✓", "undo" to "↺", "done" to "✔", "reopen" to "↺", "move" to "⇄")

private object FlagGroup { var on = false }
private fun rank(i: Item) = (if (i.done) 4 else 0) + (if (FlagGroup.on) (if (i.urgent) 0 else 2) + (if (i.star) 0 else 1) else 0)

fun visibleItems(db: Db, bucket: String, filter: String): List<Item> {
    FlagGroup.on = db.getSetting("flag_group", "0") == "1"
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
internal fun Modifier.ruled(rows: Int = 1): Modifier {
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

/** 잠금화면 위 메모의 마지막 입력 시각 */
object Idle { @Volatile var last = android.os.SystemClock.elapsedRealtime(); fun poke() { last = android.os.SystemClock.elapsedRealtime() } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotePadApp(lock: Boolean = false, onOpenApp: () -> Unit = {}, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var dark by remember { mutableStateOf(db.getSetting("dark", "0") == "1") }
    var showSettings by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var tickLocal by remember { mutableIntStateOf(0) }
    var addReq by remember { mutableStateOf("" to 0) }
    var newSec by remember { mutableStateOf<String?>(null) }
    var showRank by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf(false) }
    var foldLevel by remember { mutableIntStateOf(0) }
    if (lock) {
        val lockSec = remember { (db.getSetting("lock_sec", "10").toIntOrNull() ?: 10).coerceIn(5, 600) }
        LaunchedEffect(Unit) {
            Idle.poke()
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (android.os.SystemClock.elapsedRealtime() - Idle.last >= lockSec * 1000L) { onClose(); break }
            }
        }
    }
    var quickPage by remember { mutableIntStateOf(0) }
    val quickSel = remember { mutableStateMapOf<String, Boolean>() }
    var flash by remember { mutableStateOf("") }
    LaunchedEffect(flash) { if (flash.isNotEmpty()) { kotlinx.coroutines.delay(2500); flash = "" } }
    val tick = tickLocal + SyncState.rev.intValue
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var pendingDelete by remember { mutableStateOf<DeleteReq?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { if (lock) 1 else 4 }
    val fm = LocalFocusManager.current
    remember { FontPref.load(ctx) }
    val baseD = LocalDensity.current
    val scaledD = Density(baseD.density, baseD.fontScale * FontPref.scale)
    val imeOn = WindowInsets.isImeVisible
    LaunchedEffect(pager.currentPage) { fm.clearFocus() }
    var anyFocus by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    // 앱을 열 때 시스템이 첫 입력칸에 멋대로 포커스를 주는 경우가 있어, 사용자가 만지기 전이면 풀어 준다
    LaunchedEffect(Unit) { for (d in longArrayOf(120, 250, 400, 600)) { kotlinx.coroutines.delay(d); if (!touched) fm.clearFocus() } }
    // 키보드를 뒤로가기로 내리면 입력 모드도 같이 빠져나온다
    var wasIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeOn) { if (wasIme && !imeOn) fm.clearFocus(); wasIme = imeOn }
    val pal = if (dark) DarkPal else LightPal
    val titles = listOf("지금 할 일", "나중 할 일", "보관함", "AI 분석")

    val lifeOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifeOwner) {
        var pausedAt = 0L
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) pausedAt = System.currentTimeMillis()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && pausedAt > 0 && System.currentTimeMillis() - pausedAt > 20_000) {
                touched = false
                scope.launch { for (d in longArrayOf(150, 300, 500)) { kotlinx.coroutines.delay(d); if (!touched) fm.clearFocus() } }
            }
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME || e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) SyncState.sync(ctx)
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) SyncWorker.once(ctx)
        }
        lifeOwner.lifecycle.addObserver(ob); onDispose { lifeOwner.lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(tickLocal) { if (tickLocal > 0) { kotlinx.coroutines.delay(2500); SyncState.sync(ctx) } }
    LaunchedEffect(Unit) { SyncState.refreshWarn(db); SyncWorker.schedule(ctx) }

    BackHandler(enabled = anyFocus) { fm.clearFocus() }
    BackHandler(enabled = quick && !anyFocus && !showSettings && !searching) { quick = false; quickSel.clear() }
    LaunchedEffect(pager.currentPage, showSettings) { if (quick && (pager.currentPage != quickPage || showSettings)) { quick = false; quickSel.clear() } }
    BackHandler(enabled = showSettings || searching) { if (searching) searching = false else showSettings = false }

    fun askDelete(list: List<Item>, title: String) {
        if (list.isEmpty()) return
        pendingDelete = DeleteReq(list.map { it.id }, title, list.sumOf { db.steps(it.id).size })
    }

    CompositionLocalProvider(LocalPal provides pal) {
        Column(
            Modifier.fillMaxSize().background(pal.bg).imePadding()
                .onFocusChanged { anyFocus = it.hasFocus }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); touched = true; Idle.poke() } } },
        ) {
            Band(dark, { dark = !dark; db.setSetting("dark", if (dark) "1" else "0") }, { fm.clearFocus(); showSettings = !showSettings }, lock, onOpenApp, onClose, searching) { fm.clearFocus(); searching = !searching; showSettings = false }
            if (!lock && !showSettings) SyncBanner { fm.clearFocus(); showSettings = true }
            val page = pager.currentPage
            val listPage = !showSettings && page < 2
            val bucket = if (page == 1) "later" else "now"
            val foldList = if (listPage) remember(tick, bucket, filter) { visibleItems(db, bucket, filter) } else emptyList()
            val anyOpen = foldList.any { open[it.id] == true }
            if (searching && !lock) {
                CompositionLocalProvider(LocalDensity provides scaledD) {
                    SearchPane(tick, Modifier.weight(1f)) { pg, id ->
                        searching = false
                        if (pg < 2) open[id] = true
                        scope.launch { pager.scrollToPage(pg) }
                    }
                }
            } else {
                HeadRow(
                    title = if (showSettings) "설정" else titles[page], showFilters = listPage, filter = filter, onFilter = { filter = it },
                    showFold = listPage, foldLevel = foldLevel,
                    onFold = {
                        foldLevel = (foldLevel + 1) % 4
                        if (foldLevel == 1) (db.list("now") + db.list("later")).forEach { open[it.id] = true }
                        else if (foldLevel >= 2) (db.list("now") + db.list("later")).forEach { open[it.id] = false }
                    },
                )
                if (showSettings) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.background).navSafe()) { SettingsPane { tickLocal++ } }
                } else {
                    CompositionLocalProvider(LocalDensity provides scaledD) {
                        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !lock) { p ->
                            when (p) {
                                0 -> ListPage("now", filter, tick, open, { tickLocal++ }, { askDelete(it, it[0].title) }, if (addReq.first == "now") addReq.second else 0, quick && quickPage == 0, quickSel, foldLevel == 3) { addReq = "" to addReq.second }
                                1 -> ListPage("later", filter, tick, open, { tickLocal++ }, { askDelete(it, it[0].title) }, if (addReq.first == "later") addReq.second else 0, quick && quickPage == 1, quickSel, foldLevel == 3) { addReq = "" to addReq.second }
                                2 -> ArchivePage(tick) { tickLocal++ }
                                else -> Placeholder("AI 분석 — 3단계에서 만듭니다")
                            }
                        }
                    }
                    val (total, done) = remember(tick) { db.counts() }
                    val arcN = remember(tick) { db.archived().size }
                    if (!imeOn) Footer(page, total, done, arcN, if (lock) 0 else 4, {
                        val doneItems = (db.list("now") + db.list("later")).filter { it.done }
                        askDelete(doneItems, "완료한 ${doneItems.size}개")
                    }, { showRank = true }, quick, quickPage, quickSel.size, flash, {
                        if (quick) { quick = false; quickSel.clear() } else { fm.clearFocus(); quick = true; quickPage = page; quickSel.clear(); flash = "" }
                    }) {
                        if (quick) {
                            val ids = quickSel.keys.toList()
                            ids.forEach { db.move(it, if (quickPage == 1) "now" else "later") }
                            quick = false; quickSel.clear()
                            if (ids.isNotEmpty()) flash = if (quickPage == 1) "${ids.size}개를 지금 할 일로 올렸어요" else "${ids.size}개를 나중 할 일로 옮겼어요"
                            tickLocal++
                        } else { fm.clearFocus(); newSec = if (page == 1) "later" else "now" }
                    }
                }
            }
        }
        if (showRank) RankDialog(db, { showRank = false }) { showRank = false; tickLocal++ }
        newSec?.let { sec -> NewEditor(sec, onDone = { newSec = null; tickLocal++ }) }
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

/** 글꼴에 화살표 글자가 없어도 보이도록 직접 그린 겹꺽쇠 (위: 접기, 아래: 펼치기) */
@Composable
private fun ChevronsIcon(up: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.width / 24f
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun chev(y: Float) {
            val p = androidx.compose.ui.graphics.Path().apply {
                if (up) { moveTo(6f * u, (y + 6f) * u); lineTo(12f * u, y * u); lineTo(18f * u, (y + 6f) * u) }
                else { moveTo(6f * u, y * u); lineTo(12f * u, (y + 6f) * u); lineTo(18f * u, y * u) }
            }
            drawPath(p, color, style = st)
        }
        if (up) { chev(5f); chev(12f) } else { chev(5f); chev(12f) }
    }
}

/** 오른쪽(→, 나중 할 일로) / 왼쪽(←, 지금 할 일로) 화살표 */
@Composable
private fun ArrowIcon(right: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val st = Stroke(width = 2.4f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val d = if (right) 1f else -1f
        val cx = 12f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo((cx - 8f * d) * u, 12f * u); lineTo((cx + 8f * d) * u, 12f * u)
            moveTo((cx + 2f * d) * u, 5f * u); lineTo((cx + 8f * d) * u, 12f * u); lineTo((cx + 2f * d) * u, 19f * u)
        }
        drawPath(p, color, style = st)
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
    Column(modifier.fillMaxWidth().navSafe().padding(horizontal = 14.dp)) {
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
    showFold: Boolean, foldLevel: Int, onFold: () -> Unit,
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
            ) { ChevronsIcon(up = foldLevel == 1 || foldLevel == 2, color = pal.ink)
                }
        }
    }
}

@Composable
private fun Footer(page: Int, total: Int, done: Int, archived: Int, dots: Int, onClear: () -> Unit, onRank: () -> Unit, quick: Boolean, quickPage: Int, selN: Int, flash: String, onQuick: () -> Unit, onAdd: () -> Unit) {
    val pal = LocalPal.current
    val btn = Modifier.size(width = 62.dp, height = 44.dp)
    val navB = with(LocalDensity.current) {
        maxOf(WindowInsets.navigationBars.getBottom(this), WindowInsets.tappableElement.getBottom(this), WindowInsets.mandatorySystemGestures.getBottom(this)).toDp()
    }
    Box(Modifier.fillMaxWidth().background(pal.panel).height(66.dp + navB).padding(bottom = navB)) {
        if (page < 2) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(btn.clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable(enabled = !quick) { onClear() }.alpha(if (quick) 0.4f else 1f), Alignment.Center) {
                    Text("완료 항목\n지우기", color = pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
                }
                Box(btn.clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable(enabled = !quick) { onRank() }.alpha(if (quick) 0.4f else 1f), Alignment.Center) {
                    Text("우선순위\n가르기", color = pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
                }
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (page >= 2) (if (page == 2) (if (archived > 0) "보관 ${archived}개" else "보관한 기록 없음") else "AI 분석")
                else if (flash.isNotEmpty()) flash else if (quick) (if (quickPage == 1) "지금 할 일로 올릴 일을 고르세요" else "나중으로 보낼 일을 고르세요") else if (total > 0) "전체 ${total}개 중 ${done}개 완료" else "할 일을 적어 보세요",
                color = pal.sub, fontSize = 10.sp,
            )
            Row {
                repeat(dots) { i ->
                    Box(
                        Modifier.padding(4.dp).size(10.dp).clip(CircleShape)
                            .then(if (i == page) Modifier.background(pal.ink) else Modifier.border(1.8.dp, pal.rule, CircleShape))
                    )
                }
            }
        }
        if (page < 2) {
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    btn.clip(RoundedCornerShape(6.dp)).then(if (quick) Modifier.background(pal.ink) else Modifier.border(1.dp, pal.rule, RoundedCornerShape(6.dp))).clickable { onQuick() }, Alignment.Center,
                ) {
                    Text(if (quick) "취소" else "빠른\n정리", color = if (quick) pal.bg else pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
                }
                Box(btn.clip(RoundedCornerShape(6.dp)).background(pal.ink).clickable { onAdd() }, Alignment.Center) {
                    Text(if (quick) (if (selN > 0) "완료\n${selN}개" else "완료") else "할 일\n추가", color = pal.bg, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 13.sp)
                }
            }
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
internal fun NoteInput(
    value: String, onValue: (String) -> Unit, hint: String, maxLen: Int, onSubmit: () -> Unit,
    modifier: Modifier = Modifier, fontSize: Float = 17f, onFocus: (Boolean) -> Unit = {}, fit: Boolean = false,
    enterSubmits: Boolean = false, focusRequester: FocusRequester? = null, readOnly: Boolean = false,
) {
    val pal = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    var lines by remember { mutableIntStateOf(1) }
    var textH by remember { mutableIntStateOf(0) }
    // fit: 입력을 마친 뒤에도 여러 줄을 노트줄 배수 높이로 전부 보여준다
    val multi = fit && !focused && lines > 1
    val fitRows = rowsFor(textH, 13.dp)
    BasicTextField(
        value = value, readOnly = readOnly,
        onValueChange = { v -> if (enterSubmits && v.contains('\n')) onSubmit() else { Idle.poke(); onValue(v.take(maxLen)) } },
        modifier = modifier.height(if (focused) ROW * 5 else if (multi) ROW * fitRows else ROW)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        textStyle = TextStyle(color = pal.pen, fontSize = fontSize.sp, fontFamily = Gaegu, lineHeight = INPUT_LH),
        cursorBrush = SolidColor(pal.pen),
        visualTransformation = CharBreak,
        onTextLayout = { lines = it.lineCount; textH = it.size.height },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(top = if (focused || multi) 13.dp * FontPref.scale else 0.dp), if (focused || multi) Alignment.TopStart else Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = pal.sub.copy(alpha = 0.8f), fontSize = fontSize.sp, fontFamily = Gaegu, lineHeight = INPUT_LH)
                inner()
            }
        },
    )
}

@Composable
fun ListPage(
    bucket: String, filter: String, tick: Int, open: SnapshotStateMap<String, Boolean>,
    onChange: () -> Unit, onDelete: (List<Item>) -> Unit, addReq: Int = 0,
    quick: Boolean = false, sel: SnapshotStateMap<String, Boolean>? = null, oneLine: Boolean = false,
    onAddHandled: () -> Unit = {},
) {
    val db = Db.get(LocalContext.current)
    val order = remember(tick, bucket, filter) { mutableStateListOf<Item>().apply { addAll(visibleItems(db, bucket, filter)) } }
    val listState = rememberLazyListState()
    var topAdd by remember { mutableStateOf(false) }
    LaunchedEffect(addReq) {
        if (addReq > 0) {
            topAdd = true; onAddHandled()
            // 맨 위에 입력줄이 끼어들면 목록이 기존 첫 항목을 붙잡아 입력줄이 위로 밀려나므로, 그린 뒤와 키보드가 올라온 뒤에 다시 맨 위로 올린다
            kotlinx.coroutines.delay(60); listState.scrollToItem(0)
            kotlinx.coroutines.delay(350); listState.scrollToItem(0)
        }
    }
    val topSig = ScrollTop.n.intValue
    LaunchedEffect(topSig) { if (topSig > 0) { kotlinx.coroutines.delay(80); listState.scrollToItem(0); kotlinx.coroutines.delay(250); listState.scrollToItem(0) } }
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

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (topAdd && !quick) item(key = "topadd") { AddRow(bucket, { onChange(); }, addReq, true) { topAdd = false } }
        if (order.isEmpty()) item { Box(Modifier.fillMaxWidth().height(ROW).ruled(), Alignment.CenterStart) { Text(if (filter == "all" || filter == "left") "비어 있어요" else "해당하는 일이 없어요", color = LocalPal.current.sub, fontSize = 13.sp, modifier = Modifier.padding(start = MARGIN + 12.dp)) } }
        items(order, key = { it.id }) { r ->
            val dragging = dragId == r.id
            Box(if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragDy } else Modifier.animateItem()) {
                ItemBlock(
                    r, bucket, open[r.id] == true, dragging,
                    quick = quick, oneLine = oneLine, selected = sel?.get(r.id) == true, onSelect = { if (sel != null) { if (sel[r.id] == true) sel.remove(r.id) else sel[r.id] = true } },
                    onToggle = { open[r.id] = !(open[r.id] ?: false) }, onChange = onChange, onDelete = { onDelete(listOf(r)) },
                    onDragStart = { dragId = r.id; dragDy = 0f }, onDrag = { dragMove(it) }, onDragEnd = { dragEnd(true) }, onDragCancel = { dragEnd(false) },
                )
            }
        }
        if (!quick) item { AddRow(bucket, onChange) }
        items(10) { Box(Modifier.fillMaxWidth().height(ROW).ruled()) }
    }
}

@Composable
private fun ItemBlock(
    item: Item, bucket: String, open: Boolean, dragging: Boolean,
    onToggle: () -> Unit, onChange: () -> Unit, onDelete: () -> Unit,
    onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    quick: Boolean = false, selected: Boolean = false, onSelect: () -> Unit = {}, oneLine: Boolean = false,
) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    val fm = LocalFocusManager.current
    var editing by remember(item.id) { mutableStateOf(false) }
    var lines by remember(item.id, item.title) { mutableIntStateOf(1) }
    var titleH by remember(item.id, item.title) { mutableIntStateOf(0) }
    val dStart by rememberUpdatedState(onDragStart); val dMove by rememberUpdatedState(onDrag)
    val dEnd by rememberUpdatedState(onDragEnd); val dCancel by rememberUpdatedState(onDragCancel)
    val rows = if (oneLine) 1 else rowsFor(titleH, 3.dp)

    Column(Modifier.fillMaxWidth().then(if (dragging) Modifier.background(pal.bg) else Modifier)) {
        if (editing) TitleEditor(item, bucket, onChange, onDone = { editing = false; onChange() })
        Row(
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
                onTextLayout = { lines = it.lineCount; titleH = it.size.height }, modifier = Modifier.weight(1f).padding(end = 4.dp),
                maxLines = if (oneLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            )
            if (item.stepsTotal > 0) Text(
                "${item.stepsDone}/${item.stepsTotal}", color = pal.ink, fontSize = 10.sp,
                modifier = Modifier.padding(end = 2.dp).border(1.dp, pal.rule, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
            )
            if (quick) {
                Box(Modifier.size(width = 44.dp, height = ROW).clickable { onSelect() }, Alignment.Center) {
                    Box(Modifier.size(26.dp).border(2.dp, if (selected) pal.margin else pal.pen, RoundedCornerShape(3.dp)), Alignment.Center) {
                        if (selected) ArrowIcon(bucket == "now", pal.margin)
                    }
                }
            } else Box(Modifier.size(width = 44.dp, height = ROW).clickable { editing = true }, Alignment.Center) {
                Text("•••", color = pal.sub, fontSize = 12.sp, letterSpacing = 0.sp)
            }
        }
        if (open) Panel(item, bucket, onChange, onDelete)
    }
}

/** '할 일 추가'를 누르면 열리는 전체 화면 새 글 작성창 */
@Composable
private fun NewEditor(bucket: String, onDone: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var tf by remember { mutableStateOf(TextFieldValue("")) }
    var star by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    var finished by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var back by remember { mutableIntStateOf(0) }
    LaunchedEffect(back) { if (back > 0) { kotlinx.coroutines.delay(150); fr.requestFocus() } }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); fr.requestFocus() }
    fun finish() { if (finished) return; val v = tf.text.trim(); if (v.isEmpty()) return; finished = true; db.add(v, bucket, star, urgent, true); ScrollTop.n.intValue++; onDone() }
    fun close() { if (finished) return; if (tf.text.isBlank()) { finished = true; onDone() } else ask = true }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dd = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(dd.density, dd.fontScale * FontPref.scale)) {
            Column(Modifier.fillMaxSize().background(pal.bg).imePadding().navSafe()) {
                Row(Modifier.fillMaxWidth().background(pal.bar).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable { close() }, Alignment.Center) { Text("✕", color = pal.barInk, fontSize = 20.sp) }
                    Text(if (bucket == "now") "새 지금 할 일" else "새 나중 할 일", color = pal.barInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                    BasicTextField(
                        value = tf,
                        onValueChange = { v -> Idle.poke(); tf = v.copy(text = v.text.take(1000)) },
                        modifier = Modifier.fillMaxSize().focusRequester(fr),
                        textStyle = TextStyle(color = pal.pen, fontSize = 22.sp, fontFamily = Gaegu, lineHeight = 33.sp),
                        cursorBrush = SolidColor(pal.pen),
                        visualTransformation = CharBreak,
                    )
                    if (tf.text.isEmpty()) Text(if (bucket == "now") "지금 할 일을 적어요" else "나중에 할 일을 적어요", color = pal.sub, fontSize = 22.sp, fontFamily = Gaegu, lineHeight = 33.sp)
                }
                Row(
                    Modifier.fillMaxWidth().drawBehind { drawLine(pal.rule, Offset(0f, 0f), Offset(size.width, 0f), 1f) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
                ) {
                    Text("${tf.text.length} / 1000", color = if (tf.text.length >= 1000) pal.urgentBg else pal.sub, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconBtn("!", urgent) { urgent = !urgent }
                    IconBtn(if (star) "★" else "☆", star) { star = !star }
                    Box(Modifier.size(44.dp).clickable { finish() }, Alignment.Center) {
                        Text("✓", color = pal.pen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (ask) SaveAsk(tf.text, onSave = { ask = false; finish() }, onDiscard = { ask = false; if (!finished) { finished = true; onDone() } }, onBack = { ask = false; back++ })
}

/** ⋯ 를 누르면 열리는 제목 편집칸: 4줄 높이, 1000자, 커서는 맨 뒤(스페이스 한 칸 뒤) */
@Composable
private fun TitleEditor(item: Item, bucket: String, onChange: () -> Unit, onDone: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    val start = item.title + " "
    var tf by remember(item.id) { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val fr = remember { FocusRequester() }
    var finished by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var back by remember { mutableIntStateOf(0) }
    LaunchedEffect(back) { if (back > 0) { kotlinx.coroutines.delay(150); fr.requestFocus() } }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun finish() { if (finished) return; finished = true; db.setTitle(item.id, tf.text); onDone() }
    fun close() {
        if (finished) return
        val v = tf.text.trim()
        if (v.isEmpty() || v == item.title) { finished = true; onDone() } else ask = true
    }
    // 전체 화면 편집창
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dd = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(dd.density, dd.fontScale * FontPref.scale)) {
        Column(Modifier.fillMaxSize().background(pal.bg).imePadding().navSafe()) {
            Row(Modifier.fillMaxWidth().background(pal.bar).statusBarsPadding().height(56.dp + 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clickable { close() }, Alignment.Center) { Text("✕", color = pal.barInk, fontSize = 20.sp) }
                Text("할 일 수정", color = pal.barInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            BasicTextField(
                value = tf,
                onValueChange = { v -> Idle.poke(); tf = v.copy(text = v.text.take(1000)) },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).focusRequester(fr),
                textStyle = TextStyle(color = pal.pen, fontSize = 22.sp, fontFamily = Gaegu, lineHeight = 33.sp),
                cursorBrush = SolidColor(pal.pen),
                visualTransformation = CharBreak,
            )
            Row(
                Modifier.fillMaxWidth().drawBehind { drawLine(pal.rule, Offset(0f, 0f), Offset(size.width, 0f), 1f) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "${tf.text.length} / 1000", color = if (tf.text.length >= 1000) pal.urgentBg else pal.sub, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconBtn("!", item.urgent) { db.setFlag(item.id, "urgent", !item.urgent); onChange() }
                IconBtn(if (item.star) "★" else "☆", item.star) { db.setFlag(item.id, "star", !item.star); onChange() }
                Box(Modifier.size(40.dp).clickable { finish(); db.move(item.id, if (bucket == "now") "later" else "now"); onChange() }, Alignment.Center) {
                    ArrowIcon(right = bucket == "now", color = pal.sub)
                }
                Box(Modifier.size(44.dp).clickable { finish() }, Alignment.Center) {
                    Text("✓", color = pal.pen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
    if (ask) SaveAsk(tf.text, onSave = { ask = false; finish() }, onDiscard = { ask = false; if (!finished) { finished = true; onDone() } }, onBack = { ask = false; back++ })
}

/** 할 일을 새로 추가하면 올려서, 목록이 맨 위로 스크롤되게 한다 */
internal object ScrollTop { val n = mutableIntStateOf(0) }

/** 집중 모드: 이 할 일만 전체 화면으로 보여 주고 타이머를 돌린다 */
@Composable
private fun FocusScreen(item: Item, onClose: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var v by remember { mutableIntStateOf(0) }
    val steps = remember(v) { db.steps(item.id) }
    val nextId = steps.firstOrNull { !it.done }?.id
    var sec by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(true) }
    var awake by remember { mutableStateOf(db.getSetting("focus_awake", "1") == "1") }
    LaunchedEffect(running) { while (running) { kotlinx.coroutines.delay(1000); sec++ } }
    val appCtx = LocalContext.current
    fun end() { if (sec >= 60) { db.focusLog(item.id, sec / 60); CalLog.onFocusEnd(appCtx, db, item.title, sec / 60) }; onClose() }
    val time = (sec / 3600).let { h -> if (h > 0) "%d:%02d:%02d".format(h, (sec % 3600) / 60, sec % 60) else "%02d:%02d".format((sec % 3600) / 60, sec % 60) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { end() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }
        val dd = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(dd.density, dd.fontScale * FontPref.scale)) {
            Column(Modifier.fillMaxSize().background(pal.bg).navSafe()) {
                Row(Modifier.fillMaxWidth().background(pal.bar).statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable { end() }, Alignment.Center) { Text("✕", color = pal.barInk, fontSize = 20.sp) }
                    Text("집중", color = pal.barInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(tagged(item.title, tagColor()), color = pal.pen, fontSize = 26.sp, lineHeight = 36.sp, fontFamily = Gaegu, fontWeight = FontWeight.Bold)
                    if (item.description.isNotBlank()) Text(item.description, color = pal.ink, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = Gaegu)
                    Text(time, color = pal.ink, fontSize = 64.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            if (running) "일시정지" else "계속", color = pal.bg, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(pal.ink).clickable { running = !running }.padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "처음부터", color = pal.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, pal.ink, RoundedCornerShape(50)).clickable { sec = 0 }.padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            if (awake) "☀ 화면 꺼짐 방지: 켜짐" else "☾ 화면 꺼짐 방지: 꺼짐",
                            color = if (awake) pal.bg else pal.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).then(if (awake) Modifier.background(pal.ink) else Modifier.border(1.dp, pal.ink, RoundedCornerShape(50)))
                                .clickable { awake = !awake; db.setSetting("focus_awake", if (awake) "1" else "0") }.padding(horizontal = 18.dp, vertical = 11.dp),
                        )
                    }
                    steps.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().drawBehind { drawLine(pal.rule, Offset(0f, size.height), Offset(size.width, size.height), 1f) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = s.done, onCheckedChange = { db.toggleStep(s.id, item.id, it); v++ },
                                colors = CheckboxDefaults.colors(checkedColor = pal.margin, uncheckedColor = pal.pen, checkmarkColor = pal.bg),
                            )
                            Text(
                                s.title, color = if (s.done) pal.sub else pal.pen, fontSize = 18.sp, fontFamily = Gaegu,
                                fontWeight = if (s.id == nextId) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            )
                        }
                    if (!item.done) Text(
                        "✓ 모두 완료 · 이 일 완료", color = pal.bg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.ink)
                            .clickable { steps.filter { !it.done }.forEach { db.toggleStep(it.id, item.id, true) }; db.toggleDone(item.id, true); end() }.padding(vertical = 15.dp),
                    )
                    }
                }
            }
        }
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
    var focusMode by remember(item.id) { mutableStateOf(false) }
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
    var stepFocused by remember(item.id) { mutableStateOf(false) }
    var askStep by remember(item.id) { mutableStateOf(false) }
    var stepRefocus by remember(item.id) { mutableIntStateOf(0) }
    val stepFr = remember(item.id) { FocusRequester() }
    LaunchedEffect(stepRefocus) { if (stepRefocus > 0) { kotlinx.coroutines.delay(150); stepFr.requestFocus() } }
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
                desc, { desc = it }, "설명 적기", 1000, {}, Modifier.weight(1f).padding(end = 6.dp), 17f,
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
                var sH by remember(s.id, s.title) { mutableIntStateOf(0) }
                val sRows = rowsFor(sH, 3.dp)
                Row(
                    Modifier.fillMaxWidth().height(ROW * sRows)
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
                        fontWeight = if (s.id == nextId) FontWeight.Bold else FontWeight.Normal, lineHeight = 22.sp, onTextLayout = { sH = it.size.height },
                        textDecoration = if (s.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f).padding(end = 4.dp),
                    )
                    if (s.id == nextId) Text(
                        "지금 이 단계", color = pal.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF7E3A8)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Box(Modifier.width(44.dp * FontPref.scale).fillMaxHeight().clickable { db.deleteStep(s.id); refresh() }, Alignment.Center) {
                        Text("✕", color = pal.sub, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text("+", color = pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            NoteInput(
                newStep, { newStep = it }, if (steps.isEmpty()) "첫 번째 절차를 적어 보세요" else "다음 절차 적고 Enter", 1000,
                { db.addStep(item.id, newStep); newStep = ""; refresh() }, Modifier.weight(1f), 16f, enterSubmits = true,
                onFocus = { f -> if (!f && stepFocused && win.isWindowFocused && newStep.isNotBlank() && !askStep) askStep = true; stepFocused = f },
                focusRequester = stepFr,
            )
        }
        if (askStep) SaveAsk(newStep, onSave = { askStep = false; db.addStep(item.id, newStep); newStep = ""; refresh() }, onDiscard = { askStep = false; newStep = "" }, onBack = { askStep = false; stepRefocus++ })
        Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(MARGIN + 10.dp))
            Text(
                "◎ 집중", color = pal.ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable { focus.clearFocus(); focusMode = true }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.weight(1f))
            if (steps.isNotEmpty()) Text("${steps.count { it.done }} / ${steps.size} 단계 완료", color = pal.sub, fontSize = 10.sp, modifier = Modifier.padding(end = 10.dp))
            Text(
                "삭제", color = pal.ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp)).clickable { onDelete() }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        AiHelper(item, desc, onAddSteps = { refresh() }, onAppendDesc = { t -> desc = (desc + (if (desc.isBlank()) "" else "\n\n") + t).take(1000); db.setDesc(item.id, desc); savedDesc = desc })
        if (focusMode) FocusScreen(item) { focusMode = false; refresh() }
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
private fun AddRow(bucket: String, onChange: () -> Unit, addReq: Int = 0, top: Boolean = false, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = Db.get(ctx)
    val pal = LocalPal.current
    var t by remember { mutableStateOf("") }
    var star by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    var refocus by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    val win = androidx.compose.ui.platform.LocalWindowInfo.current
    // 맨 위 입력줄은 '할 일 추가'를 눌러 새로 생길 때 한 번만 포커스를 받는다(페이지가 다시 그려질 때마다 열리지 않게)
    LaunchedEffect(Unit) { if (top) { kotlinx.coroutines.delay(120); fr.requestFocus() } }
    LaunchedEffect(refocus) { if (refocus > 0) { kotlinx.coroutines.delay(150); fr.requestFocus() } }
    fun submit() { if (t.isNotBlank()) { db.add(t, bucket, star, urgent, top); if (top) ScrollTop.n.intValue++; t = ""; star = false; urgent = false; onChange(); onDone() } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text("+", color = pal.margin, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            NoteInput(
                t, { t = it }, if (bucket == "now") "지금 할 일 적고 ✓" else "나중에 할 일 적고 ✓", 1000, { submit() },
                Modifier.weight(1f).padding(end = 10.dp), 18f, focusRequester = fr,
                onFocus = { f ->
                    if (!f && focused && win.isWindowFocused && t.isNotBlank() && !ask) ask = true
                    else if (!f && focused && top && t.isBlank() && !ask) onDone()
                    focused = f
                },
            )
        }
        // 입력칸 아래, 오른쪽 정렬: ☆ ! ✓
        if (focused || t.isNotBlank() || star || urgent) {
            Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "복사", color = pal.ink, fontSize = 12.sp,
                    modifier = Modifier.padding(start = MARGIN + 10.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, pal.rule, RoundedCornerShape(6.dp))
                        .clickable {
                            if (t.isBlank()) android.widget.Toast.makeText(ctx, "복사할 내용이 없어요", android.widget.Toast.LENGTH_SHORT).show()
                            else {
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("memo", t))
                                android.widget.Toast.makeText(ctx, "복사했어요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
                Spacer(Modifier.weight(1f))
                IconBtn(if (star) "★" else "☆", star) { star = !star }
                IconBtn("!", urgent) { urgent = !urgent }
                Box(Modifier.size(40.dp).clickable { submit() }, Alignment.Center) {
                    Text("✓", color = pal.pen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        if (ask) SaveAsk(t, onSave = { ask = false; submit() }, onDiscard = { ask = false; t = ""; star = false; urgent = false; if (top) onDone() }, onBack = { ask = false; refocus++ })
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
    /** 메인 화면 위쪽에 띄울 경고 (비어 있으면 안 띄움) */
    val warn = mutableStateOf("")
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    fun refreshWarn(db: Db) {
        val d = Sync.driveOn(db); val f = Sync.folderOn(db); val err = db.getSetting("sync_err", "")
        warn.value = when {
            !d && !f -> "⚠ 동기화가 꺼져 있어요 · 눌러서 설정"
            !d -> "⚠ 구글 드라이브가 연결되어 있지 않아요 · 눌러서 연결"
            err.isNotEmpty() -> "⚠ 동기화 실패: " + Ai.cut(err, 70) + " · 눌러서 확인"
            else -> ""
        }
    }

    /** 동기화 결과를 화면 문구로 바꾼다 (화면에서 돌린 것·백그라운드 작업에서 돌린 것 모두) */
    fun report(db: Db, r: Sync.Result, force: Boolean = false) {
        status.value = when {
            r.errors.isNotEmpty() -> "동기화 일부 실패: " + r.errors.joinToString(" / ")
            r.saved == 0 && r.got == 0 -> "바뀐 내용이 없어서 동기화를 건너뛰었어요" + (if (r.notes.isNotEmpty()) " (" + r.notes.joinToString(", ") + ")" else "")
            else -> "동기화 완료 (저장 ${r.saved}곳 · 가져온 변경 ${r.got}건)" + (if (r.notes.isNotEmpty()) " · " + r.notes.joinToString(", ") else "")
        }
        refreshWarn(db)
        if (r.got > 0 || force) rev.intValue++
    }

    fun sync(ctx: android.content.Context, force: Boolean = false) {
        val db = Db.get(ctx)
        if (!Sync.enabled(db)) { status.value = "동기화가 꺼져 있어요. 설정에서 폰 폴더나 구글 드라이브를 연결해 주세요"; refreshWarn(db); return }
        if (!busy.compareAndSet(false, true)) return
        status.value = "동기화 중…"
        Thread {
            try { report(db, Sync.run(ctx), force) }
            catch (e: Exception) { status.value = "동기화 실패: " + (e.message ?: e.javaClass.simpleName); db.setSetting("sync_err", e.message ?: e.javaClass.simpleName); refreshWarn(db) }
            finally { busy.set(false) }
        }.start()
    }
}

/** 메인 화면 위쪽의 동기화 경고줄. 누르면 설정으로 간다. */
@Composable
private fun SyncBanner(onOpen: () -> Unit) {
    val w = SyncState.warn.value
    if (w.isEmpty()) return
    val pal = LocalPal.current
    Text(
        w, color = pal.urgentBg, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, maxLines = 2,
        modifier = Modifier.fillMaxWidth().background(pal.panel).clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}


private data class RankState(val sorted: List<Item>, val i: Int, val lo: Int, val hi: Int, val n: Int)

@Composable
private fun RankCard(modifier: Modifier, item: Item, onClick: () -> Unit) {
    val pal = LocalPal.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.panel).border(2.dp, pal.pen, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(item.title, color = pal.pen, fontSize = 24.sp, lineHeight = 32.sp, fontFamily = Gaegu, fontWeight = FontWeight.Bold, maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        val first = item.description.lines().firstOrNull { it.isNotBlank() }
        if (first != null) Text(first, color = pal.sub, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val tags = listOfNotNull(if (item.urgent) "! 긴급" else null, if (item.star) "★ 중요" else null, if (item.stepsTotal > 0) "절차 ${item.stepsTotal}개" else null)
        if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** 지금 할 일 두 개씩 맞붙여 더 급한 쪽을 고르면 전체 우선순위가 정해진다 (이진 삽입 정렬 방식이라 비교 횟수가 적다) */
@Composable
private fun RankDialog(db: Db, onClose: () -> Unit, onApplied: () -> Unit) {
    val pal = LocalPal.current
    val items = remember { db.list("now").filter { !it.done } }
    var st by remember { mutableStateOf(RankState(items.take(1), 1, 0, 1, 0)) }
    val hist = remember { mutableStateListOf<RankState>() }
    var groupFlags by remember { mutableStateOf(false) }
    val total = remember { (1 until items.size).sumOf { j -> kotlin.math.ceil(kotlin.math.log2((j + 1).toDouble())).toInt() } }
    fun pick(challengerWins: Boolean) {
        hist.add(st)
        val mid = (st.lo + st.hi) / 2
        var lo = st.lo; var hi = st.hi
        if (challengerWins) hi = mid else lo = mid + 1
        var sorted = st.sorted; var i = st.i
        if (lo >= hi) { sorted = sorted.toMutableList().also { it.add(lo, items[i]) }; i++; lo = 0; hi = sorted.size }
        st = RankState(sorted, i, lo, hi, st.n + 1)
    }
    val finished = items.size >= 2 && st.i >= items.size
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dd = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(dd.density, dd.fontScale * FontPref.scale)) {
            Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navSafe()) {
                Row(Modifier.fillMaxWidth().background(pal.bar).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable { onClose() }, Alignment.Center) { Text("✕", color = pal.barInk, fontSize = 20.sp) }
                    Text("우선순위 가르기", color = pal.barInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(
                        "되돌리기", color = pal.barInk.copy(alpha = if (hist.isNotEmpty()) 1f else 0.35f), fontSize = 13.sp,
                        modifier = Modifier.clickable(enabled = hist.isNotEmpty()) { st = hist.removeAt(hist.lastIndex) }.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
                if (items.size < 2) {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { Text("비교할 지금 할 일이 2개 이상 필요해요", color = pal.sub, fontSize = 15.sp) }
                } else if (!finished) {
                    val mid = (st.lo + st.hi) / 2
                    Text(
                        "어느 쪽이 더 우선인가요? 눌러서 고르세요  ·  ${st.n + 1}번째 비교 (최대 ${total}번)",
                        color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RankCard(Modifier.weight(1f), items[st.i]) { pick(true) }
                        Text("VS", color = pal.margin, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        RankCard(Modifier.weight(1f), st.sorted[mid]) { pick(false) }
                    }
                } else {
                    Text("결과 · 위에서부터 우선순위", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        st.sorted.forEachIndexed { idx, it ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}위", color = pal.margin, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                                Text(it.title, color = pal.pen, fontSize = 18.sp, fontFamily = Gaegu, modifier = Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${items.size - idx}점", color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { groupFlags = !groupFlags }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = groupFlags, onCheckedChange = { groupFlags = it })
                        Text("긴급·중요 표시 항목을 순서와 상관없이 먼저 보이기 (끄면 이 순서 그대로 보여요)", color = pal.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "다시 하기", color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).border(1.dp, pal.ink, RoundedCornerShape(50))
                                .clickable { hist.clear(); st = RankState(items.take(1), 1, 0, 1, 0) }.padding(vertical = 14.dp),
                        )
                        Text(
                            "이 순서로 정리", color = pal.bg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1.4f).clip(RoundedCornerShape(50)).background(pal.ink)
                                .clickable { db.setSetting("flag_group", if (groupFlags) "1" else "0"); db.reorder(st.sorted.map { it.id }); onApplied() }.padding(vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

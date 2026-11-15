package com.donggun.oxfordtodo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Pal(
    val bg: Color, val ink: Color, val sub: Color, val rule: Color, val margin: Color,
    val panel: Color, val bar: Color, val barInk: Color, val accent: Color,
)

val LightPal = Pal(
    bg = Color(0xFFFBF6E6), ink = Color(0xFF2B2B2B), sub = Color(0xFF8A8A8A),
    rule = Color(0xFFC9D3E0), margin = Color(0xFFE4A0A0), panel = Color(0xFFF3EBD0),
    bar = Color(0xFF1F3A5F), barInk = Color.White, accent = Color(0xFFD9822B),
)
val DarkPal = Pal(
    bg = Color.Black, ink = Color(0xFFA8A8A8), sub = Color(0xFF707070),
    rule = Color(0xFF1E1E1E), margin = Color(0xFF3B2323), panel = Color(0xFF0A0A0A),
    bar = Color.Black, barInk = Color(0xFFA8A8A8), accent = Color(0xFFA8A8A8),
)
val LocalPal = staticCompositionLocalOf { LightPal }

private val ROW = 50.dp
private val MARGIN = 50.dp
private val TS = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
private fun fmt(ts: Long) = TS.format(Date(ts))

/** 노트지 한 줄: 아래 괘선 + 왼쪽 빨간 이중 여백선 */
@Composable
private fun Modifier.ruled(): Modifier {
    val p = LocalPal.current
    return this.drawBehind {
        val m = MARGIN.toPx()
        drawLine(p.margin, Offset(m, 0f), Offset(m, size.height), 1.5f)
        drawLine(p.margin, Offset(m + 4.dp.toPx(), 0f), Offset(m + 4.dp.toPx(), size.height), 1f)
        drawLine(p.rule, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
    }
}

@Composable
fun NotePadApp() {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var dark by remember { mutableStateOf(db.getSetting("dark", "0") == "1") }
    var showSettings by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var tickLocal by remember { mutableIntStateOf(0) }
    val tick = tickLocal + SyncState.rev.intValue
    val pager = rememberPagerState { 4 }
    // 앱을 열 때와 편집 후 잠시 뒤 자동 동기화
    val lifeOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifeOwner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME || e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) SyncState.sync(ctx)
        }
        lifeOwner.lifecycle.addObserver(ob); onDispose { lifeOwner.lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(tickLocal) { if (tickLocal > 0) { kotlinx.coroutines.delay(2500); SyncState.sync(ctx) } }
    val pal = if (dark) DarkPal else LightPal
    val titles = listOf("지금 할 일", "나중 할 일", "보관함", "AI 분석")

    BackHandler(enabled = showSettings) { showSettings = false }

    CompositionLocalProvider(LocalPal provides pal) {
        Column(Modifier.fillMaxSize().background(pal.bg)) {
            TitleBar(
                title = if (showSettings) "설정" else titles[pager.currentPage],
                showFilters = !showSettings && pager.currentPage < 2,
                filter = filter, onFilter = { filter = it },
                dark = dark,
                onDark = { dark = !dark; db.setSetting("dark", if (dark) "1" else "0") },
                onSettings = { showSettings = !showSettings },
            )
            if (showSettings) {
                Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    SettingsPane()
                }
            } else {
                HorizontalPager(pager, Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> ListPage("now", filter, tick) { tickLocal++ }
                        1 -> ListPage("later", filter, tick) { tickLocal++ }
                        2 -> Placeholder("보관함 — 2단계에서 만듭니다")
                        else -> Placeholder("AI 분석 — 3단계에서 만듭니다")
                    }
                }
                val now = remember(tick) { db.list("now") }
                Footer(pager.currentPage, now.size, now.count { it.done })
            }
        }
    }
}

@Composable
private fun TitleBar(
    title: String, showFilters: Boolean, filter: String, onFilter: (String) -> Unit,
    dark: Boolean, onDark: () -> Unit, onSettings: () -> Unit,
) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().background(pal.bar).statusBarsPadding().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = pal.barInk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (showFilters) {
            listOf("all" to "전체", "left" to "남은", "urgent" to "긴급", "star" to "중요").forEach { (k, l) ->
                Text(
                    l, fontSize = 13.sp, fontWeight = if (filter == k) FontWeight.Bold else FontWeight.Normal,
                    color = if (filter == k) pal.barInk else pal.barInk.copy(alpha = 0.45f),
                    modifier = Modifier.clickable { onFilter(k) }.padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
        Text("⚙", color = pal.barInk, fontSize = 18.sp, modifier = Modifier.clickable { onSettings() }.padding(horizontal = 6.dp, vertical = 8.dp))
        Box(Modifier.size(30.dp).clickable { onDark() }, Alignment.Center) {
            Box(
                Modifier.size(13.dp).clip(CircleShape)
                    .background(if (dark) pal.barInk else Color.Transparent)
                    .border(1.5.dp, pal.barInk, CircleShape)
            )
        }
    }
}

@Composable
private fun Footer(page: Int, total: Int, done: Int) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxWidth().background(pal.panel).navigationBarsPadding().height(44.dp)) {
        Text("전체 ${total}개 중 ${done}개 완료", color = pal.sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
        Row(Modifier.align(Alignment.Center)) {
            repeat(4) { i ->
                Box(Modifier.padding(4.dp).size(7.dp).clip(CircleShape).background(if (i == page) pal.ink else pal.rule))
            }
        }
    }
}

@Composable
private fun Placeholder(msg: String) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(msg, color = pal.sub, fontSize = 14.sp) }
}

@Composable
fun ListPage(bucket: String, filter: String, tick: Int, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val all = remember(tick, bucket) { db.list(bucket) }
    val rows = all.filter {
        when (filter) {
            "left" -> !it.done
            "urgent" -> it.urgent && !it.done
            "star" -> it.star
            else -> true
        }
    }
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().imePadding()) {
        items(rows, key = { r -> r.id }) { r ->
            ItemBlock(r, bucket, open == r.id, { open = if (open == r.id) null else r.id }, onChange)
        }
        item { AddRow(bucket, onChange) }
        items(14) { Box(Modifier.fillMaxWidth().height(ROW).ruled()) }
    }
}

@Composable
private fun ItemBlock(item: Item, bucket: String, open: Boolean, onOpen: () -> Unit, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(ROW).ruled().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MARGIN), Alignment.Center) {
                if (bucket == "now") {
                    Checkbox(
                        checked = item.done, onCheckedChange = { db.toggleDone(item.id, it); onChange() },
                        colors = CheckboxDefaults.colors(checkedColor = pal.accent, uncheckedColor = pal.sub, checkmarkColor = pal.bg),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (item.star) Text("★ ", color = pal.accent, fontSize = 16.sp)
            Text(
                item.title, color = if (item.done) pal.sub else pal.ink, fontSize = 17.sp,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (item.urgent) {
                Text(
                    "급", color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).border(1.dp, pal.accent, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (item.stepsTotal > 0) Text("${item.stepsDone}/${item.stepsTotal}", color = pal.sub, fontSize = 12.sp)
            Text(if (open) "▴" else "▾", color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (open) Panel(item, bucket, onChange)
    }
}

@Composable
private fun Panel(item: Item, bucket: String, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var desc by remember(item.id) { mutableStateOf(item.description) }
    var v by remember(item.id) { mutableIntStateOf(0) }
    var logOpen by remember(item.id) { mutableStateOf(false) }
    var newStep by remember(item.id) { mutableStateOf("") }
    val steps = remember(item.id, v) { db.steps(item.id) }
    val logs = remember(item.id, v, item.done) { db.logs(item.id) }
    fun refresh() { v++; onChange() }
    val field = TextStyle(color = pal.ink, fontSize = 15.sp)

    Column(Modifier.fillMaxWidth().background(pal.panel)) {
        // 첫 줄: 설명 입력 + ☆ ! ↓
        Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(MARGIN + 12.dp))
            BasicTextField(
                value = desc, onValueChange = { desc = it; db.setDesc(item.id, it) },
                modifier = Modifier.weight(1f), singleLine = true, textStyle = field, cursorBrush = SolidColor(pal.ink),
                decorationBox = { inner ->
                    Box { if (desc.isEmpty()) Text("설명 쓰기", color = pal.sub, fontSize = 15.sp); inner() }
                },
            )
            IconBtn(if (item.star) "★" else "☆", item.star) { db.setFlag(item.id, "star", !item.star); onChange() }
            IconBtn("!", item.urgent) { db.setFlag(item.id, "urgent", !item.urgent); onChange() }
            IconBtn(if (bucket == "now") "↓" else "↑", false) { db.move(item.id, if (bucket == "now") "later" else "now"); onChange() }
        }
        // 절차
        steps.forEach { s ->
            Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(MARGIN), Alignment.Center) {
                    Checkbox(
                        checked = s.done, onCheckedChange = { db.toggleStep(s.id, item.id, it); refresh() },
                        colors = CheckboxDefaults.colors(checkedColor = pal.accent, uncheckedColor = pal.sub, checkmarkColor = pal.bg),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    s.title, color = if (s.done) pal.sub else pal.ink, fontSize = 15.sp, modifier = Modifier.weight(1f),
                    textDecoration = if (s.done) TextDecoration.LineThrough else null,
                )
                Text("✕", color = pal.sub, fontSize = 14.sp, modifier = Modifier.clickable { db.deleteStep(s.id); refresh() }.padding(12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(MARGIN), Alignment.Center) { Text("＋", color = pal.sub, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = newStep, onValueChange = { newStep = it },
                modifier = Modifier.weight(1f), singleLine = true, textStyle = field, cursorBrush = SolidColor(pal.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { db.addStep(item.id, newStep); newStep = ""; refresh() }),
                decorationBox = { inner ->
                    Box { if (newStep.isEmpty()) Text("절차 추가", color = pal.sub, fontSize = 15.sp); inner() }
                },
            )
        }
        // 로그 (자동 기록, 읽기 전용): 접힌 상태는 한 줄 미리보기
        Row(
            Modifier.fillMaxWidth().height(ROW).ruled().clickable { logOpen = !logOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(MARGIN), Alignment.Center) { Text("로그", color = pal.sub, fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp))
            val last = logs.lastOrNull()
            Text(
                if (logOpen) "접기" else if (last != null) "${fmt(last.ts)}  ${last.text}" else "기록 없음",
                color = pal.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(if (logOpen) "▴" else "▾", color = pal.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (logOpen) logs.forEach { l ->
            Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(MARGIN + 12.dp))
                Text("${fmt(l.ts)}  ${l.text}", color = pal.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun IconBtn(label: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.size(40.dp).clickable(onClick = onClick), Alignment.Center) {
        Text(label, color = if (on) pal.accent else pal.sub, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddRow(bucket: String, onChange: () -> Unit) {
    val db = Db.get(LocalContext.current)
    val pal = LocalPal.current
    var t by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(MARGIN), Alignment.Center) { Text("＋", color = pal.sub, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = t, onValueChange = { t = it },
            modifier = Modifier.weight(1f), singleLine = true,
            textStyle = TextStyle(color = pal.ink, fontSize = 17.sp), cursorBrush = SolidColor(pal.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { db.add(t, bucket); t = ""; onChange() }),
            decorationBox = { inner -> Box { if (t.isEmpty()) Text("할 일 추가", color = pal.sub, fontSize = 17.sp); inner() } },
        )
    }
}


/** 동기화 상태(화면 갱신용). 로그인되어 있을 때만 서버와 주고받는다. */
object SyncState {
    val rev = mutableIntStateOf(0)
    val status = mutableStateOf("")
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    fun sync(ctx: android.content.Context, force: Boolean = false) {
        val db = Db.get(ctx)
        if (!Sync.loggedIn(db) || !busy.compareAndSet(false, true)) return
        status.value = "동기화 중…"
        Thread {
            try {
                val (up, down) = Sync.run(ctx)
                status.value = "동기화 완료 (올림 $up · 받음 $down)"
                if (down > 0 || force) rev.intValue++
            } catch (e: Exception) { status.value = "동기화 실패: " + (e.message ?: e.javaClass.simpleName) }
            finally { busy.set(false) }
        }.start()
    }
}

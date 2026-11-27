package com.donggun.oxfordtodo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LK_INK = Color(0xFFF3EFE2)
private val LK_SUB = Color(0xFFA8A496)
private val LK_GRAY = Color(0xFF8D897C)
private val LK_BEIGE = Color(0xFFFBF6E0)
private val LK_NAVY = Color(0xFF1C2A4A)

/**
 * 새 잠금화면. 검은 배경에 시계 · 메시지(누르면 횟수 +1) · 횟수/수정 · 메모 입력칸 · Now / 할일추가.
 * Now는 입력한 내용을 '지금 생각'으로 시간과 함께 기록하고(앱의 Now 페이지에서 시간순으로 봄), 할일추가는 '지금 할 일' 맨 위에 새 할 일로 넣는다.
 * 횟수는 "오늘 / 누적"으로 보인다(오늘은 하루마다 0부터).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LockHome(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val imeOn = WindowInsets.isImeVisible
    val db = remember { Db.get(ctx) }
    val lockSec = remember { (db.getSetting("lock_sec", "10").toIntOrNull() ?: 10).coerceIn(5, 600) }
    LaunchedEffect(Unit) {
        Idle.poke()
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (android.os.SystemClock.elapsedRealtime() - Idle.last >= lockSec * 1000L) { onClose(); break }
        }
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(10_000) } }

    val today = remember(now) { SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(now) }
    val cfg = remember { db.lockGet() }
    var message by remember { mutableStateOf(cfg.text) }
    var count by remember { mutableIntStateOf(if (cfg.day == today) cfg.cnt else 0) }
    var total by remember { mutableIntStateOf(maxOf(cfg.total, count)) }
    var editing by remember { mutableStateOf(false) }
    var maxFs by remember { mutableFloatStateOf(cfg.fs) }
    fun saveFs() { db.lockSetFs(maxFs); SyncWorker.once(ctx) }
    val hide = imeOn && !editing
    var text by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf("") }
    LaunchedEffect(flash) { if (flash.isNotEmpty()) { kotlinx.coroutines.delay(2000); flash = "" } }

    fun bump() {
        if (cfg.day != today && count > 0 && db.lockGet().day != today) count = 0
        count++; total++
        db.lockSetCount(count, total, today)
    }
    fun submit() {
        val v = text.trim()
        if (v.isEmpty()) { flash = if (editing) "메시지를 적어 주세요" else "내용을 적어 주세요"; return }
        if (editing) {
            message = v.take(300); db.lockSetText(message); SyncWorker.once(ctx)
            editing = false; text = ""; flash = "메시지를 바꿨어요"
        } else {
            val lines = v.lines()
            val id = db.add(lines.first(), "now", false, false, true)
            val rest = lines.drop(1).joinToString("\n").trim()
            if (id.isNotEmpty() && rest.isNotEmpty()) db.setDesc(id, rest)
            text = ""; flash = "할 일 목록에 추가했어요"
            SyncWorker.once(ctx)
        }
    }
    fun record() {
        val v = text.trim()
        if (v.isEmpty()) { flash = "내용을 적어 주세요"; return }
        if (editing) { flash = "메시지 수정 중이에요. 저장이나 취소를 눌러 주세요"; return }
        db.nowAdd(v); text = ""; flash = "now에 기록되었습니다"
        SyncWorker.once(ctx)
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); Idle.poke() } } }
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 20.dp),
    ) {
        if (!imeOn) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(now), color = LK_INK, fontSize = 88.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
            Text(SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(now), color = LK_SUB, fontSize = 16.sp)
        }
        Column((if (hide) Modifier else Modifier.weight(1f)).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (!hide) BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                var fs by remember(message, maxHeight, maxWidth, maxFs) { mutableStateOf(maxFs) }
                Text(
                    message, color = LK_INK, fontSize = fs.sp, fontFamily = Gaegu, textAlign = TextAlign.Center, lineHeight = (fs * 1.375f).sp,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { if (it.didOverflowHeight && fs > 12f) fs = (fs - 1f).coerceAtLeast(12f) },
                    modifier = Modifier.heightIn(min = 48.dp, max = maxHeight).clip12().clickable { bump() }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (!hide) Spacer(Modifier.height(10.dp))
            if (!hide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$count / $total", color = LK_GRAY, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(50))
                        .clickable { if (editing) { editing = false; text = "" } else { editing = true; text = message } }.padding(horizontal = 20.dp),
                    Alignment.Center,
                ) { Text(if (editing) "취소" else "수정", color = Color(0xFFD6D2C4), fontSize = 14.sp) }
            }
            if (editing) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("글자 크기", color = LK_SUB, fontSize = 14.sp)
                Box(Modifier.padding(start = 8.dp).size(34.dp).clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(50)).clickable { Idle.poke(); maxFs = (maxFs - 1f).coerceAtLeast(14f); saveFs() }, Alignment.Center) { Text("−", color = Color(0xFFD6D2C4), fontSize = 18.sp) }
                androidx.compose.material3.Slider(
                    value = maxFs, onValueChange = { Idle.poke(); maxFs = Math.round(it).toFloat() }, onValueChangeFinished = { saveFs() },
                    valueRange = 14f..64f, modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = LK_BEIGE, activeTrackColor = LK_BEIGE, inactiveTrackColor = Color(0xFF3D3D3D)),
                )
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(50)).clickable { Idle.poke(); maxFs = (maxFs + 1f).coerceAtMost(64f); saveFs() }, Alignment.Center) { Text("+", color = Color(0xFFD6D2C4), fontSize = 18.sp) }
                Text(maxFs.toInt().toString(), color = LK_INK, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
            }
        }
        Text(
            "그것은 바로 내가 이루고자 하는 것을\n하루에 100번씩, 100일 동안 중얼거리는 것이었다.",
            color = Color(0xFFA8A496), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 12.dp),
        )
        Box((if (hide) Modifier.weight(1f) else if (imeOn) Modifier.height(120.dp) else Modifier.height(150.dp)).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            BasicTextField(
                value = text, onValueChange = { Idle.poke(); text = it.take(1000) }, modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = LK_INK, fontSize = 16.sp, lineHeight = 24.sp), cursorBrush = SolidColor(LK_BEIGE),
                keyboardOptions = KeyboardOptions.Default,
            )
        }
        Text(
            flash, color = LK_BEIGE, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(34.dp).padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF666666), RoundedCornerShape(14.dp)).clickable { record() },
                Alignment.Center,
            ) { Text("Now", color = LK_INK, fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp) }
            Box(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF666666), RoundedCornerShape(14.dp)).clickable { submit() },
                Alignment.Center,
            ) { Text(if (editing) "저장" else "할일추가", color = LK_INK, fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp) }
        }
    }
}

private fun Modifier.clip12(): Modifier = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

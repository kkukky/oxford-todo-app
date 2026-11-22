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
@Composable
fun LockHome(onClose: () -> Unit) {
    val ctx = LocalContext.current
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
    var message by remember { mutableStateOf(db.getSetting("lock_msg", "오늘도 한 걸음씩")) }
    var count by remember { mutableIntStateOf(if (db.getSetting("lock_cnt_day", "") == today) (db.getSetting("lock_cnt", "0").toIntOrNull() ?: 0) else 0) }
    var total by remember { mutableIntStateOf(maxOf(db.getSetting("lock_total", "0").toIntOrNull() ?: 0, count)) }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf("") }
    LaunchedEffect(flash) { if (flash.isNotEmpty()) { kotlinx.coroutines.delay(2000); flash = "" } }

    fun bump() {
        if (db.getSetting("lock_cnt_day", "") != today) count = 0
        count++; total++
        db.setSetting("lock_cnt", count.toString()); db.setSetting("lock_cnt_day", today); db.setSetting("lock_total", total.toString())
    }
    fun submit() {
        val v = text.trim()
        if (v.isEmpty()) { flash = if (editing) "메시지를 적어 주세요" else "내용을 적어 주세요"; return }
        if (editing) {
            message = v.lines().first().take(60); db.setSetting("lock_msg", message)
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
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("HH:mm", Locale.KOREA).format(now), color = LK_INK, fontSize = 88.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
            Text(SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(now), color = LK_SUB, fontSize = 16.sp)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                message, color = LK_INK, fontSize = 32.sp, fontFamily = Gaegu, textAlign = TextAlign.Center, lineHeight = 44.sp,
                modifier = Modifier.heightIn(min = 48.dp).clip12().clickable { bump() }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$count / $total", color = LK_GRAY, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(50))
                        .clickable { if (editing) { editing = false; text = "" } else { editing = true; text = message } }.padding(horizontal = 20.dp),
                    Alignment.Center,
                ) { Text(if (editing) "취소" else "수정", color = Color(0xFFD6D2C4), fontSize = 14.sp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (text.isEmpty()) Text(if (editing) "새 메시지를 적으세요" else "메모를 입력하세요", color = LK_GRAY, fontSize = 16.sp)
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
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp)).background(LK_BEIGE).clickable { submit() },
                Alignment.Center,
            ) { Text(if (editing) "저장" else "할일추가", color = LK_NAVY, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

private fun Modifier.clip12(): Modifier = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

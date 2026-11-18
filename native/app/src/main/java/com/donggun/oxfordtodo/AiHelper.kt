package com.donggun.oxfordtodo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class AiMsg(val who: String, val label: String, val text: String, val applied: String = "")

@Composable
private fun AxBtn(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(if (primary) 6.dp else 14.dp)
    Text(
        label, color = if (primary) pal.bg else pal.ink, fontSize = 12.sp,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f).clip(shape)
            .then(if (primary) Modifier.background(pal.ink) else Modifier.border(1.dp, pal.rule, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun AxMemo(label: String, text: String, hint: String = "", readOnly: Boolean = true, onChange: (String) -> Unit = {}, load: Boolean = false) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().ruled(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(MARGIN).height(ROW), Alignment.Center) { Text(label, color = pal.margin, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        if (load) Box(Modifier.weight(1f).height(ROW), Alignment.CenterStart) { Text(text, color = pal.sub, fontSize = 16.sp, fontFamily = Gaegu) }
        else NoteInput(text, onChange, hint, 1000, {}, Modifier.weight(1f).padding(end = 8.dp), 16f, fit = true, readOnly = readOnly)
    }
}

@Composable
private fun AxActs(content: @Composable RowScope.() -> Unit) {
    FlowRowCompat { content() }
}

@Composable
private fun FlowRowCompat(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = MARGIN + 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** 지금 할 일 패널 안의 AI 도우미: 켜기 → 추측 확인 메모칸 → 확인/보완 → AI 응답 메모칸이 아래로 이어짐 */
@Composable
internal fun AiHelper(item: Item, desc: String, onAddSteps: () -> Unit, onAppendDesc: (String) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val pal = LocalPal.current
    val scope = rememberCoroutineScope()
    var on by remember(item.id) { mutableStateOf(false) }
    var phase by remember(item.id) { mutableStateOf("off") } // guessing confirm thinking ready
    var guess by remember(item.id) { mutableStateOf("") }
    var guessOrig by remember(item.id) { mutableStateOf("") }
    var draft by remember(item.id) { mutableStateOf("") }
    var err by remember(item.id) { mutableStateOf("") }
    var usedTick by remember { mutableIntStateOf(0) }
    val thread = remember(item.id) { mutableStateListOf<AiMsg>() }
    val cap = Ai.lenCap(db)

    fun analyze() {
        phase = "guessing"; err = ""; thread.clear()
        scope.launch {
            val sys = "너는 할 일 메모 도우미야. 아래 할 일을 보고 어떤 일인지 추측해서 한국어로 짧게 써. 형식은 정확히 3줄: '무슨 일: …', '목표: …', '지금 상태: …'. 전체 150자 이내. 다른 말은 쓰지 마."
            val r = Ai.call(db, sys, listOf(Ai.Msg("user", Ai.context(db, item, desc))), 300, 400)
            usedTick++
            r.onSuccess { guess = it + "\n맞으면 '맞아요', 다르면 고쳐서 보내 주세요."; guessOrig = guess; phase = "confirm" }
                .onFailure { err = it.message ?: "실패"; on = false; phase = "off" }
        }
    }

    fun ask(first: Boolean, req: String) {
        phase = "thinking"; err = ""
        scope.launch {
            val sys = "너는 사용자의 할 일을 함께 진행해 주는 도우미야. 한국어로 답하고, 반드시 ${cap}자 이내로 써. 절차·단계를 제안할 때는 '1. ', '2. ' 번호 목록으로만 쓰고 군더더기 설명은 줄여."
            val msgs = ArrayList<Ai.Msg>()
            val head = Ai.context(db, item, desc) + "\n\n내가 확인한 이해: " + Ai.cut(guess, Ai.CAP_IN_MSG)
            msgs.add(Ai.Msg("user", head + "\n\n" + (if (first) "이 일을 어떻게 진행하면 좋을지 단계로 제안해 줘." else "(이어서 대화합니다)")))
            // 이어가기: 최근 메시지만 (확인 메시지 제외), 역할이 번갈아 가도록
            val recent = thread.filter { it.label != "확인" }.takeLast(Ai.KEEP_MSGS)
            if (!first) {
                var last = "user"
                msgs.add(Ai.Msg("assistant", "알겠어요. 무엇을 도와드릴까요?")); last = "assistant"
                recent.forEach { m ->
                    val role = if (m.who == "ai") "assistant" else "user"
                    if (role == last) { val prev = msgs.removeAt(msgs.lastIndex); msgs.add(Ai.Msg(role, prev.text + "\n" + Ai.cut(m.text, Ai.CAP_IN_MSG))) }
                    else { msgs.add(Ai.Msg(role, Ai.cut(m.text, Ai.CAP_IN_MSG))); last = role }
                }
                if (last == "assistant") msgs.add(Ai.Msg("user", Ai.cut(req, Ai.CAP_IN_MSG)))
            }
            val r = Ai.call(db, sys, msgs, (cap * 1.6).toInt().coerceIn(200, 2000), cap + 60)
            usedTick++
            r.onSuccess { thread.add(AiMsg("ai", "AI", it)); phase = "ready" }
                .onFailure { err = it.message ?: "실패"; phase = if (thread.isEmpty()) "confirm" else "ready" }
        }
    }

    // 켜기/끄기 줄
    Row(Modifier.fillMaxWidth().height(ROW).ruled(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(MARGIN), Alignment.Center) { Text("AI", color = pal.margin, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text("✦ AI 도우미", color = pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = on, onCheckedChange = { v ->
                if (v) {
                    if (!Ai.hasKey(db)) { Toast.makeText(ctx, "설정 → AI 도우미에서 API 키를 먼저 입력해 주세요", Toast.LENGTH_LONG).show() }
                    else { on = true; analyze() }
                } else { on = false; phase = "off"; thread.clear(); err = "" }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = pal.ink, checkedThumbColor = pal.bg),
        )
        Spacer(Modifier.weight(1f))
        if (on && phase != "guessing") Box(Modifier.padding(end = 8.dp)) { AxBtn("다시 분석", enabled = phase != "thinking") { analyze() } }
    }
    if (!on) return
    if (err.isNotEmpty()) AxActs { Text(err, color = pal.urgentBg, fontSize = 12.sp) }
    when (phase) {
        "guessing" -> AxMemo("AI", "내용을 분석하는 중…", load = true)
        "confirm" -> {
            AxMemo("확인", guess, "추측한 내용을 확인해 주세요", readOnly = false, onChange = { guess = it })
            AxActs {
                AxBtn("맞아요", primary = true) { thread.add(AiMsg("me", "확인", guess)); ask(true, guess) }
                AxBtn("고친 내용으로 보내기", enabled = guess != guessOrig && guess.isNotBlank()) { thread.add(AiMsg("me", "확인", guess)); ask(true, guess) }
            }
        }
        else -> {
            thread.forEachIndexed { i, m ->
                AxMemo(m.label, m.text, readOnly = true)
                if (m.who == "ai") AxActs {
                    val steps = Regex("(?m)^\\s*\\d+[.)]\\s*(.+)$").findAll(m.text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
                    if (m.applied.isNotEmpty()) Text("✓ ${m.applied}", color = pal.margin, fontSize = 12.sp)
                    else {
                        AxBtn("절차로 추가", primary = true, enabled = steps.isNotEmpty()) {
                            steps.take(10).forEach { db.addStep(item.id, it.take(1000)) }
                            thread[i] = AiMsg(m.who, m.label, m.text, "절차 ${minOf(steps.size, 10)}개를 추가했어요"); onAddSteps()
                        }
                        AxBtn("설명에 넣기") { onAppendDesc(m.text); thread[i] = AiMsg(m.who, m.label, m.text, "설명에 넣었어요") }
                    }
                    AxBtn("지우기") { thread.removeAt(i) }
                }
            }
            if (phase == "thinking") AxMemo("AI", "응답을 만드는 중…", load = true)
            else {
                AxMemo("나", draft, "이어서 요청 적기", readOnly = false, onChange = { draft = it })
                AxActs {
                    AxBtn("보내기", primary = true, enabled = draft.isNotBlank()) { val v = draft.trim(); draft = ""; thread.add(AiMsg("me", "나", v)); ask(false, v) }
                    listOf("더 쪼개줘", "초안 써줘", "시간 계획").forEach { c -> AxBtn(c) { thread.add(AiMsg("me", "나", c)); ask(false, c) } }
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") usedTick
    Text(
        "오늘 ${Ai.todayCount(db)} / ${Ai.dayLimit(db)}회 · 응답 ${cap}자 이내", color = pal.sub, fontSize = 10.sp,
        modifier = Modifier.padding(start = MARGIN + 8.dp, bottom = 6.dp),
    )
}

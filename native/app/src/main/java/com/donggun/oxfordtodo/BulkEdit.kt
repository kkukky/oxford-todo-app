package com.donggun.oxfordtodo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 지금 할 일을 글 한 덩어리로 바꾸고(toText), 고친 글을 다시 반영(parse → apply)한다.
 *  - 제목: 줄 맨 앞에 "0 "·"1 "이 없는 줄. 항목 사이는 빈 줄
 *  - "0 " 로 시작하는 줄: 설명 (여러 줄 가능)
 *  - "1 " 로 시작하는 줄: 세부 절차 (한 줄에 하나)
 * 같은 제목(공백 차이 무시)은 같은 항목으로 보고 완료·중요·긴급·기록을 그대로 유지한다.
 */
object Bulk {
    class Entry(val title: String, val desc: String, val steps: List<String>)

    private fun norm(s: String) = s.trim().replace(Regex("\\s+"), " ")
    private fun descNorm(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    fun toText(db: Db): String {
        val sb = StringBuilder()
        db.list("now").forEach { it ->
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(it.title.replace(Regex("\\s*\\n\\s*"), " "))
            it.description.lines().map { l -> l.trimEnd() }.filter { l -> l.isNotBlank() }.forEach { l -> sb.append("\n0 ").append(l) }
            db.steps(it.id).forEachIndexed { i, s -> sb.append("\n").append(i + 1).append(" ").append(s.title.replace(Regex("\\s*\\n\\s*"), " ")) }
        }
        return sb.toString()
    }

    /** (항목들, 제목이 없는 묶음 수) */
    fun parse(text: String): Pair<List<Entry>, Int> {
        val out = ArrayList<Entry>()
        var bad = 0
        var title = ArrayList<String>(); var desc = ArrayList<String>(); var steps = ArrayList<Pair<Int, String>>(); var any = false
        fun flush() {
            if (any) {
                if (title.isEmpty()) bad++
                else out.add(Entry(title.joinToString(" ").trim(), desc.joinToString("\n").trim(), steps.sortedBy { it.first }.map { it.second }))
            }
            title = ArrayList(); desc = ArrayList(); steps = ArrayList(); any = false
        }
        for (raw in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flush(); continue }
            any = true
            val m = Regex("^(\\d+) (.*)$").matchEntire(line)
            when {
                m != null -> {
                    val n = m.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE; val t = m.groupValues[2]
                    if (n == 0) desc.add(t) else { val s = t.trim(); if (s.isNotEmpty()) steps.add(n to s) }
                }
                line.all { it.isDigit() } -> {}
                else -> title.add(line.trim())
            }
        }
        flush()
        return out to bad
    }

    /** 정리된 글(절차 번호를 1부터 차례로) */
    fun entriesText(entries: List<Entry>): String = entries.joinToString("\n\n") { e ->
        val sb = StringBuilder(e.title)
        e.desc.lines().filter { it.isNotBlank() }.forEach { sb.append("\n0 ").append(it) }
        e.steps.forEachIndexed { i, t -> sb.append("\n").append(i + 1).append(" ").append(t) }
        sb.toString()
    }

    /** 반영했을 때 사라질 항목 수 */
    fun removedCount(db: Db, entries: List<Entry>): Int {
        val existing = db.list("now"); val used = BooleanArray(existing.size)
        for (e in entries) {
            val k = existing.indices.firstOrNull { !used[it] && norm(existing[it].title) == norm(e.title) }
            if (k != null) used[k] = true
        }
        return used.count { !it }
    }

    /** (새로 만든 수, 바뀐 수, 삭제한 수) */
    fun apply(db: Db, entries: List<Entry>): Triple<Int, Int, Int> {
        val w = db.writableDatabase
        var added = 0; var changed = 0; var removed = 0
        w.beginTransaction()
        try {
            val existing = db.list("now"); val used = BooleanArray(existing.size)
            val order = ArrayList<String>()
            for (e in entries) {
                val k = existing.indices.firstOrNull { !used[it] && norm(existing[it].title) == norm(e.title) }
                val id: String
                if (k == null) {
                    id = db.add(e.title)
                    if (id.isEmpty()) continue
                    added++
                    if (e.desc.isNotEmpty()) db.setDesc(id, e.desc)
                } else {
                    used[k] = true; id = existing[k].id
                    if (descNorm(existing[k].description) != descNorm(e.desc)) { db.setDesc(id, e.desc); changed++ }
                }
                order.add(id)
                // 절차: 같은 제목은 유지(완료 상태 보존), 없는 것은 추가, 빠진 것은 삭제
                val cur = db.steps(id); val su = BooleanArray(cur.size); var stepChanged = false
                for (t in e.steps) {
                    val j = cur.indices.firstOrNull { !su[it] && norm(cur[it].title) == norm(t) }
                    if (j != null) su[j] = true else { db.addStep(id, t); stepChanged = true }
                }
                cur.indices.filter { !su[it] }.forEach { db.deleteStep(cur[it].id); stepChanged = true }
                val now = db.steps(id); val nu = BooleanArray(now.size); val ids = ArrayList<String>()
                for (t in e.steps) {
                    val j = now.indices.firstOrNull { !nu[it] && norm(now[it].title) == norm(t) } ?: continue
                    nu[j] = true; ids.add(now[j].id)
                }
                if (ids.size > 1 && ids != now.map { it.id }) { db.reorderSteps(ids); stepChanged = true }
                if (stepChanged && k != null) changed++
            }
            existing.indices.filter { !used[it] }.forEach { db.deleteItem(existing[it].id); removed++ }
            if (order.isNotEmpty()) db.reorder(order)
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        return Triple(added, changed, removed)
    }
}

@Composable
fun BulkEditDialog(db: Db, onClose: () -> Unit, onSaved: () -> Unit) {
    val initial = remember { Bulk.toText(db) }
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<List<Bulk.Entry>, Int>?>(null) }
    var askClose by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun tryClose() { if (text != initial) askClose = true else onClose() }
    fun commit(entries: List<Bulk.Entry>) {
        val (a, c, r) = Bulk.apply(db, entries)
        result = "새 항목 ${a} · 바뀐 항목 ${c} · 삭제 ${r}"
    }
    fun save() {
        val (entries, bad) = Bulk.parse(text)
        if (bad > 0) { err = "제목이 없는 묶음이 ${bad}개 있어요. 묶음의 첫 줄에 제목을 써 주세요 (숫자+띄어쓰기로 시작하면 설명/절차로 읽혀요)."; return }
        val removed = Bulk.removedCount(db, entries)
        if (removed > 0) confirm = entries to removed else commit(entries)
    }

    Dialog(
        onDismissRequest = { tryClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dd = LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(dd.density, dd.fontScale * FontPref.scale)) {
            val cs = MaterialTheme.colorScheme
            Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ tryClose() }) { Text("✕ 닫기") }
                    Text("지금 할 일 편집", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button({ save() }, enabled = text != initial) { Text("저장") }
                }
                Text(
                    "제목만 줄마다 쓰고 항목 사이는 빈 줄 · \"0 \" 로 시작하는 줄 = 설명 · \"1 \", \"2 \", \"3 \" … 으로 시작하는 줄 = 절차 (번호 순서대로 정리돼요)",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.65f),
                )
                HorizontalDivider()
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).onFocusChanged { f ->
                        if (f.isFocused) wasFocused = true
                        else if (wasFocused) {
                            val (es, bad) = Bulk.parse(text)
                            if (bad == 0 && es.isNotEmpty()) { val n = Bulk.entriesText(es); if (n != text) text = n }
                        }
                    },
                    textStyle = TextStyle(color = cs.onBackground, fontSize = 16.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(cs.primary),
                )
            }
        }
    }

    err?.let { m -> AlertDialog(onDismissRequest = { err = null }, confirmButton = { TextButton({ err = null }) { Text("확인") } }, text = { Text(m) }) }
    confirm?.let { (entries, n) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("항목 ${n}개가 삭제돼요") },
            text = { Text("글에서 빠지거나 제목이 바뀐 항목은 삭제 처리돼요. 제목을 고친 항목은 새 항목이 되어 기록이 이어지지 않아요. 저장할까요?") },
            confirmButton = { TextButton({ confirm = null; commit(entries) }) { Text("저장") } },
            dismissButton = { TextButton({ confirm = null }) { Text("취소") } },
        )
    }
    if (askClose) AlertDialog(
        onDismissRequest = { askClose = false },
        title = { Text("저장하지 않고 닫을까요?") },
        confirmButton = { TextButton({ askClose = false; onClose() }) { Text("저장 안 함") } },
        dismissButton = { TextButton({ askClose = false }) { Text("계속 편집") } },
    )
    result?.let { m -> AlertDialog(onDismissRequest = { result = null; onSaved() }, title = { Text("저장했어요") }, text = { Text(m) }, confirmButton = { TextButton({ result = null; onSaved() }) { Text("확인") } }) }
}

package com.donggun.oxfordtodo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFFBF6E6)
val Navy = Color(0xFF1F3A5F)
val Ink = Color(0xFF2B2B2B)
val Rule = Color(0xFFC9D3E0)
val Margin = Color(0xFFE4A0A0)

/** '지금 할 일' / '나중 할 일' 목록 + 맨 아래 메모 입력. 잠금화면에서는 bucket="now"만 쓴다. */
@Composable
fun TodoPane(bucket: String, hint: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var rows by remember { mutableStateOf(db.list(bucket)) }
    var text by remember { mutableStateOf("") }
    fun reload() { rows = db.list(bucket) }

    Column(modifier.background(Paper)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.id }) { it ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(it.done, { c -> db.toggleDone(it.id, c); reload() })
                    Text(
                        (if (it.star) "★ " else "") + it.title + (if (it.urgent) "  급" else ""),
                        color = Ink, fontSize = 17.sp,
                        textDecoration = if (it.done) TextDecoration.LineThrough else null,
                    )
                }
                HorizontalDivider(color = Rule)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(hint) }, singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { db.add(text, bucket); text = ""; reload() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { db.add(text, bucket); text = ""; reload() }) { Text("추가") }
        }
    }
}

@Composable
fun Header(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

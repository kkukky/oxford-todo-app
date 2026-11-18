package com.donggun.oxfordtodo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.identity.Identity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { NotePadApp() } }
    }
}

@Composable
fun SettingsPane(onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var on by remember { mutableStateOf(db.getSetting("lock_on", "0") == "1") }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var showBulk by remember { mutableStateOf(false) }

    // 설정 화면에 다녀온 뒤 권한 상태를 다시 읽는다
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) overlay = Settings.canDrawOverlays(ctx) }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("화면 켜면 잠금화면 위에 메모 표시", style = MaterialTheme.typography.titleMedium)
                Text("잠금화면에서는 '지금 할 일'만 보이고 메모를 바로 추가할 수 있어요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(on, { v ->
                on = v; db.setSetting("lock_on", if (v) "1" else "0")
                if (v) ScreenOnService.start(ctx) else ScreenOnService.stop(ctx)
            })
        }
        HorizontalDivider()
        Text("필요한 권한", style = MaterialTheme.typography.titleMedium)
        Text(if (overlay) "✓ 다른 앱 위에 표시: 허용됨" else "✗ 다른 앱 위에 표시: 꺼짐 (화면이 켜질 때 바로 뜨려면 필요)")
        Button(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
        }) { Text("'다른 앱 위에 표시' 설정 열기") }
        Button(onClick = { notif.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("알림 권한 허용") }
        Button(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }) { Text("알림 설정 열기 (전체 화면 알림 허용)") }
        HorizontalDivider()
        var lockSec by remember { mutableStateOf(db.getSetting("lock_sec", "10")) }
        Text("잠금화면 메모 자동 종료", style = MaterialTheme.typography.titleMedium)
        Text("잠금화면 위에서 아무 입력이 없으면 이 시간 뒤에 닫혀요.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("10" to "10초", "30" to "30초", "60" to "1분").forEach { (v, l) ->
                if (lockSec == v) Button(onClick = {}) { Text(l) }
                else OutlinedButton(onClick = { lockSec = v; db.setSetting("lock_sec", v) }) { Text(l) }
            }
        }
        HorizontalDivider()
        HorizontalDivider()
        var aiKey by remember { mutableStateOf(Ai.key(db)) }
        var aiKeyIn by remember { mutableStateOf("") }
        var aiLen by remember { mutableStateOf(db.getSetting("ai_len", "300")) }
        var aiLimit by remember { mutableStateOf(db.getSetting("ai_limit", "10")) }
        var aiModel by remember { mutableStateOf(db.getSetting("ai_model", "claude-sonnet-5-5")) }
        Text("AI 도우미 (내 API 키)", style = MaterialTheme.typography.titleMedium)
        Text(
            "내 Anthropic API 키를 폰에만 저장해서 직접 호출해요(드라이브·폰 폴더 동기화에는 포함되지 않아요). 사용한 만큼 키 계정에 요금이 나가므로 아래 한도로 사용량을 제한해요.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(if (aiKey.isBlank()) "키가 저장되어 있지 않아요" else "키 저장됨 (…${aiKey.takeLast(4)})", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = aiKeyIn, onValueChange = { aiKeyIn = it }, label = { Text("API 키 붙여넣기") }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { val k = aiKeyIn.trim(); if (k.isNotEmpty()) { db.setSetting("ai_key", k); aiKey = k; aiKeyIn = "" } }, enabled = aiKeyIn.isNotBlank()) { Text("키 저장") }
            OutlinedButton(onClick = { db.setSetting("ai_key", ""); aiKey = "" }, enabled = aiKey.isNotBlank()) { Text("키 지우기") }
        }
        Text("응답 길이 한도 (글자)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("300" to "300자", "600" to "600자", "1000" to "1000자").forEach { (v, l) ->
                if (aiLen == v) Button(onClick = {}) { Text(l) } else OutlinedButton(onClick = { aiLen = v; db.setSetting("ai_len", v) }) { Text(l) }
            }
        }
        Text("하루 호출 한도", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("10" to "10회", "30" to "30회", "100" to "100회").forEach { (v, l) ->
                if (aiLimit == v) Button(onClick = {}) { Text(l) } else OutlinedButton(onClick = { aiLimit = v; db.setSetting("ai_limit", v) }) { Text(l) }
            }
        }
        Text("모델", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("claude-haiku-5-5" to "Haiku (저렴)", "claude-sonnet-5-5" to "Sonnet (똑똑)").forEach { (v, l) ->
                if (aiModel == v) Button(onClick = {}) { Text(l) } else OutlinedButton(onClick = { aiModel = v; db.setSetting("ai_model", v) }) { Text(l) }
            }
        }
        Text("오늘 ${Ai.todayCount(db)}회 사용", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        StoragePane(db)
        HorizontalDivider()
        var flagOn by remember { mutableStateOf(db.getSetting("flag_group", "0") == "1") }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("긴급·중요 항목을 먼저 보이기", style = MaterialTheme.typography.titleMedium)
                Text("끄면 목록 순서(맨 위 추가, 우선순위 가르기 결과)를 그대로 보여요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(flagOn, { v -> flagOn = v; db.setSetting("flag_group", if (v) "1" else "0"); onChanged() })
        }
        HorizontalDivider()
        Text("데이터 편집", style = MaterialTheme.typography.titleMedium)
        Text("지금 할 일 전체를 글로 보면서 한꺼번에 고쳐요.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { showBulk = true }) { Text("지금 할 일 한꺼번에 편집 (전체 화면)") }
        HorizontalDivider()
        FontPane()
        HorizontalDivider()
        OutlinedButton(onClick = { ctx.startActivity(Intent(ctx, LockActivity::class.java)) }) { Text("잠금화면 메모 화면 미리보기") }
        HorizontalDivider()
        val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
        Text("옥스포드 투두노트패드 v$ver", style = MaterialTheme.typography.bodySmall)
    }
    if (showBulk) BulkEditDialog(db, { showBulk = false }) { showBulk = false; onChanged() }
}


@Composable
fun StoragePane(db: Db) {
    val ctx = LocalContext.current
    var fsLabel by remember { mutableStateOf(Store.label(ctx)) }
    var gdOn by remember { mutableStateOf(Sync.driveOn(db)) }
    var gdMail by remember { mutableStateOf(Sync.email(db)) }
    var msg by remember { mutableStateOf("") }
    val st = SyncState.status.value

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { Store.setTree(ctx, uri); fsLabel = Store.label(ctx); msg = ""; SyncState.sync(ctx, true) }
    }
    fun done(res: com.google.android.gms.auth.api.identity.AuthorizationResult?) {
        db.setSetting("gd_on", "1"); gdOn = true
        res?.toGoogleSignInAccount()?.email?.let { db.setSetting("gd_email", it); gdMail = it }
        msg = ""; SyncState.sync(ctx, true)
    }
    val auth = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try { done(Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data)) }
        catch (e: Exception) { msg = "연결하지 못했어요: " + (e.message ?: "취소됨") }
    }
    fun connect() {
        Identity.getAuthorizationClient(ctx).authorize(Sync.authRequest())
            .addOnSuccessListener { res ->
                if (res.hasResolution()) auth.launch(androidx.activity.result.IntentSenderRequest.Builder(res.pendingIntent!!.intentSender).build())
                else done(res)
            }
            .addOnFailureListener { msg = "연결 실패: " + (it.message ?: it.javaClass.simpleName) }
    }

    Text("데이터 저장 · 동기화", style = MaterialTheme.typography.titleMedium)
    Text("메모는 이 폰의 폴더와 구글 드라이브의 같은 이름 폴더(OXFORDToDoNotepad)에 파일로 저장돼요. 두 곳 중 나중에 고친 쪽이 이깁니다.", style = MaterialTheme.typography.bodySmall)

    Text("① 폰 폴더", style = MaterialTheme.typography.titleSmall)
    if (fsLabel.isNotEmpty()) {
        Text("사용 중: $fsLabel")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pick.launch(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")) }) { Text("폴더 바꾸기") }
            OutlinedButton(onClick = { Store.clear(ctx); fsLabel = "" }) { Text("사용 안 함") }
        }
    } else {
        Text("'Documents' 폴더를 고른 뒤 '이 폴더 사용'을 누르면 그 안에 OXFORDToDoNotepad 폴더가 만들어져요.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { pick.launch(Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments")) }) { Text("폰 폴더 선택") }
    }

    Text("② 구글 드라이브", style = MaterialTheme.typography.titleSmall)
    if (gdOn) {
        Text("연결됨" + if (gdMail.isNotEmpty()) ": $gdMail" else "")
        OutlinedButton(onClick = { db.setSetting("gd_on", "0"); gdOn = false }) { Text("연결 끊기") }
    } else {
        Text("내 드라이브에 OXFORDToDoNotepad 폴더를 만들고 이 앱이 만든 파일만 읽고 써요.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { connect() }) { Text("구글 드라이브 연결") }
    }

    if (fsLabel.isNotEmpty() || gdOn) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
        fun at(ms: String) = ms.toLongOrNull()?.let { fmt.format(java.util.Date(it)) } ?: "아직 없음"
        val lastCheck = remember(st) { at(db.getSetting("last_check", "")) }
        val lastChange = remember(st) { at(db.getSetting("last_change", "")) }
        Text("마지막 확인: $lastCheck", style = MaterialTheme.typography.bodySmall)
        Text("마지막 변경 반영: $lastChange", style = MaterialTheme.typography.bodySmall)
        if (st.isNotEmpty()) Text(st, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { SyncState.sync(ctx, true) }) { Text("지금 동기화") }
    }
    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
}


@Composable
fun FontPane() {
    val ctx = LocalContext.current
    var opts by remember { mutableStateOf<List<FontPref.Opt>?>(null) }
    LaunchedEffect(Unit) { opts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FontPref.options() } }
    Text("글꼴 크기", style = MaterialTheme.typography.titleMedium)
    Text("메모 목록의 글씨와 줄 간격이 함께 바뀌어요. 보통이 기본 크기예요.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FontPref.LEVEL_NAMES.forEachIndexed { i, name ->
            val on = FontPref.sizeIdx == i
            Column(
                Modifier.weight(1f)
                    .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable { FontPref.setSize(ctx, i) }.padding(vertical = 8.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("가", fontFamily = FontPref.family, fontSize = (16 * FontPref.LEVELS[i]).sp)
                Text(name, fontSize = 10.sp)
            }
        }
    }
    Text("가나다라 오늘 할 일 Abc 123", fontFamily = FontPref.family, fontSize = (19 * FontPref.scale).sp)
    HorizontalDivider()
    Text("글꼴", style = MaterialTheme.typography.titleMedium)
    Text("메모에 쓸 글꼴을 골라요. 이 폰에 들어 있는 글꼴만 나와요.", style = MaterialTheme.typography.bodySmall)
    val list = opts
    if (list == null) Text("글꼴 목록을 불러오는 중…", style = MaterialTheme.typography.bodySmall)
    else list.forEach { o ->
        val on = FontPref.id == o.id
        Column(
            Modifier.fillMaxWidth()
                .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .clickable { FontPref.set(ctx, o) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(o.name, style = MaterialTheme.typography.labelSmall)
            Text("가나다라 오늘 할 일 Abc 123", fontFamily = o.family, fontSize = 19.sp)
        }
    }
}

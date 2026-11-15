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
fun SettingsPane() {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var on by remember { mutableStateOf(db.getSetting("lock_on", "0") == "1") }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
        StoragePane(db)
        HorizontalDivider()
        FontPane()
        HorizontalDivider()
        OutlinedButton(onClick = { ctx.startActivity(Intent(ctx, LockActivity::class.java)) }) { Text("잠금화면 메모 화면 미리보기") }
    }
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

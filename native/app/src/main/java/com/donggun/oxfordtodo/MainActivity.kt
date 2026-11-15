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
        OutlinedButton(onClick = { ctx.startActivity(Intent(ctx, LockActivity::class.java)) }) { Text("잠금화면 메모 화면 미리보기") }
    }
}

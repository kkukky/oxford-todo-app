package com.donggun.oxfordtodo

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 잠금화면 위에 뜨는 화면. 먼저 새 잠금 홈(시계·메시지·입력칸)을 보여 주고, Now를 누르면 '지금 할 일' 한 페이지만 보인다.
 * 나중 할 일·보관함·AI 분석 등 다른 페이지는 잠금 해제 후 앱에서만 볼 수 있다.
 */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                var showList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (showList) NotePadApp(lock = true, onOpenApp = { openApp() }, onClose = { showList = false })
                else LockHome(onNow = { showList = true }, onClose = { finish() })
            }
        }
    }

    /** 잠금 해제를 요청한 뒤(생체/PIN) 전체 앱을 연다. */
    private fun openApp() {
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                startActivity(Intent(this@LockActivity, MainActivity::class.java))
                finish()
            }
        })
    }
}

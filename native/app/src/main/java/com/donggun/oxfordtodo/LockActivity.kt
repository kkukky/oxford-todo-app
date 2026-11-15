package com.donggun.oxfordtodo

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 잠금화면 위에 뜨는 화면. '지금 할 일' 한 페이지만 보여 주고 메모만 바로 추가할 수 있다.
 * 나중 할 일·보관함·AI 분석 등 다른 페이지는 잠금 해제 후 앱에서만 볼 수 있다.
 */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme { NotePadApp(lock = true, onOpenApp = { openApp() }, onClose = { finish() }) }
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

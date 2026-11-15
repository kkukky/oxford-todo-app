package com.donggun.oxfordtodo

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings

/** 화면이 켜지는 순간을 감지해서 잠금화면 위에 LockActivity를 띄운다. */
class ScreenOnService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_ON) showLock(c)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_KEEP, "백그라운드 실행", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOCK, "잠금화면 메모", NotificationManager.IMPORTANCE_HIGH)
        )
        val n = Notification.Builder(this, CH_KEEP)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("잠금화면 메모 켜짐")
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    companion object {
        const val CH_KEEP = "keep"
        const val CH_LOCK = "lock"

        fun showLock(c: Context) {
            val km = c.getSystemService(KeyguardManager::class.java)
            if (!km.isKeyguardLocked) return // 이미 잠금 해제된 상태면 띄우지 않음
            val i = Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 1순위: '다른 앱 위에 표시' 권한이 있으면 백그라운드에서도 바로 시작 가능
            if (Settings.canDrawOverlays(c)) {
                runCatching { c.startActivity(i); return }
            }
            // 2순위: 전체 화면 알림(사용자가 '전체 화면 알림'을 허용해야 동작)
            val pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(c, CH_LOCK)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("지금 할 일")
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build()
            c.getSystemService(NotificationManager::class.java).notify(2, n)
        }

        fun start(c: Context) { c.startForegroundService(Intent(c, ScreenOnService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, ScreenOnService::class.java)) }
    }
}

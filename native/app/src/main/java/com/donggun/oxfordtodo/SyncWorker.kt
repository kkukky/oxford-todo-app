package com.donggun.oxfordtodo

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** 앱이 꺼져 있어도 일정 간격으로, 그리고 앱을 닫는 순간 확실히 끝까지 동기화한다. */
class SyncWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val db = Db.get(applicationContext)
        if (!Sync.enabled(db)) return Result.success()
        try { SyncState.report(db, Sync.run(applicationContext)) }
        catch (e: Exception) { db.setSetting("sync_err", e.message ?: e.javaClass.simpleName); SyncState.refreshWarn(db) }
        return Result.success()
    }

    companion object {
        private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 30분마다. 동기화가 꺼져 있으면 예약을 취소한다. */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (!Sync.enabled(Db.get(ctx))) { wm.cancelUniqueWork("oxford-sync-periodic"); return }
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(NET).build()
            wm.enqueueUniquePeriodicWork("oxford-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** 앱을 닫는 순간: 화면을 벗어나도 끝까지 돌도록 작업으로 한 번 더 예약 */
        fun once(ctx: Context) {
            if (!Sync.enabled(Db.get(ctx))) return
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(NET).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("oxford-sync-once", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

package com.donggun.oxfordtodo

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** 집중 모드가 끝나면 폰 캘린더의 'OXFORDTODO' 캘린더에 일정을 남긴다. 캘린더는 사용자가 구글 캘린더에서 미리 만들어 둔다. */
object CalLog {
    const val NAME = "OXFORDTODO"

    fun enabled(db: Db) = db.getSetting("cal_on", "0") == "1"

    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 이름이 OXFORDTODO인 캘린더 id (없으면 null). 쓰기 가능한 것만 */
    fun findId(ctx: Context): Long? {
        if (!granted(ctx)) return null
        val c = ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, null,
        ) ?: return null
        c.use {
            while (it.moveToNext()) {
                if (it.getString(1)?.trim().equals(NAME, ignoreCase = true) && it.getInt(2) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return it.getLong(0)
            }
        }
        return null
    }

    /** 일정 하나 기록. 성공하면 null, 실패하면 이유 */
    fun record(ctx: Context, title: String, minutes: Long, endMs: Long): String? {
        if (!granted(ctx)) return "캘린더 권한이 없어요"
        val id = findId(ctx) ?: return "‘$NAME’ 캘린더를 찾지 못했어요"
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, id)
            put(CalendarContract.Events.TITLE, title.ifBlank { "(제목 없음)" })
            put(CalendarContract.Events.DESCRIPTION, "집중 ${minutes}분 (옥스포드 투두노트패드)")
            put(CalendarContract.Events.DTSTART, endMs - minutes * 60_000)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        return try {
            val u = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
            if (u != null && ContentUris.parseId(u) > 0) null else "일정을 저장하지 못했어요"
        } catch (e: Exception) { "일정 저장 오류: ${e.message}" }
    }

    /** 설정이 켜져 있으면 기록하고 결과를 설정에 남긴다 (백그라운드 스레드) */
    fun onFocusEnd(ctx: Context, db: Db, title: String, minutes: Long) {
        if (!enabled(db)) return
        val end = System.currentTimeMillis()
        Thread {
            val err = record(ctx.applicationContext, title, minutes, end)
            db.setSetting("cal_last", if (err == null) "기록함: $title · ${minutes}분" else "실패: $err")
        }.start()
    }
}

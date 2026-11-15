package com.donggun.oxfordtodo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class Item(
    val id: String, val title: String, val bucket: String,
    val star: Boolean, val urgent: Boolean, val done: Boolean,
)

/** 로컬 SQLite. 스키마는 Supabase 동기화를 염두에 둔 구조(UUID, updated_at, deleted_at). */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "todo.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
              id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
              bucket TEXT NOT NULL DEFAULT 'now', star INTEGER NOT NULL DEFAULT 0,
              urgent INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0,
              completed_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
              deleted_at INTEGER, archived_at INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE steps(
              id TEXT PRIMARY KEY, item_id TEXT NOT NULL, title TEXT NOT NULL,
              done INTEGER NOT NULL DEFAULT 0, done_at INTEGER, position INTEGER NOT NULL DEFAULT 0,
              updated_at INTEGER NOT NULL, deleted_at INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE logs(
              id TEXT PRIMARY KEY, item_id TEXT NOT NULL, type TEXT NOT NULL,
              text TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    private fun now() = System.currentTimeMillis()

    fun list(bucket: String): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT id,title,bucket,star,urgent,done FROM items " +
                "WHERE bucket=? AND deleted_at IS NULL AND archived_at IS NULL ORDER BY done, created_at",
            arrayOf(bucket)
        ).use { c ->
            while (c.moveToNext()) out.add(
                Item(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) == 1, c.getInt(4) == 1, c.getInt(5) == 1)
            )
        }
        return out
    }

    fun add(title: String, bucket: String = "now") {
        val t = title.trim()
        if (t.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val ts = now()
        val v = ContentValues().apply {
            put("id", id); put("title", t); put("bucket", bucket)
            put("created_at", ts); put("updated_at", ts)
        }
        writableDatabase.insert("items", null, v)
        log(id, "start", "등록")
    }

    fun toggleDone(id: String, done: Boolean) {
        val ts = now()
        val v = ContentValues().apply {
            put("done", if (done) 1 else 0)
            if (done) put("completed_at", ts) else putNull("completed_at")
            put("updated_at", ts)
        }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
        log(id, if (done) "done" else "reopen", if (done) "완료" else "다시 진행")
    }

    private fun log(itemId: String, type: String, text: String) {
        val v = ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("item_id", itemId)
            put("type", type); put("text", text); put("created_at", now())
        }
        writableDatabase.insert("logs", null, v)
    }

    fun getSetting(key: String, def: String): String =
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else def
        }

    fun setSetting(key: String, value: String) {
        val v = ContentValues().apply { put("key", key); put("value", value) }
        writableDatabase.insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext).also { inst = it }
        }
    }
}

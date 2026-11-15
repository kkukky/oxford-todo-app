package com.donggun.oxfordtodo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class Item(
    val id: String, val title: String, val description: String, val bucket: String,
    val star: Boolean, val urgent: Boolean, val done: Boolean,
    val stepsTotal: Int = 0, val stepsDone: Int = 0,
)

data class Step(val id: String, val title: String, val done: Boolean)
data class LogRow(val ts: Long, val type: String, val text: String)

/** 로컬 SQLite. 스키마는 Supabase 동기화를 염두에 둔 구조(UUID, updated_at, deleted_at). */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "todo.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
              id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
              bucket TEXT NOT NULL DEFAULT 'now', star INTEGER NOT NULL DEFAULT 0,
              urgent INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0,
              completed_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
              deleted_at INTEGER, archived_at INTEGER,
              sort_order REAL NOT NULL DEFAULT 0, synced_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE steps(
              id TEXT PRIMARY KEY, item_id TEXT NOT NULL, title TEXT NOT NULL,
              done INTEGER NOT NULL DEFAULT 0, done_at INTEGER, position INTEGER NOT NULL DEFAULT 0,
              updated_at INTEGER NOT NULL, deleted_at INTEGER, synced_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE logs(
              id TEXT PRIMARY KEY, item_id TEXT NOT NULL, type TEXT NOT NULL,
              text TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER, synced_at INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) {
            db.execSQL("ALTER TABLE items ADD COLUMN sort_order REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE items ADD COLUMN synced_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE items SET sort_order = created_at")
            db.execSQL("ALTER TABLE steps ADD COLUMN synced_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE logs ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE logs ADD COLUMN deleted_at INTEGER")
            db.execSQL("ALTER TABLE logs ADD COLUMN synced_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE logs SET updated_at = created_at")
        }
    }

    private fun now() = System.currentTimeMillis()

    fun list(bucket: String): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT id,title,description,bucket,star,urgent,done," +
                "(SELECT COUNT(*) FROM steps s WHERE s.item_id=items.id AND s.deleted_at IS NULL)," +
                "(SELECT COUNT(*) FROM steps s WHERE s.item_id=items.id AND s.deleted_at IS NULL AND s.done=1) " +
                "FROM items WHERE bucket=? AND deleted_at IS NULL AND archived_at IS NULL ORDER BY done, sort_order, created_at",
            arrayOf(bucket)
        ).use { c ->
            while (c.moveToNext()) out.add(
                Item(
                    c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getInt(4) == 1, c.getInt(5) == 1, c.getInt(6) == 1, c.getInt(7), c.getInt(8)
                )
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
            put("created_at", ts); put("updated_at", ts); put("sort_order", ts.toDouble())
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
            put("type", type); put("text", text); put("created_at", now()); put("updated_at", now())
        }
        writableDatabase.insert("logs", null, v)
    }

    fun setDesc(id: String, text: String) {
        val v = ContentValues().apply { put("description", text); put("updated_at", now()) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }

    fun setFlag(id: String, col: String, on: Boolean) {
        require(col == "star" || col == "urgent")
        val v = ContentValues().apply { put(col, if (on) 1 else 0); put("updated_at", now()) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }

    fun move(id: String, bucket: String) {
        val v = ContentValues().apply { put("bucket", bucket); put("updated_at", now()) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }

    fun steps(itemId: String): List<Step> {
        val out = ArrayList<Step>()
        readableDatabase.rawQuery(
            "SELECT id,title,done FROM steps WHERE item_id=? AND deleted_at IS NULL ORDER BY position, updated_at",
            arrayOf(itemId)
        ).use { c -> while (c.moveToNext()) out.add(Step(c.getString(0), c.getString(1), c.getInt(2) == 1)) }
        return out
    }

    fun addStep(itemId: String, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        val pos = readableDatabase.rawQuery("SELECT COALESCE(MAX(position),0)+1 FROM steps WHERE item_id=?", arrayOf(itemId))
            .use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val v = ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("item_id", itemId); put("title", t)
            put("position", pos); put("updated_at", now())
        }
        writableDatabase.insert("steps", null, v)
    }

    fun toggleStep(stepId: String, itemId: String, done: Boolean) {
        val title = readableDatabase.rawQuery("SELECT title FROM steps WHERE id=?", arrayOf(stepId))
            .use { if (it.moveToFirst()) it.getString(0) else "" }
        val ts = now()
        val v = ContentValues().apply {
            put("done", if (done) 1 else 0)
            if (done) put("done_at", ts) else putNull("done_at")
            put("updated_at", ts)
        }
        writableDatabase.update("steps", v, "id=?", arrayOf(stepId))
        log(itemId, if (done) "step" else "undo", (if (done) "절차 완료: " else "절차 취소: ") + title)
    }

    fun deleteStep(stepId: String) {
        val v = ContentValues().apply { put("deleted_at", now()); put("updated_at", now()) }
        writableDatabase.update("steps", v, "id=?", arrayOf(stepId))
    }

    fun logs(itemId: String): List<LogRow> {
        val out = ArrayList<LogRow>()
        readableDatabase.rawQuery(
            "SELECT created_at,type,text FROM logs WHERE item_id=? ORDER BY created_at", arrayOf(itemId)
        ).use { c -> while (c.moveToNext()) out.add(LogRow(c.getLong(0), c.getString(1), c.getString(2))) }
        return out
    }

    /** 길게 눌러 옮긴 뒤의 순서(같은 목록의 id 순서)를 저장 */
    fun reorder(ids: List<String>) {
        val db = writableDatabase; val ts = now()
        db.beginTransaction()
        try {
            ids.forEachIndexed { i, id ->
                val v = ContentValues().apply { put("sort_order", (i + 1).toDouble()); put("updated_at", ts) }
                db.update("items", v, "id=?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setTitle(id: String, title: String) {
        val t = title.trim().take(400); if (t.isEmpty()) return
        val v = ContentValues().apply { put("title", t); put("updated_at", now()) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }

    /** 완료 항목을 보관함으로 / 보관함에서 꺼내기 / 완전 삭제(삭제 표시) */
    fun archiveDone(): Int {
        val ts = now(); val v = ContentValues().apply { put("archived_at", ts); put("updated_at", ts) }
        return writableDatabase.update("items", v, "done=1 AND archived_at IS NULL AND deleted_at IS NULL", null)
    }
    fun archive(id: String) {
        val ts = now(); val v = ContentValues().apply { put("archived_at", ts); put("updated_at", ts) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }
    fun restore(id: String) {
        val v = ContentValues().apply { putNull("archived_at"); put("updated_at", now()) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }
    fun deleteItem(id: String) {
        val ts = now(); val v = ContentValues().apply { put("deleted_at", ts); put("updated_at", ts) }
        writableDatabase.update("items", v, "id=?", arrayOf(id))
    }
    fun archived(): List<Item> {
        val out = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT id,title,description,bucket,star,urgent,done,0,0 FROM items WHERE archived_at IS NOT NULL AND deleted_at IS NULL ORDER BY archived_at DESC", null
        ).use { c ->
            while (c.moveToNext()) out.add(Item(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getInt(4) == 1, c.getInt(5) == 1, c.getInt(6) == 1, 0, 0))
        }
        return out
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

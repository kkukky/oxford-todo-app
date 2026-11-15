package com.donggun.oxfordtodo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** 폰의 Documents/OXFORDToDoNotepad 폴더에 데이터 파일을 저장한다. (사용자가 폴더를 한 번 선택하면 그 권한을 기억한다) */
object Store {
    const val DIR = "OXFORDToDoNotepad"
    const val FILE = "oxford-todo-data.json"

    fun setTree(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {}
        Db.get(ctx).setSetting("fs_tree", uri.toString())
    }

    fun clear(ctx: Context) = Db.get(ctx).setSetting("fs_tree", "")

    /** 선택한 폴더 안의 OXFORDToDoNotepad 폴더(없으면 만든다). 선택한 폴더 자체가 그 이름이면 그대로 쓴다. */
    fun dir(ctx: Context): DocumentFile? {
        val t = Db.get(ctx).getSetting("fs_tree", "")
        if (t.isEmpty()) return null
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(t)) ?: return null
        if (!root.exists() || !root.canWrite()) return null
        if (root.name == DIR) return root
        return root.findFile(DIR)?.takeIf { it.isDirectory } ?: root.createDirectory(DIR)
    }

    fun label(ctx: Context): String {
        val t = Db.get(ctx).getSetting("fs_tree", "")
        if (t.isEmpty()) return ""
        val root = DocumentFile.fromTreeUri(ctx, Uri.parse(t)) ?: return ""
        return if (root.name == DIR) DIR else (root.name ?: "") + "/" + DIR
    }

    fun read(ctx: Context): String? {
        val f = dir(ctx)?.findFile(FILE) ?: return null
        return try { ctx.contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
    }

    fun write(ctx: Context, text: String): Boolean {
        val d = dir(ctx) ?: return false
        val f = d.findFile(FILE) ?: d.createFile("application/json", FILE) ?: return false
        return try {
            ctx.contentResolver.openOutputStream(f.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        } catch (_: Exception) { false }
    }
}

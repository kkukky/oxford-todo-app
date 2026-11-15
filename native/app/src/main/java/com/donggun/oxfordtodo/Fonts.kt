package com.donggun.oxfordtodo

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/** 메모 글꼴 선택. 기본은 개구 손글씨, 그 밖에는 이 폰의 시스템 글꼴 파일들. */
object FontPref {
    class Opt(val id: String, val name: String, val family: FontFamily)

    private val gaegu = FontFamily(Font(R.font.gaegu_regular, FontWeight.Normal), Font(R.font.gaegu_bold, FontWeight.Bold))
    var family: FontFamily by mutableStateOf(gaegu)
        private set
    var id: String by mutableStateOf("gaegu")
        private set
    private var loaded = false
    private var cache: List<Opt>? = null

    private val FIXED = listOf(
        Opt("gaegu", "개구 손글씨 (기본)", gaegu),
        Opt("g:default", "시스템 기본", FontFamily.Default),
        Opt("g:sans", "고딕 (산세리프)", FontFamily.SansSerif),
        Opt("g:serif", "명조 (세리프)", FontFamily.Serif),
        Opt("g:mono", "고정폭", FontFamily.Monospace),
        Opt("g:cursive", "필기체", FontFamily.Cursive),
    )

    private fun resolve(id: String): FontFamily {
        FIXED.firstOrNull { it.id == id }?.let { return it.family }
        if (id.startsWith("f:")) {
            val f = File(id.removePrefix("f:"))
            if (f.exists()) try { return FontFamily(Typeface.createFromFile(f)) } catch (_: Exception) {}
        }
        return gaegu
    }

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        id = Db.get(ctx).getSetting("font", "gaegu")
        family = resolve(id)
    }

    fun set(ctx: Context, o: Opt) {
        Db.get(ctx).setSetting("font", o.id)
        id = o.id; family = o.family
    }

    /** 폰에 들어 있는 글꼴 목록 (파일을 열어 보므로 백그라운드에서 부른다) */
    fun options(): List<Opt> {
        cache?.let { return it }
        val seen = HashSet<String>()
        val sys = ArrayList<Opt>()
        val dirs = listOf("/system/fonts", "/system/product/fonts", "/product/fonts", "/system_ext/fonts", "/data/fonts")
        val skip = listOf("Emoji", "Symbols", "Color", "Icon", "Arabic", "Hebrew", "Thai", "Devanagari", "Bengali", "Tamil", "Telugu", "Gujarati", "Gurmukhi", "Kannada", "Malayalam", "Sinhala", "Khmer", "Lao", "Myanmar", "Ethiopic", "Armenian", "Georgian")
        for (d in dirs) {
            val list = File(d).listFiles() ?: continue
            for (f in list.sortedBy { it.name }) {
                val n = f.name
                if (!(n.endsWith(".ttf", true) || n.endsWith(".otf", true))) continue
                val base = n.substringBeforeLast('.')
                if (base.contains('-') && !base.endsWith("-Regular", true)) continue
                if (skip.any { base.contains(it, true) }) continue
                val fam = base.removeSuffix("-Regular").removeSuffix("-regular")
                if (fam.startsWith("Noto") && fam !in setOf("NotoSerif", "NotoSansKR", "NotoSerifKR", "NotoSansCJK", "NotoSerifCJK", "NotoSansMono")) continue
                if (!seen.add(fam.lowercase())) continue
                val tf = try { Typeface.createFromFile(f) } catch (_: Exception) { continue }
                val p = Paint().apply { typeface = tf }
                if (!p.hasGlyph("A") && !p.hasGlyph("가")) continue
                val pretty = fam.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replace('_', ' ')
                sys.add(Opt("f:" + f.absolutePath, pretty, FontFamily(tf)))
            }
        }
        val out = FIXED + sys
        cache = out
        return out
    }
}

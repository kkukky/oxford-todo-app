package com.donggun.oxfordwriter

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 한글(HWPX) 파일 만들기. 한컴 기본 빈 문서(assets/skeleton.hwpx)에 제목과 본문 문단을 채워 넣는다. */
object Hwpx {
    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private val BAD = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]")
    private fun clean(t: String) = t.replace(BAD, "")

    fun build(ctx: Context, title: String, body: String): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        ctx.assets.open("skeleton.hwpx").use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) entries[e.name] = z.readBytes()
                    e = z.nextEntry
                }
            }
        }
        val tp = if (title.isBlank()) "" else esc(clean(title))
        var sec = String(entries["Contents/section0.xml"] ?: ByteArray(0), Charsets.UTF_8)
        sec = sec.replace(
            "<hp:run charPrIDRef=\"0\"><hp:t/></hp:run>",
            "<hp:run charPrIDRef=\"0\">" + (if (tp.isNotEmpty()) "<hp:t>$tp</hp:t>" else "<hp:t/>") + "</hp:run>",
        )
        var n = 1000
        val paras = StringBuilder()
        body.replace("\r", "").split("\n").forEach { line ->
            val t = clean(line)
            n++
            paras.append("<hp:p id=\"$n\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\"><hp:run charPrIDRef=\"0\">")
                .append(if (t.isNotEmpty()) "<hp:t>${esc(t)}</hp:t>" else "<hp:t/>").append("</hp:run></hp:p>")
        }
        sec = sec.replace("</hs:sec>", paras.toString() + "</hs:sec>")
        var hpf = String(entries["Contents/content.hpf"] ?: ByteArray(0), Charsets.UTF_8)
        hpf = hpf.replace("<opf:title/>", "<opf:title>$tp</opf:title>")

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            val mt = "application/hwp+zip".toByteArray(Charsets.UTF_8)
            val me = ZipEntry("mimetype")
            me.method = ZipEntry.STORED
            me.size = mt.size.toLong(); me.compressedSize = mt.size.toLong()
            val crc = CRC32(); crc.update(mt); me.crc = crc.value
            zos.putNextEntry(me); zos.write(mt); zos.closeEntry()
            for ((name, data) in entries) {
                if (name == "mimetype") continue
                val bytes = when (name) {
                    "Contents/section0.xml" -> sec.toByteArray(Charsets.UTF_8)
                    "Contents/content.hpf" -> hpf.toByteArray(Charsets.UTF_8)
                    "Preview/PrvText.txt" -> clean(title + "\n" + body).take(1000).toByteArray(Charsets.UTF_8)
                    else -> data
                }
                zos.putNextEntry(ZipEntry(name)); zos.write(bytes); zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}

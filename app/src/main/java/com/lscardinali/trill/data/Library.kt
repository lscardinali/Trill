package com.lscardinali.trill.data

import android.content.Context
import android.os.Environment
import java.io.File

data class Game(val platform: String, val name: String, val fileName: String)

/** Scans ROM folders so games can be picked with a controller instead of typed. */
object Library {

    private val ignoredExt = setOf(
        "txt", "xml", "png", "jpg", "jpeg", "webp", "gif", "nfo", "dat", "db", "json", "cfg", "ini",
        "sav", "srm", "state", "log", "md", "bak", "tmp", "mp4", "mp3", "ogg", "pdf", "sh", "bat",
    )
    private val tags = Regex("""\s*[\(\[][^\)\]]*[\)\]]""")

    fun candidateRoots(): List<File> {
        val roots = mutableListOf<File>()
        val storage = File("/storage").listFiles().orEmpty().filter { it.isDirectory && it.name != "self" && it.name != "emulated" }
        for (base in storage + Environment.getExternalStorageDirectory()) {
            base.listFiles()?.filter { it.isDirectory && it.name.equals("roms", ignoreCase = true) }?.let(roots::addAll)
        }
        return roots
    }

    fun scan(customRoot: String?): List<Game> {
        val roots = customRoot?.takeIf { it.isNotBlank() }?.let { listOf(File(it)) } ?: candidateRoots()
        val games = mutableListOf<Game>()
        for (root in roots) {
            for (platform in root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }) {
                val seen = HashSet<String>()
                for (entry in platform.listFiles().orEmpty()) {
                    if (entry.name.startsWith(".")) continue
                    if (entry.isFile && entry.extension.lowercase() in ignoredExt) continue
                    if (entry.isDirectory && entry.name.lowercase() in setOf("media", "images", "videos", "manuals", "bios")) continue
                    val clean = cleanName(entry)
                    if (clean.isNotBlank() && seen.add(clean.lowercase())) {
                        games += Game(platform.name, clean, entry.name)
                    }
                }
            }
        }
        return games.sortedWith(compareBy({ it.platform.lowercase() }, { it.name.lowercase() }))
    }

    fun cleanName(f: File): String {
        val base = if (f.isFile) f.nameWithoutExtension else f.name
        return cleanString(base)
    }

    fun cleanString(base: String): String {
        return base.replace('_', ' ').replace(tags, "")
            .replace(Regex("""\s+(Disc|CD)\s*\d+$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ").trim()
    }

    fun defaultOutputDir(@Suppress("UNUSED_PARAMETER") ctx: Context) =
        File(Environment.getExternalStorageDirectory(), "Music/Jingles").absolutePath
}

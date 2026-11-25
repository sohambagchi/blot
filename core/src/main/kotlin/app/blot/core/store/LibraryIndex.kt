package app.blot.core.store

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The cached summary of every directory under the notes root, kept as one JSON file in
 * app-private storage. It is only a cache: deleting it costs one full re-read of the folder.
 *
 * Keys are paths under the root: `<name>` for a top-level directory (a note, or a folder when it
 * has no summary) and `<folder>/<name>` for a directory in a folder.
 */
internal class LibraryIndex(private val file: File, private val rootKey: String) {
    /**
     * [dirModified] is the note directory's modified time when [summary] was taken; null when
     * the store itself just wrote the note and the new time is not known yet;
     * [TreeEntry.UNKNOWN_TIME] when the time can't be trusted and the note must be read again.
     * [summary] is null for a directory that holds no note.
     */
    data class Entry(val dirModified: Long?, val summary: NoteSummary?)

    val entries = HashMap<String, Entry>()

    /** True when the file on disk doesn't match [entries] and should be saved. */
    var dirty = false
        private set

    /** Loads the file; on any problem (missing, corrupt, other root) the index starts empty. */
    fun load() {
        entries.clear()
        dirty = true
        val parsed = try {
            MiniJson.parse(file.readText()) as Map<*, *>
        } catch (e: Exception) {
            return
        }
        if (parsed["version"] != VERSION || parsed["root"] != rootKey) return
        try {
            for (d in parsed["dirs"] as List<*>) {
                d as Map<*, *>
                val name = d["name"] as String
                val note = d["note"] as Map<*, *>?
                val folder = name.substringBefore('/', "").ifEmpty { null }
                val summary = note?.let {
                    NoteSummary(NoteId(name.substringAfter('/')), it["title"] as String, it["modified"] as Long, it["damaged"] as Boolean, folder)
                }
                entries[name] = Entry(d["dirModified"] as Long?, summary)
            }
            dirty = false
        } catch (e: Exception) {
            entries.clear()
        }
    }

    /** Writes the index; a crash leaves either the old file or the new one. */
    fun save() {
        val dirs = entries.entries.sortedBy { it.key }.map { (name, e) ->
            mapOf(
                "name" to name,
                "dirModified" to e.dirModified,
                "note" to e.summary?.let {
                    mapOf("title" to it.title, "modified" to it.modified, "damaged" to it.damaged)
                },
            )
        }
        val text = MiniJson.write(mapOf("version" to VERSION, "root" to rootKey, "dirs" to dirs)).toString()
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        dirty = false
    }

    companion object {
        private const val VERSION = 2L

        /** The key of directory [name] in [folder] (null: the top level). */
        fun key(folder: String?, name: String) = if (folder == null) name else "$folder/$name"
    }
}

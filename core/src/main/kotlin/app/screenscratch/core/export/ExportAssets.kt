package app.screenscratch.core.export

import app.screenscratch.core.store.NoteId
import app.screenscratch.core.store.NoteStore
import java.io.File

/** Reads a note's files for export by their path in the note directory, such as `assets/<hash>.png`. */
fun interface ExportAssets {
    /** The file's bytes, or null when it is missing or unreadable. */
    fun read(path: String): ByteArray?

    companion object {
        /** Has no files; images export as placeholders. */
        val NONE = ExportAssets { null }

        /** The assets of note [id] in [store]. */
        fun of(store: NoteStore, id: NoteId) = ExportAssets { path ->
            if (!path.startsWith(PREFIX)) null else runCatching { store.readAsset(id, path.removePrefix(PREFIX)) }.getOrNull()
        }

        /** Files under a note directory [dir]. */
        fun directory(dir: File) = ExportAssets { path ->
            val file = File(dir, path)
            if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) null
            else runCatching { file.readBytes() }.getOrNull()
        }

        private const val PREFIX = "assets/"
    }
}

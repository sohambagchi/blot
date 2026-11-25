package app.blot.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.blot.core.export.ExportAssets
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.model.Note
import java.io.File
import java.io.FileNotFoundException

/** Where exports are written and how they are handed to other apps. Blocking; call off the main thread except [shareIntent]. */
object ExportFiles {
    /** The cache directory shared through the FileProvider (see `res/xml/file_paths.xml`). */
    const val CACHE_DIR = "exports"

    /** Cached exports older than this are deleted when a new one is written. */
    const val KEEP_MS = 60 * 60 * 1000L

    /** A file name for [title]: characters other apps reject replaced, never empty. */
    fun fileName(title: String, format: ExportFormat): String {
        val base = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(80)
            .ifEmpty { "Note" }
        return "$base.${format.extension}"
    }

    /** Exports [note] into the app cache and returns the file. */
    fun writeToCache(
        context: Context,
        note: Note,
        assets: ExportAssets,
        options: ExportOptions,
        exporter: NoteExporter = NoteExporter(),
        now: Long = System.currentTimeMillis(),
    ): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { if (now - it.lastModified() > KEEP_MS) it.deleteRecursively() }
        // One subdirectory per export keeps the shared name readable without clashing with an export still being read.
        val sub = File(dir, now.toString(36)).apply { mkdirs() }
        val file = File(sub, fileName(note.title, options.format))
        try {
            file.outputStream().buffered().use { exporter.export(note, assets, options, it) }
        } catch (e: Throwable) {
            sub.deleteRecursively()
            throw e
        }
        return file
    }

    /** A content URI for a file from [writeToCache], readable by apps the intent grants it to. */
    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** A chooser that sends [file] (from [writeToCache]) as [format] to another app. */
    fun shareIntent(context: Context, file: File, format: ExportFormat, title: String = file.nameWithoutExtension): Intent {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the grant to the chosen app; EXTRA_STREAM alone does not.
        send.clipData = ClipData.newUri(context.contentResolver, title, uri)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Exports [note] to a document [uri] the user picked, replacing its content. */
    fun writeToUri(
        context: Context,
        uri: Uri,
        note: Note,
        assets: ExportAssets,
        options: ExportOptions,
        exporter: NoteExporter = NoteExporter(),
    ) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
        stream.buffered().use { exporter.export(note, assets, options, it) }
    }
}

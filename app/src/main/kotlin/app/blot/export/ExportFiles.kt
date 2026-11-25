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
import app.blot.core.model.NoteTitle
import java.io.File
import java.io.FileNotFoundException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where exports are written and how they are handed to other apps. Blocking; call off the main thread except [shareIntent]. */
object ExportFiles {
    /** The cache directory shared through the FileProvider (see `res/xml/file_paths.xml`). */
    const val CACHE_DIR = "exports"

    /** Cached exports older than this are deleted when a new one is written. */
    const val KEEP_MS = 60 * 60 * 1000L

    private val UNTITLED_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm", Locale.ROOT)

    /**
     * The export's name without extension: [note]'s title with characters file systems and other
     * apps reject replaced, or, for an untitled note, "Blot" and its creation time in [zone]
     * (e.g. "Blot 2026-11-24 2105").
     */
    fun baseName(note: Note, zone: ZoneId = ZoneId.systemDefault()): String =
        safeName(note.title).ifEmpty { "Blot " + UNTITLED_STAMP.format(note.created.atZone(zone)) }

    /** The file name for [note] exported as [format]; see [baseName]. */
    fun fileName(note: Note, format: ExportFormat, zone: ZoneId = ZoneId.systemDefault()): String =
        "${baseName(note, zone)}.${format.extension}"

    /** The subject of a share: the title as typed, or [baseName] for an untitled note. */
    fun shareTitle(note: Note, zone: ZoneId = ZoneId.systemDefault()): String =
        NoteTitle.clean(note.title).ifEmpty { baseName(note, zone) }

    /** [title] made safe as a file name: no path separators, reserved or control characters; "" if nothing is left. */
    fun safeName(title: String): String {
        val s = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
        if (s.codePointCount(0, s.length) <= MAX_NAME) return s
        return s.substring(0, s.offsetByCodePoints(0, MAX_NAME)).trimEnd('.', ' ')
    }

    /** Longest base name, in code points; leaves room for the extension under common 255-byte limits. */
    private const val MAX_NAME = 80

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
        val file = File(sub, fileName(note, options.format))
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

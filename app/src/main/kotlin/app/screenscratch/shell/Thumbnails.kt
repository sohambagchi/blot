package app.screenscratch.shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import app.screenscratch.canvas.SceneRenderer
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Paper
import app.screenscratch.core.model.Rect
import app.screenscratch.ui.library.PageKind
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** What a library card shows besides the title: the page kind and count. */
data class CardInfo(val kind: PageKind, val pages: Int) {
    companion object {
        fun of(note: Note): CardInfo = when (val p = note.page) {
            PageSetup.Infinite -> CardInfo(PageKind.Infinite, 1)
            is PageSetup.Pages -> CardInfo(
                when (p.paper) {
                    Paper.A4 -> PageKind.A4
                    Paper.LETTER -> PageKind.Letter
                    Paper.SCREEN -> PageKind.Screen
                },
                p.count,
            )
        }
    }
}

/**
 * Library thumbnails as PNGs in an app-private directory, keyed by note id, the note's modified
 * time and the theme they were drawn for, with the note's [CardInfo] beside them. Writing a
 * note's thumbnail removes those of its older versions. The notes folder and its library index
 * never hold thumbnails. Thread-safe.
 */
class ThumbnailCache(private val dir: File) {
    private val lock = Any()

    /** The cached PNG for this version of the note, or null. */
    fun file(id: String, modified: Long, dark: Boolean): File? =
        File(dir, "${key(id, modified, dark)}.png").takeIf { it.isFile }

    fun info(id: String, modified: Long): CardInfo? = try {
        val (kind, pages) = File(dir, "${base(id, modified)}.info").readText().trim().split(' ')
        CardInfo(PageKind.valueOf(kind), pages.toInt())
    } catch (e: Exception) {
        null
    }

    /** Stores [png] (null for a blank note) and [info] for this version of the note. */
    fun put(id: String, modified: Long, dark: Boolean, png: Bitmap?, info: CardInfo): Unit = synchronized(lock) {
        dir.mkdirs()
        val keep = base(id, modified)
        dir.listFiles { f -> f.name.startsWith(prefix(id)) && !f.name.startsWith("${keep}.") && !f.name.startsWith("${keep}_") }
            ?.forEach { it.delete() }
        writeAtomically(File(dir, "$keep.info")) { it.write("${info.kind.name} ${info.pages}".toByteArray()) }
        val target = File(dir, "${key(id, modified, dark)}.png")
        if (png == null) {
            writeAtomically(target) {}
        } else {
            writeAtomically(target) { png.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Forgets every version of note [id]. */
    fun remove(id: String): Unit = synchronized(lock) {
        dir.listFiles { f -> f.name.startsWith(prefix(id)) }?.forEach { it.delete() }
    }

    private fun writeAtomically(target: File, write: (FileOutputStream) -> Unit) {
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use(write)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("cannot write $target")
        }
    }

    companion object {
        /** The file name stem for a version of note [id] drawn for one theme. */
        fun key(id: String, modified: Long, dark: Boolean) = "${base(id, modified)}_${if (dark) "dark" else "light"}"

        private fun base(id: String, modified: Long) = "${prefix(id)}$modified"

        // Ids are directory names from the notes folder and may hold any character; escaping keeps
        // them file-safe and free of '_', which separates the parts.
        private fun prefix(id: String) = buildString {
            for (ch in id) {
                if (ch.isLetterOrDigit() && ch.code < 128 || ch == '-' || ch == '.') append(ch) else append("%%%04x".format(ch.code))
            }
            append('_')
        }

        /** Decodes a cached PNG; null for a blank note's empty file or an unreadable one. */
        fun decode(file: File): Bitmap? = if (file.length() == 0L) null else BitmapFactory.decodeFile(file.path)
    }
}

/** Renders a note's elements, fitted and centred on a transparent [WIDTH] × [HEIGHT] bitmap. */
object ThumbnailRenderer {
    const val WIDTH = 480
    const val HEIGHT = 200
    private const val MARGIN = 12f

    /** Pixels per canvas unit at most, so a small scribble isn't blown up. */
    private const val MAX_SCALE = 2f

    /** The thumbnail of [note] for the theme [dark], or null when it has no elements. [renderer] must be this thread's. */
    fun render(note: Note, dark: Boolean, renderer: SceneRenderer, width: Int = WIDTH, height: Int = HEIGHT): Bitmap? {
        if (note.elements.isEmpty()) return null
        val bounds = note.elements.map { it.bounds }.reduce(Rect::union)
        val scale = min(min((width - 2 * MARGIN) / bounds.width.coerceAtLeast(1f), (height - 2 * MARGIN) / bounds.height.coerceAtLeast(1f)), MAX_SCALE)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(width / 2f - bounds.centerX * scale, height / 2f - bounds.centerY * scale)
        canvas.scale(scale, scale)
        for (e in note.elements) renderer.drawElement(canvas, e, dark)
        return bmp
    }
}

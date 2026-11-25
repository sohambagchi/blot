package app.blot.shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import app.blot.canvas.AssetImages
import app.blot.canvas.TextBoxRenderer
import app.blot.core.model.AssetRef
import app.blot.core.model.TextBox
import app.blot.markdown.MarkdownImageLoader
import app.blot.markdown.MarkdownLayout
import app.blot.markdown.drawTextBox
import app.blot.markdown.textBoxLayout
import app.blot.markdown.textBoxWidth
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A note's images, decoded from its `assets/` and cached. Loads run through [launch] (off the
 * main thread) and call back when done; [bitmap] never blocks. Bitmaps are software
 * (`ARGB_8888`, never `HARDWARE`) so they can be drawn into thumbnails, PDF pages and the
 * low-latency buffer. Thread-safe.
 *
 * @param read the bytes of an asset by file name (`<sha-256>.<ext>`); may block.
 */
class NoteAssetImages(
    private val read: (String) -> ByteArray,
    private val launch: (() -> Unit) -> Unit,
    maxBytes: Int = defaultCacheBytes(),
) : AssetImages {
    private val lock = Any()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed = HashSet<String>()
    private val waiting = HashMap<String, MutableSet<() -> Unit>>()

    override fun bitmap(asset: AssetRef, onLoaded: () -> Unit): Bitmap? = request(fileName(asset), onLoaded)

    /** [bitmap] for a markdown image destination such as `assets/<hash>.png`. */
    fun byPath(path: String, onLoaded: () -> Unit): Bitmap? {
        val name = path.removePrefix("./").removePrefix("assets/")
        if (name.contains('/')) return null
        return request(name, onLoaded)
    }

    /** Loads [asset] on the calling thread if needed; for background rendering only. */
    fun loadNow(asset: AssetRef): Bitmap? {
        val name = fileName(asset)
        synchronized(lock) {
            cache.get(name)?.let { return it }
            if (name in failed) return null
        }
        return decodeInto(name)
    }

    private fun request(name: String, onLoaded: () -> Unit): Bitmap? {
        synchronized(lock) {
            cache.get(name)?.let { return it }
            if (name in failed) return null
            val first = name !in waiting
            waiting.getOrPut(name) { LinkedHashSet() }.add(onLoaded)
            if (!first) return null
        }
        launch {
            decodeInto(name)
            val callbacks = synchronized(lock) { waiting.remove(name) }.orEmpty()
            for (c in callbacks) c()
        }
        return null
    }

    private fun decodeInto(name: String): Bitmap? {
        val bmp = try {
            decode(read(name))
        } catch (e: Exception) {
            Log.w(TAG, "couldn't load asset $name", e)
            null
        }
        synchronized(lock) { if (bmp != null) cache.put(name, bmp) else failed += name }
        return bmp
    }

    companion object {
        private const val TAG = "NoteAssetImages"

        /** Longest side a decoded image keeps; larger ones are subsampled. */
        const val MAX_SIDE = 4096

        fun fileName(asset: AssetRef) = "${asset.sha256}.${asset.extension}"

        fun defaultCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        /** Decodes [bytes] as a software bitmap no larger than [MAX_SIDE] on its longest side; null if they aren't an image. */
        fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}

/**
 * Draws text boxes as markdown, laid out by [textBoxLayout] as every export does. Layouts are cached by element id, source, width, font size, colour and theme, and
 * built in full before they are shared, since the UI thread and the ink render thread both draw.
 * Thread-safe.
 *
 * @param images resolves `assets/…` images in the markdown; null draws their placeholders.
 * @param dark the theme: dark mode's muted, code and link colours.
 */
class MarkdownTextBoxRenderer(
    private val images: NoteAssetImages? = null,
    @Volatile var dark: Boolean = false,
) : TextBoxRenderer {
    private data class Key(val id: String, val markdown: String, val width: Int, val fontSize: Float, val color: Int, val dark: Boolean)

    private val layouts = LruCache<Key, MarkdownLayout>(CACHE_SIZE)

    /** Called on any thread when an image a text box was waiting for has loaded; the canvas should redraw. */
    @Volatile var onImageLoaded: () -> Unit = {}

    private val imageLoader = MarkdownImageLoader { path ->
        images?.byPath(path) {
            synchronized(layouts) { layouts.evictAll() }
            onImageLoaded()
        }
    }

    /** Cached layouts; for tests. */
    val cachedCount: Int get() = synchronized(layouts) { layouts.size() }

    override fun draw(canvas: Canvas, box: TextBox, color: Int) {
        val layout = layout(box, color)
        synchronized(layout) { drawTextBox(canvas, box, layout) }
    }

    /** The laid-out markdown's height; colour and theme never change it, so the box's own colour is used. */
    override fun measureHeight(box: TextBox): Float {
        val layout = layout(box, box.color.value)
        return synchronized(layout) { layout.height.toFloat() }
    }

    private fun layout(box: TextBox, color: Int): MarkdownLayout {
        val key = Key(box.id, box.markdown, textBoxWidth(box), box.fontSize, color, dark)
        return synchronized(layouts) { layouts.get(key) } ?: build(box, key).also { synchronized(layouts) { layouts.put(key, it) } }
    }

    private fun build(box: TextBox, key: Key): MarkdownLayout {
        val layout = textBoxLayout(box, key.color, key.dark, imageLoader)
        synchronized(layout) {
            layout.height
            layout.decorations
        }
        return layout
    }

    private companion object {
        const val CACHE_SIZE = 128
    }
}

package app.screenscratch.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import app.screenscratch.core.model.AssetRef
import app.screenscratch.core.model.TextBox

/**
 * Draws a text box's content. The canvas is in canvas units (the viewport is already applied),
 * so [TextBox.frame] and [TextBox.fontSize] are used as they are. Called from the UI thread and
 * from the ink render thread, so implementations must be thread-safe.
 */
fun interface TextBoxRenderer {
    /** Draws [box] in [color], which is [TextBox.color] after any dark-mode transform. */
    fun draw(canvas: Canvas, box: TextBox, color: Int)
}

/** Shows a text box's markdown source as plain wrapped text, until the markdown renderer replaces it. */
class PlainTextBoxRenderer : TextBoxRenderer {
    private class Key(val box: TextBox, val color: Int) {
        override fun equals(other: Any?) = other is Key && other.box === box && other.color == color
        override fun hashCode() = System.identityHashCode(box) * 31 + color
    }

    private val layouts = LruCache<Key, StaticLayout>(64)

    override fun draw(canvas: Canvas, box: TextBox, color: Int) {
        val key = Key(box, color)
        val layout = synchronized(layouts) { layouts[key] } ?: build(box, color).also { synchronized(layouts) { layouts.put(key, it) } }
        canvas.save()
        canvas.clipRect(box.frame.left, box.frame.top, box.frame.right, box.frame.bottom)
        canvas.translate(box.frame.x, box.frame.y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun build(box: TextBox, color: Int): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = box.fontSize
            this.color = color
        }
        val width = box.frame.width.toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(box.markdown, 0, box.markdown.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }
}

/**
 * Supplies decoded images for a note's assets. Called from the UI thread and from the ink render
 * thread, so implementations must be thread-safe and must not block on I/O.
 */
fun interface AssetImages {
    /**
     * The decoded bitmap for [asset], or null if it isn't loaded (a placeholder is drawn). An
     * implementation that loads asynchronously calls [onLoaded] once the bitmap is ready, so the
     * canvas redraws.
     */
    fun bitmap(asset: AssetRef, onLoaded: () -> Unit): Bitmap?

    companion object {
        /** Loads nothing; every image draws as a placeholder. */
        val NONE = AssetImages { _, _ -> null }
    }
}

package app.blot.markdown

import androidx.annotation.ColorInt

/**
 * Colours and base size for rendered markdown. [baseTextSize] is in dp; [MarkdownLayout]
 * multiplies it by the density it is given. Every other size is derived from it.
 */
data class MarkdownTheme(
    @ColorInt val textColor: Int,
    @ColorInt val mutedColor: Int,
    @ColorInt val codeBackground: Int,
    @ColorInt val linkColor: Int,
    @ColorInt val quoteBarColor: Int,
    /** Page colour behind the text; used for the tick inside a checked task box. */
    @ColorInt val background: Int,
    val baseTextSize: Float = 16f,
) {
    companion object {
        /** Light page: #1C1B19 text on #F6F5F2. */
        val Light = MarkdownTheme(
            textColor = 0xFF1C1B19.toInt(),
            mutedColor = 0xFF66635C.toInt(),
            codeBackground = 0xFFEFEDE8.toInt(),
            linkColor = 0xFF2F6BDE.toInt(),
            quoteBarColor = 0xFFD6D3CC.toInt(),
            background = 0xFFF6F5F2.toInt(),
        )

        /** Dark page: #ECEAE6 text on #17171A. */
        val Dark = MarkdownTheme(
            textColor = 0xFFECEAE6.toInt(),
            mutedColor = 0xFFA3A09A.toInt(),
            codeBackground = 0xFF2E2E33.toInt(),
            linkColor = 0xFF6EA0FF.toInt(),
            quoteBarColor = 0xFF4A4A51.toInt(),
            background = 0xFF17171A.toInt(),
        )
    }
}

/** Resolves an image destination as written in the source (e.g. `assets/<hash>.png`). */
fun interface MarkdownImageLoader {
    /**
     * Returns the decoded image, or null to draw a placeholder with the alt text. Called on every
     * layout rebuild (each edit or width change), so implementations should cache. The bitmap
     * must not be [android.graphics.Bitmap.Config.HARDWARE] if the layout will be drawn into a
     * Bitmap or PDF canvas.
     */
    fun load(path: String): android.graphics.Bitmap?

    companion object {
        /** Resolves nothing, so every image draws as a placeholder. */
        val None = MarkdownImageLoader { null }
    }
}

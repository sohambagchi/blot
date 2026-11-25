package app.blot.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.blot.canvas.AssetImages
import app.blot.core.export.ExportAssets
import app.blot.core.model.AssetRef
import app.blot.markdown.MarkdownImageLoader

/**
 * Decodes a note's images once per export, synchronously, as software bitmaps (a Bitmap or PDF
 * canvas cannot draw hardware ones). Serves both the scene's images and markdown images.
 * Not thread-safe; one per export.
 */
internal class ExportBitmaps(private val assets: ExportAssets) : AssetImages, MarkdownImageLoader {
    private val decoded = HashMap<String, Bitmap?>()

    override fun bitmap(asset: AssetRef, onLoaded: () -> Unit): Bitmap? = load(asset.path)

    override fun load(path: String): Bitmap? = decoded.getOrPut(path) {
        assets.read(path)?.let { bytes ->
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    fun recycle() {
        decoded.values.forEach { it?.recycle() }
        decoded.clear()
    }
}

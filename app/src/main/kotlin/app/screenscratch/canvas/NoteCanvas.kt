package app.screenscratch.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The inking canvas for [controller]'s note.
 *
 * While a stroke is drawn, the low-latency ink layer sits above the whole window over this
 * composable's bounds, so Compose content overlapping the canvas is hidden until pen-up; keep
 * toolbars beside the canvas rather than over it.
 *
 * @param textBoxRenderer draws text boxes; null keeps the plain-text fallback.
 * @param assetImages supplies images; null draws placeholders.
 */
@Composable
fun NoteCanvas(
    controller: CanvasController,
    modifier: Modifier = Modifier,
    textBoxRenderer: TextBoxRenderer? = null,
    assetImages: AssetImages? = null,
) {
    key(controller) {
        AndroidView(
            factory = { InkCanvasView(it, controller) },
            // Cut the SurfaceView's hole ourselves, as the pen test does: on Android 12 the window
            // only cuts it once the surface reports a first frame.
            modifier = modifier.clipToBounds().drawBehind { drawRect(Color.Black, blendMode = BlendMode.Clear) },
            update = { v ->
                if (textBoxRenderer != null && v.textBoxRenderer !== textBoxRenderer) v.textBoxRenderer = textBoxRenderer
                if (assetImages != null && v.assetImages !== assetImages) v.assetImages = assetImages
            },
        )
    }
}

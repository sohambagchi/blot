package app.blot.canvas

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A rounded rectangle of chrome over the canvas, in the canvas view's pixels; see
 * [CanvasController.inkExclusions].
 */
data class InkExclusion(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float = 0f) {
    companion object {
        /**
         * The exclusion for a chrome rectangle given relative to the canvas view, grown outward
         * to whole pixels so no anti-aliased edge of ink shows beside the chrome's border.
         */
        fun covering(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 0f) =
            InkExclusion(floor(left), floor(top), ceil(right), ceil(bottom), radius.coerceAtLeast(0f))
    }
}

/**
 * Keeps in-progress ink out of [InkExclusion]s: [clipOut] before drawing ink, [clear] to wipe
 * what a buffer already holds there. One instance per drawing thread.
 */
class InkClip {
    private val path = Path()
    private var pathFor: List<InkExclusion>? = null
    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR }

    /** Clips [canvas] to everything outside [exclusions]. Call between `save` and `restore`. */
    fun clipOut(canvas: Canvas, exclusions: List<InkExclusion>) {
        if (exclusions.isEmpty()) return
        canvas.clipOutPath(path(exclusions))
    }

    /** Clears [exclusions] in [canvas] to transparent. */
    fun clear(canvas: Canvas, exclusions: List<InkExclusion>) {
        for (e in exclusions) canvas.drawRoundRect(e.left, e.top, e.right, e.bottom, e.radius, e.radius, eraser)
    }

    private fun path(exclusions: List<InkExclusion>): Path {
        if (pathFor !== exclusions) {
            path.rewind()
            // All clockwise, so the non-zero fill is the union where they overlap.
            for (e in exclusions) path.addRoundRect(e.left, e.top, e.right, e.bottom, e.radius, e.radius, Path.Direction.CW)
            pathFor = exclusions
        }
        return path
    }
}

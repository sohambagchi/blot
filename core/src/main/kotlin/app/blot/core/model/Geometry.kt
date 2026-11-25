package app.blot.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * A position in canvas units.
 *
 * Canvas units are the note's coordinate space: x grows right, y grows down, and one unit
 * is one dp (1/160 inch) at 100% zoom.
 */
@Serializable
data class Point(val x: Float, val y: Float)

/** An axis-aligned rectangle in canvas units, from its top-left corner. Width and height are ≥ 0. */
@Serializable
data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
    init {
        require(width >= 0f && height >= 0f) { "negative size $width×$height" }
    }

    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    /** True when the rectangles overlap or touch. */
    fun intersects(other: Rect): Boolean =
        left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom

    /** The smallest rectangle containing both. */
    fun union(other: Rect): Rect = fromEdges(
        min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom),
    )

    /** Grows every edge outward by [d] canvas units. */
    fun inflate(d: Float): Rect = Rect(x - d, y - d, width + 2 * d, height + 2 * d)

    companion object {
        /** The rectangle with the given edges; swapped edges are put in order. */
        fun fromEdges(left: Float, top: Float, right: Float, bottom: Float): Rect {
            val l = min(left, right)
            val t = min(top, bottom)
            return Rect(l, t, max(left, right) - l, max(top, bottom) - t)
        }

        /** The rectangle centred on ([cx], [cy]) with half-extents [hw] and [hh]. */
        fun around(cx: Float, cy: Float, hw: Float, hh: Float): Rect = Rect(cx - hw, cy - hh, 2 * hw, 2 * hh)
    }
}

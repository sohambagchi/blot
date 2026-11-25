package app.blot.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

    /** The axis-aligned box around this rectangle turned by [rotation] radians about its centre. */
    fun rotatedBounds(rotation: Float): Rect {
        if (rotation == 0f) return this
        val c = abs(cos(rotation))
        val s = abs(sin(rotation))
        val hw = width / 2f
        val hh = height / 2f
        return around(centerX, centerY, hw * c + hh * s, hw * s + hh * c)
    }

    /**
     * The corners of this rectangle grown by [pad] and turned by [rotation] radians about its
     * centre, as `x0, y0, …`: top-left, top-right, bottom-right, bottom-left before turning.
     */
    fun rotatedCorners(rotation: Float, pad: Float = 0f): FloatArray {
        val c = cos(rotation)
        val s = sin(rotation)
        val hw = width / 2f + pad
        val hh = height / 2f + pad
        val out = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
        for (i in 0 until 4) {
            val lx = out[i * 2]
            val ly = out[i * 2 + 1]
            out[i * 2] = centerX + lx * c - ly * s
            out[i * 2 + 1] = centerY + lx * s + ly * c
        }
        return out
    }

    /** True when ([px], [py]) lies in this rectangle grown by [pad] and turned by [rotation] about its centre. */
    fun containsRotated(rotation: Float, px: Float, py: Float, pad: Float = 0f): Boolean {
        val dx = px - centerX
        val dy = py - centerY
        val c = cos(rotation)
        val s = sin(rotation)
        // The inverse rotation takes the point into the rectangle's own axes.
        val lx = dx * c + dy * s
        val ly = -dx * s + dy * c
        return abs(lx) <= width / 2f + pad && abs(ly) <= height / 2f + pad
    }

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

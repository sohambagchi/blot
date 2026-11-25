package app.blot.markdown.math

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Filled outlines (moves, lines, cubics, closes) in px, y down, filled with the non-zero rule.
 * Typeset maths is one of these: glyph outlines and rules, the same for the screen, PNG, PDF
 * (via [toPath]) and SVG (via [svgData]). Immutable.
 */
class MathPath internal constructor(
    private val verbs: ByteArray,
    private val coords: FloatArray,
    /** Ink bounds: the outlines' control points. Empty (all 0) when there are none. */
    val bounds: RectF,
) {
    val isEmpty: Boolean get() = verbs.isEmpty()

    /** An Android path of these outlines moved by ([dx], [dy]). */
    fun toPath(dx: Float = 0f, dy: Float = 0f): Path {
        val p = Path()
        p.fillType = Path.FillType.WINDING
        var c = 0
        for (v in verbs) {
            when (v) {
                MOVE -> { p.moveTo(coords[c] + dx, coords[c + 1] + dy); c += 2 }
                LINE -> { p.lineTo(coords[c] + dx, coords[c + 1] + dy); c += 2 }
                CUBIC -> {
                    p.cubicTo(
                        coords[c] + dx, coords[c + 1] + dy, coords[c + 2] + dx, coords[c + 3] + dy,
                        coords[c + 4] + dx, coords[c + 5] + dy,
                    )
                    c += 6
                }
                else -> p.close()
            }
        }
        return p
    }

    /** SVG path data for these outlines moved by ([dx], [dy]); [num] formats one coordinate. */
    fun svgData(dx: Float, dy: Float, num: (Float, StringBuilder) -> Unit): String {
        val sb = StringBuilder(coords.size * 6)
        var c = 0
        fun pt(i: Int) {
            num(coords[i] + dx, sb)
            sb.append(' ')
            num(coords[i + 1] + dy, sb)
        }
        for (v in verbs) {
            when (v) {
                MOVE -> { sb.append('M'); pt(c); c += 2 }
                LINE -> { sb.append('L'); pt(c); c += 2 }
                CUBIC -> {
                    sb.append('C'); pt(c); sb.append(' '); pt(c + 2); sb.append(' '); pt(c + 4)
                    c += 6
                }
                else -> sb.append('Z')
            }
        }
        return sb.toString()
    }

    internal companion object {
        const val MOVE: Byte = 0
        const val LINE: Byte = 1
        const val CUBIC: Byte = 2
        const val CLOSE: Byte = 3

        val EMPTY = MathPath(ByteArray(0), FloatArray(0), RectF())
    }
}

/** Accumulates [MathPath] outlines. Not thread-safe. */
internal class MathPathBuilder {
    private var verbs = ByteArray(64)
    private var coords = FloatArray(256)
    private var nv = 0
    private var nc = 0
    private var minX = Float.POSITIVE_INFINITY
    private var minY = Float.POSITIVE_INFINITY
    private var maxX = Float.NEGATIVE_INFINITY
    private var maxY = Float.NEGATIVE_INFINITY

    private fun verb(v: Byte) {
        if (nv == verbs.size) verbs = verbs.copyOf(nv * 2)
        verbs[nv++] = v
    }

    private fun point(x: Float, y: Float) {
        if (nc + 2 > coords.size) coords = coords.copyOf(coords.size * 2)
        coords[nc++] = x
        coords[nc++] = y
        minX = min(minX, x); maxX = max(maxX, x)
        minY = min(minY, y); maxY = max(maxY, y)
    }

    fun moveTo(x: Float, y: Float) { verb(MathPath.MOVE); point(x, y) }

    fun lineTo(x: Float, y: Float) { verb(MathPath.LINE); point(x, y) }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        verb(MathPath.CUBIC); point(x1, y1); point(x2, y2); point(x3, y3)
    }

    fun close() = verb(MathPath.CLOSE)

    /** A rectangle, wound like the font's outer contours once y is flipped. */
    fun rect(left: Float, top: Float, right: Float, bottom: Float) {
        if (right <= left || bottom <= top) return
        moveTo(left, bottom)
        lineTo(right, bottom)
        lineTo(right, top)
        lineTo(left, top)
        close()
    }

    /**
     * Adds [outline] (font units, y up) scaled by [scale] with its origin at ([x], [y]) in this
     * builder's y-down space.
     */
    fun glyph(outline: GlyphOutline, x: Float, y: Float, scale: Float) {
        val v = outline.verbs
        val c = outline.coords
        var i = 0
        for (verb in v) {
            when (verb) {
                MathPath.MOVE -> { moveTo(x + c[i] * scale, y - c[i + 1] * scale); i += 2 }
                MathPath.LINE -> { lineTo(x + c[i] * scale, y - c[i + 1] * scale); i += 2 }
                MathPath.CUBIC -> {
                    cubicTo(
                        x + c[i] * scale, y - c[i + 1] * scale, x + c[i + 2] * scale, y - c[i + 3] * scale,
                        x + c[i + 4] * scale, y - c[i + 5] * scale,
                    )
                    i += 6
                }
                else -> close()
            }
        }
    }

    fun build(): MathPath =
        if (nv == 0) MathPath.EMPTY
        else MathPath(verbs.copyOf(nv), coords.copyOf(nc), RectF(minX, minY, maxX, maxY))
}

/** One glyph's outline in font units, y up, and its control box. */
internal class GlyphOutline(
    val verbs: ByteArray,
    val coords: FloatArray,
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float,
)

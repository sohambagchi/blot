package app.blot.core.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where a [Background]'s marks fall inside [area], in canvas units:
 * - [Pattern.DOTS]: a dot at every ([xs], [ys]) pair.
 * - [Pattern.GRID]: vertical lines at [xs] and horizontal lines at [ys], each spanning [area].
 * - [Pattern.RULED]: horizontal lines at [ys] spanning [area]; [xs] is empty.
 */
class PatternMarks(val pattern: Pattern, val area: Rect, val xs: FloatArray, val ys: FloatArray) {
    companion object {
        /** Screen pixels below which the marks are thinned out. */
        const val MIN_SCREEN_SPACING = 6f

        /** The largest [stride]; beyond it the marks are not drawn at all. */
        const val MAX_STRIDE = 64

        /** Dot radius, as the design's dot grid. */
        const val DOT_RADIUS = 1.2f

        /** Grid and ruled line width. */
        const val LINE_WIDTH = 1f

        /**
         * How many cells apart the drawn marks are when one canvas unit is [pxPerUnit] pixels: the
         * smallest power of two that keeps them at least [MIN_SCREEN_SPACING] pixels apart, or 0
         * when that would exceed [MAX_STRIDE]. Thinned marks stay on the cells whose index is a
         * multiple of the stride, so they don't move as the view pans.
         */
        fun stride(spacing: Float, pxPerUnit: Float): Int {
            var k = 1
            while (spacing * k * pxPerUnit < MIN_SCREEN_SPACING) {
                k *= 2
                if (k > MAX_STRIDE) return 0
            }
            return k
        }

        /**
         * The marks of [background] that fall in [visible]. [sheet] is the page the pattern fills,
         * with its cells starting at the page's top-left corner; null is the infinite canvas, with
         * cells from the canvas origin. On a page, grid lines on the page's edges are left out, and
         * ruled lines start one spacing below the top and stop at least one spacing above the
         * bottom. Null when nothing is visible or [stride] is 0.
         */
        fun of(background: Background, sheet: Rect?, visible: Rect, stride: Int = 1): PatternMarks? {
            if (stride <= 0) return null
            val area = if (sheet == null) visible else intersection(sheet, visible) ?: return null
            if (area.width <= 0f || area.height <= 0f) return null
            val ox = sheet?.left ?: 0f
            val oy = sheet?.top ?: 0f
            val s = background.spacing
            val step = s * stride
            val none = FloatArray(0)
            return when (background.pattern) {
                Pattern.DOTS -> PatternMarks(
                    Pattern.DOTS, area,
                    positions(area.left, area.right, ox + s / 2, step, Int.MIN_VALUE, Int.MAX_VALUE),
                    positions(area.top, area.bottom, oy + s / 2, step, Int.MIN_VALUE, Int.MAX_VALUE),
                )
                Pattern.GRID -> {
                    // Lines strictly inside the page: index 1 up to the last before the far edge.
                    val lastX = if (sheet == null) Int.MAX_VALUE else lastInside(sheet.width, step)
                    val lastY = if (sheet == null) Int.MAX_VALUE else lastInside(sheet.height, step)
                    val first = if (sheet == null) Int.MIN_VALUE else 1
                    PatternMarks(
                        Pattern.GRID, area,
                        positions(area.left, area.right, ox, step, first, lastX),
                        positions(area.top, area.bottom, oy, step, first, lastY),
                    )
                }
                Pattern.RULED -> {
                    val ys = if (sheet == null) {
                        positions(area.top, area.bottom, oy, step, Int.MIN_VALUE, Int.MAX_VALUE)
                    } else {
                        // Rows k = 1… with y ≤ bottom − spacing, of which the stride keeps every k-th.
                        val lastRow = floor((sheet.height - s) / s + EPS).toInt()
                        positions(area.top, area.bottom, oy, step, 1, lastRow / stride)
                    }
                    PatternMarks(Pattern.RULED, area, none, ys)
                }
            }
        }

        private const val EPS = 1e-4f

        /** The highest index i with i × [step] strictly inside (0, [length]). */
        private fun lastInside(length: Float, step: Float): Int = ceil(length / step - EPS).toInt() - 1

        /** origin + i × step for i in [minIndex, maxIndex] that lie in [from, to]. */
        private fun positions(from: Float, to: Float, origin: Float, step: Float, minIndex: Int, maxIndex: Int): FloatArray {
            val i0 = max(ceil((from - origin) / step).toLong(), minIndex.toLong())
            val i1 = min(floor((to - origin) / step).toLong(), maxIndex.toLong())
            if (i1 < i0) return FloatArray(0)
            return FloatArray((i1 - i0 + 1).toInt()) { origin + (i0 + it) * step }
        }

        private fun intersection(a: Rect, b: Rect): Rect? {
            val l = max(a.left, b.left)
            val t = max(a.top, b.top)
            val r = min(a.right, b.right)
            val btm = min(a.bottom, b.bottom)
            return if (r <= l || btm <= t) null else Rect(l, t, r - l, btm - t)
        }
    }
}

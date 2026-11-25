package app.blot.core.geometry

import app.blot.core.model.Rect
import app.blot.core.model.ShapeKind
import kotlin.math.PI
import kotlin.math.min

/**
 * Each [ShapeKind]'s outline inscribed in a frame, unrotated. The proportions follow the toolbar
 * glyphs (24-unit icons in the design), scaled to the frame:
 *
 * - Rounded rectangle: corner radius 2/7 of the shorter side (the glyph's 4 on 14), at most
 *   [ROUND_CORNER_MAX].
 * - Triangle: apex at the top middle, base along the bottom.
 * - Diamond: the frame's edge midpoints.
 * - Hexagon: flat top and bottom, the side points at mid-height, the top and bottom edges inset
 *   by a quarter of the width (the glyph's 4.5 on 18; a regular hexagon's inset).
 * - Parallelogram: the top edge shifted right by 2/9 of the width (the glyph's 4 on 18).
 * - Cylinder: elliptical caps as wide as the frame, each cap's vertical radius 5/28 of the width
 *   (the glyph's 2.5 on 14) but at most a quarter of the height; the top cap is a whole ellipse,
 *   the bottom cap only its front half.
 *
 * Closed subpaths run clockwise on screen. The cylinder's front edge of the top cap is an open
 * subpath inside the closed silhouette that also runs clockwise, so a non-zero fill of the whole
 * path fills just the silhouette; [PathData.closedOnly] is the fill on its own.
 */
object ShapeOutlines {
    const val ROUND_CORNER_SHARE = 2f / 7f
    const val ROUND_CORNER_MAX = 32f
    const val HEXAGON_INSET = 0.25f
    const val PARALLELOGRAM_SLANT = 2f / 9f
    const val CYLINDER_CAP_PER_WIDTH = 5f / 28f
    const val CYLINDER_CAP_MAX_PER_HEIGHT = 0.25f

    private const val PI_F = PI.toFloat()

    /** [kind]'s outline in [frame]. */
    fun of(kind: ShapeKind, frame: Rect): PathData {
        val l = frame.left
        val t = frame.top
        val r = frame.right
        val b = frame.bottom
        val cx = frame.centerX
        val cy = frame.centerY
        val w = frame.width
        val p = PathData.Builder()
        when (kind) {
            ShapeKind.RECTANGLE -> p.moveTo(l, t).lineTo(r, t).lineTo(r, b).lineTo(l, b).close()
            ShapeKind.ROUNDED_RECTANGLE -> {
                val k = cornerRadius(frame)
                p.moveTo(l + k, t).lineTo(r - k, t)
                    .arcTo(r - k, t + k, k, k, -PI_F / 2, PI_F / 2).lineTo(r, b - k)
                    .arcTo(r - k, b - k, k, k, 0f, PI_F / 2).lineTo(l + k, b)
                    .arcTo(l + k, b - k, k, k, PI_F / 2, PI_F / 2).lineTo(l, t + k)
                    .arcTo(l + k, t + k, k, k, PI_F, PI_F / 2).close()
            }
            ShapeKind.ELLIPSE -> p.moveTo(r, cy).arcTo(cx, cy, w / 2, frame.height / 2, 0f, 2 * PI_F).close()
            ShapeKind.TRIANGLE -> p.moveTo(cx, t).lineTo(r, b).lineTo(l, b).close()
            ShapeKind.DIAMOND -> p.moveTo(cx, t).lineTo(r, cy).lineTo(cx, b).lineTo(l, cy).close()
            ShapeKind.HEXAGON -> {
                val i = w * HEXAGON_INSET
                p.moveTo(l + i, t).lineTo(r - i, t).lineTo(r, cy).lineTo(r - i, b).lineTo(l + i, b).lineTo(l, cy).close()
            }
            ShapeKind.PARALLELOGRAM -> {
                val o = w * PARALLELOGRAM_SLANT
                p.moveTo(l + o, t).lineTo(r, t).lineTo(r - o, b).lineTo(l, b).close()
            }
            ShapeKind.CYLINDER -> {
                val rx = w / 2
                val ry = cylinderCap(frame)
                val top = t + ry
                val bottom = b - ry
                // Silhouette: the top cap's back half, the right side, the bottom cap's front half.
                p.moveTo(l, top).arcTo(cx, top, rx, ry, PI_F, PI_F).lineTo(r, bottom)
                    .arcTo(cx, bottom, rx, ry, 0f, PI_F).close()
                // The top cap's front half.
                p.moveTo(r, top).arcTo(cx, top, rx, ry, 0f, PI_F)
            }
        }
        return p.build()
    }

    /** A rounded rectangle's corner radius in [frame]. */
    fun cornerRadius(frame: Rect): Float = min(min(frame.width, frame.height) * ROUND_CORNER_SHARE, ROUND_CORNER_MAX)

    /** A cylinder's cap's vertical radius in [frame]. */
    fun cylinderCap(frame: Rect): Float = min(frame.width * CYLINDER_CAP_PER_WIDTH, frame.height * CYLINDER_CAP_MAX_PER_HEIGHT)
}

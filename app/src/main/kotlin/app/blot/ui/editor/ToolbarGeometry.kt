package app.blot.ui.editor

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/** Where the toolbar goes when dropped, and where a [ToolbarPlacement] lands on screen. All values in dp. */
object ToolbarGeometry {
    /** Gap between a docked toolbar and the screen edge. */
    const val MARGIN = 16f
    /** A drop whose nearest bar edge is closer than this to a screen edge docks there. */
    const val SNAP_DISTANCE = 48f
    /** Top of the portrait rail in the design: below the back pill (16 + 52 + 16). */
    const val RAIL_TOP = 84f

    /**
     * The top-left corner of a bar of [w] × [h] for [placement] in a [cw] × [ch] window. A bar
     * that would cover one of [obstacles] moves vertically past it, [MARGIN] clear: below an
     * obstacle in the top half of the window, above one in the bottom half.
     */
    fun topLeft(placement: ToolbarPlacement?, w: Float, h: Float, cw: Float, ch: Float, obstacles: List<Rect> = emptyList()): Pair<Float, Float> {
        val (x, y) = placed(placement, w, h, cw, ch)
        var ny = y
        repeat(obstacles.size) {
            for (o in obstacles) {
                if (x < o.right && x + w > o.left && ny < o.bottom && ny + h > o.top) {
                    ny = if (o.center.y < ch / 2f) o.bottom + MARGIN else o.top - MARGIN - h
                }
            }
        }
        return x to ny.coerceIn(MARGIN, max(MARGIN, ch - h - MARGIN))
    }

    private fun placed(placement: ToolbarPlacement?, w: Float, h: Float, cw: Float, ch: Float): Pair<Float, Float> {
        val maxX = max(MARGIN, cw - w - MARGIN)
        val maxY = max(MARGIN, ch - h - MARGIN)
        return when (placement) {
            null -> if (isPortrait(cw, ch)) MARGIN to min(RAIL_TOP, maxY) else ((cw - w) / 2f).coerceIn(MARGIN, maxX) to MARGIN
            is ToolbarPlacement.Floating -> placement.x.coerceIn(MARGIN, maxX) to placement.y.coerceIn(MARGIN, maxY)
            is ToolbarPlacement.Docked -> when (placement.edge) {
                Edge.Top -> (placement.along - w / 2f).coerceIn(MARGIN, maxX) to MARGIN
                Edge.Bottom -> (placement.along - w / 2f).coerceIn(MARGIN, maxX) to maxY
                Edge.Left -> MARGIN to (placement.along - h / 2f).coerceIn(MARGIN, maxY)
                Edge.Right -> maxX to (placement.along - h / 2f).coerceIn(MARGIN, maxY)
            }
        }
    }

    /** Whether the toolbar is a vertical rail: as placed, or by default in portrait. */
    fun isVertical(placement: ToolbarPlacement?, cw: Float, ch: Float): Boolean =
        placement?.vertical ?: isPortrait(cw, ch)

    fun isPortrait(cw: Float, ch: Float) = ch > cw

    /**
     * The placement for a bar of [w] × [h] dropped with its top-left at ([x], [y]): docked to the
     * nearest edge within [SNAP_DISTANCE], otherwise floating where it was dropped, keeping its
     * orientation. Docking to the top or bottom makes it a pill and to a side a rail, centred
     * where the dropped bar was centred.
     */
    fun drop(x: Float, y: Float, w: Float, h: Float, cw: Float, ch: Float, vertical: Boolean): ToolbarPlacement {
        val distances = listOf(
            Edge.Left to x,
            Edge.Top to y,
            Edge.Right to cw - (x + w),
            Edge.Bottom to ch - (y + h),
        )
        val (edge, distance) = distances.minBy { it.second }
        if (distance > SNAP_DISTANCE) {
            return ToolbarPlacement.Floating(
                x.coerceIn(MARGIN, max(MARGIN, cw - w - MARGIN)),
                y.coerceIn(MARGIN, max(MARGIN, ch - h - MARGIN)),
                vertical,
            )
        }
        val along = if (edge == Edge.Top || edge == Edge.Bottom) x + w / 2f else y + h / 2f
        return ToolbarPlacement.Docked(edge, along)
    }

    /** The side of the bar that menus and the colour panel open towards: away from the docked edge. */
    fun opensTowards(placement: ToolbarPlacement?, cw: Float, ch: Float): Edge {
        if (placement is ToolbarPlacement.Docked) {
            return when (placement.edge) {
                Edge.Top -> Edge.Bottom
                Edge.Bottom -> Edge.Top
                Edge.Left -> Edge.Right
                Edge.Right -> Edge.Left
            }
        }
        val vertical = isVertical(placement, cw, ch)
        if (placement is ToolbarPlacement.Floating) {
            return if (vertical) {
                if (placement.x > cw / 2f) Edge.Left else Edge.Right
            } else {
                if (placement.y > ch / 2f) Edge.Top else Edge.Bottom
            }
        }
        return if (vertical) Edge.Right else Edge.Bottom
    }
}

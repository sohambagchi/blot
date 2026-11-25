package app.blot.canvas

import app.blot.core.edit.Selection
import app.blot.core.edit.SelectionEdits
import app.blot.core.edit.Similarity
import app.blot.core.edit.Snap
import app.blot.core.model.Arrowhead
import app.blot.core.model.Element
import app.blot.core.model.Line
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.TextBox
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The select, shape, line and text tools' pen gestures, in canvas units. While a gesture runs
 * the note is unchanged and the canvas view draws [lasso], [marquee], [draft] and the selection
 * through [transform]; the gesture's end applies it as one undoable edit.
 */
internal class ToolGestures(private val c: CanvasController) {
    private sealed class Gesture(val x0: Float, val y0: Float) {
        var maxDistance = 0f
    }
    private class Lasso(x0: Float, y0: Float) : Gesture(x0, y0)
    private class Marquee(x0: Float, y0: Float) : Gesture(x0, y0)
    private class Move(x0: Float, y0: Float, val ids: Set<String>) : Gesture(x0, y0)
    private class Scale(
        x0: Float, y0: Float,
        val ids: Set<String>,
        val anchorX: Float, val anchorY: Float,
        val cornerX: Float, val cornerY: Float,
        val minFactor: Float,
    ) : Gesture(x0, y0)
    private class DrawShape(x0: Float, y0: Float, val id: String) : Gesture(x0, y0)
    private class DrawLine(x0: Float, y0: Float, val id: String) : Gesture(x0, y0)
    private class PlaceText(x0: Float, y0: Float) : Gesture(x0, y0)

    private var gesture: Gesture? = null
    private var lastX = 0f
    private var lastY = 0f

    /** The lasso drawn so far, `x0, y0, …`; [lassoCount] points are valid. */
    var lasso = FloatArray(128)
        private set
    var lassoCount = 0
        private set

    /** The selection rectangle being dragged, or null. */
    var marquee: Rect? = null
        private set

    /** The shape or line being drawn, not yet in the note. */
    var draft: Element? = null
        private set

    /** Applied to the selection while it is moved or scaled. */
    var transform: Similarity = Similarity.IDENTITY
        private set

    val active: Boolean get() = gesture != null

    /** Canvas units for [v] dp on screen at the current zoom. */
    private fun dp(v: Float) = v / c.viewport.zoom

    fun start(x: Float, y: Float) {
        cancel()
        lastX = x
        lastY = y
        gesture = when (c.tool) {
            Tool.SELECT -> selectStart(x, y)
            Tool.SHAPE -> DrawShape(x, y, Element.newId())
            Tool.LINE -> DrawLine(x, y, Element.newId())
            Tool.TEXT -> PlaceText(x, y)
            else -> null
        }
        if (gesture is Lasso) {
            lassoCount = 0
            addLassoPoint(x, y)
        }
        c.overlayChanged()
    }

    private fun selectStart(x: Float, y: Float): Gesture {
        val ids = c.selection
        val bounds = c.selectionBounds ?: return if (c.selectMode == SelectMode.LASSO) Lasso(x, y) else Marquee(x, y)
        val box = bounds.inflate(dp(SELECTION_PAD_DP))
        val corners = floatArrayOf(box.left, box.top, box.right, box.top, box.right, box.bottom, box.left, box.bottom)
        var best = -1
        var bestD = dp(HANDLE_REACH_DP)
        for (k in 0 until 4) {
            val d = hypot(x - corners[k * 2], y - corners[k * 2 + 1])
            if (d <= bestD) {
                best = k
                bestD = d
            }
        }
        if (best >= 0) {
            val a = (best + 2) % 4
            val size = max(bounds.width, bounds.height)
            return Scale(
                x, y, ids,
                corners[a * 2], corners[a * 2 + 1], corners[best * 2], corners[best * 2 + 1],
                minFactor = if (size <= 0f) 1f else min(1f, MIN_SCALED_SIZE / size),
            )
        }
        if (box.inflate(dp(PICK_DP)).contains(x, y)) return Move(x, y, ids)
        return if (c.selectMode == SelectMode.LASSO) Lasso(x, y) else Marquee(x, y)
    }

    fun move(x: Float, y: Float) {
        val g = gesture ?: return
        lastX = x
        lastY = y
        g.maxDistance = max(g.maxDistance, hypot(x - g.x0, y - g.y0))
        when (g) {
            is Lasso -> addLassoPoint(x, y)
            is Marquee -> marquee = Rect.fromEdges(g.x0, g.y0, x, y)
            is Move -> transform = Similarity.translate(x - g.x0, y - g.y0)
            is Scale -> {
                // The grabbed corner follows the pen, keeping the offset at which it was grabbed.
                val f = SelectionEdits.cornerScale(
                    g.anchorX, g.anchorY, g.cornerX, g.cornerY,
                    x - (g.x0 - g.cornerX), y - (g.y0 - g.cornerY), g.minFactor,
                )
                transform = Similarity.scale(f, g.anchorX, g.anchorY)
            }
            is DrawShape -> draft = if (isTap(g)) null else Shape(
                g.id, c.shapeKind, Snap.dragRect(g.x0, g.y0, x, y, c.constrainShapes), c.color, c.width, c.shapeFill,
            )
            is DrawLine -> draft = if (isTap(g)) null else line(g, x, y)
            is PlaceText -> Unit
        }
        c.overlayChanged()
    }

    private fun line(g: DrawLine, x: Float, y: Float): Line {
        val end = if (c.snapAngles) Snap.angle(g.x0, g.y0, x, y) else Point(x, y)
        val arrows = c.lineArrows
        return Line(
            g.id,
            listOf(Point(g.x0, g.y0), end),
            c.color,
            c.width,
            startArrow = if (arrows == LineArrows.BOTH) c.arrowhead else Arrowhead.NONE,
            endArrow = if (arrows == LineArrows.NONE) Arrowhead.NONE else c.arrowhead,
        )
    }

    private fun isTap(g: Gesture) = g.maxDistance < dp(TAP_SLOP_DP)

    fun end() {
        val g = gesture ?: return
        val x = lastX
        val y = lastY
        val tap = isTap(g)
        val committedDraft = draft
        val ring = lasso.copyOf(lassoCount * 2)
        reset()
        when (g) {
            is Lasso -> c.select(if (tap) pickAt(g.x0, g.y0) else Selection.lasso(c.note.value.elements, ring).toSet())
            is Marquee -> c.select(if (tap) pickAt(g.x0, g.y0) else Selection.rectangle(c.note.value.elements, Rect.fromEdges(g.x0, g.y0, x, y)).toSet())
            is Move -> if (tap) {
                val hit = pickAt(g.x0, g.y0)
                if (hit.isNotEmpty() && hit.first() !in g.ids) c.select(hit)
            } else {
                c.transformSelection(g.ids, Similarity.translate(x - g.x0, y - g.y0))
            }
            is Scale -> {
                val f = SelectionEdits.cornerScale(
                    g.anchorX, g.anchorY, g.cornerX, g.cornerY,
                    x - (g.x0 - g.cornerX), y - (g.y0 - g.cornerY), g.minFactor,
                )
                if (!tap && f != 1f) c.transformSelection(g.ids, Similarity.scale(f, g.anchorX, g.anchorY))
            }
            is DrawShape, is DrawLine -> {
                val d = committedDraft
                if (!tap && d != null && (d !is Shape || (d.frame.width > 0f && d.frame.height > 0f))) {
                    c.edit { it.copy(elements = it.elements + d) }
                }
            }
            is PlaceText -> placeText(g, x, y, tap)
        }
        c.overlayChanged()
    }

    private fun placeText(g: PlaceText, x: Float, y: Float, tap: Boolean) {
        if (tap) {
            val hit = c.note.value.elements.lastOrNull { it is TextBox && it.frame.inflate(dp(PICK_DP)).contains(g.x0, g.y0) }
            if (hit is TextBox) {
                c.beginTextEdit(hit)
                return
            }
        }
        val size = c.textSize
        val frame = if (tap) {
            Rect(g.x0, g.y0 - size * 0.6f, CanvasController.DEFAULT_TEXT_WIDTH, size * TEXT_LINE_HEIGHT)
        } else {
            Rect(min(g.x0, x), min(g.y0, y), max(abs(x - g.x0), MIN_TEXT_WIDTH), size * TEXT_LINE_HEIGHT)
        }
        c.beginTextEdit(TextBox(Element.newId(), frame, "", size, c.color))
    }

    fun cancel() {
        if (gesture == null) return
        reset()
        c.overlayChanged()
    }

    private fun reset() {
        gesture = null
        lassoCount = 0
        marquee = null
        draft = null
        transform = Similarity.IDENTITY
    }

    private fun pickAt(x: Float, y: Float): Set<String> =
        setOfNotNull(Selection.pick(c.note.value.elements, x, y, dp(PICK_DP), c.outlineCache::outline)?.id)

    private fun addLassoPoint(x: Float, y: Float) {
        val n = lassoCount
        if (n > 0 && hypot(x - lasso[n * 2 - 2], y - lasso[n * 2 - 1]) < dp(LASSO_STEP_DP)) return
        if (lasso.size < (n + 1) * 2) lasso = lasso.copyOf(lasso.size * 2)
        lasso[n * 2] = x
        lasso[n * 2 + 1] = y
        lassoCount = n + 1
    }

    companion object {
        /** A pen that moves less than this (dp on screen) between down and up has tapped. */
        const val TAP_SLOP_DP = 6f

        /** How far from a tap an element's ink may be and still be picked, dp on screen. */
        const val PICK_DP = 6f

        /** The selection box's gap around the selected elements' bounds, dp on screen. */
        const val SELECTION_PAD_DP = 6f

        /** How far from a corner handle a pen-down grabs it, dp on screen. */
        const val HANDLE_REACH_DP = 22f

        /** Lasso points closer than this to the last one (dp on screen) are skipped. */
        const val LASSO_STEP_DP = 2f

        /** Scaling stops when the selection's longer side would be smaller than this, canvas units. */
        const val MIN_SCALED_SIZE = 4f

        /** A new text box's height, in font sizes, before it is measured. */
        const val TEXT_LINE_HEIGHT = 1.4f

        /** The narrowest text box a drag makes, canvas units. */
        const val MIN_TEXT_WIDTH = 40f
    }
}

internal fun Rect.contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

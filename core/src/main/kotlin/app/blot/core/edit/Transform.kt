package app.blot.core.edit

import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox
import kotlin.math.max

/**
 * A uniform scale by [scale] about ([originX], [originY]), then a move by ([dx], [dy]), in canvas
 * units: `p' = origin + (p − origin) × scale + d`. [scale] is > 0.
 */
data class Similarity(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val originX: Float = 0f,
    val originY: Float = 0f,
) {
    init {
        require(scale > 0f) { "scale $scale" }
    }

    val isIdentity: Boolean get() = dx == 0f && dy == 0f && scale == 1f

    fun x(x: Float): Float = originX + (x - originX) * scale + dx
    fun y(y: Float): Float = originY + (y - originY) * scale + dy
    fun rect(r: Rect): Rect = Rect(x(r.x), y(r.y), r.width * scale, r.height * scale)

    companion object {
        val IDENTITY = Similarity()

        fun translate(dx: Float, dy: Float) = Similarity(dx = dx, dy = dy)
        fun scale(scale: Float, originX: Float, originY: Float) = Similarity(scale = scale, originX = originX, originY = originY)
    }
}

/**
 * This element moved and scaled by [t], with the same id. Widths, outline widths and font sizes
 * scale with it, so a stroke looks the same at the new size. Stroke samples are snapped to
 * `note.json`'s 1/16-unit grid, so the moved stroke is the one a reload draws.
 */
fun Element.transformed(t: Similarity): Element {
    if (t.isIdentity) return this
    return when (this) {
        is Stroke -> copy(
            points = points.map { it.copy(x = PartialErase.snapXy(t.x(it.x)), y = PartialErase.snapXy(t.y(it.y))) },
            width = width * t.scale,
        )
        is Shape -> copy(frame = t.rect(frame), width = width * t.scale)
        is Line -> copy(points = points.map { Point(t.x(it.x), t.y(it.y)) }, width = width * t.scale)
        is TextBox -> copy(frame = t.rect(frame), fontSize = fontSize * t.scale)
        is Image -> copy(frame = t.rect(frame))
    }
}

/** This element with the id [id] and nothing else changed. */
fun Element.withId(id: String): Element = when (this) {
    is Stroke -> copy(id = id)
    is Shape -> copy(id = id)
    is Line -> copy(id = id)
    is TextBox -> copy(id = id)
    is Image -> copy(id = id)
}

/** Selection edits on a list of elements in stacking order. Ids not in the list are ignored. */
object SelectionEdits {
    /** [elements] with those in [ids] transformed by [t], in place in the stacking order. */
    fun transform(elements: List<Element>, ids: Set<String>, t: Similarity): List<Element> =
        if (t.isIdentity || ids.isEmpty()) elements else elements.map { if (it.id in ids) it.transformed(t) else it }

    /** [elements] without those in [ids]. */
    fun delete(elements: List<Element>, ids: Set<String>): List<Element> = elements.filterNot { it.id in ids }

    /**
     * [elements] with copies of those in [ids] added on top, in their stacking order, moved by
     * ([dx], [dy]) and with fresh ids. Returns the new list and the copies' ids.
     */
    fun duplicate(
        elements: List<Element>,
        ids: Set<String>,
        dx: Float,
        dy: Float,
        newId: () -> String = Element::newId,
    ): Pair<List<Element>, List<String>> {
        val copies = elements.filter { it.id in ids }.map { it.withId(newId()).transformed(Similarity.translate(dx, dy)) }
        return (elements + copies) to copies.map { it.id }
    }

    /**
     * The uniform scale factor for dragging the corner of a box at ([cornerX], [cornerY]) to
     * ([x], [y]) while the opposite corner ([anchorX], [anchorY]) stays put: the drag projected on
     * the box's diagonal, so the corner follows the pen along it. At least [minFactor].
     */
    fun cornerScale(
        anchorX: Float, anchorY: Float,
        cornerX: Float, cornerY: Float,
        x: Float, y: Float,
        minFactor: Float,
    ): Float {
        val dx = cornerX - anchorX
        val dy = cornerY - anchorY
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return 1f
        return max(minFactor, ((x - anchorX) * dx + (y - anchorY) * dy) / len2)
    }
}

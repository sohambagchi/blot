package app.blot.core.edit

import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A uniform scale by [scale] and a turn by [rotation] radians (clockwise on screen) about
 * ([originX], [originY]), then a move by ([dx], [dy]), in canvas units:
 * `p' = origin + R(rotation) · (p − origin) × scale + d`. [scale] is > 0.
 */
data class Similarity(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val originX: Float = 0f,
    val originY: Float = 0f,
    val rotation: Float = 0f,
) {
    init {
        require(scale > 0f) { "scale $scale" }
    }

    private val cos = cos(rotation)
    private val sin = sin(rotation)

    val isIdentity: Boolean get() = dx == 0f && dy == 0f && scale == 1f && rotation == 0f

    fun mapX(x: Float, y: Float): Float =
        if (rotation == 0f) originX + (x - originX) * scale + dx
        else originX + ((x - originX) * cos - (y - originY) * sin) * scale + dx

    fun mapY(x: Float, y: Float): Float =
        if (rotation == 0f) originY + (y - originY) * scale + dy
        else originY + ((x - originX) * sin + (y - originY) * cos) * scale + dy

    /** The axis-aligned box around [r] mapped by this transform. */
    fun bounds(r: Rect): Rect {
        if (rotation == 0f) return Rect(mapX(r.x, r.y), mapY(r.x, r.y), r.width * scale, r.height * scale)
        var l = Float.POSITIVE_INFINITY
        var t = Float.POSITIVE_INFINITY
        var rr = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        for ((x, y) in listOf(r.left to r.top, r.right to r.top, r.right to r.bottom, r.left to r.bottom)) {
            val px = mapX(x, y)
            val py = mapY(x, y)
            l = min(l, px); rr = max(rr, px)
            t = min(t, py); b = max(b, py)
        }
        return Rect.fromEdges(l, t, rr, b)
    }

    /**
     * [frame], a rectangle drawn turned by its own rotation about its centre, after this
     * transform: its centre moves, its size scales, and this transform's [rotation] is added to
     * the element's (see [Angles.add]).
     */
    fun frame(frame: Rect): Rect {
        if (rotation == 0f) return Rect(mapX(frame.x, frame.y), mapY(frame.x, frame.y), frame.width * scale, frame.height * scale)
        val cx = mapX(frame.centerX, frame.centerY)
        val cy = mapY(frame.centerX, frame.centerY)
        return Rect.around(cx, cy, frame.width * scale / 2f, frame.height * scale / 2f)
    }

    companion object {
        val IDENTITY = Similarity()

        fun translate(dx: Float, dy: Float) = Similarity(dx = dx, dy = dy)
        fun scale(scale: Float, originX: Float, originY: Float) = Similarity(scale = scale, originX = originX, originY = originY)
        fun rotate(radians: Float, originX: Float, originY: Float) = Similarity(rotation = radians, originX = originX, originY = originY)
    }
}

/** Angle arithmetic in radians. */
object Angles {
    private const val TWO_PI = (2 * PI).toFloat()

    /** [a] wrapped into (−π, π]. */
    fun normalize(a: Float): Float {
        var r = a % TWO_PI
        if (r <= -PI.toFloat()) r += TWO_PI
        if (r > PI.toFloat()) r -= TWO_PI
        return r
    }

    /** [a] + [b] wrapped into (−π, π]; exactly [a] when [b] is 0, so unrotated elements stay at 0. */
    fun add(a: Float, b: Float): Float = if (b == 0f) a else normalize(a + b)
}

/**
 * This element moved, scaled and turned by [t], with the same id. Widths, outline widths and
 * font sizes scale with it, so a stroke looks the same at the new size. Strokes and lines turn
 * their points (and a stroke its samples' pen orientation); shapes, text boxes and images move
 * their frame's centre and add the turn to their rotation. Stroke samples are snapped to
 * `note.json`'s 1/16-unit grid, so the changed stroke is the one a reload draws.
 */
fun Element.transformed(t: Similarity): Element {
    if (t.isIdentity) return this
    return when (this) {
        is Stroke -> copy(
            points = points.map {
                it.copy(
                    x = PartialErase.snapXy(t.mapX(it.x, it.y)),
                    y = PartialErase.snapXy(t.mapY(it.x, it.y)),
                    orientation = Angles.add(it.orientation, t.rotation),
                )
            },
            width = width * t.scale,
        )
        is Shape -> copy(frame = t.frame(frame), width = width * t.scale, rotation = Angles.add(rotation, t.rotation))
        is Line -> copy(points = points.map { Point(t.mapX(it.x, it.y), t.mapY(it.x, it.y)) }, width = width * t.scale)
        is TextBox -> copy(frame = t.frame(frame), fontSize = fontSize * t.scale, rotation = Angles.add(rotation, t.rotation))
        is Image -> copy(frame = t.frame(frame), rotation = Angles.add(rotation, t.rotation))
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

/** This element in [group] (null for none) and nothing else changed. */
fun Element.withGroup(group: String?): Element = if (group == this.group) this else when (this) {
    is Stroke -> copy(group = group)
    is Shape -> copy(group = group)
    is Line -> copy(group = group)
    is TextBox -> copy(group = group)
    is Image -> copy(group = group)
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
     * ([dx], [dy]) and with fresh ids. Copies of a group form a new group of their own. Returns
     * the new list and the copies' ids.
     */
    fun duplicate(
        elements: List<Element>,
        ids: Set<String>,
        dx: Float,
        dy: Float,
        newId: () -> String = Element::newId,
    ): Pair<List<Element>, List<String>> {
        val groups = HashMap<String, String>()
        val copies = elements.filter { it.id in ids }.map { e ->
            val g = e.group?.let { groups.getOrPut(it, newId) }
            e.withId(newId()).withGroup(g).transformed(Similarity.translate(dx, dy))
        }
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

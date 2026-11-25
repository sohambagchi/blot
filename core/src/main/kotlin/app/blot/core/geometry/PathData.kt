package app.blot.core.geometry

import app.blot.core.model.Point
import app.blot.core.model.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A segment of a [SubPath], drawn from the current point (the previous segment's [end], or the
 * subpath's start). Coordinates are canvas units.
 */
sealed interface PathSegment {
    val end: Point

    data class LineTo(override val end: Point) : PathSegment

    data class QuadTo(val control: Point, override val end: Point) : PathSegment

    data class CubicTo(val c1: Point, val c2: Point, override val end: Point) : PathSegment

    /**
     * Part of the axis-aligned ellipse centred on ([cx], [cy]) with radii [rx] and [ry], from
     * angle [start] through [sweep] radians; positive sweeps turn clockwise on screen (y down).
     * The current point is the ellipse's point at [start]. |[sweep]| is at most π, so every
     * arc maps to one SVG arc command and to one `android.graphics.Path.arcTo`.
     */
    data class ArcTo(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val start: Float, val sweep: Float) : PathSegment {
        init {
            require(abs(sweep) <= PI.toFloat() + 1e-4f) { "arc sweep $sweep" }
        }

        override val end: Point get() = pointAt(start + sweep)

        fun pointAt(angle: Float) = Point(cx + rx * cos(angle), cy + ry * sin(angle))
    }
}

/** A run of segments from [start]; [closed] joins the last point back to [start]. */
data class SubPath(val start: Point, val segments: List<PathSegment>, val closed: Boolean)

/** A flattened subpath: points `x0, y0, …`, closed or open. A closed one doesn't repeat its first point. */
class Polyline(val xy: FloatArray, val closed: Boolean) {
    val count: Int get() = xy.size / 2
}

/**
 * A vector outline, the one description of a shape's or line's geometry that the canvas, hit
 * testing, bounds and SVG all draw from. Coordinates are canvas units.
 */
data class PathData(val subpaths: List<SubPath>) {
    /** Only the closed subpaths: what a fill covers. */
    fun closedOnly(): PathData = PathData(subpaths.filter { it.closed })

    /**
     * Straight-segment approximations of the subpaths, turned by [rotation] radians (clockwise on
     * screen) about ([cx], [cy]). Curves become [CURVE_STEPS] segments, arcs one segment per
     * 2π/[CIRCLE_STEPS] of sweep; the error is far below a pixel at the sizes notes use.
     */
    fun flatten(rotation: Float = 0f, cx: Float = 0f, cy: Float = 0f): List<Polyline> {
        val c = cos(rotation)
        val s = sin(rotation)
        return subpaths.map { sp ->
            val out = FloatList()
            fun addRaw(x: Float, y: Float) {
                if (rotation == 0f) out.add(x, y) else {
                    val dx = x - cx
                    val dy = y - cy
                    out.add(cx + dx * c - dy * s, cy + dx * s + dy * c)
                }
            }
            addRaw(sp.start.x, sp.start.y)
            var cur = sp.start
            for (seg in sp.segments) {
                when (seg) {
                    is PathSegment.LineTo -> addRaw(seg.end.x, seg.end.y)
                    is PathSegment.QuadTo -> for (i in 1..CURVE_STEPS) {
                        val t = i.toFloat() / CURVE_STEPS
                        val u = 1 - t
                        addRaw(
                            u * u * cur.x + 2 * u * t * seg.control.x + t * t * seg.end.x,
                            u * u * cur.y + 2 * u * t * seg.control.y + t * t * seg.end.y,
                        )
                    }
                    is PathSegment.CubicTo -> for (i in 1..CURVE_STEPS) {
                        val p = Bezier.point(cur, seg.c1, seg.c2, seg.end, i.toFloat() / CURVE_STEPS)
                        addRaw(p.x, p.y)
                    }
                    is PathSegment.ArcTo -> {
                        val n = max(2, ceil(abs(seg.sweep) / (2 * PI) * CIRCLE_STEPS - 1e-4).toInt())
                        for (i in 1..n) {
                            val p = seg.pointAt(seg.start + seg.sweep * i / n)
                            addRaw(p.x, p.y)
                        }
                    }
                }
                cur = seg.end
            }
            if (sp.closed && out.size >= 4) {
                // Drop a last point that lands back on the first.
                val n = out.size
                if (abs(out[n - 2] - out[0]) < EPS && abs(out[n - 1] - out[1]) < EPS) out.size -= 2
            }
            Polyline(out.toArray(), sp.closed)
        }
    }

    /**
     * The axis-aligned box around this path stroked [width] wide with mitred joins (miter limit
     * [miterLimit], beyond which a join is bevelled, as in SVG and Android) and butt caps, after
     * turning it by [rotation] about ([cx], [cy]). Curves count by their flattened points, so the
     * box may miss a curve's true extreme by the flattening error (a few thousandths of a unit
     * per hundred units of radius).
     */
    fun strokeBounds(width: Float, rotation: Float = 0f, cx: Float = 0f, cy: Float = 0f, miterLimit: Float = 4f): Rect {
        val box = Box()
        val h = width / 2f
        for (pl in flatten(rotation, cx, cy)) {
            val xy = pl.xy
            val n = pl.count
            for (i in 0 until n) box.add(xy[i * 2], xy[i * 2 + 1])
            if (h == 0f || n < 2) continue
            val segs = if (pl.closed) n else n - 1
            for (k in 0 until segs) {
                val j = (k + 1) % n
                val dx = xy[j * 2] - xy[k * 2]
                val dy = xy[j * 2 + 1] - xy[k * 2 + 1]
                val len = hypot(dx, dy)
                if (len == 0f) continue
                val nx = -dy / len * h
                val ny = dx / len * h
                box.add(xy[k * 2] + nx, xy[k * 2 + 1] + ny); box.add(xy[k * 2] - nx, xy[k * 2 + 1] - ny)
                box.add(xy[j * 2] + nx, xy[j * 2 + 1] + ny); box.add(xy[j * 2] - nx, xy[j * 2 + 1] - ny)
            }
            // Mitre tips at sharp joins.
            for (i in 0 until n) {
                if (!pl.closed && (i == 0 || i == n - 1)) continue
                val p = (i - 1 + n) % n
                val q = (i + 1) % n
                val ax = xy[p * 2] - xy[i * 2]
                val ay = xy[p * 2 + 1] - xy[i * 2 + 1]
                val bx = xy[q * 2] - xy[i * 2]
                val by = xy[q * 2 + 1] - xy[i * 2 + 1]
                val la = hypot(ax, ay)
                val lb = hypot(bx, by)
                if (la == 0f || lb == 0f) continue
                val ux = ax / la + bx / lb
                val uy = ay / la + by / lb
                val sum = hypot(ux, uy)
                // sin(φ/2) for the angle φ between the two edges at the join.
                val sinHalf = hypot(ax / la - bx / lb, ay / la - by / lb) / 2f
                if (sum < 1e-6f || sinHalf < 1e-6f || 1f / sinHalf > miterLimit) continue
                val d = h / sinHalf
                box.add(xy[i * 2] - ux / sum * d, xy[i * 2 + 1] - uy / sum * d)
            }
        }
        return box.rect()
    }

    /**
     * The box around every point and control point. It contains the path (a Bézier lies in its
     * control points' hull; arcs count by their ellipse's extremes within the sweep, sampled).
     */
    fun controlBounds(): Rect {
        val box = Box()
        for (sp in subpaths) {
            box.add(sp.start.x, sp.start.y)
            for (seg in sp.segments) {
                when (seg) {
                    is PathSegment.LineTo -> Unit
                    is PathSegment.QuadTo -> box.add(seg.control.x, seg.control.y)
                    is PathSegment.CubicTo -> { box.add(seg.c1.x, seg.c1.y); box.add(seg.c2.x, seg.c2.y) }
                    is PathSegment.ArcTo -> for (i in 0..CIRCLE_STEPS / 2) {
                        val p = seg.pointAt(seg.start + seg.sweep * i / (CIRCLE_STEPS / 2))
                        box.add(p.x, p.y)
                    }
                }
                box.add(seg.end.x, seg.end.y)
            }
        }
        return box.rect()
    }

    /**
     * SVG path data: `M`, `L`, `Q`, `C`, `A` and `Z` commands with absolute coordinates written by
     * [num], which appends a number to the builder.
     */
    fun svg(num: (Float, StringBuilder) -> Unit): String {
        val sb = StringBuilder()
        fun pt(p: Point) {
            num(p.x, sb)
            sb.append(' ')
            num(p.y, sb)
        }
        for (sp in subpaths) {
            sb.append('M')
            pt(sp.start)
            for (seg in sp.segments) {
                when (seg) {
                    is PathSegment.LineTo -> { sb.append('L'); pt(seg.end) }
                    is PathSegment.QuadTo -> { sb.append('Q'); pt(seg.control); sb.append(' '); pt(seg.end) }
                    is PathSegment.CubicTo -> { sb.append('C'); pt(seg.c1); sb.append(' '); pt(seg.c2); sb.append(' '); pt(seg.end) }
                    is PathSegment.ArcTo -> {
                        sb.append('A')
                        num(seg.rx, sb); sb.append(' ')
                        num(seg.ry, sb)
                        // No x-rotation; never the large arc (sweeps are at most π); sweep flag 1 is clockwise on screen.
                        sb.append(" 0 0 ").append(if (seg.sweep >= 0f) '1' else '0').append(' ')
                        pt(seg.end)
                    }
                }
            }
            if (sp.closed) sb.append('Z')
        }
        return sb.toString()
    }

    /** Builds a [PathData] one subpath at a time. */
    class Builder {
        private val subpaths = ArrayList<SubPath>()
        private var start: Point? = null
        private var segments = ArrayList<PathSegment>()

        fun moveTo(x: Float, y: Float) = apply {
            flush(false)
            start = Point(x, y)
        }

        fun lineTo(x: Float, y: Float) = apply { segments += PathSegment.LineTo(Point(x, y)) }

        fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = apply { segments += PathSegment.QuadTo(Point(cx, cy), Point(x, y)) }

        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
            apply { segments += PathSegment.CubicTo(Point(c1x, c1y), Point(c2x, c2y), Point(x, y)) }

        /** An arc of the ellipse at ([cx], [cy]) from [start] through [sweep] radians, split into parts of at most π. */
        fun arcTo(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float) = apply {
            val parts = max(1, ceil(abs(sweep) / PI.toFloat() - 1e-4f).toInt())
            for (i in 0 until parts) segments += PathSegment.ArcTo(cx, cy, rx, ry, start + sweep * i / parts, sweep / parts)
        }

        fun close() = apply { flush(true) }

        fun build(): PathData {
            flush(false)
            return PathData(subpaths.toList())
        }

        private fun flush(closed: Boolean) {
            val s = start ?: return
            subpaths += SubPath(s, segments.toList(), closed)
            start = null
            segments = ArrayList()
        }
    }

    companion object {
        /** Straight segments per Bézier when flattening. */
        const val CURVE_STEPS = 16

        /** Straight segments per full turn of an arc when flattening. */
        const val CIRCLE_STEPS = 48

        private const val EPS = 1e-3f
    }
}

/** Cubic Bézier evaluation. */
object Bezier {
    fun point(p0: Point, c1: Point, c2: Point, p3: Point, t: Float): Point {
        val u = 1 - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return Point(a * p0.x + b * c1.x + c * c2.x + d * p3.x, a * p0.y + b * c1.y + c * c2.y + d * p3.y)
    }

    /**
     * The cubic split at [t] by de Casteljau's construction: the control points of the part
     * before [t] ([first]) or after it.
     */
    fun split(p0: Point, c1: Point, c2: Point, p3: Point, t: Float, first: Boolean): List<Point> {
        fun lerp(a: Point, b: Point) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val ab = lerp(p0, c1)
        val bc = lerp(c1, c2)
        val cd = lerp(c2, p3)
        val abc = lerp(ab, bc)
        val bcd = lerp(bc, cd)
        val m = lerp(abc, bcd)
        return if (first) listOf(p0, ab, abc, m) else listOf(m, bcd, cd, p3)
    }
}

private class Box {
    var l = Float.POSITIVE_INFINITY
    var t = Float.POSITIVE_INFINITY
    var r = Float.NEGATIVE_INFINITY
    var b = Float.NEGATIVE_INFINITY

    fun add(x: Float, y: Float) {
        l = min(l, x); r = max(r, x)
        t = min(t, y); b = max(b, y)
    }

    fun rect(): Rect = if (l > r) Rect(0f, 0f, 0f, 0f) else Rect.fromEdges(l, t, r, b)
}

/** A growable float array. */
internal class FloatList(capacity: Int = 32) {
    private var a = FloatArray(capacity)
    var size = 0

    operator fun get(i: Int) = a[i]

    fun add(x: Float, y: Float) {
        if (size + 2 > a.size) a = a.copyOf(max(a.size * 2, size + 2))
        a[size++] = x
        a[size++] = y
    }

    fun toArray(): FloatArray = a.copyOf(size)
}

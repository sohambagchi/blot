package app.screenscratch.core.edit

import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.core.model.format.PointCodec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Partial-eraser geometry: a stroke is cut where the eraser passes over it, keeping the point
 * lists of what survives.
 *
 * Ported from xnotes' area eraser (`Stroke.erasedBy` and `EraseSession`), MIT,
 * Copyright (c) 2026 Shardul Vikram Singh, https://github.com/shardulvs/xnotes-android: a sample
 * the eraser covers is dropped, and the surviving samples split into maximal contiguous runs,
 * each a new stroke. Changes from xnotes: the eraser is the capsule swept between two eraser
 * samples, not a circle; it reaches the ink's edge rather than the centre line; and each cut ends
 * on a point interpolated onto the eraser's edge, so the cut doesn't depend on how far apart the
 * pen's samples were, and an eraser crossing between two samples still cuts.
 */
object PartialErase {
    /**
     * The runs of [points] left after the capsule from ([ax], [ay]) to ([bx], [by]) with radius
     * [reach] passes: null when it covers no part of the polyline (nothing changes), an empty
     * list when it covers all of it. Runs are in order; each has at least one point.
     */
    fun split(points: List<StrokePoint>, ax: Float, ay: Float, bx: Float, by: Float, reach: Float): List<List<StrokePoint>>? {
        val n = points.size
        if (n == 0) return null
        val r2 = reach * reach
        fun covered(p: StrokePoint) = PlaneMath.segmentPointDistanceSq(ax, ay, bx, by, p.x, p.y) <= r2
        fun dist(t: Float, p: StrokePoint, q: StrokePoint): Float =
            PlaneMath.segmentPointDistanceSq(ax, ay, bx, by, p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t) - r2

        val inside = BooleanArray(n) { covered(points[it]) }
        var cut = inside.any { it }
        val runs = ArrayList<List<StrokePoint>>()
        var run: ArrayList<StrokePoint>? = if (inside[0]) null else arrayListOf(points[0])
        for (i in 0 until n - 1) {
            val p = points[i]
            val q = points[i + 1]
            when {
                !inside[i] && !inside[i + 1] -> {
                    val sd = PlaneMath.segmentSegmentDistanceSq(ax, ay, bx, by, p.x, p.y, q.x, q.y)
                    if (sd <= r2) {
                        // The eraser crosses between two kept samples.
                        cut = true
                        val tMin = minimise { dist(it, p, q) }
                        run!! += lerp(p, q, crossing(0f, tMin) { dist(it, p, q) })
                        runs += run
                        run = arrayListOf(lerp(p, q, crossing(1f, tMin) { dist(it, p, q) }))
                    }
                    run!! += q
                }
                !inside[i] && inside[i + 1] -> {
                    run!! += lerp(p, q, crossing(0f, 1f) { dist(it, p, q) })
                    runs += run
                    run = null
                }
                inside[i] && !inside[i + 1] -> {
                    run = arrayListOf(lerp(p, q, crossing(1f, 0f) { dist(it, p, q) }), q)
                }
                else -> Unit // A capsule is convex: a segment with both ends in it lies in it.
            }
        }
        if (run != null) runs += run
        if (!cut) return null
        return runs.map { dedupe(it) }
    }

    /**
     * The strokes left of [stroke] after the eraser capsule from ([ax], [ay]) to ([bx], [by])
     * with radius [r] passes, each with a fresh id and its times counted from its own first
     * point; null when the eraser misses the stroke's ink. The eraser reaches half the stroke's
     * width past its samples.
     */
    fun erase(
        stroke: Stroke,
        ax: Float, ay: Float, bx: Float, by: Float, r: Float,
        newId: () -> String = Element::newId,
    ): List<Stroke>? {
        val reach = r + stroke.width / 2f
        val box = Rect.fromEdges(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)).inflate(reach)
        if (!stroke.bounds.intersects(box)) return null
        val runs = split(stroke.points, ax, ay, bx, by, reach) ?: return null
        return runs.map { pts ->
            val t0 = pts.first().timeMs
            stroke.copy(id = newId(), points = pts.map { it.copy(timeMs = it.timeMs - t0) })
        }
    }

    // The function is convex on [0, 1] (distance from a moving point to a convex set), so a
    // ternary search finds its minimum and bisection finds where it crosses zero.
    private inline fun minimise(f: (Float) -> Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(40) {
            val m1 = lo + (hi - lo) / 3
            val m2 = hi - (hi - lo) / 3
            if (f(m1) <= f(m2)) hi = m2 else lo = m1
        }
        return (lo + hi) / 2
    }

    /** The t between [outside] (f > 0) and [inside] (f ≤ 0) where f crosses zero, on the outside's side. */
    private inline fun crossing(outside: Float, inside: Float, f: (Float) -> Float): Float {
        var o = outside
        var i = inside
        repeat(24) {
            val m = (o + i) / 2
            if (f(m) <= 0f) i = m else o = m
        }
        return o
    }

    private fun lerp(p: StrokePoint, q: StrokePoint, t: Float): StrokePoint = StrokePoint(
        x = snapXy(p.x + (q.x - p.x) * t),
        y = snapXy(p.y + (q.y - p.y) * t),
        pressure = snapPressure(p.pressure + (q.pressure - p.pressure) * t),
        tilt = p.tilt + (q.tilt - p.tilt) * t,
        orientation = if (t < 0.5f) p.orientation else q.orientation,
        timeMs = p.timeMs + ((q.timeMs - p.timeMs) * t).toLong(),
    )

    /** Drops an interpolated end point (only a run's ends are interpolated) that snapped onto its neighbour. */
    private fun dedupe(run: List<StrokePoint>): List<StrokePoint> {
        var from = 0
        var to = run.size
        if (to - from >= 2 && same(run[0], run[1])) from++
        if (to - from >= 2 && same(run[to - 1], run[to - 2])) to--
        return if (from == 0 && to == run.size) run else run.subList(from, to).toList()
    }

    private fun same(a: StrokePoint, b: StrokePoint) = abs(a.x - b.x) < 1e-6f && abs(a.y - b.y) < 1e-6f

    internal fun snapXy(v: Float): Float =
        (v.toDouble() * PointCodec.XY_STEPS_PER_UNIT).roundToLong().toFloat() / PointCodec.XY_STEPS_PER_UNIT

    internal fun snapPressure(p: Float): Float =
        (p.coerceIn(0f, 1f).toDouble() * PointCodec.PRESSURE_STEPS).roundToLong().toFloat() / PointCodec.PRESSURE_STEPS
}

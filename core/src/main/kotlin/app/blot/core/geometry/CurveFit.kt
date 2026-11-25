package app.blot.core.geometry

import app.blot.core.edit.PlaneMath
import app.blot.core.model.Point
import kotlin.math.abs
import kotlin.math.hypot

/** Turns a pen drag into the few points a curved line passes through. */
object CurveFit {
    /** A curve keeps at most this many points; a drag that needs more is simplified harder. */
    const val MAX_POINTS = 8

    /** How far a straight drag's curve bows out, as a share of its length. */
    const val BOW = 0.12f

    /**
     * The points a curve through the drag samples [xy] (`x0, y0, …`, at least one) passes
     * through: the drag simplified by Ramer–Douglas–Peucker with [tolerance] (canvas units), which
     * is doubled until at most [maxPoints] are left. The first and last samples are kept. A drag
     * that simplifies to its two ends bows out by [BOW] of its length, towards the side the pen
     * strayed to most (left of the drag's direction if it never strayed), so it is still a curve.
     */
    fun fit(xy: FloatArray, tolerance: Float, maxPoints: Int = MAX_POINTS): List<Point> {
        val n = xy.size / 2
        require(n >= 1) { "no samples" }
        val first = Point(xy[0], xy[1])
        val last = Point(xy[n * 2 - 2], xy[n * 2 - 1])
        if (n == 1) return listOf(first, last)
        var tol = tolerance.coerceAtLeast(1e-3f)
        var kept = simplify(xy, tol)
        while (kept.size > maxPoints) {
            tol *= 2
            kept = simplify(xy, tol)
        }
        if (kept.size > 2) return kept
        val dx = last.x - first.x
        val dy = last.y - first.y
        val chord = hypot(dx, dy)
        if (chord == 0f) return listOf(first, last)
        // Signed offset from the chord; positive is to the right of the drag's direction on screen.
        var strayed = 0f
        for (i in 1 until n - 1) {
            val d = (dx * (xy[i * 2 + 1] - first.y) - dy * (xy[i * 2] - first.x)) / chord
            if (abs(d) > abs(strayed)) strayed = d
        }
        val side = if (strayed > 0f) 1f else -1f
        val bow = BOW * chord * side
        val mid = Point((first.x + last.x) / 2 - dy / chord * bow, (first.y + last.y) / 2 + dx / chord * bow)
        return listOf(first, mid, last)
    }

    /**
     * The samples of [xy] that Ramer–Douglas–Peucker keeps at [tolerance]: the ends, and every
     * sample further than [tolerance] from the chord of the span it splits.
     */
    fun simplify(xy: FloatArray, tolerance: Float): List<Point> {
        val n = xy.size / 2
        if (n <= 2) return List(n) { Point(xy[it * 2], xy[it * 2 + 1]) }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        stack.addLast(n - 1)
        val t2 = tolerance * tolerance
        while (stack.isNotEmpty()) {
            val b = stack.removeLast()
            val a = stack.removeLast()
            var worst = -1
            var worstD = t2
            for (i in a + 1 until b) {
                val d = PlaneMath.segmentPointDistanceSq(xy[a * 2], xy[a * 2 + 1], xy[b * 2], xy[b * 2 + 1], xy[i * 2], xy[i * 2 + 1])
                if (d > worstD) {
                    worst = i
                    worstD = d
                }
            }
            if (worst < 0) continue
            keep[worst] = true
            stack.addLast(a); stack.addLast(worst)
            stack.addLast(worst); stack.addLast(b)
        }
        return (0 until n).filter { keep[it] }.map { Point(xy[it * 2], xy[it * 2 + 1]) }
    }
}

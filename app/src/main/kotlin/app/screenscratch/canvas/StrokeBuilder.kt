package app.screenscratch.canvas

import app.screenscratch.core.ink.StrokeEnd
import app.screenscratch.core.ink.StrokeOptions
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.PenKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.core.model.format.PointCodec
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** How strokes are shaped: the perfect-freehand options, shared by finished and in-progress ink. */
object InkStyle {
    const val THINNING = 0.65
    const val SMOOTHING = 0.5
    const val STREAMLINE = 0.3

    // perfect-freehand's pressure simulation and first-point default (see StrokeOutliner).
    private const val RATE_OF_PRESSURE_CHANGE = 0.275
    const val FIRST_SIMULATED_PRESSURE = 0.25f

    /** Outline options for a finished [stroke]. */
    fun options(stroke: Stroke): StrokeOptions = StrokeOptions(
        size = stroke.width.toDouble(),
        thinning = THINNING,
        smoothing = SMOOTHING,
        streamline = STREAMLINE,
        simulatePressure = !stroke.pressureReported,
        start = StrokeEnd(cap = true),
        end = StrokeEnd(cap = true),
        last = true,
    )

    /** The outline radius at [pressure] for a stroke of [width]: perfect-freehand's `getStrokeRadius` with linear easing. */
    fun radius(width: Float, pressure: Float): Float = width * (0.5f - THINNING.toFloat() * (0.5f - pressure))

    /** perfect-freehand's `simulatePressure`: the next pressure after moving [distance] with a stroke of [width]. */
    fun simulatePressure(previous: Float, distance: Float, width: Float): Float {
        val sp = min(1f, distance / width)
        val rp = min(1f, 1 - sp)
        return min(1f, previous + (rp - previous) * (sp * RATE_OF_PRESSURE_CHANGE.toFloat()))
    }
}

/**
 * One piece of in-progress ink in canvas units: the convex hull of a circle of radius [r0] at
 * ([x0], [y0]) and one of radius [r1] at ([x1], [y1]). A stroke's first piece has both ends at
 * the first sample.
 */
data class LiveSegment(val x0: Float, val y0: Float, val r0: Float, val x1: Float, val y1: Float, val r1: Float)

/**
 * Collects one stroke's samples in canvas units and hands out the in-progress ink for each.
 *
 * Positions and pressures are snapped to `note.json`'s quantisation as they arrive, so the
 * outline drawn at pen-up is the one a reload draws. Tilt and orientation are clamped to their
 * ranges and time is made relative to the first sample and non-decreasing.
 */
class StrokeBuilder(
    val color: Argb,
    val width: Float,
    val pressureReported: Boolean,
    val pen: PenKind = PenKind.PEN,
) {
    private val points = ArrayList<StrokePoint>()
    private var startMs = 0L
    private var lastRadius = 0f
    private var simulated = InkStyle.FIRST_SIMULATED_PRESSURE

    val size: Int get() = points.size
    val isEmpty: Boolean get() = points.isEmpty()

    /** Adds a sample at canvas ([x], [y]) and returns the ink to draw for it, or null if it adds nothing. */
    fun add(x: Float, y: Float, pressure: Float, tilt: Float, orientation: Float, timeMs: Long): LiveSegment? {
        if (points.isEmpty()) startMs = timeMs
        val prev = points.lastOrNull()
        val p = StrokePoint(
            x = snapXy(x),
            y = snapXy(y),
            pressure = snapPressure(pressure),
            tilt = tilt.coerceIn(0f, (PI / 2).toFloat()),
            orientation = wrapAngle(orientation),
            timeMs = max(prev?.timeMs ?: 0L, timeMs - startMs),
        )
        if (prev != null && prev.x == p.x && prev.y == p.y) {
            // Same position: keep the sample (its pressure and time count) but draw nothing new.
            points += p
            return null
        }
        points += p
        val r = if (pressureReported) {
            InkStyle.radius(width, p.pressure)
        } else {
            val d = if (prev == null) 0f else hypot(p.x - prev.x, p.y - prev.y)
            simulated = if (prev == null) simulated else InkStyle.simulatePressure(simulated, d, width)
            InkStyle.radius(width, simulated)
        }
        val seg = if (prev == null) LiveSegment(p.x, p.y, r, p.x, p.y, r) else LiveSegment(prev.x, prev.y, lastRadius, p.x, p.y, r)
        lastRadius = r
        return seg
    }

    /** The finished stroke, or null if no sample was added. */
    fun build(id: String): Stroke? =
        if (points.isEmpty()) null else Stroke(id, points.toList(), color, width, pen, pressureReported)

    /** Every in-progress segment so far, for redrawing the whole stroke. */
    fun segments(): List<LiveSegment> {
        val out = ArrayList<LiveSegment>(points.size)
        var simulated = InkStyle.FIRST_SIMULATED_PRESSURE
        var prev: StrokePoint? = null
        var prevR = 0f
        for (p in points) {
            if (prev != null && prev.x == p.x && prev.y == p.y) continue
            val r = if (pressureReported) InkStyle.radius(width, p.pressure) else {
                if (prev != null) simulated = InkStyle.simulatePressure(simulated, hypot(p.x - prev.x, p.y - prev.y), width)
                InkStyle.radius(width, simulated)
            }
            out += if (prev == null) LiveSegment(p.x, p.y, r, p.x, p.y, r) else LiveSegment(prev.x, prev.y, prevR, p.x, p.y, r)
            prev = p
            prevR = r
        }
        return out
    }

    companion object {
        fun snapXy(v: Float): Float = (v.toDouble() * PointCodec.XY_STEPS_PER_UNIT).roundToLong().toFloat() / PointCodec.XY_STEPS_PER_UNIT

        fun snapPressure(p: Float): Float {
            val c = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
            return (c.toDouble() * PointCodec.PRESSURE_STEPS).roundToLong().toFloat() / PointCodec.PRESSURE_STEPS
        }

        private fun wrapAngle(a: Float): Float {
            if (a.isNaN()) return 0f
            var v = a.toDouble()
            while (v > PI) v -= 2 * PI
            while (v < -PI) v += 2 * PI
            return v.toFloat()
        }
    }
}

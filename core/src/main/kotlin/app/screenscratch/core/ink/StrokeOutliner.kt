// Kotlin port of perfect-freehand 1.2.3 (tag v1.2.3, commit f56f097e0e211fffa1601b93883e4d9f9dccf122):
// https://github.com/steveruizok/perfect-freehand/tree/v1.2.3/packages/perfect-freehand/src
//
// MIT License
//
// Copyright (c) 2021 Stephen Ruiz Ltd
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package app.screenscratch.core.ink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Outline polygon of a stroke through [points]. */
fun getStroke(points: List<FreehandPoint>, options: StrokeOptions = StrokeOptions()): List<OutlinePoint> =
    StrokeOutliner().run {
        outline(points, options)
        outlinePoints()
    }

/** Outline polygon of a stroke through [points] as x0, y0, x1, y1, …, ready for a `Path`. */
fun getStrokeFlat(points: List<FreehandPoint>, options: StrokeOptions = StrokeOptions()): FloatArray =
    StrokeOutliner().run {
        outline(points, options)
        toFloatArray()
    }

/** The streamlined points that [getStrokeOutlinePoints] outlines. Uses [StrokeOptions.size], `streamline` and `last`. */
fun getStrokePoints(points: List<FreehandPoint>, options: StrokeOptions = StrokeOptions()): List<StrokePoint> =
    StrokeOutliner().run {
        load(points)
        computeStrokePoints(options)
        strokePoints()
    }

/** Outline polygon around [points] from [getStrokePoints]. */
fun getStrokeOutlinePoints(points: List<StrokePoint>, options: StrokeOptions = StrokeOptions()): List<OutlinePoint> =
    StrokeOutliner().run {
        loadStrokePoints(points)
        computeOutline(options)
        outlinePoints()
    }

internal fun getStrokeRadius(size: Double, thinning: Double, pressure: Double, easing: Easing = Easing.Linear): Double =
    size * easing.ease(0.5 - thinning * (0.5 - pressure))

internal fun simulatePressure(prevPressure: Double, distance: Double, size: Double): Double {
    val sp = min(1.0, distance / size)
    val rp = min(1.0, 1 - sp)
    return min(1.0, prevPressure + (rp - prevPressure) * (sp * RATE_OF_PRESSURE_CHANGE))
}

private const val RATE_OF_PRESSURE_CHANGE = 0.275
private const val FIXED_PI = PI + 0.0001
private const val START_CAP_SEGMENTS = 13
private const val END_CAP_SEGMENTS = 29
private const val CORNER_CAP_SEGMENTS = 13
private const val END_NOISE_THRESHOLD = 3.0
private const val MIN_STREAMLINE_T = 0.15
private const val STREAMLINE_T_RANGE = 0.85
private const val MIN_RADIUS = 0.01
private const val DEFAULT_FIRST_PRESSURE = 0.25
private const val DEFAULT_PRESSURE = 0.5

/**
 * Reusable outliner that keeps its working buffers between calls, so re-outlining an in-progress
 * stroke on every input event allocates nothing once the buffers have grown. Not thread-safe.
 */
class StrokeOutliner {
    // Input points after upstream's one- and two-point padding.
    private var inX = DoubleArray(64)
    private var inY = DoubleArray(64)
    private var inP = DoubleArray(64)
    private var inCount = 0

    // Stroke points (getStrokePoints' output), as parallel arrays.
    private var sx = DoubleArray(64)
    private var sy = DoubleArray(64)
    private var sp = DoubleArray(64)
    private var svx = DoubleArray(64)
    private var svy = DoubleArray(64)
    private var sd = DoubleArray(64)
    private var sl = DoubleArray(64)
    private var sCount = 0

    // Left and right sides of the outline, x/y interleaved.
    private var left = DoubleArray(128)
    private var leftCount = 0
    private var right = DoubleArray(128)
    private var rightCount = 0

    /** x0, y0, x1, y1, … of the latest outline. Only the first `pointCount * 2` entries are valid. */
    var xy = FloatArray(256)
        private set

    /** Number of points in the latest outline. */
    var pointCount = 0
        private set

    /** Outlines [points] into [xy] and returns the number of outline points. */
    fun outline(points: List<FreehandPoint>, options: StrokeOptions = StrokeOptions()): Int {
        load(points)
        return outlineLoaded(options)
    }

    /** Outlines the first [count] points of [xyp] (x, y, pressure triples) into [xy] and returns the number of outline points. */
    fun outline(xyp: FloatArray, count: Int, options: StrokeOptions = StrokeOptions()): Int {
        require(count >= 0 && count * 3 <= xyp.size) { "count $count out of range for ${xyp.size} floats" }
        beginLoad(count)
        for (i in 0 until count) addInput(xyp[i * 3].toDouble(), xyp[i * 3 + 1].toDouble(), xyp[i * 3 + 2].toDouble())
        finishLoad()
        return outlineLoaded(options)
    }

    /** A copy of the latest outline as x0, y0, x1, y1, …. */
    fun toFloatArray(): FloatArray = xy.copyOf(pointCount * 2)

    /** The latest outline as points. */
    fun outlinePoints(): List<OutlinePoint> = List(pointCount) { OutlinePoint(xy[it * 2], xy[it * 2 + 1]) }

    private fun outlineLoaded(options: StrokeOptions): Int {
        computeStrokePoints(options)
        computeOutline(options)
        return pointCount
    }

    internal fun load(points: List<FreehandPoint>) {
        beginLoad(points.size)
        for (p in points) addInput(p.x.toDouble(), p.y.toDouble(), p.pressure.toDouble())
        finishLoad()
    }

    private fun beginLoad(count: Int) {
        // Padding can grow the input to 5 points.
        val capacity = max(count, 5)
        if (inX.size < capacity) {
            inX = DoubleArray(capacity)
            inY = DoubleArray(capacity)
            inP = DoubleArray(capacity)
        }
        inCount = 0
    }

    private fun addInput(x: Double, y: Double, p: Double) {
        inX[inCount] = x
        inY[inCount] = y
        inP[inCount] = p
        inCount++
    }

    private fun finishLoad() {
        // Two points become five, to avoid "dash" lines with tapered ends; upstream's interpolated points drop pressure.
        if (inCount == 2) {
            val x0 = inX[0]
            val y0 = inY[0]
            val x1 = inX[1]
            val y1 = inY[1]
            for (i in 1 until 5) {
                val t = i / 4.0
                inX[i] = x0 + (x1 - x0) * t
                inY[i] = y0 + (y1 - y0) * t
                inP[i] = Double.NaN
            }
            inCount = 5
        }
        // One point gets a second at a (1, 1) offset.
        if (inCount == 1) addInput(inX[0] + 1, inY[0] + 1, inP[0])
    }

    internal fun loadStrokePoints(points: List<StrokePoint>) {
        sCount = 0
        ensureStrokeCapacity(points.size)
        for ((i, p) in points.withIndex()) {
            sx[i] = p.x
            sy[i] = p.y
            sp[i] = p.pressure
            svx[i] = p.vectorX
            svy[i] = p.vectorY
            sd[i] = p.distance
            sl[i] = p.runningLength
        }
        sCount = points.size
    }

    internal fun strokePoints(): List<StrokePoint> =
        List(sCount) { StrokePoint(sx[it], sy[it], sp[it], svx[it], svy[it], sd[it], sl[it]) }

    private fun ensureStrokeCapacity(capacity: Int) {
        if (sx.size >= capacity) return
        val n = max(capacity, sx.size * 2)
        sx = sx.copyOf(n)
        sy = sy.copyOf(n)
        sp = sp.copyOf(n)
        svx = svx.copyOf(n)
        svy = svy.copyOf(n)
        sd = sd.copyOf(n)
        sl = sl.copyOf(n)
    }

    /** getStrokePoints: streamlines the loaded input into the stroke point arrays. */
    internal fun computeStrokePoints(options: StrokeOptions) {
        sCount = 0
        val n = inCount
        if (n == 0) return
        ensureStrokeCapacity(n)
        val size = options.size
        val isComplete = options.last
        val t = MIN_STREAMLINE_T + (1 - options.streamline) * STREAMLINE_T_RANGE

        sx[0] = inX[0]
        sy[0] = inY[0]
        sp[0] = if (inP[0] >= 0) inP[0] else DEFAULT_FIRST_PRESSURE
        svx[0] = 1.0
        svy[0] = 1.0
        sd[0] = 0.0
        sl[0] = 0.0
        sCount = 1

        var hasReachedMinimumLength = false
        var runningLength = 0.0
        var prevX = sx[0]
        var prevY = sy[0]
        val lastIndex = n - 1

        for (i in 1 until n) {
            val x: Double
            val y: Double
            if (isComplete && i == lastIndex) {
                x = inX[i]
                y = inY[i]
            } else {
                x = prevX + (inX[i] - prevX) * t
                y = prevY + (inY[i] - prevY) * t
            }
            if (x == prevX && y == prevY) continue

            val distance = hypot(y - prevY, x - prevX)
            runningLength += distance

            // Wait until the line is `size` long before adding points, to avoid noise at the start.
            if (i < lastIndex && !hasReachedMinimumLength) {
                if (runningLength < size) continue
                hasReachedMinimumLength = true
            }

            val dx = prevX - x
            val dy = prevY - y
            val len = hypot(dx, dy)
            val k = sCount
            sx[k] = x
            sy[k] = y
            sp[k] = if (inP[i] >= 0) inP[i] else DEFAULT_PRESSURE
            svx[k] = dx / len
            svy[k] = dy / len
            sd[k] = distance
            sl[k] = runningLength
            sCount = k + 1
            prevX = x
            prevY = y
        }

        // The first point takes the second point's vector.
        if (sCount > 1) {
            svx[0] = svx[1]
            svy[0] = svy[1]
        } else {
            svx[0] = 0.0
            svy[0] = 0.0
        }
    }

    private fun pushLeft(x: Double, y: Double) {
        if (left.size < leftCount * 2 + 2) left = left.copyOf(left.size * 2)
        left[leftCount * 2] = x
        left[leftCount * 2 + 1] = y
        leftCount++
    }

    private fun pushRight(x: Double, y: Double) {
        if (right.size < rightCount * 2 + 2) right = right.copyOf(right.size * 2)
        right[rightCount * 2] = x
        right[rightCount * 2 + 1] = y
        rightCount++
    }

    private fun emit(x: Double, y: Double) {
        if (xy.size < pointCount * 2 + 2) xy = xy.copyOf(xy.size * 2)
        xy[pointCount * 2] = x.toFloat()
        xy[pointCount * 2 + 1] = y.toFloat()
        pointCount++
    }

    /** Emits (x, y) rotated around (cx, cy) by r radians. */
    private fun emitRotated(x: Double, y: Double, cx: Double, cy: Double, r: Double) {
        val s = sin(r)
        val c = cos(r)
        val px = x - cx
        val py = y - cy
        emit(px * c - py * s + cx, px * s + py * c + cy)
    }

    /** getStrokeOutlinePoints: outlines the stroke point arrays into [xy]. */
    internal fun computeOutline(options: StrokeOptions) {
        pointCount = 0
        leftCount = 0
        rightCount = 0
        val n = sCount
        val size = options.size
        if (n == 0 || size <= 0) return

        val smoothing = options.smoothing
        val thinning = options.thinning
        val simulate = options.simulatePressure
        val easing = options.easing
        val isComplete = options.last
        val capStart = options.start.cap
        val capEnd = options.end.cap
        val taperStartEase = options.start.easing ?: Easing.EaseOutQuad
        val taperEndEase = options.end.easing ?: Easing.EaseOutCubic

        val totalLength = sl[n - 1]
        val taperStart = taperDistance(options.start.taper, size, totalLength)
        val taperEnd = taperDistance(options.end.taper, size, totalLength)
        val minDistance = (size * smoothing) * (size * smoothing)

        // Initial pressure averages the first few points, to prevent fat starts.
        var prevPressure = sp[0]
        for (i in 0 until min(n, 10)) {
            val pressure = if (simulate) simulatePressure(prevPressure, sd[i], size) else sp[i]
            prevPressure = (prevPressure + pressure) / 2
        }

        var radius = getStrokeRadius(size, thinning, sp[n - 1], easing)
        var firstRadius = Double.NaN

        var prevVx = svx[0]
        var prevVy = svy[0]
        var prevLeftX = sx[0]
        var prevLeftY = sy[0]
        var prevRightX = prevLeftX
        var prevRightY = prevLeftY
        var isPrevPointSharpCorner = false

        for (i in 0 until n) {
            var pressure = sp[i]
            val px = sx[i]
            val py = sy[i]
            val vx = svx[i]
            val vy = svy[i]
            val runningLength = sl[i]
            val isLastPoint = i == n - 1

            // Remove noise from the end of the line.
            if (!isLastPoint && totalLength - runningLength < END_NOISE_THRESHOLD) continue

            // JS truthiness: thinning of 0 or NaN gives a constant width.
            if (thinning != 0.0 && !thinning.isNaN()) {
                if (simulate) pressure = simulatePressure(prevPressure, sd[i], size)
                radius = getStrokeRadius(size, thinning, pressure, easing)
            } else {
                radius = size / 2
            }
            if (firstRadius.isNaN()) firstRadius = radius

            val taperStartStrength =
                if (runningLength < taperStart) taperStartEase.ease(runningLength / taperStart) else 1.0
            val taperEndStrength =
                if (totalLength - runningLength < taperEnd) taperEndEase.ease((totalLength - runningLength) / taperEnd) else 1.0
            radius = max(MIN_RADIUS, radius * min(taperStartStrength, taperEndStrength))

            val nextVx = if (!isLastPoint) svx[i + 1] else vx
            val nextVy = if (!isLastPoint) svy[i + 1] else vy
            val nextDpr = if (!isLastPoint) vx * nextVx + vy * nextVy else 1.0
            val prevDpr = vx * prevVx + vy * prevVy

            val isPointSharpCorner = prevDpr < 0 && !isPrevPointSharpCorner
            val isNextPointSharpCorner = nextDpr < 0

            if (isPointSharpCorner || isNextPointSharpCorner) {
                // Sharp corner: draw a rounded cap at this point.
                val ox = prevVy * radius
                val oy = -prevVx * radius
                val step = 1.0 / CORNER_CAP_SEGMENTS
                var t = 0.0
                while (t <= 1) {
                    rotateAround(px - ox, py - oy, px, py, FIXED_PI * t)
                    pushLeft(rotX, rotY)
                    rotateAround(px + ox, py + oy, px, py, FIXED_PI * -t)
                    pushRight(rotX, rotY)
                    t += step
                }
                prevLeftX = left[leftCount * 2 - 2]
                prevLeftY = left[leftCount * 2 - 1]
                prevRightX = right[rightCount * 2 - 2]
                prevRightY = right[rightCount * 2 - 1]
                if (isNextPointSharpCorner) isPrevPointSharpCorner = true
                continue
            }

            isPrevPointSharpCorner = false

            if (isLastPoint) {
                val ox = vy * radius
                val oy = -vx * radius
                pushLeft(px - ox, py - oy)
                pushRight(px + ox, py + oy)
                continue
            }

            // Project to either side, along the perpendicular of the vector blended toward the next one.
            val bx = nextVx + (vx - nextVx) * nextDpr
            val by = nextVy + (vy - nextVy) * nextDpr
            val ox = by * radius
            val oy = -bx * radius

            val lx = px - ox
            val ly = py - oy
            if (i <= 1 || dist2(prevLeftX, prevLeftY, lx, ly) > minDistance) {
                pushLeft(lx, ly)
                prevLeftX = lx
                prevLeftY = ly
            }

            val rx = px + ox
            val ry = py + oy
            if (i <= 1 || dist2(prevRightX, prevRightY, rx, ry) > minDistance) {
                pushRight(rx, ry)
                prevRightX = rx
                prevRightY = ry
            }

            prevPressure = pressure
            prevVx = vx
            prevVy = vy
        }

        // Caps. Upstream builds them as arrays and returns left + endCap + reversed right + startCap;
        // this writes the same sequence straight into the output buffer.
        val firstX = sx[0]
        val firstY = sy[0]

        if (n == 1) {
            if (!(taperStart != 0.0 || taperEnd != 0.0) || isComplete) {
                drawDot(firstX, firstY, if (firstRadius != 0.0 && !firstRadius.isNaN()) firstRadius else radius)
                return
            }
            // A tapered, unfinished single point has no caps.
            for (i in 0 until leftCount) emit(left[i * 2], left[i * 2 + 1])
            for (i in rightCount - 1 downTo 0) emit(right[i * 2], right[i * 2 + 1])
            return
        }

        for (i in 0 until leftCount) emit(left[i * 2], left[i * 2 + 1])

        val lastX = sx[n - 1]
        val lastY = sy[n - 1]
        // per(neg(vector))
        val dirX = -svy[n - 1]
        val dirY = svx[n - 1]
        if (taperEnd != 0.0) {
            emit(lastX, lastY)
        } else if (capEnd) {
            // Round end cap: 1.5 turns, to handle sharp end turns.
            val startX = lastX + dirX * radius
            val startY = lastY + dirY * radius
            val step = 1.0 / END_CAP_SEGMENTS
            var t = step
            while (t < 1) {
                emitRotated(startX, startY, lastX, lastY, FIXED_PI * 3 * t)
                t += step
            }
        } else {
            emit(lastX + dirX * radius, lastY + dirY * radius)
            emit(lastX + dirX * (radius * 0.99), lastY + dirY * (radius * 0.99))
            emit(lastX - dirX * (radius * 0.99), lastY - dirY * (radius * 0.99))
            emit(lastX - dirX * radius, lastY - dirY * radius)
        }

        for (i in rightCount - 1 downTo 0) emit(right[i * 2], right[i * 2 + 1])

        if (taperStart != 0.0) {
            // Tapered start: no cap.
        } else if (capStart) {
            val step = 1.0 / START_CAP_SEGMENTS
            var t = step
            while (t <= 1) {
                emitRotated(right[0], right[1], firstX, firstY, FIXED_PI * t)
                t += step
            }
        } else {
            val cx = left[0] - right[0]
            val cy = left[1] - right[1]
            emit(firstX - cx * 0.5, firstY - cy * 0.5)
            emit(firstX - cx * 0.51, firstY - cy * 0.51)
            emit(firstX + cx * 0.51, firstY + cy * 0.51)
            emit(firstX + cx * 0.5, firstY + cy * 0.5)
        }
    }

    private fun drawDot(cx: Double, cy: Double, radius: Double) {
        // start = center + uni(per(center - (center + (1, 1)))) * -radius
        val ax = cx - (cx + 1)
        val ay = cy - (cy + 1)
        val len = hypot(ay, -ax)
        val startX = cx + (ay / len) * -radius
        val startY = cy + (-ax / len) * -radius
        val step = 1.0 / START_CAP_SEGMENTS
        var t = step
        while (t <= 1) {
            emitRotated(startX, startY, cx, cy, FIXED_PI * 2 * t)
            t += step
        }
    }

    // Result of rotateAround, kept in fields so the corner loop doesn't allocate.
    private var rotX = 0.0
    private var rotY = 0.0

    private fun rotateAround(x: Double, y: Double, cx: Double, cy: Double, r: Double) {
        val s = sin(r)
        val c = cos(r)
        val px = x - cx
        val py = y - cy
        rotX = px * c - py * s + cx
        rotY = px * s + py * c + cy
    }

    private fun taperDistance(taper: Taper, size: Double, totalLength: Double): Double = when (taper) {
        Taper.None -> 0.0
        Taper.Full -> max(size, totalLength)
        is Taper.Distance -> taper.distance
    }
}

// sqrt instead of java.lang.Math.hypot, which is several times slower; the overflow guard isn't needed here.
private fun hypot(a: Double, b: Double): Double = sqrt(a * a + b * b)

private fun dist2(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = ax - bx
    val dy = ay - by
    return dx * dx + dy * dy
}

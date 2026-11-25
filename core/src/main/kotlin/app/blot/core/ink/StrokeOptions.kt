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

package app.blot.core.ink

/**
 * One sampled input point. [pressure] is 0–1; NaN or a negative value means "not reported",
 * which upstream treats as 0.25 for the first point and 0.5 for the rest.
 */
data class FreehandPoint(val x: Float, val y: Float, val pressure: Float = Float.NaN)

/** One point of a stroke outline polygon. */
data class OutlinePoint(val x: Float, val y: Float)

/** A streamlined input point with its pressure, unit vector back to the previous point, and distances. */
data class StrokePoint(
    val x: Double,
    val y: Double,
    val pressure: Double,
    val vectorX: Double,
    val vectorY: Double,
    val distance: Double,
    val runningLength: Double,
)

/** Maps 0–1 to 0–1: pressure before it sets the radius, or position within a taper. */
fun interface Easing {
    fun ease(t: Double): Double

    companion object {
        val Linear = Easing { it }
        val EaseOutQuad = Easing { it * (2 - it) }
        val EaseOutCubic = Easing { val u = it - 1; u * u * u + 1 }
    }
}

/** How far a stroke end narrows to a point. */
sealed interface Taper {
    /** No taper. */
    data object None : Taper

    /** Taper over the whole stroke: the larger of size and the stroke's length. */
    data object Full : Taper

    /** Taper over [distance] (same units as the points); 0 means no taper. */
    data class Distance(val distance: Double) : Taper
}

/**
 * Options for one end of the stroke. A tapered end has no cap. A null [easing] means upstream's default:
 * [Easing.EaseOutQuad] at the start, [Easing.EaseOutCubic] at the end.
 */
data class StrokeEnd(
    val cap: Boolean = true,
    val taper: Taper = Taper.None,
    val easing: Easing? = null,
)

/**
 * The options for [getStroke], with upstream's defaults.
 *
 * @property size the base diameter of the stroke.
 * @property thinning the effect of pressure on the stroke's size, -1–1; 0 gives a constant width.
 * @property smoothing how much to soften the stroke's edges, 0–1.
 * @property streamline how much to streamline the stroke, 0–1.
 * @property easing applied to each point's pressure.
 * @property simulatePressure whether to derive pressure from speed instead of reading it from the points.
 * @property start cap, taper and easing for the start of the line.
 * @property end cap, taper and easing for the end of the line.
 * @property last whether the points are a completed stroke: the last point is used as is, not streamlined.
 */
data class StrokeOptions(
    val size: Double = 16.0,
    val thinning: Double = 0.5,
    val smoothing: Double = 0.5,
    val streamline: Double = 0.5,
    val easing: Easing = Easing.Linear,
    val simulatePressure: Boolean = true,
    val start: StrokeEnd = StrokeEnd(),
    val end: StrokeEnd = StrokeEnd(),
    val last: Boolean = false,
)

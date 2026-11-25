package app.screenscratch.core.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class StrokeOutlinerTest {
    private fun line(count: Int, step: Float, pressure: (Int) -> Float = { 0.5f }) =
        List(count) { FreehandPoint(it * step, 100f, pressure(it)) }

    /** Largest distance from y = 100 among outline points with x in [from, to]. */
    private fun halfWidth(outline: List<OutlinePoint>, from: Float, to: Float): Float =
        outline.filter { it.x in from..to }.maxOf { abs(it.y - 100f) }

    // Ported from upstream's getStrokeRadius.spec.ts.
    @Test
    fun strokeRadius() {
        val pressures = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)
        fun radii(thinning: Double, easing: Easing = Easing.Linear) =
            pressures.map { getStrokeRadius(100.0, thinning, it, easing) }
        assertEquals(listOf(50.0, 50.0, 50.0, 50.0, 50.0), radii(0.0))
        assertEquals(listOf(25.0, 37.5, 50.0, 62.5, 75.0), radii(0.5))
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), radii(1.0))
        assertEquals(listOf(75.0, 62.5, 50.0, 37.5, 25.0), radii(-0.5))
        assertEquals(listOf(100.0, 75.0, 50.0, 25.0, 0.0), radii(-1.0))
        val square = Easing { it * it }
        assertEquals(listOf(0.0, 6.25, 25.0, 56.25, 100.0), radii(1.0, square))
        assertEquals(listOf(100.0, 56.25, 25.0, 6.25, 0.0), radii(-1.0, square))
    }

    @Test
    fun emptyInput() {
        assertTrue(getStroke(emptyList()).isEmpty())
        assertTrue(getStrokePoints(emptyList()).isEmpty())
        assertEquals(0, getStrokeFlat(emptyList()).size)
        assertTrue(getStrokeOutlinePoints(emptyList()).isEmpty())
        assertTrue(getStroke(line(10, 5f), StrokeOptions(size = 0.0)).isEmpty())
    }

    @Test
    fun singlePointIsARoundBlob() {
        val p = FreehandPoint(50f, 60f, 0.5f)
        val outline = getStroke(listOf(p))
        assertTrue(outline.size > 10)
        assertTrue(outline.all { hypot(it.x - p.x, it.y - p.y) < 16f })
    }

    @Test
    fun repeatedPointIsADot() {
        // Every streamlined point equals the first, so upstream draws a 13-point circle.
        val outline = getStroke(List(5) { FreehandPoint(10f, 10f, 0.5f) })
        assertEquals(13, outline.size)
        val radii = outline.map { hypot(it.x - 10f, it.y - 10f) }
        assertTrue(radii.toString(), radii.all { abs(it - radii[0]) < 1e-3f && it > 0f })
    }

    @Test
    fun straightLineStaysWithinItsRadius() {
        // Constant real pressure 0.5 with thinning 0.5 gives radius size / 2 = 8.
        val options = StrokeOptions(simulatePressure = false, last = true)
        val outline = getStroke(line(101, 3f), options)
        assertTrue(outline.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(outline.all { abs(it.y - 100f) <= 8f + 1e-3f })
        assertTrue(outline.all { it.x >= -8f - 1e-3f && it.x <= 308f + 1e-3f })
        assertEquals(8f, halfWidth(outline, 100f, 200f), 1e-3f)
        assertTrue(outline.any { it.y < 100f } && outline.any { it.y > 100f })
    }

    @Test
    fun realPressureSetsTheWidth() {
        val options = StrokeOptions(simulatePressure = false, last = true)
        val outline = getStroke(line(101, 3f) { 0.1f + 0.8f * it / 100f }, options)
        assertTrue(halfWidth(outline, 240f, 270f) > 1.5f * halfWidth(outline, 30f, 60f))
    }

    @Test
    fun simulatedPressureIgnoresAllButTheFirstPressure() {
        val light = line(101, 3f) { if (it == 0) 0.5f else 0.1f }
        val heavy = line(101, 3f) { if (it == 0) 0.5f else 0.9f }
        assertEquals(getStroke(light), getStroke(heavy))
        val real = StrokeOptions(simulatePressure = false)
        assertNotEquals(getStroke(light, real), getStroke(heavy, real))
    }

    @Test
    fun simulatedPressureThinsFastStrokes() {
        val slow = getStroke(line(301, 1f))
        val fast = getStroke(line(31, 10f))
        assertTrue(halfWidth(slow, 100f, 200f) > halfWidth(fast, 100f, 200f))
    }

    @Test
    fun taperedEndsNarrowToAPoint() {
        val options = StrokeOptions(
            simulatePressure = false,
            last = true,
            start = StrokeEnd(taper = Taper.Distance(60.0)),
            end = StrokeEnd(taper = Taper.Distance(60.0)),
        )
        val outline = getStroke(line(101, 3f), options)
        val middle = halfWidth(outline, 120f, 180f)
        assertEquals(8f, middle, 1e-3f)
        assertTrue(halfWidth(outline, 0f, 10f) < middle / 2)
        assertTrue(halfWidth(outline, 290f, 300f) < middle / 2)
        // A tapered end has no cap: the outline turns at the last point itself.
        assertTrue(outline.any { it.x == 300f && it.y == 100f })
        assertTrue(outline.all { it.x >= -1e-3f && it.x <= 300f + 1e-3f })

        val full = getStroke(line(101, 3f), options.copy(start = StrokeEnd(taper = Taper.Full), end = StrokeEnd(taper = Taper.Full)))
        assertTrue(halfWidth(full, 0f, 30f) < halfWidth(full, 120f, 180f))
    }

    @Test
    fun flatArrayMatchesPoints() {
        val input = InkFixtures.input("tablet-1")
        val points = getStroke(input)
        val flat = getStrokeFlat(input)
        assertArrayEquals(points.flatMap { listOf(it.x, it.y) }.toFloatArray(), flat, 0f)
    }

    @Test
    fun reusedOutlinerMatchesFreshResults() {
        val long = InkFixtures.input("tablet-2")
        val short = InkFixtures.input("numberPairs")
        val options = StrokeOptions(simulatePressure = false)
        val outliner = StrokeOutliner()
        outliner.outline(long, options)
        val count = outliner.outline(short, options)
        assertArrayEquals(getStrokeFlat(short, options), outliner.xy.copyOf(count * 2), 0f)

        val xyp = FloatArray(long.size * 3)
        long.forEachIndexed { i, p -> xyp[i * 3] = p.x; xyp[i * 3 + 1] = p.y; xyp[i * 3 + 2] = p.pressure }
        outliner.outline(xyp, long.size, options)
        assertArrayEquals(getStrokeFlat(long, options), outliner.toFloatArray(), 0f)
    }

    // Ported from upstream's getStroke.spec.ts: random option combinations never produce NaN.
    @Test
    fun randomOptionsNeverProduceNaN() {
        val random = Random(1)
        val inputs = listOf("onePoint", "twoPoints", "twoEqualPoints", "withDuplicates", "manyPoints", "corners", "tablet-1")
            .map { InkFixtures.input(it) }
        fun taper() = when (random.nextInt(3)) {
            0 -> Taper.None
            1 -> Taper.Full
            else -> Taper.Distance(random.nextDouble() * 100)
        }
        repeat(500) {
            val options = StrokeOptions(
                size = random.nextDouble() * 100,
                thinning = random.nextDouble() * 2 - 1,
                streamline = random.nextDouble(),
                smoothing = random.nextDouble(),
                simulatePressure = random.nextBoolean(),
                last = random.nextBoolean(),
                start = StrokeEnd(cap = random.nextBoolean(), taper = taper()),
                end = StrokeEnd(cap = random.nextBoolean(), taper = taper()),
            )
            for (input in inputs) {
                val outline = getStrokeFlat(input, options)
                assertTrue("$options", outline.all { it.isFinite() })
            }
        }
    }

    @Test
    fun outliningARealStrokeIsFast() {
        val input = InkFixtures.input("tablet-2")
        assertEquals(620, input.size)
        val options = StrokeOptions(size = 6.0, thinning = 0.7, simulatePressure = false)
        val outliner = StrokeOutliner()
        repeat(3000) { outliner.outline(input, options) }

        val runs = 500
        val times = LongArray(runs) {
            val start = System.nanoTime()
            outliner.outline(input, options)
            System.nanoTime() - start
        }.sorted()
        val medianMs = times[runs / 2] / 1e6
        val p95Ms = times[runs * 95 / 100] / 1e6

        // An in-progress stroke is re-outlined on every input event: all 620 prefixes.
        val prefixStart = System.nanoTime()
        for (n in 1..input.size) outliner.outline(input.subList(0, n), options)
        val prefixMs = (System.nanoTime() - prefixStart) / 1e6

        println("620-point outline: median %.3f ms, p95 %.3f ms; all 620 prefixes %.1f ms".format(medianMs, p95Ms, prefixMs))
        assertTrue("median $medianMs ms", medianMs < 0.5)
    }
}

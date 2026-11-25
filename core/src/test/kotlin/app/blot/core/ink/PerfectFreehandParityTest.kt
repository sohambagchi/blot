package app.blot.core.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/** Compares the port with perfect-freehand 1.2.3's own output (resources/ink/parity.txt, from core/src/test/js/parity.mjs). */
class PerfectFreehandParityTest {
    private val tolerance = 1e-3

    private fun check(kind: String, compare: (InkFixtures.Block) -> String?) {
        val blocks = InkFixtures.parity.filter { it.kind == kind }
        assertTrue("no $kind blocks", blocks.isNotEmpty())
        val failures = blocks.mapNotNull { block -> compare(block)?.let { "$block: $it" } }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${blocks.size} $kind cases differ:\n" + failures.take(10).joinToString("\n"))
        }
    }

    private fun near(a: Double, b: Double) = abs(a - b) <= tolerance

    @Test
    fun outlinesMatchUpstream() = check("outline") { block ->
        val actual = getStroke(InkFixtures.input(block.input), block.options)
        if (actual.size != block.count) return@check "expected ${block.count} points, got ${actual.size}"
        actual.indices.firstOrNull { i ->
            !near(actual[i].x.toDouble(), block.rows[i][0]) || !near(actual[i].y.toDouble(), block.rows[i][1])
        }?.let { i -> "point $i: expected ${block.rows[i].toList()}, got ${actual[i]}" }
    }

    @Test
    fun strokePointsMatchUpstream() = check("points") { block ->
        val actual = getStrokePoints(InkFixtures.input(block.input), block.options)
        if (actual.size != block.count) return@check "expected ${block.count} points, got ${actual.size}"
        actual.indices.firstOrNull { i ->
            val p = actual[i]
            val values = doubleArrayOf(p.x, p.y, p.pressure, p.vectorX, p.vectorY, p.distance, p.runningLength)
            values.indices.any { !near(values[it], block.rows[i][it]) }
        }?.let { i -> "point $i: expected ${block.rows[i].toList()}, got ${actual[i]}" }
    }

    @Test
    fun randomOptionsMatchUpstream() = check("summary") { block ->
        val outline = getStrokeFlat(InkFixtures.input(block.input), block.options)
        val count = outline.size / 2
        if (count != block.count) return@check "expected ${block.count} points, got $count"
        var sumX = 0.0
        var sumY = 0.0
        for (i in 0 until count) {
            sumX += outline[i * 2]
            sumY += outline[i * 2 + 1]
        }
        val actual = if (count == 0) doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) else doubleArrayOf(
            sumX, sumY,
            outline[0].toDouble(), outline[1].toDouble(),
            outline[count * 2 - 2].toDouble(), outline[count * 2 - 1].toDouble(),
        )
        val expected = block.rows[0]
        if (actual.indices.all { near(actual[it], expected[it]) }) null
        else "expected ${expected.toList()}, got ${actual.toList()}"
    }

    @Test
    fun outlineOfStrokePointsEqualsStroke() {
        for (name in listOf("hey", "corners", "tablet-2", "onePoint", "twoEqualPoints")) {
            val input = InkFixtures.input(name)
            val options = StrokeOptions(last = true, start = StrokeEnd(taper = Taper.Distance(30.0)))
            assertEquals(name, getStroke(input, options), getStrokeOutlinePoints(getStrokePoints(input, options), options))
        }
    }
}

package app.screenscratch.core.edit

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialEraseTest {
    /** A horizontal polyline at y = 0 with samples every [step] units from 0 to [length]. */
    private fun line(length: Float, step: Float): List<StrokePoint> =
        List((length / step).toInt() + 1) { StrokePoint(it * step, 0f, 0.5f, timeMs = it * 4L) }

    @Test
    fun missingThePolylineChangesNothing() {
        assertNull(PartialErase.split(line(100f, 1f), 50f, 20f, 60f, 20f, 5f))
    }

    @Test
    fun aHoleInTheMiddleSplitsInTwoAtTheEraserEdge() {
        val runs = PartialErase.split(line(100f, 1f), 50f, -20f, 50f, 20f, 5f)!!
        assertEquals(2, runs.size)
        // Cut where the polyline meets the capsule's edge: x = 45 and x = 55.
        assertEquals(45f, runs[0].last().x, 1 / 16f)
        assertEquals(55f, runs[1].first().x, 1 / 16f)
        assertEquals(0f, runs[0].first().x)
        assertEquals(100f, runs[1].last().x)
        // The samples in between are dropped; the rest are kept as they were.
        assertTrue(runs.flatten().none { it.x > 45.1f && it.x < 54.9f })
        assertEquals(46 + 46, runs.sumOf { it.size })
    }

    @Test
    fun crossingBetweenSparseSamplesStillCuts() {
        // Samples 40 units apart; the eraser passes at x = 20 between the first two.
        val runs = PartialErase.split(line(80f, 40f), 20f, -10f, 20f, 10f, 2f)!!
        assertEquals(2, runs.size)
        assertEquals(listOf(0f, 18f), runs[0].map { it.x })
        assertEquals(listOf(22f, 40f, 80f), runs[1].map { it.x })
        // Interpolated points carry interpolated pressure and time.
        assertEquals(4L * 22 / 40, runs[1][0].timeMs)
    }

    @Test
    fun trimmingAnEndLeavesOneRun() {
        val runs = PartialErase.split(line(100f, 1f), 100f, 0f, 100f, 0f, 10f)!!
        assertEquals(1, runs.size)
        assertEquals(90f, runs[0].last().x, 1 / 16f)
    }

    @Test
    fun coveringEverythingLeavesNothing() {
        assertEquals(emptyList<List<StrokePoint>>(), PartialErase.split(line(10f, 1f), 0f, 0f, 10f, 0f, 3f))
        // A single-sample dot.
        assertEquals(0, PartialErase.split(listOf(StrokePoint(5f, 5f, 0.5f, timeMs = 0)), 5f, 5f, 5f, 5f, 1f)!!.size)
    }

    @Test
    fun eraseMakesStrokesWithNewIdsAndRebasedTimes() {
        val s = Stroke("s", line(100f, 1f), Argb.BLACK, 4f)
        var n = 0
        val pieces = PartialErase.erase(s, 50f, -20f, 50f, 20f, 3f) { "p${n++}" }!!
        assertEquals(listOf("p0", "p1"), pieces.map { it.id })
        // The eraser reaches half the stroke's width further: 3 + 2.
        assertEquals(45f, pieces[0].points.last().x, 1 / 16f)
        assertEquals(55f, pieces[1].points.first().x, 1 / 16f)
        assertEquals(0L, pieces[1].points.first().timeMs)
        for (p in pieces) {
            assertEquals(s.width, p.width)
            assertEquals(s.color, p.color)
            assertNotEquals(s.id, p.id)
        }
        // Out of reach of the ink.
        assertNull(PartialErase.erase(s, 50f, 6f, 50f, 20f, 3f))
    }
}

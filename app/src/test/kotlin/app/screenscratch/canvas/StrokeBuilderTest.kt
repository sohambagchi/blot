package app.screenscratch.canvas

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.format.PointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class StrokeBuilderTest {
    @Test
    fun snapsToTheFileQuantisationAndMakesTimeRelative() {
        val b = StrokeBuilder(Argb.BLACK, 2f, pressureReported = true)
        b.add(10.03f, 20.97f, 0.50001f, 2f, 4f, 1000)
        b.add(11f, 21f, 1.5f, -1f, -4f, 1004)
        b.add(12f, 22f, 0.3f, 0f, 0f, 1003) // out of order: kept non-decreasing
        val s = b.build("id")!!
        assertEquals(10f, s.points[0].x, 1 / 32f)
        assertEquals(0f, (s.points[0].x * 16) % 1f, 1e-4f)
        assertEquals(0f, (s.points[0].y * 16) % 1f, 1e-4f)
        val q = s.points[0].pressure * PointCodec.PRESSURE_STEPS
        assertEquals(Math.round(q).toFloat(), q, 1e-2f)
        assertEquals(1f, s.points[1].pressure)
        assertEquals((PI / 2).toFloat(), s.points[0].tilt, 1e-6f)
        assertEquals(0f, s.points[1].tilt)
        assertEquals(4f - 2 * PI.toFloat(), s.points[0].orientation, 1e-5f)
        assertEquals(listOf(0L, 4L, 4L), s.points.map { it.timeMs })
        // Encoding the snapped stroke and decoding it gives the same points.
        assertEquals(s.points.map { it.x to it.y }, PointCodec.decode(PointCodec.encode(s.points)).map { it.x to it.y })
    }

    @Test
    fun liveSegmentsUseTheOutlineRadius() {
        val b = StrokeBuilder(Argb.BLACK, 4f, pressureReported = true)
        val first = b.add(0f, 0f, 0.5f, 0f, 0f, 0)!!
        assertEquals(2f, first.r0, 1e-3f) // pressure 0.5: half the width
        assertEquals(first.x0, first.x1)
        val second = b.add(10f, 0f, 1f, 0f, 0f, 4)!!
        assertEquals(first.r1, second.r0)
        assertEquals(InkStyle.radius(4f, 1f), second.r1, 1e-4f)
        assertEquals(4f * 0.825f, second.r1, 1e-4f)
        assertNull(b.add(10f, 0f, 0.7f, 0f, 0f, 8)) // no movement: nothing new to draw
        assertEquals(3, b.size)
        assertEquals(listOf(first, second), b.segments())
    }

    @Test
    fun simulatedPressureMatchesIncrementalAndReplayed() {
        val b = StrokeBuilder(Argb.BLACK, 3f, pressureReported = false)
        val live = (0 until 20).mapNotNull { b.add(it * 2f, (it % 3).toFloat(), 0f, 0f, 0f, it * 4L) }
        assertEquals(live, b.segments())
        assertTrue(live.all { it.r1 > 0f && it.r1 <= 3f })
        val stroke = b.build("s")
        assertNotNull(stroke)
        assertEquals(false, stroke!!.pressureReported)
    }

    @Test
    fun emptyBuilderBuildsNothing() {
        assertNull(StrokeBuilder(Argb.BLACK, 1f, true).build("x"))
    }
}

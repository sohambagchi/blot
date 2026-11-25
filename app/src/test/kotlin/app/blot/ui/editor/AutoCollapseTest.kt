package app.blot.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCollapseTest {
    /** Feeds activity every [stepMs] from [from] to [to] inclusive; returns the times that asked to collapse. */
    private fun AutoCollapse.write(from: Long, to: Long, stepMs: Long = 16): List<Long> =
        (from..to step stepMs).filter { onWritingActivity(it) }

    @Test
    fun collapsesOnceAfterFifteenSecondsOfContinuousWriting() {
        val a = AutoCollapse()
        val fired = a.write(1_000, 40_000, 100)
        assertEquals(listOf(16_000L), fired)
    }

    @Test
    fun shortPausesBetweenStrokesStillCount() {
        val a = AutoCollapse()
        // 2 s strokes with 1 s pen-up gaps between them.
        val fired = (0 until 8).flatMap { i -> a.write(i * 3_000L, i * 3_000L + 2_000, 50) }
        assertEquals(listOf(15_000L), fired)
    }

    @Test
    fun aLongPauseStartsOver() {
        val a = AutoCollapse()
        assertTrue(a.write(0, 10_000, 100).isEmpty())
        assertTrue(a.write(14_000, 24_000, 100).isEmpty())
        assertEquals(listOf(29_000L), a.write(24_100, 30_000, 100))
    }

    @Test
    fun resetStartsANewSpell() {
        val a = AutoCollapse()
        assertEquals(listOf(15_000L), a.write(0, 16_000, 100))
        a.reset()
        assertFalse(a.onWritingActivity(16_100))
        assertEquals(listOf(31_100L), a.write(16_200, 32_000, 100))
    }

    @Test
    fun customDurations() {
        val a = AutoCollapse(collapseAfterMs = 5_000, pauseMs = 500)
        assertEquals(listOf(5_000L), a.write(0, 6_000, 100))
    }
}

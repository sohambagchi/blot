package app.blot.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternMarksTest {
    private fun floats(vararg v: Float) = v

    @Test
    fun strideThinsBelowSixPixels() {
        assertEquals(1, PatternMarks.stride(28f, 1f))
        assertEquals(1, PatternMarks.stride(28f, 0.25f)) // 7 px
        assertEquals(2, PatternMarks.stride(28f, 0.2f)) // 5.6 px
        assertEquals(4, PatternMarks.stride(8f, 0.2f)) // 1.6 px → 6.4 px
        assertEquals(0, PatternMarks.stride(8f, 0.01f))
        assertEquals(1, PatternMarks.stride(8f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun infiniteCanvasGivesOnlyTheVisibleCells() {
        val visible = Rect(100f, 200f, 300f, 150f)
        val grid = PatternMarks.of(Background(Pattern.GRID, 28f), null, visible)!!
        assertEquals(visible, grid.area)
        assertArrayEquals(FloatArray(11) { 112f + 28f * it }, grid.xs, 0f)
        assertArrayEquals(FloatArray(5) { 224f + 28f * it }, grid.ys, 0f)

        val dots = PatternMarks.of(Background(Pattern.DOTS, 28f), null, visible)!!
        assertArrayEquals(FloatArray(10) { 126f + 28f * it }, dots.xs, 0f)
        assertArrayEquals(floats(210f, 238f, 266f, 294f, 322f, 350f), dots.ys, 0f)

        val ruled = PatternMarks.of(Background(Pattern.RULED, 45f), null, visible)!!
        assertEquals(0, ruled.xs.size)
        assertArrayEquals(floats(225f, 270f, 315f), ruled.ys, 0f)
    }

    @Test
    fun farAwayAndNegativeAreasStayCheap() {
        val far = PatternMarks.of(Background(Pattern.GRID, 28f), null, Rect(1e6f, -1e6f, 100f, 100f))!!
        assertTrue(far.xs.size in 3..4 && far.ys.size in 3..4)
        val neg = PatternMarks.of(Background(Pattern.DOTS, 28f), null, Rect(-56f, -56f, 56f, 56f))!!
        assertArrayEquals(floats(-42f, -14f), neg.xs, 0f)
    }

    @Test
    fun pagesClipToThePageAndSkipItsEdges() {
        val page = Rect(0f, 0f, 140f, 100f)
        val grid = PatternMarks.of(Background(Pattern.GRID, 28f), page, Rect(-50f, -50f, 500f, 500f))!!
        assertEquals(page, grid.area)
        assertArrayEquals(floats(28f, 56f, 84f, 112f), grid.xs, 0f)
        assertArrayEquals(floats(28f, 56f, 84f), grid.ys, 0f)

        // A later page starts its cells at its own corner.
        val second = Rect(0f, 1000f, 140f, 100f)
        val dots = PatternMarks.of(Background(Pattern.DOTS, 28f), second, Rect(0f, 900f, 140f, 300f))!!
        assertEquals(1014f, dots.ys.first())
        assertEquals(second, dots.area)

        assertNull(PatternMarks.of(Background(Pattern.GRID, 28f), page, Rect(200f, 0f, 50f, 50f)))
    }

    @Test
    fun ruledPagesKeepAOneSpacingMarginTopAndBottom() {
        val page = Rect(0f, 0f, 300f, 450f)
        val all = PatternMarks.of(Background(Pattern.RULED, 45f), page, page)!!
        assertArrayEquals(FloatArray(9) { 45f * (it + 1) }, all.ys, 0f)
        // 460 tall: the last rule stays at 405, since 450 would leave less than a spacing below.
        val taller = PatternMarks.of(Background(Pattern.RULED, 45f), Rect(0f, 0f, 300f, 460f), page.copy(height = 460f))!!
        assertEquals(405f, taller.ys.last())
        val a4 = PageSetup.Pages.a4().pageRect(0)
        val ys = PatternMarks.of(Background(Pattern.RULED, 45f), a4, a4)!!.ys
        assertEquals(45f, ys.first())
        assertTrue(a4.bottom - ys.last() >= 45f)
        assertTrue(a4.bottom - ys.last() < 90f)
    }

    @Test
    fun thinnedMarksStayOnTheirCells() {
        val visible = Rect(10f, 10f, 400f, 400f)
        val thin = PatternMarks.of(Background(Pattern.GRID, 28f), null, visible, stride = 2)!!
        assertTrue(thin.xs.all { it % 56f == 0f })
        val panned = PatternMarks.of(Background(Pattern.GRID, 28f), null, visible.copy(x = 38f), stride = 2)!!
        assertTrue(panned.xs.all { it % 56f == 0f })
        val ruled = PatternMarks.of(Background(Pattern.RULED, 45f), Rect(0f, 0f, 300f, 450f), Rect(0f, 0f, 300f, 450f), stride = 2)!!
        assertArrayEquals(floats(90f, 180f, 270f, 360f), ruled.ys, 0f)
        assertNull(PatternMarks.of(Background(Pattern.GRID, 28f), null, visible, stride = 0))
    }
}

package app.screenscratch.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class ElementTest {
    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1e-3f)
        assertEquals(expected.top, actual.top, 1e-3f)
        assertEquals(expected.right, actual.right, 1e-3f)
        assertEquals(expected.bottom, actual.bottom, 1e-3f)
    }

    @Test
    fun strokeBoundsAreSamplesGrownByWidth() {
        val s = Stroke(
            "s",
            listOf(StrokePoint(10f, 20f, 0.5f, timeMs = 0), StrokePoint(30f, 5f, 0.5f, timeMs = 4)),
            Argb.BLACK, width = 2f,
        )
        assertRect(Rect.fromEdges(8f, 3f, 32f, 22f), s.bounds)
    }

    @Test
    fun rotatedRectangleBoundsCoverItsCorners() {
        val frame = Rect(0f, 0f, 40f, 20f)
        val r = Shape("r", ShapeKind.RECTANGLE, frame, Argb.BLACK, width = 2f, rotation = (PI / 2).toFloat())
        // A quarter turn swaps the extents about the centre (20, 10).
        assertRect(Rect.around(20f, 10f, 11f, 21f), r.bounds)
    }

    @Test
    fun rotatedEllipseBoundsAreExact() {
        val frame = Rect(0f, 0f, 40f, 20f)
        val e = Shape("e", ShapeKind.ELLIPSE, frame, Argb.BLACK, width = 0f, rotation = (PI / 4).toFloat())
        val h = sqrt((400f + 100f) / 2f)
        assertRect(Rect.around(20f, 10f, h, h), e.bounds)
    }

    @Test
    fun lineBoundsIncludeArrowheads() {
        val pts = listOf(Point(0f, 0f), Point(100f, 50f))
        val plain = Line("l", pts, Argb.BLACK, width = 2f)
        assertRect(Rect.fromEdges(-1f, -1f, 101f, 51f), plain.bounds)
        val arrow = plain.copy(endArrow = Arrowhead.TRIANGLE)
        assertEquals(8f, arrow.arrowheadLength)
        assertRect(Rect.fromEdges(-9f, -9f, 109f, 59f), arrow.bounds)
    }

    @Test
    fun invalidElementsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Stroke("s", emptyList(), Argb.BLACK, 1f) }
        assertThrows(IllegalArgumentException::class.java) { Line("l", listOf(Point(0f, 0f)), Argb.BLACK, 1f) }
        assertThrows(IllegalArgumentException::class.java) { AssetRef("abc", "png") }
        assertThrows(IllegalArgumentException::class.java) { AssetRef("a".repeat(64), "PNG") }
    }

    @Test
    fun argbParsesAndPrints() {
        assertEquals("#FF112233", Argb.of(255, 0x11, 0x22, 0x33).toHex())
        assertEquals(Argb.of(255, 0x11, 0x22, 0x33), Argb.parse("#112233"))
        assertEquals(Argb.of(0x80, 0xAB, 0xCD, 0xEF), Argb.parse("#80abcdef"))
        assertThrows(IllegalArgumentException::class.java) { Argb.parse("#-12345") }
    }

    @Test
    fun pagesStackWithGaps() {
        val p = PageSetup.Pages.a4(Orientation.LANDSCAPE, count = 3)
        assertEquals(Orientation.LANDSCAPE, p.orientation)
        assertEquals(1870.866f, p.width, 1e-2f)
        assertEquals(1322.835f, p.height, 1e-2f)
        assertEquals(2 * (p.height + PageSetup.Pages.PAGE_GAP), p.pageRect(2).top, 1e-3f)
        assertEquals(Orientation.PORTRAIT, PageSetup.Pages.letter().orientation)
    }
}

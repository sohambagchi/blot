package app.blot.core.export

import app.blot.core.geometry.LineRoutes
import app.blot.core.geometry.ShapeOutlines
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.Element
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Note
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element as XmlElement
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

class SvgShapesAndRoutesTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val palette = ExportPalette(0xFFFFFFFF.toInt(), 0xFFE4E4E7.toInt(), 0xFFEDEDED.toInt(), 0xFFBDBDBD.toInt(), 0xFFD6D3CB.toInt())

    private fun svg(els: List<Element>, color: (Int) -> Int = { it }): Document {
        val writer = SvgNoteWriter(palette, { FloatArray(0) }, color = color)
        val text = StringBuilder().also {
            writer.write(Note("n", created = t0, modified = t0, elements = els), ExportOptions(format = ExportFormat.SVG), it)
        }.toString()
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    private fun Document.all(name: String): List<XmlElement> {
        val list = getElementsByTagNameNS(SvgNoteWriter.SVG_NS, name)
        return List(list.length) { list.item(it) as XmlElement }
    }

    private val newKinds = ShapeKind.entries.filter { !it.isFormat1 }

    @Test
    fun everyNewShapeIsAPathOfItsOutline() {
        val frame = Rect(10f, 20f, 180f, 160f)
        for (kind in newKinds) {
            val s = Shape("s", kind, frame, Argb.BLACK, 3f, fill = Argb(0xFFFFF59D.toInt()), rotation = 0.5f)
            val path = svg(listOf(s)).all("path").single()
            val d = path.getAttribute("d")
            assertEquals(kind.name, ShapeOutlines.of(kind, frame).svg(SvgXml::num), d)
            assertTrue(d, d.startsWith("M") && d.contains("Z"))
            assertEquals("#FFF59D", path.getAttribute("fill"))
            assertEquals("#000000", path.getAttribute("stroke"))
            assertEquals("3", path.getAttribute("stroke-width"))
            assertTrue(path.getAttribute("transform"), path.getAttribute("transform").startsWith("rotate(28.65 100 100"))
        }
    }

    @Test
    fun pathDataSpellsOutEachKind() {
        val f = Rect(3f, 4f, 18f, 16f)
        assertEquals("M7.5 4L16.5 4L21 12L16.5 20L7.5 20L3 12Z", ShapeOutlines.of(ShapeKind.HEXAGON, f).svg(SvgXml::num))
        assertEquals("M12 4L21 20L3 20Z", ShapeOutlines.of(ShapeKind.TRIANGLE, f).svg(SvgXml::num))
        // Corner arcs, quarter turns clockwise.
        val rr = ShapeOutlines.of(ShapeKind.ROUNDED_RECTANGLE, Rect(4f, 5f, 16f, 14f)).svg(SvgXml::num)
        assertEquals("M8 5L16 5A4 4 0 0 1 20 9L20 15A4 4 0 0 1 16 19L8 19A4 4 0 0 1 4 15L4 9A4 4 0 0 1 8 5Z", rr)
        // The cylinder's silhouette, then the top cap's front edge as an open subpath.
        val cyl = ShapeOutlines.of(ShapeKind.CYLINDER, Rect(5f, 3.5f, 14f, 17f)).svg(SvgXml::num)
        assertEquals("M5 6A7 2.5 0 0 1 19 6L19 18A7 2.5 0 0 1 5 18ZM19 6A7 2.5 0 0 1 5 6", cyl)
    }

    @Test
    fun rectanglesAndEllipsesKeepTheirElements() {
        val doc = svg(listOf(
            Shape("r", ShapeKind.RECTANGLE, Rect(0f, 0f, 10f, 10f), Argb.BLACK, 1f),
            Shape("e", ShapeKind.ELLIPSE, Rect(20f, 0f, 10f, 10f), Argb.BLACK, 1f),
        ))
        assertEquals(1, doc.all("ellipse").size)
        assertEquals(0, doc.all("path").size)
    }

    @Test
    fun darkExportsRecolourNewShapes() {
        val doc = svg(listOf(Shape("d", ShapeKind.DIAMOND, Rect(0f, 0f, 20f, 20f), Argb.BLACK, 2f, fill = Argb.WHITE))) { if (it == Argb.BLACK.value) 0xFFFFFFFF.toInt() else 0xFF000A62.toInt() }
        val p = doc.all("path").single()
        assertEquals("#000A62", p.getAttribute("fill"))
        assertEquals("#FFFFFF", p.getAttribute("stroke"))
    }

    @Test
    fun elbowLinesHaveRoundedCornersAndHeads() {
        val l = Line("e", LineRoutes.elbow(0f, 0f, 200f, 100f), Argb.BLACK, 2f, Arrowhead.NONE, Arrowhead.TRIANGLE, route = LineRoute.ELBOW)
        val paths = svg(listOf(l)).all("path")
        assertEquals(2, paths.size)
        val shaft = paths[0].getAttribute("d")
        assertTrue(shaft, shaft.startsWith("M0 0L88 0C"))
        assertEquals(2, shaft.count { it == 'C' })
        assertEquals("none", paths[0].getAttribute("fill"))
        assertEquals("round", paths[0].getAttribute("stroke-linejoin"))
        // The triangle points right at the end.
        val head = paths[1].getAttribute("d")
        assertTrue(head, head.startsWith("M200 100L"))
        assertEquals("#000000", paths[1].getAttribute("fill"))
    }

    @Test
    fun curvesAreCubicsThroughTheirPoints() {
        val pts = listOf(Point(0f, 0f), Point(60f, -40f), Point(140f, 30f), Point(200f, -10f))
        val l = Line("c", pts, Argb.BLACK, 2f, Arrowhead.ARROW, Arrowhead.ARROW, route = LineRoute.CURVE)
        val paths = svg(listOf(l)).all("path")
        assertEquals(3, paths.size)
        val d = paths[0].getAttribute("d")
        assertTrue(d, d.startsWith("M0 0C"))
        assertEquals(3, d.count { it == 'C' })
        assertTrue(d, d.endsWith(" 200 -10"))
        // Open V heads with their tips on the ends.
        assertTrue(paths[1].getAttribute("d").contains("L0 0L"))
        assertTrue(paths[2].getAttribute("d").contains("L200 -10L"))
    }
}

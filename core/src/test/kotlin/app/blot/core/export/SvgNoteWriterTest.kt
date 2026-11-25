package app.blot.core.export

import app.blot.core.ink.StrokeOptions
import app.blot.core.ink.StrokeOutliner
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.AssetRef
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element as XmlElement
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import kotlin.math.sin

class SvgNoteWriterTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val palette = ExportPalette(0xFFFFFFFF.toInt(), 0xFFE4E4E7.toInt(), 0xFFEDEDED.toInt(), 0xFFBDBDBD.toInt(), 0xFFD6D3CB.toInt())
    private val photo = AssetRef("a".repeat(64), "jpg")
    private val missing = AssetRef("b".repeat(64), "png")

    private fun outline(s: Stroke): FloatArray {
        val o = StrokeOutliner()
        val xyp = FloatArray(s.points.size * 3)
        s.points.forEachIndexed { i, p -> xyp[i * 3] = p.x; xyp[i * 3 + 1] = p.y; xyp[i * 3 + 2] = p.pressure }
        o.outline(xyp, s.points.size, StrokeOptions(size = s.width.toDouble()))
        return o.toFloatArray()
    }

    private fun wave(id: String, y: Float) = Stroke(
        id, List(40) { i -> StrokePoint(40f + i * 10f, y + 20f * sin(i / 4f), 0.5f, timeMs = i * 4L) }, Argb(0xFF1E5BD8.toInt()), 4f,
    )

    private val elements: List<Element> = listOf(
        wave("wave", 100f),
        Stroke("dot", listOf(StrokePoint(500f, 60f, 0.5f, timeMs = 0)), Argb(0x80E53935.toInt()), 6f),
        Shape("rect", ShapeKind.RECTANGLE, Rect(40f, 200f, 200f, 100f), Argb.BLACK, 2f, fill = Argb(0xFFFFF59D.toInt())),
        Shape("oval", ShapeKind.ELLIPSE, Rect(300f, 200f, 160f, 90f), Argb(0xFF2E7D32.toInt()), 3f, rotation = 0.3f),
        Line("arrow", listOf(Point(40f, 360f), Point(300f, 360f), Point(360f, 420f)), Argb(0xFF6A1B9A.toInt()), 2f, Arrowhead.TRIANGLE, Arrowhead.ARROW),
        Line("plain", listOf(Point(40f, 450f), Point(200f, 450f)), Argb.BLACK, 1f),
        TextBox("text", Rect(400f, 320f, 240f, 80f), "a < b & \"c\" 'd'\u0007\nsecond line", 16f, Argb.BLACK),
        Image("photo", photo, Rect(500f, 100f, 96f, 64f)),
        Image("missing", missing, Rect(620f, 100f, 48f, 48f)),
    )

    private fun note(page: PageSetup = PageSetup.Infinite, els: List<Element> = elements) =
        Note("n", created = t0, modified = t0, page = page, elements = els)

    private fun svg(note: Note, options: ExportOptions = ExportOptions(format = ExportFormat.SVG), text: (TextBox) -> SvgText? = { null }): String {
        val writer = SvgNoteWriter(
            palette, ::outline,
            textLayout = text,
            imageHref = { path -> if (path == photo.path) SvgXml.dataUri(byteArrayOf(1, 2, 3), "jpg") else null },
        )
        return StringBuilder().also { writer.write(note, options, it) }.toString()
    }

    private fun parse(s: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)))

    private fun Document.all(name: String): List<XmlElement> {
        val list = getElementsByTagNameNS(SvgNoteWriter.SVG_NS, name)
        return List(list.length) { list.item(it) as XmlElement }
    }

    private fun XmlElement.floats(attr: String) = getAttribute(attr).split(' ').map { it.toFloat() }

    @Test
    fun wellFormedWithViewBoxFromContentBounds() {
        val doc = parse(svg(note()))
        val root = doc.documentElement
        assertEquals("svg", root.localName)
        assertEquals(SvgNoteWriter.SVG_NS, root.namespaceURI)
        val expected = ExportPlan.contentBounds(elements)!!.inflate(24f)
        val vb = root.floats("viewBox")
        assertEquals(expected.x, vb[0], 0.01f)
        assertEquals(expected.y, vb[1], 0.01f)
        assertEquals(expected.width, vb[2], 0.01f)
        assertEquals(expected.height, vb[3], 0.01f)
        assertEquals(expected.width, root.getAttribute("width").toFloat(), 0.01f)
    }

    @Test
    fun elementCounts() {
        val doc = parse(svg(note()))
        // 2 stroke outlines, 2 shafts, 1 triangle, 1 V, 1 placeholder cross.
        assertEquals(7, doc.all("path").size)
        // Paper, sheet clip, text clip, placeholder, rectangle shape.
        assertEquals(5, doc.all("rect").size)
        assertEquals(1, doc.all("ellipse").size)
        assertEquals(1, doc.all("image").size)
        assertEquals(2, doc.all("text").size)
        assertEquals(0, doc.getElementsByTagNameNS(SvgNoteWriter.SVG_NS, "foreignObject").length)

        val img = doc.all("image").single()
        assertTrue(img.getAttributeNS(SvgNoteWriter.XLINK_NS, "href").startsWith("data:image/jpeg;base64,AQID"))
        val ellipse = doc.all("ellipse").single()
        assertTrue(ellipse.getAttribute("transform"), ellipse.getAttribute("transform").startsWith("rotate(17.19 380 245"))
        val translucent = doc.all("path").first { it.getAttribute("fill") == "#E53935" }
        assertEquals("0.5", translucent.getAttribute("fill-opacity"))
    }

    @Test
    fun arrowheadsMatchTheCanvasGeometry() {
        val line = elements.filterIsInstance<Line>().first()
        val doc = parse(svg(note(els = listOf(line))))
        val paths = doc.all("path")
        assertEquals("shaft, triangle and V", 3, paths.size)
        val triangle = paths.single { it.getAttribute("d").endsWith("Z") }
        assertEquals("#6A1B9A", triangle.getAttribute("fill"))
        assertTrue(triangle.getAttribute("d").startsWith("M40 360L"))
        val v = paths.last()
        assertEquals("none", v.getAttribute("fill"))
        assertEquals("round", v.getAttribute("stroke-linecap"))
        // The V's tip is the line's end.
        assertTrue(v.getAttribute("d"), v.getAttribute("d").contains("L360 420L"))

        // The shaft stops at the triangle's base, and every head point stays within arrowheadLength.
        val shaft = LineGeometry.shaft(line)
        assertEquals(line.arrowheadLength * kotlin.math.cos(LineGeometry.ARROW_HALF_ANGLE), shaft[0].x - 40f, 0.01f)
        for (atStart in listOf(true, false)) {
            val head = LineGeometry.head(line, atStart)!!
            val tip = if (atStart) line.points.first() else line.points.last()
            for (p in head.points) assertTrue(hypot(p.x - tip.x, p.y - tip.y) <= line.arrowheadLength + 1e-3f)
        }
        assertEquals(null, LineGeometry.head(line.copy(endArrow = Arrowhead.NONE), atStart = false))
    }

    @Test
    fun escapesText() {
        val raw = svg(note(els = listOf(elements.first { it is TextBox })))
        assertTrue(raw.contains("a &lt; b &amp; &quot;c&quot; &apos;d&apos;"))
        assertFalse(raw.contains('\u0007'))
        val texts = parse(raw).all("text")
        assertEquals("a < b & \"c\" 'd'", texts[0].textContent)
        assertEquals("second line", texts[1].textContent)
    }

    @Test
    fun writesLaidOutRunsAndDecorations() {
        val layout = SvgText(
            lines = listOf(
                SvgText.TextLine(
                    18f,
                    listOf(
                        SvgText.Run("Bold ", 0f, 40f, 16f, weight = 700, color = 0xFF111111.toInt()),
                        SvgText.Run("code", 40f, 36f, 16f, monospace = true, color = 0xFF111111.toInt()),
                        SvgText.Run("<link>", 76f, 50f, 16f, color = 0xFF2F6BDE.toInt(), underline = true, link = "https://x.test/?a=1&b=2"),
                    ),
                ),
                SvgText.TextLine(40f, emptyList()),
                SvgText.TextLine(60f, listOf(SvgText.Run("gone", 20f, 30f, 16f, italic = true, strikethrough = true, color = 0xFF111111.toInt()))),
            ),
            decorations = listOf(
                SvgText.Decoration.Box(38f, 4f, 78f, 22f, 0xFFEFEDE8.toInt(), 3f),
                SvgText.Decoration.Circle(10f, 54f, 2f, 0xFF111111.toInt()),
                SvgText.Decoration.Outline(0f, 0f, 10f, 10f, 0xFF66635C.toInt(), 1f),
                SvgText.Decoration.Polyline(listOf(1f, 2f, 3f, 4f, 5f, 2f), 0xFFFFFFFF.toInt(), 1f),
                SvgText.Decoration.Label("1.", 0f, 60f, 16f, 0xFF111111.toInt()),
                SvgText.Decoration.Image(photo.path, 0f, 70f, 40f, 90f),
            ),
        )
        val box = TextBox("t", Rect(10f, 20f, 200f, 100f), "ignored", 16f, Argb.BLACK)
        val doc = parse(svg(note(els = listOf(box)), text = { layout }))
        val texts = doc.all("text")
        assertEquals("two text lines and one label", 3, texts.size)
        val tspans = doc.all("tspan")
        assertEquals(4, tspans.size)
        assertEquals("700", tspans[0].getAttribute("font-weight"))
        assertEquals(SvgNoteWriter.MONO, tspans[1].getAttribute("font-family"))
        assertEquals("<link>", tspans[2].textContent)
        assertEquals("underline", tspans[2].getAttribute("text-decoration"))
        assertEquals("50", tspans[2].getAttribute("textLength"))
        val a = doc.all("a").single()
        assertEquals("https://x.test/?a=1&b=2", a.getAttributeNS(SvgNoteWriter.XLINK_NS, "href"))
        assertEquals("italic", tspans[3].getAttribute("font-style"))
        assertEquals("line-through", tspans[3].getAttribute("text-decoration"))
        assertEquals("Bold code<link>", texts[1].textContent)
        assertEquals(1, doc.all("circle").size)
        assertEquals(1, doc.all("polyline").size)
        assertEquals(1, doc.all("image").size)
        val group = doc.all("g").first { it.hasAttribute("transform") }
        assertEquals("translate(10 20)", group.getAttribute("transform"))
        assertTrue(group.getAttribute("clip-path").startsWith("url(#text"))
    }

    @Test
    fun pagesStackWithOneClipEach() {
        val page = PageSetup.Pages.a4(count = 3)
        val onSecond = wave("second", page.pageRect(1).top + 100f)
        val n = note(page, elements + onSecond)
        val doc = parse(svg(n, ExportOptions(format = ExportFormat.SVG, dotGrid = true)))
        val vb = doc.documentElement.floats("viewBox")
        assertEquals(page.width, vb[2], 0.01f)
        assertEquals(page.height * 3 + PageSetup.Pages.PAGE_GAP * 2, vb[3], 0.01f)
        assertEquals(3, doc.all("clipPath").count { it.getAttribute("id").startsWith("page") })
        assertEquals(3, doc.all("pattern").size)
        assertEquals("one desk rect", 1, doc.all("rect").count { it.getAttribute("fill") == "#E4E4E7" })

        val doc2 = parse(svg(n, ExportOptions(format = ExportFormat.SVG, range = ExportRange.Pages(1, 1))))
        val vb2 = doc2.documentElement.floats("viewBox")
        assertEquals(page.pageRect(1).top, vb2[1], 0.01f)
        assertEquals(page.height, vb2[3], 0.01f)
        assertEquals("only the stroke on page 2", 1, doc2.all("path").size)
    }

    @Test
    fun transparentDarkSelection() {
        val dark = { c: Int -> c xor 0x00FFFFFF }
        val writer = SvgNoteWriter(palette, ::outline, color = dark)
        val out = StringBuilder()
        writer.write(
            note(), ExportOptions(format = ExportFormat.SVG, range = ExportRange.Selection(setOf("rect")), background = ExportBackground.TRANSPARENT), out,
        )
        val doc = parse(out.toString())
        val rects = doc.all("rect")
        assertEquals("sheet clip and the shape", 2, rects.size)
        val shape = rects.single { it.hasAttribute("fill") }
        assertEquals("#000A62", shape.getAttribute("fill"))
        assertEquals("#FFFFFF", shape.getAttribute("stroke"))
        val vb = doc.documentElement.floats("viewBox")
        assertEquals(40f - 1f - 24f, vb[0], 0.01f)
    }

    @Test
    fun numbersAndColours() {
        assertEquals("0", SvgXml.num(-0.001f))
        assertEquals("-1.5", SvgXml.num(-1.5f))
        assertEquals("12.34", SvgXml.num(12.344f))
        assertEquals("3", SvgXml.num(2.999f))
        assertEquals("0.05", SvgXml.num(0.05f))
        assertEquals("#00FF80", SvgXml.rgb(0x8000FF80.toInt()))
        assertEquals(null, SvgXml.opacity(0xFF000000.toInt()))
        assertEquals("image/png", SvgXml.imageMime("png"))
        assertNotNull(SvgXml.dataUri(ByteArray(0), "webp"))
    }
}

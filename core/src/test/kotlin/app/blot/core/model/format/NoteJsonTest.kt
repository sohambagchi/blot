package app.blot.core.model.format

import app.blot.core.FORMAT_VERSION
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.AssetRef
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.Orientation
import app.blot.core.model.PageSetup
import app.blot.core.model.Paper
import app.blot.core.model.PenKind
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NoteJsonTest {
    // Points already on the quantisation grid, so the round trip is exact.
    private val realPoints = PointCodec.decode(PointCodec.encode(Fixtures.stroke(Fixtures.strokeNames[0])))

    private val note = Note(
        id = "0b6c1c9e-6a3e-4e55-9d0e-0d5c1f4f7a10",
        title = "Every element",
        created = Instant.parse("2026-11-24T19:00:00Z"),
        modified = Instant.parse("2026-11-24T19:20:01.123456Z"),
        page = PageSetup.Pages.a4(Orientation.LANDSCAPE, count = 2),
        elements = listOf(
            Image("img", AssetRef("ab".repeat(32), "png"), Rect(0f, 0f, 640f, 480f)),
            Stroke("s1", realPoints, Argb.parse("#FF202020"), width = 2.5f, pen = PenKind.PEN),
            Stroke(
                "s2", listOf(StrokePoint(5f, 5f, 0f, timeMs = 0)), Argb.parse("#80FFEB3B"), 12f,
                PenKind.HIGHLIGHTER, pressureReported = false,
            ),
            Shape("r", ShapeKind.RECTANGLE, Rect(10f, 10f, 100f, 60f), Argb.BLACK, 2f, rotation = 0.25f),
            Shape("e", ShapeKind.ELLIPSE, Rect(-50f, 30f, 80f, 80f), Argb.parse("#FF1565C0"), 0f, fill = Argb.WHITE),
            Line(
                "l", listOf(Point(0f, 0f), Point(40.5f, -3f), Point(90f, 12f)), Argb.parse("#FFD32F2F"), 3f,
                startArrow = Arrowhead.ARROW, endArrow = Arrowhead.TRIANGLE,
            ),
            TextBox(
                "t", Rect(200f, 300f, 400f, 120f),
                "# Title\n\n- item with \"quotes\", a tab\tand ünïcødé ✓", 18f, Argb.BLACK,
            ),
        ),
    )

    @Test
    fun everyElementTypeRoundTrips() {
        val text = NoteJson.encode(note)
        assertEquals(note, NoteJson.decode(text))
        assertEquals(text, NoteJson.encode(NoteJson.decode(text)))
    }

    @Test
    fun fileStartsWithFormatAndUsesTypeTags() {
        val text = NoteJson.encode(note)
        assertTrue(text, text.startsWith("{\n  \"format\": $FORMAT_VERSION,\n"))
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(FORMAT_VERSION, root.getValue("format").jsonPrimitive.int)
        for (tag in listOf("stroke", "shape", "line", "text", "image", "pages")) {
            assertTrue(tag, text.contains("\"type\": \"$tag\""))
        }
        assertTrue(text.contains("\"color\": \"#FF202020\""))
        assertTrue(text.contains("\"modified\": \"2026-11-24T19:20:01.123456Z\""))
    }

    @Test
    fun defaultNoteRoundTrips() {
        val empty = Note("n", created = Instant.EPOCH, modified = Instant.EPOCH)
        assertEquals(empty, NoteJson.decode(NoteJson.encode(empty)))
        assertTrue(NoteJson.encode(empty).contains("\"type\": \"infinite\""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val read = NoteJson.decode(Fixtures.resource("notes/unknown-fields.json"))
        assertEquals("From a newer app", read.title)
        assertEquals(Paper.A4, (read.page as PageSetup.Pages).paper)
        assertEquals(3, read.elements.size)
        val stroke = read.elements[0] as Stroke
        assertEquals(PenKind.PENCIL, stroke.pen)
        assertEquals(listOf(1.5f, -0.25f), stroke.points.map { it.x })
        val shape = read.elements[1] as Shape
        assertEquals(Argb.of(0x40, 0, 0xFF, 0), shape.fill)
        assertEquals(Rect(10f, 20f, 100f, 50f), shape.frame)
        assertEquals("# Hello", (read.elements[2] as TextBox).markdown)
    }

    @Test
    fun optionalFieldsMayBeMissing() {
        val text = """
            {"format": 1, "id": "n", "created": "2026-01-01T00:00:00Z", "modified": "2026-01-01T00:00:00Z",
             "elements": [{"type": "shape", "id": "r", "kind": "rectangle",
                           "frame": {"x": 0, "y": 0, "width": 1, "height": 1}, "color": "#FF000000", "width": 1}]}
        """.trimIndent()
        val read = NoteJson.decode(text)
        assertEquals(PageSetup.Infinite, read.page)
        assertEquals("", read.title)
        assertNull((read.elements.single() as Shape).fill)
    }

    @Test
    fun badFilesAreRejected() {
        val good = NoteJson.encode(note)
        val bad = listOf(
            "[]",
            "{",
            good.replace("\"format\": $FORMAT_VERSION", "\"format\": ${FORMAT_VERSION + 1}"),
            good.replace("\"format\": $FORMAT_VERSION,", ""),
            good.replace("\"format\": $FORMAT_VERSION", "\"format\": \"1\""),
            good.replace("\"type\": \"line\"", "\"type\": \"hologram\""),
            good.replace("\"kind\": \"ellipse\"", "\"kind\": \"star\""),
            good.replace("\"color\": \"#FF202020\"", "\"color\": \"red\""),
            good.replace("\"id\": \"s2\"", "\"id\": \"s1\""),
            good.replace(Regex("\"points\": \"[^\"]+\""), "\"points\": \"AAAA\""),
            good.replace("\"created\": \"2026-11-24T19:00:00Z\"", "\"created\": \"yesterday\""),
            good.replace(Regex("\"sha256\": \"[^\"]+\""), "\"sha256\": \"../../etc/passwd\""),
        )
        bad.forEachIndexed { i, text ->
            assertTrue("case $i unchanged", i < 2 || text != good)
            assertThrows("case $i", NoteFormatException::class.java) { NoteJson.decode(text) }
        }
    }
}

package app.blot.core.model.format

import app.blot.core.FORMAT_VERSION
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShapesAndRoutesJsonTest {
    private val t0 = Instant.parse("2026-11-24T19:00:00Z")

    private fun note(els: List<Element>) = Note("n", created = t0, modified = t0, elements = els)

    private fun format(text: String) = Json.parseToJsonElement(text).jsonObject.getValue("format").jsonPrimitive.int

    private val names = mapOf(
        ShapeKind.RECTANGLE to "rectangle",
        ShapeKind.ELLIPSE to "ellipse",
        ShapeKind.ROUNDED_RECTANGLE to "roundedRectangle",
        ShapeKind.TRIANGLE to "triangle",
        ShapeKind.DIAMOND to "diamond",
        ShapeKind.HEXAGON to "hexagon",
        ShapeKind.PARALLELOGRAM to "parallelogram",
        ShapeKind.CYLINDER to "cylinder",
    )

    @Test
    fun everyShapeKindRoundTrips() {
        assertEquals(ShapeKind.entries.toSet(), names.keys)
        for (kind in ShapeKind.entries) {
            val n = note(listOf(Shape("s", kind, Rect(1f, 2f, 30f, 40f), Argb.BLACK, 2f, fill = Argb.WHITE, rotation = 0.5f)))
            val text = NoteJson.encode(n)
            assertEquals(kind.name, n, NoteJson.decode(text))
            assertTrue(text, text.contains("\"kind\": \"${names[kind]}\""))
            assertEquals(kind.name, if (kind.isFormat1) 1 else 2, format(text))
        }
    }

    @Test
    fun everyRouteRoundTripsAndStraightIsLeftOut() {
        val pts = listOf(Point(0f, 0f), Point(50f, 0f), Point(50f, 40f), Point(90f, 40f))
        for (route in LineRoute.entries) {
            val n = note(listOf(Line("l", pts, Argb.BLACK, 2f, Arrowhead.TRIANGLE, Arrowhead.ARROW, route = route, group = "g")))
            val text = NoteJson.encode(n)
            assertEquals(route.name, n, NoteJson.decode(text))
            val el = Json.parseToJsonElement(text).jsonObject.getValue("elements").jsonArray.single().jsonObject
            if (route == LineRoute.STRAIGHT) {
                assertFalse(text, "route" in el)
                assertEquals(1, format(text))
            } else {
                assertEquals(route.name.lowercase(), el.getValue("route").jsonPrimitive.content)
                assertEquals(2, format(text))
            }
        }
    }

    @Test
    fun aNoteIsWrittenWithTheLowestFormatItNeeds() {
        val plain = listOf(
            Shape("r", ShapeKind.RECTANGLE, Rect(0f, 0f, 1f, 1f), Argb.BLACK, 1f),
            Line("l", listOf(Point(0f, 0f), Point(1f, 1f)), Argb.BLACK, 1f),
        )
        assertEquals(1, NoteJson.formatOf(note(plain)))
        assertEquals(1, NoteJson.formatOf(note(emptyList())))
        assertEquals(2, NoteJson.formatOf(note(plain + Shape("h", ShapeKind.HEXAGON, Rect(0f, 0f, 1f, 1f), Argb.BLACK, 1f))))
        assertEquals(2, NoteJson.formatOf(note(plain + Line("c", listOf(Point(0f, 0f), Point(1f, 1f)), Argb.BLACK, 1f, route = LineRoute.CURVE))))
        assertEquals(2, FORMAT_VERSION)
    }

    @Test
    fun aFormat1FileStillReadsAndWritesByteForByte() {
        val before = Fixtures.resource("notes/every-element-0.12.json")
        assertTrue(before.startsWith("{\n  \"format\": 1,\n"))
        val read = NoteJson.decode(before)
        assertTrue(read.elements.filterIsInstance<Line>().all { it.route == LineRoute.STRAIGHT })
        assertEquals(before, NoteJson.encode(read))
        // Adding an elbow line needs format 2; everything else stays as it was.
        val more = read.copy(elements = read.elements + Line("e", listOf(Point(0f, 0f), Point(9f, 0f), Point(9f, 9f)), Argb.BLACK, 1f, route = LineRoute.ELBOW))
        val after = NoteJson.encode(more)
        assertEquals(2, format(after))
        val elementsStart = before.indexOf("  \"elements\"")
        assertEquals(before.substring(before.indexOf("\n  \"id\""), elementsStart), after.substring(after.indexOf("\n  \"id\""), after.indexOf("  \"elements\"")))
        assertTrue(after.contains(before.substring(elementsStart, before.lastIndexOf("    }")).trimEnd()))
    }

    @Test
    fun format2FilesReadAndNewerOnesAreRefused() {
        val text = """{"format": 2, "id": "n", "created": "2026-11-24T19:00:00Z", "modified": "2026-11-24T19:00:00Z",
            "elements": [{"type": "shape", "id": "c", "kind": "cylinder", "frame": {"x": 0, "y": 0, "width": 4, "height": 8},
            "color": "#FF000000", "width": 1},
            {"type": "line", "id": "l", "points": [{"x": 0, "y": 0}, {"x": 5, "y": 5}], "color": "#FF000000", "width": 1, "route": "curve"}]}"""
        val read = NoteJson.decode(text)
        assertEquals(ShapeKind.CYLINDER, (read.elements[0] as Shape).kind)
        assertEquals(LineRoute.CURVE, (read.elements[1] as Line).route)
        assertThrows(NoteFormatException::class.java) { NoteJson.decode(text.replace("\"format\": 2", "\"format\": 3")) }
        assertThrows(NoteFormatException::class.java) { NoteJson.decode(text.replace("\"curve\"", "\"zigzag\"")) }
    }
}

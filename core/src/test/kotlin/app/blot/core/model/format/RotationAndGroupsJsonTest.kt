package app.blot.core.model.format

import app.blot.core.model.Argb
import app.blot.core.model.AssetRef
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RotationAndGroupsJsonTest {
    private val t0 = Instant.parse("2026-11-24T19:00:00Z")
    private val asset = AssetRef("cd".repeat(32), "jpg")

    private fun note(vararg els: app.blot.core.model.Element) = Note("n", created = t0, modified = t0, elements = els.toList())

    @Test
    fun filesWrittenBeforeRotationAndGroupsAreByteIdentical() {
        // Written by 0.12.2, before text boxes and images had a rotation and elements a group.
        val before = Fixtures.resource("notes/every-element-0.12.json")
        assertEquals(before, NoteJson.encode(NoteJson.decode(before)))
    }

    @Test
    fun rotatedTextAndImageRoundTrip() {
        val n = note(
            TextBox("t", Rect(10f, 20f, 200f, 60f), "turned", 18f, Argb.BLACK, rotation = 0.5f),
            Image("i", asset, Rect(0f, 0f, 64f, 48f), rotation = -1.25f),
        )
        val text = NoteJson.encode(n)
        assertEquals(n, NoteJson.decode(text))
        val els = Json.parseToJsonElement(text).jsonObject.getValue("elements").jsonArray
        assertEquals(0.5f, els[0].jsonObject.getValue("rotation").jsonPrimitive.float)
        assertEquals(-1.25f, els[1].jsonObject.getValue("rotation").jsonPrimitive.float)
        assertTrue(text.startsWith("{\n  \"format\": 1,"))
    }

    @Test
    fun zeroRotationAndNoGroupAreLeftOut() {
        val text = NoteJson.encode(note(TextBox("t", Rect(0f, 0f, 10f, 10f), "x", 12f, Argb.BLACK), Image("i", asset, Rect(0f, 0f, 1f, 1f))))
        assertFalse(text, text.contains("rotation"))
        assertFalse(text, text.contains("group"))
    }

    @Test
    fun groupsRoundTripOnEveryElementType() {
        val g = "g1"
        val n = note(
            Stroke("s", listOf(StrokePoint(1f, 2f, 1f, timeMs = 0)), Argb.BLACK, 2f, group = g),
            Shape("r", ShapeKind.RECTANGLE, Rect(0f, 0f, 10f, 10f), Argb.BLACK, 1f, group = g),
            Line("l", listOf(Point(0f, 0f), Point(5f, 5f)), Argb.BLACK, 1f, group = g),
            TextBox("t", Rect(0f, 0f, 10f, 10f), "x", 12f, Argb.BLACK, group = g),
            Image("i", asset, Rect(0f, 0f, 1f, 1f), group = "g2"),
        )
        val text = NoteJson.encode(n)
        assertEquals(n, NoteJson.decode(text))
        val els = Json.parseToJsonElement(text).jsonObject.getValue("elements").jsonArray
        assertEquals(listOf("g1", "g1", "g1", "g1", "g2"), els.map { it.jsonObject.getValue("group").jsonPrimitive.content })
    }

    @Test
    fun anOlderFileWithoutTheFieldsReadsAsUnrotatedAndUngrouped() {
        val text = """{"format": 1, "id": "n", "created": "2026-11-24T19:00:00Z", "modified": "2026-11-24T19:00:00Z",
            "elements": [{"type": "image", "id": "i", "asset": {"sha256": "${"cd".repeat(32)}", "extension": "jpg"},
            "frame": {"x": 0, "y": 0, "width": 4, "height": 4}}]}"""
        val img = NoteJson.decode(text).elements.single() as Image
        assertEquals(0f, img.rotation)
        assertEquals(null, img.group)
    }
}

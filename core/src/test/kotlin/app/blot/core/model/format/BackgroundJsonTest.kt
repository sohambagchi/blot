package app.blot.core.model.format

import app.blot.core.FORMAT_VERSION
import app.blot.core.model.Background
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Pattern
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackgroundJsonTest {
    private val t0 = Instant.parse("2026-11-24T19:00:00Z")

    @Test
    fun filesWithoutABackgroundAreByteIdentical() {
        val before = Fixtures.resource("notes/every-element-0.12.json")
        val note = NoteJson.decode(before)
        assertNull(note.background)
        assertEquals(before, NoteJson.encode(note))
        assertFalse(NoteJson.encode(Note("n", created = t0, modified = t0)).contains("background"))
    }

    @Test
    fun eachPatternRoundTrips() {
        for (pattern in Pattern.entries) {
            for (page in listOf(PageSetup.Infinite, PageSetup.Pages.a4(count = 2))) {
                val n = Note("n", created = t0, modified = t0, page = page, background = Background(pattern, 33.5f))
                val text = NoteJson.encode(n)
                assertEquals(n, NoteJson.decode(text))
                assertEquals(text, NoteJson.encode(NoteJson.decode(text)))
                val bg = Json.parseToJsonElement(text).jsonObject.getValue("background").jsonObject
                assertEquals(pattern.name.lowercase(), bg.getValue("pattern").jsonPrimitive.content)
                assertEquals(33.5f, bg.getValue("spacing").jsonPrimitive.float)
            }
        }
        assertEquals(1, FORMAT_VERSION)
    }

    @Test
    fun theFieldFollowsTheElements() {
        val text = NoteJson.encode(Note("n", created = t0, modified = t0, background = Background(Pattern.RULED)))
        assertTrue(text, text.endsWith("  \"elements\": [],\n  \"background\": {\n    \"pattern\": \"ruled\",\n    \"spacing\": 45.0\n  }\n}\n"))
    }

    @Test
    fun aMissingSpacingTakesThePatternsDefault() {
        val n = NoteJson.decode(
            """{"format": 1, "id": "n", "created": "2026-11-24T19:00:00Z", "modified": "2026-11-24T19:00:00Z", "background": {"pattern": "grid"}}""",
        )
        assertEquals(Background(Pattern.GRID, 28f), n.background)
    }

    @Test
    fun badBackgroundsAreRejected() {
        for (bg in listOf("""{"pattern": "hexes"}""", """{"pattern": "dots", "spacing": 0}""", """{"pattern": "dots", "spacing": -3}""", """{"spacing": 20}""")) {
            assertThrows(bg, NoteFormatException::class.java) {
                NoteJson.decode("""{"format": 1, "id": "n", "created": "2026-11-24T19:00:00Z", "modified": "2026-11-24T19:00:00Z", "background": $bg}""")
            }
        }
    }
}

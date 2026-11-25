package app.blot.export

import app.blot.core.export.ExportFormat
import app.blot.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExportFileNameTest {
    private val created = Instant.parse("2026-11-24T21:05:30Z")
    private fun note(title: String) = Note(id = "n", title = title, created = created, modified = created)

    @Test
    fun titledNotesUseTheTitle() {
        assertEquals("Raft notes.png", ExportFiles.fileName(note("Raft notes"), ExportFormat.PNG))
        assertEquals("Raft notes", ExportFiles.shareTitle(note("  Raft notes ")))
    }

    @Test
    fun untitledNotesUseTheCreationTimeInLocalTime() {
        val utc = ZoneId.of("UTC")
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals("Blot 2026-11-24 2105.pdf", ExportFiles.fileName(note(""), ExportFormat.PDF, utc))
        assertEquals("Blot 2026-11-25 0235.svg", ExportFiles.fileName(note("   "), ExportFormat.SVG, kolkata))
        assertEquals("Blot 2026-11-24 2105", ExportFiles.shareTitle(note(""), utc))
        // A title of nothing but characters a file name can't keep counts as untitled.
        assertEquals("Blot 2026-11-24 2105.png", ExportFiles.fileName(note(" ... "), ExportFormat.PNG, utc))
    }

    @Test
    fun rejectedCharactersAreReplaced() {
        assertEquals("Pages _ sample_ 2", ExportFiles.safeName("Pages / sample: 2"))
        assertEquals("a_b_c_d_e_f_g_h_i_j", ExportFiles.safeName("a\\b*c?d\"e<f>g|h\u0001i\nj"))
        assertEquals("hidden", ExportFiles.safeName("..hidden.. "))
        assertEquals("", ExportFiles.safeName("  "))
    }

    @Test
    fun longTitlesAreCutWithoutSplittingCodePoints() {
        val emoji = "📝"
        val name = ExportFiles.safeName(emoji.repeat(100))
        assertEquals(80, name.codePointCount(0, name.length))
        assertEquals(emoji.repeat(80), name)
        assertEquals("x".repeat(79), ExportFiles.safeName("x".repeat(79) + ". tail".padEnd(40, 'y')))
    }
}

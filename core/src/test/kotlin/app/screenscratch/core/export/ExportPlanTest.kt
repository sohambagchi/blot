package app.screenscratch.core.export

import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Point
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class ExportPlanTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private fun box(id: String, x: Float, y: Float) = Shape(id, ShapeKind.RECTANGLE, Rect(x, y, 100f, 50f), Argb.BLACK, 0f, fill = Argb.BLACK)

    @Test
    fun infiniteNoteFramesItsContent() {
        val note = Note("n", created = t0, modified = t0, elements = listOf(box("a", 10f, 20f), box("b", 300f, 400f)))
        val plan = ExportPlan.of(note, ExportOptions(margin = 10f))
        assertEquals(Rect(0f, 10f, 410f, 450f), plan.bounds)
        assertEquals(listOf(plan.bounds), plan.sheets.map { it.rect })
        assertEquals(Point(0f, 0f), plan.sheets[0].dotOrigin)

        val empty = ExportPlan.of(note.copy(elements = emptyList()), ExportOptions(margin = 10f))
        assertEquals(Rect(-10f, -10f, 21f, 21f), empty.bounds)

        val area = ExportPlan.of(note, ExportOptions(range = ExportRange.Area(Rect(0f, 0f, 50f, 50f))))
        assertEquals(listOf("a"), area.elements.map { it.id })
    }

    @Test
    fun pageRangesClampToTheNote() {
        val page = PageSetup.Pages.letter(count = 4)
        val note = Note("n", created = t0, modified = t0, page = page, elements = listOf(box("a", 10f, page.pageRect(3).top + 10f)))
        assertEquals(4, ExportPlan.of(note, ExportOptions()).sheets.size)
        val last = ExportPlan.of(note, ExportOptions(range = ExportRange.Pages(3, 9)))
        assertEquals(listOf(page.pageRect(3)), last.sheets.map { it.rect })
        assertEquals(Point(0f, page.pageRect(3).top), last.sheets[0].dotOrigin)
        assertEquals(listOf("a"), last.elements.map { it.id })
        assertEquals(0, ExportPlan.of(note, ExportOptions(range = ExportRange.Pages(0, 1))).elements.size)
    }

    @Test
    fun sizes() {
        val a4 = ExportSizes.pdfPage(Rect(0f, 0f, PageSetup.Pages.A4_SHORT, PageSetup.Pages.A4_LONG))
        assertEquals(595 to 842, a4.width to a4.height)
        val huge = ExportSizes.pdfPage(Rect(0f, 0f, 100_000f, 10f))
        assertEquals(14_400, huge.width)
        val png = ExportSizes.png(Rect(0f, 0f, 1000f, 500f), 2f, 1L shl 25)
        assertEquals(Triple(2000, 1000, 2f), Triple(png.width, png.height, png.scale))
        val capped = ExportSizes.png(Rect(0f, 0f, 10_000f, 10_000f), 3f, 1_000_000)
        assertTrue(capped.width.toLong() * capped.height <= 1_000_000)
    }

    @Test
    fun dotsSitInCellCentres() {
        val dots = DotGrid.dots(Rect(0f, 0f, 56f, 30f), Point(0f, 0f)).toList()
        assertEquals(listOf(Point(14f, 14f), Point(42f, 14f)), dots)
        assertEquals(Point(14f, 1914f), DotGrid.dots(Rect(0f, 1900f, 20f, 20f), Point(0f, 1900f)).single())
    }

    @Test
    fun directoryAssetsStayInsideTheNote() {
        val dir = Files.createTempDirectory("note").toFile()
        File(dir, "assets").mkdirs()
        File(dir, "assets/x.png").writeBytes(byteArrayOf(7))
        File(dir.parentFile, "outside.png").writeBytes(byteArrayOf(1))
        val assets = ExportAssets.directory(dir)
        assertEquals(7.toByte(), assets.read("assets/x.png")!![0])
        assertEquals(null, assets.read("../outside.png"))
        assertEquals(null, assets.read("assets/missing.png"))
        dir.deleteRecursively()
    }
}

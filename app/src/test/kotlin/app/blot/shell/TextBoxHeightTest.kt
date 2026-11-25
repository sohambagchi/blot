package app.blot.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.canvas.CanvasController
import app.blot.canvas.CanvasPalette
import app.blot.canvas.OutlineCache
import app.blot.canvas.SceneRenderer
import app.blot.canvas.Viewport
import app.blot.core.model.Argb
import app.blot.core.model.Rect
import app.blot.core.model.TextBox
import app.blot.markdown.MarkdownTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * A text box from the text tool to the screen, as the app wires it: the controller measures with
 * [MarkdownTextBoxRenderer] and [SceneRenderer] draws with it. Renders land in
 * `app/build/outputs/markdown/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextBoxHeightTest {
    private val outDir = File("build/outputs/markdown").apply { mkdirs() }
    private val renderer = MarkdownTextBoxRenderer()

    private fun controller(): CanvasController =
        CanvasController(CanvasController.newNote()).also { it.textMeasurer = renderer::measureHeight }

    /** Types [markdown] into a new box at (40, 40), as a text-tool tap does, and commits it. */
    private fun CanvasController.type(markdown: String, width: Float = 320f): TextBox {
        val size = CanvasController.DEFAULT_TEXT_SIZE
        beginTextEdit(TextBox("t", Rect(40f, 40f, width, size * 1.4f), "", size, Argb.BLACK))
        updateTextDraft(markdown)
        finishTextEdit()
        return note.value.elements.single() as TextBox
    }

    private fun render(c: CanvasController, dark: Boolean, name: String): Bitmap {
        val box = c.note.value.elements.single() as TextBox
        val w = (box.frame.right + 40).toInt()
        val h = (box.frame.bottom + 40).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.dark = dark
        SceneRenderer(OutlineCache(), renderer).draw(Canvas(bmp), c.note.value, Viewport(), w, h, dark)
        File(outDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun Bitmap.count(top: Float, bottom: Float, left: Float = 0f, right: Float = width.toFloat(), test: (Int) -> Boolean): Int {
        var n = 0
        for (y in top.toInt().coerceAtLeast(0) until bottom.toInt().coerceAtMost(height)) {
            for (x in left.toInt().coerceAtLeast(0) until right.toInt().coerceAtMost(width)) if (test(getPixel(x, y))) n++
        }
        return n
    }

    private fun isInk(p: Int) = (p and 0xFF) < 0x80 && (p shr 8 and 0xFF) < 0x80

    @Test
    fun committedBoxGetsTheFullMarkdownHeightAndRendersAsMarkdown() {
        val c = controller()
        val box = c.type(SAMPLE)
        val measured = renderer.measureHeight(box)
        assertEquals(measured, box.frame.height, 0.01f)
        // Heading, paragraph, three list items and a three-line code block: far more than the
        // one line the tool's placeholder frame holds.
        assertTrue("height ${box.frame.height}", box.frame.height > box.fontSize * 9)

        val bmp = render(c, dark = false, "text-box-height-light.png")
        val f = box.frame
        // The code block's background is drawn (only the markdown renderer draws one), in the box's lower half.
        val codeBg = MarkdownTheme.Light.codeBackground
        val codeRows = (f.top.toInt() until f.bottom.toInt()).filter { y -> bmp.count(y.toFloat(), y + 1f, f.left, f.right) { it == codeBg } > 50 }
        assertTrue("code background rows ${codeRows.size}", codeRows.size > box.fontSize * 2)
        assertTrue(codeRows.first() > f.top + f.height / 2)
        // The last line of the code block is inside the frame and inked, not clipped away.
        assertTrue(bmp.count(codeRows.last() - box.fontSize * 1.2f, codeRows.last().toFloat(), f.left, f.right, ::isInk) > 5)
        // The heading's ink is taller than a body line's: it's styled, not shown as "# ..." source.
        val headingInk = inkRows(bmp, f.top, f.top + box.fontSize * 2f, f.left, f.right)
        assertTrue("heading ink rows $headingInk", headingInk > box.fontSize * 0.75f)
        // Nothing is drawn below the frame.
        assertEquals(0, bmp.count(f.bottom + 1, bmp.height.toFloat(), test = ::isInk))
    }

    @Test
    fun heightFollowsEditsThemeAndWidth() {
        val c = controller()
        val first = c.type("# Title\n\nOne line")
        val twoLine = first.frame.height

        c.editTextBox(first.id)
        c.updateTextDraft("# Title\n\nOne line\n\n- and\n- a list")
        c.finishTextEdit()
        val edited = c.note.value.elements.single() as TextBox
        assertTrue(edited.frame.height > twoLine + edited.fontSize * 2)
        assertEquals(renderer.measureHeight(edited), edited.frame.height, 0.01f)

        renderer.dark = true
        c.redraw()
        assertEquals(edited.frame.height, (c.note.value.elements.single() as TextBox).frame.height, 0.01f)
        renderer.dark = false

        c.select(setOf(edited.id))
        c.scaleSelection(0.5f, edited.frame.x, edited.frame.y)
        val scaled = c.note.value.elements.single() as TextBox
        assertEquals(renderer.measureHeight(scaled), scaled.frame.height, 0.01f)
        assertTrue(abs(scaled.frame.height - edited.frame.height / 2) < edited.fontSize)
    }

    @Test
    fun narrowingABoxReflowsItsHeight() {
        val c = controller()
        val wide = c.type(SAMPLE, width = 480f)
        c.edit { n -> n.copy(elements = listOf(wide.copy(frame = wide.frame.copy(width = 160f)))) }
        val narrow = c.note.value.elements.single() as TextBox
        assertTrue(narrow.frame.height > wide.frame.height)
        assertEquals(renderer.measureHeight(narrow), narrow.frame.height, 0.01f)
    }

    @Test
    fun boxesSavedWithAStaleHeightAreFittedWhenTheRendererArrives() {
        val stale = TextBox("t", Rect(0f, 0f, 320f, 25f), SAMPLE, 18f, Argb.BLACK)
        val c = CanvasController(CanvasController.newNote().copy(elements = listOf(stale)))
        assertEquals(25f, (c.note.value.elements.single() as TextBox).frame.height)
        c.textMeasurer = renderer::measureHeight
        val fitted = c.note.value.elements.single() as TextBox
        assertEquals(renderer.measureHeight(stale), fitted.frame.height, 0.01f)
        assertTrue(!c.canUndo)
    }

    @Test
    fun rendersDark() {
        val c = controller()
        val box = c.type(SAMPLE)
        val bmp = render(c, dark = true, "text-box-height-dark.png")
        assertEquals(CanvasPalette.DARK.paper, bmp.getPixel(2, 2))
        val bg = MarkdownTheme.Dark.codeBackground
        assertTrue(bmp.count(box.frame.top, box.frame.bottom, box.frame.left, box.frame.right) { it == bg } > 500)
    }

    private fun inkRows(bmp: Bitmap, top: Float, bottom: Float, left: Float, right: Float): Int =
        (top.toInt() until bottom.toInt()).count { y -> bmp.count(y.toFloat(), y + 1f, left, right, ::isInk) > 0 }

    private companion object {
        val SAMPLE = """
            # Shopping

            Remember **bold** things and *soft* ones.

            - eggs
            - flour
            - milk

            ```
            fun bake() {
                oven.on()
            }
            ```
        """.trimIndent()
    }
}

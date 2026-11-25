package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.ui.editor.Palettes
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A stroke per colour-panel palette chip, laid out as the panel's grid (a column per palette,
 * dark to light down it), rendered to `app/build/outputs/canvas-render/palette-{light,dark}.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaletteRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val cellW = 90f
    private val cellH = 60f

    private fun cellX(col: Int) = 20f + col * cellW
    private fun cellY(row: Int) = 20f + row * cellH

    private fun note(): Note {
        val strokes = Palettes.flatMapIndexed { col, (_, chips) ->
            chips.mapIndexed { row, hex ->
                val pts = List(40) { i ->
                    val t = i / 39f
                    val x = cellX(col) + 8f + t * (cellW - 30f)
                    val y = cellY(row) + cellH / 2 + 10f * sin(t * 2 * PI).toFloat()
                    StrokePoint(StrokeBuilder.snapXy(x), StrokeBuilder.snapXy(y), StrokeBuilder.snapPressure(0.7f), timeMs = i * 4L)
                }
                Stroke("s$col-$row", pts, Argb(hex.toInt()), 8f, pressureReported = false)
            }
        }
        return Note("palette", created = t0, modified = t0, page = PageSetup.Infinite, elements = strokes)
    }

    private fun render(dark: Boolean): Bitmap {
        val w = (40f + Palettes.size * cellW).toInt()
        val h = (40f + 4 * cellH).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        SceneRenderer(OutlineCache()).draw(Canvas(bmp), note(), Viewport(zoom = 1f, density = 1f), w, h, dark)
        File(outDir, "palette-${if (dark) "dark" else "light"}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun near(expected: Int, actual: Int, tol: Int) =
        listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tol }

    @Test
    fun eachChipDrawsAsTheTransformSays() {
        for (dark in listOf(false, true)) {
            val bmp = render(dark)
            Palettes.forEachIndexed { col, (_, chips) ->
                chips.forEachIndexed { row, hex ->
                    // The wave crosses its midline at its start, middle and end; sample the middle.
                    val x = (cellX(col) + 8f + (cellW - 30f) / 2).toInt()
                    val y = (cellY(row) + cellH / 2).toInt()
                    val want = if (dark) InkColors.darkMode(hex.toInt()) else hex.toInt()
                    val got = bmp.getPixel(x, y)
                    assertTrue("%s %08X: want %08X, got %08X".format(if (dark) "dark" else "light", hex, want, got), near(want, got, 6))
                }
            }
        }
    }
}

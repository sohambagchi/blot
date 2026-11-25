package app.screenscratch.shell

import android.graphics.Bitmap
import app.screenscratch.canvas.CanvasController
import app.screenscratch.canvas.OutlineCache
import app.screenscratch.canvas.SceneRenderer
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.ui.library.PageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val png = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)

    @Test
    fun keysSeparateNotesVersionsAndThemes() {
        val keys = setOf(
            ThumbnailCache.key("a", 1, false),
            ThumbnailCache.key("a", 1, true),
            ThumbnailCache.key("a", 2, false),
            ThumbnailCache.key("b", 1, false),
            // Ids are folder names and may hold anything; they must not run into each other.
            ThumbnailCache.key("a_1", 1, false),
            ThumbnailCache.key("a/1", 1, false),
        )
        assertEquals(6, keys.size)
        for (k in keys) assertTrue(k, k.matches(Regex("[A-Za-z0-9.%_-]+")))
    }

    @Test
    fun aNewVersionReplacesTheOldOne() {
        val cache = ThumbnailCache(tmp.root)
        cache.put("n", 100, false, png, CardInfo(PageKind.A4, 2))
        cache.put("n", 100, true, png, CardInfo(PageKind.A4, 2))
        cache.put("other", 5, false, png, CardInfo(PageKind.Infinite, 1))
        assertNotNull(cache.file("n", 100, false))
        assertNotNull(cache.file("n", 100, true))
        assertEquals(CardInfo(PageKind.A4, 2), cache.info("n", 100))
        assertNull(cache.file("n", 101, false))

        cache.put("n", 200, false, png, CardInfo(PageKind.A4, 3))
        assertNull(cache.file("n", 100, false))
        assertNull(cache.file("n", 100, true))
        assertNull(cache.info("n", 100))
        assertEquals(CardInfo(PageKind.A4, 3), cache.info("n", 200))
        assertNotNull(cache.file("other", 5, false))

        cache.remove("n")
        assertNull(cache.file("n", 200, false))
        assertNotNull(cache.file("other", 5, false))
    }

    @Test
    fun aBlankNoteIsCachedAsKnownBlank() {
        val cache = ThumbnailCache(tmp.root)
        cache.put("blank", 1, false, null, CardInfo(PageKind.Infinite, 1))
        val f = cache.file("blank", 1, false)
        assertNotNull(f)
        assertNull(ThumbnailCache.decode(f!!))
    }

    @Test
    fun rendersInkCentredOnATransparentBitmap() {
        val pts = List(30) { StrokePoint(100f + it * 10f, 50f + it * 2f, 0.6f, timeMs = it * 4L) }
        val note = CanvasController.newNote(PageSetup.Pages.a4()).copy(elements = listOf(Stroke("s", pts, Argb.BLACK, 4f)))
        val bmp = ThumbnailRenderer.render(note, false, SceneRenderer(OutlineCache()))!!
        assertEquals(ThumbnailRenderer.WIDTH, bmp.width)
        assertEquals(0, bmp.getPixel(2, 2) ushr 24)
        assertNotEquals(0, bmp.getPixel(bmp.width / 2, bmp.height / 2) ushr 24)
        assertEquals(CardInfo(PageKind.A4, 1), CardInfo.of(note))
        assertNull(ThumbnailRenderer.render(note.copy(elements = emptyList()), false, SceneRenderer(OutlineCache())))
    }
}

package app.screenscratch.shell

import android.os.Looper
import app.screenscratch.canvas.CanvasController
import app.screenscratch.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AutosaveTest {
    private val saved = ArrayList<Note>()
    private val autosave = Autosave({ saved += it })
    private val base = CanvasController.newNote()
    private fun note(title: String) = base.copy(title = title)
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun savesOnceASecondAfterTheLastEdit() {
        autosave.onEdit(note("a"))
        advance(600)
        autosave.onEdit(note("b"))
        advance(600)
        autosave.onEdit(note("c"))
        advance(999)
        assertTrue(saved.isEmpty())
        advance(1)
        assertEquals(listOf("c"), saved.map { it.title })
        advance(5_000)
        assertEquals(1, saved.size)
    }

    @Test
    fun flushSavesAtOnceAndOnlyOnce() {
        autosave.onEdit(note("a"))
        autosave.flush()
        assertEquals(listOf("a"), saved.map { it.title })
        assertFalse(autosave.hasPending)
        advance(2_000)
        autosave.flush()
        assertEquals(1, saved.size)
    }

    @Test
    fun aFailedSaveIsRetriedOnTheNextFlushUnlessANewerEditWaits() {
        autosave.retry(note("failed"))
        autosave.flush()
        assertEquals(listOf("failed"), saved.map { it.title })

        autosave.onEdit(note("newer"))
        autosave.retry(note("older"))
        autosave.flush()
        assertEquals(listOf("failed", "newer"), saved.map { it.title })
    }

    @Test
    fun cancelDropsThePendingEdit() {
        autosave.onEdit(note("a"))
        autosave.cancel()
        advance(2_000)
        assertTrue(saved.isEmpty())
    }
}

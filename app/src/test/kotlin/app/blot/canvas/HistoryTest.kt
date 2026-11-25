package app.blot.canvas

import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HistoryTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private fun note(n: Int) = Note(
        "n", created = t0, modified = t0,
        elements = List(n) { Stroke("s$it", listOf(StrokePoint(it.toFloat(), 0f, 0.5f, timeMs = 0)), Argb.BLACK, 1f) },
    )

    @Test
    fun undoAndRedoWalkTheSnapshots() {
        val h = History()
        val n0 = note(0)
        val n1 = note(1)
        val n2 = note(2)
        h.push(n0)
        h.push(n1)
        assertSame(n1, h.undo(n2))
        assertSame(n0, h.undo(n1))
        assertNull(h.undo(n0))
        assertSame(n1, h.redo(n0))
        assertSame(n2, h.redo(n1))
        assertNull(h.redo(n2))
    }

    @Test
    fun anEditClearsRedo() {
        val h = History()
        h.push(note(0))
        h.undo(note(1))
        assertTrue(h.canRedo)
        h.push(note(0))
        assertFalse(h.canRedo)
        assertTrue(h.canUndo)
    }

    @Test
    fun stepsAreBounded() {
        val h = History(maxSteps = 3)
        repeat(10) { h.push(note(it)) }
        assertEquals(3, h.undoSize)
        assertEquals(9, h.undo(note(10))!!.elements.size)
    }

    @Test
    fun elementReferencesAreBoundedOldestFirst() {
        val h = History(maxSteps = 100, maxElementRefs = 25)
        for (n in 1..10) h.push(note(n)) // 55 refs in all
        // Only the newest that fit within 25: 10 + 9 = 19; adding 8 would make 27.
        assertEquals(2, h.undoSize)
        assertEquals(10, h.undo(note(11))!!.elements.size)
        // The redo entry (11) counts too: 9 + 11 = 20.
        assertEquals(1, h.undoSize)
        assertEquals(1, h.redoSize)
    }

    @Test
    fun clearForgetsEverything() {
        val h = History()
        h.push(note(1))
        h.undo(note(2))
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }
}

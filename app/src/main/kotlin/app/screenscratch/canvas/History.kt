package app.screenscratch.canvas

import app.screenscratch.core.model.Note

/**
 * Undo and redo as immutable [Note] snapshots. Snapshots share their element objects, so one
 * costs about a reference per element; the history keeps at most [maxSteps] undo steps and at
 * most [maxElementRefs] element references across both stacks, dropping the oldest first.
 */
class History(val maxSteps: Int = 200, val maxElementRefs: Int = 1_000_000) {
    private val undo = ArrayDeque<Note>()
    private val redo = ArrayDeque<Note>()
    private var refs = 0L

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    val undoSize: Int get() = undo.size
    val redoSize: Int get() = redo.size

    /** Records [before], the note as it was before an edit. Clears the redo stack. */
    fun push(before: Note) {
        undo.addLast(before)
        refs += before.elements.size
        while (redo.isNotEmpty()) refs -= redo.removeLast().elements.size
        trim()
    }

    /** The note to go back to from [current], or null if there is none. */
    fun undo(current: Note): Note? {
        val n = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        refs += current.elements.size - n.elements.size
        trim()
        return n
    }

    /** The note to go forward to from [current], or null if there is none. */
    fun redo(current: Note): Note? {
        val n = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        refs += current.elements.size - n.elements.size
        trim()
        return n
    }

    fun clear() {
        undo.clear()
        redo.clear()
        refs = 0
    }

    private fun trim() {
        while (undo.size > maxSteps) refs -= undo.removeFirst().elements.size
        while (refs > maxElementRefs && undo.isNotEmpty()) refs -= undo.removeFirst().elements.size
        while (refs > maxElementRefs && redo.isNotEmpty()) refs -= redo.removeFirst().elements.size
    }
}

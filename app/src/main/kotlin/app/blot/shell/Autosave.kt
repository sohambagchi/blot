package app.blot.shell

import android.os.Handler
import android.os.Looper
import app.blot.core.model.Note

/**
 * Debounces saves: [onEdit] records the latest note and [save] gets it [delayMs] after the last
 * edit; [flush] hands over a pending note at once (leaving the editor, `onStop`). [save] is
 * called on [handler]'s thread and should move the write off it. Main thread only.
 */
class Autosave(
    private val save: (Note) -> Unit,
    private val delayMs: Long = DELAY_MS,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var pending: Note? = null
    private val fire = Runnable { flush() }

    /** True while an edit waits to be saved. */
    val hasPending: Boolean get() = pending != null

    fun onEdit(note: Note) {
        pending = note
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, delayMs)
    }

    fun flush() {
        handler.removeCallbacks(fire)
        val n = pending ?: return
        pending = null
        save(n)
    }

    /** A save of [note] failed: keep it for the next [flush] unless a newer edit is already waiting. */
    fun retry(note: Note) {
        if (pending == null) pending = note
    }

    /** Drops anything pending without saving. */
    fun cancel() {
        handler.removeCallbacks(fire)
        pending = null
    }

    companion object {
        const val DELAY_MS = 1_000L
    }
}

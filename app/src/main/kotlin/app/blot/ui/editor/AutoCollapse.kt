package app.blot.ui.editor

/**
 * Decides when continuous writing should collapse the toolbar. The canvas reports pen activity
 * (pen down and each move) through [onWritingActivity]; a gap longer than [pauseMs] ends the
 * writing spell. Once a spell has lasted [collapseAfterMs], [onWritingActivity] returns true
 * exactly once. It never asks to expand: only the drag handle does that.
 */
class AutoCollapse(
    val collapseAfterMs: Long = 15_000,
    val pauseMs: Long = 3_000,
) {
    private var spellStart = -1L
    private var last = -1L
    private var fired = false

    /** Records activity at [nowMs] (a monotonic clock); true when the toolbar should collapse now. */
    fun onWritingActivity(nowMs: Long): Boolean {
        if (spellStart < 0 || nowMs - last > pauseMs) {
            spellStart = nowMs
            fired = false
        }
        last = nowMs
        if (!fired && nowMs - spellStart >= collapseAfterMs) {
            fired = true
            return true
        }
        return false
    }

    /** Starts over, e.g. after the user expands the toolbar mid-spell. */
    fun reset() {
        spellStart = -1L
        last = -1L
        fired = false
    }
}

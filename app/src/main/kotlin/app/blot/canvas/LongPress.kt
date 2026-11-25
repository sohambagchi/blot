package app.blot.canvas

import kotlin.math.hypot

/**
 * Detects a press held still: [arm] at pointer-down, [move] with each new position, and [fire]
 * once [timeoutMs] have passed. A move further than [slop] from the down position, or [disarm]
 * (another pointer, lift, cancel), breaks it. Times are milliseconds on one clock, positions in
 * one unit (view pixels on the canvas). Not thread-safe.
 */
internal class LongPress(private val slop: Float, val timeoutMs: Long = TIMEOUT_MS) {
    var armed = false
        private set
    private var x0 = 0f
    private var y0 = 0f
    private var t0 = 0L

    fun arm(x: Float, y: Float, timeMs: Long) {
        armed = true
        x0 = x
        y0 = y
        t0 = timeMs
    }

    /** Returns whether the press is still armed. */
    fun move(x: Float, y: Float): Boolean {
        if (armed && hypot(x - x0, y - y0) > slop) armed = false
        return armed
    }

    fun disarm() {
        armed = false
    }

    /** True, once, when the press has been held for [timeoutMs] at [timeMs]; it is then disarmed. */
    fun fire(timeMs: Long): Boolean {
        if (!armed || timeMs - t0 < timeoutMs) return false
        armed = false
        return true
    }

    companion object {
        /** How long a press is held to open the selection menu, as Android's default long press. */
        const val TIMEOUT_MS = 400L
    }
}

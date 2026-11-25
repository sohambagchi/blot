package app.screenscratch.shell

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.view.InputDevice
import app.screenscratch.canvas.FrontBufferContent
import app.screenscratch.ui.theme.ThemeSetting

/**
 * The app's persisted settings. "Pen only" defaults per device: on when an input device reports
 * `SOURCE_STYLUS` (the tablet), off otherwise (the phone), until the user changes it.
 */
class AppSettings(
    private val prefs: SharedPreferences,
    private val hasStylus: () -> Boolean,
) {
    var theme: ThemeSetting
        get() = prefs.getString(KEY_THEME, null)?.let { s -> ThemeSetting.entries.firstOrNull { it.name == s } } ?: ThemeSetting.System
        set(v) = prefs.edit().putString(KEY_THEME, v.name).apply()

    var swapSideButtons: Boolean
        get() = prefs.getBoolean(KEY_SWAP, false)
        set(v) = prefs.edit().putBoolean(KEY_SWAP, v).apply()

    var penOnly: Boolean
        get() = if (prefs.contains(KEY_PEN_ONLY)) prefs.getBoolean(KEY_PEN_ONLY, true) else hasStylus()
        set(v) = prefs.edit().putBoolean(KEY_PEN_ONLY, v).apply()

    /** Ink only by default: chrome over the canvas stays visible while writing, and it was faster on the emulator. */
    var frontBuffer: FrontBufferContent
        get() = prefs.getString(KEY_FRONT, null)?.let { s -> FrontBufferContent.entries.firstOrNull { it.name == s } } ?: FrontBufferContent.INK_ONLY
        set(v) = prefs.edit().putString(KEY_FRONT, v.name).apply()

    companion object {
        const val PREFS = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_SWAP = "swap_side_buttons"
        private const val KEY_PEN_ONLY = "pen_only"
        private const val KEY_FRONT = "front_buffer"

        fun of(context: Context) = AppSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) { hasStylus(context) }

        /** Whether any input device reports `SOURCE_STYLUS`. */
        fun hasStylus(context: Context): Boolean {
            val im = context.getSystemService(InputManager::class.java) ?: return false
            return im.inputDeviceIds.any { id -> im.getInputDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true }
        }
    }
}

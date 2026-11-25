package app.blot.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The app's colour tokens, named as in the design canvas (`c.*` in `Main.dc.html` and `Library.dc.html`). */
@Immutable
data class BlotColors(
    val dark: Boolean,
    val bg: Color,
    val dot: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val active: Color,
    val activeText: Color,
    val hover: Color,
    val code: Color,
    val selected: Color,
    val chip: Color,
    val chipText: Color,
    val danger: Color,
    /** Shadow of the editor's floating bars and panels. */
    val shadow: Color,
    /** The library's slightly stronger shadow (menus, new-note button). */
    val shadowStrong: Color,
    val scrim: Color,
) {
    companion object {
        val Light = BlotColors(
            dark = false,
            bg = Color(0xFFF6F5F2),
            dot = Color(0xFFD6D3CB),
            surface = Color(0xFFFFFFFF),
            card = Color(0xFFFFFFFF),
            border = Color(0xFFE3E0D8),
            text = Color(0xFF1C1B19),
            muted = Color(0xFF66635C),
            active = Color(0xFF1C1B19),
            activeText = Color(0xFFFFFFFF),
            hover = Color(0xFFEFEDE8),
            code = Color(0xFFEFEDE8),
            selected = Color(0xFFEAE7E0),
            chip = Color(0xFFEFEDE8),
            chipText = Color(0xFF4F4C46),
            danger = Color(0xFFC93535),
            shadow = Color(0x141C1B19),
            shadowStrong = Color(0x1A1C1B19),
            scrim = Color(0x591C1B19),
        )

        val Dark = BlotColors(
            dark = true,
            bg = Color(0xFF17171A),
            dot = Color(0xFF2B2B30),
            surface = Color(0xFF222226),
            card = Color(0xFF1E1E22),
            border = Color(0xFF36363C),
            text = Color(0xFFECEAE6),
            muted = Color(0xFFA3A09A),
            active = Color(0xFFECEAE6),
            activeText = Color(0xFF17171A),
            hover = Color(0xFF2E2E33),
            code = Color(0xFF2E2E33),
            selected = Color(0xFF2E2E33),
            chip = Color(0xFF2E2E33),
            chipText = Color(0xFFC9C6BF),
            danger = Color(0xFFFF6B6B),
            shadow = Color(0x73000000),
            shadowStrong = Color(0x73000000),
            scrim = Color(0x8C000000),
        )
    }
}

package app.blot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The user's theme choice in Settings. */
enum class ThemeSetting { System, Light, Dark }

val LocalScratchColors = staticCompositionLocalOf { BlotColors.Light }

/** Provides [BlotColors] for [setting], following the system for [ThemeSetting.System]. */
@Composable
fun BlotTheme(setting: ThemeSetting = ThemeSetting.System, content: @Composable () -> Unit) {
    val dark = when (setting) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    BlotTheme(dark, content)
}

@Composable
fun BlotTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalScratchColors provides if (dark) BlotColors.Dark else BlotColors.Light,
        content = content,
    )
}

/** Shorthand for the current [BlotColors]. */
val colors: BlotColors
    @Composable get() = LocalScratchColors.current

/** Corner radii from the design, in dp. */
object Radii {
    val switch = 10.dp
    val chip = 8.dp
    val menuItem = 10.dp
    val toolButton = 11.dp
    val segment = 9.dp
    val field = 12.dp
    val menu = 14.dp
    val pill = 14.dp
    val card = 14.dp
    val bar = 16.dp
    val dialog = 18.dp
}

/** A CSS-style box shadow: `0 offsetY blur color`. */
data class Elevation(val offsetY: Dp, val blur: Dp, val strong: Boolean = false) {
    companion object {
        /** Floating bars and pills: `0 2px 10px`. */
        val Bar = Elevation(2.dp, 10.dp)
        /** Menus and panels: `0 8px 28px`. */
        val Menu = Elevation(8.dp, 28.dp)
        /** Dialogs: `0 12px 40px`. */
        val Dialog = Elevation(12.dp, 40.dp)
        /** The phone's bottom pill: `0 6px 20px`. */
        val PhonePill = Elevation(6.dp, 20.dp)
        /** Library menus: `0 8px 28px` with the stronger shadow colour. */
        val LibraryMenu = Elevation(8.dp, 28.dp, strong = true)
        /** New-note button: `0 4px 16px`. */
        val Fab = Elevation(4.dp, 16.dp, strong = true)
        /** Size buttons above it: `0 2px 10px`. */
        val FabItem = Elevation(2.dp, 10.dp, strong = true)
        /** Selected segment: `0 1px 3px`. */
        val Segment = Elevation(1.dp, 3.dp)
    }
}

/** The switch knob's fixed shadow colour, `rgba(0,0,0,0.25)`. */
val KnobShadow = Color(0x40000000)

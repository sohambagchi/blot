package app.blot.ui.theme

import androidx.compose.ui.graphics.Color
import app.blot.canvas.InkColors as CanvasInk
import app.blot.core.model.Argb

/** The four quick ink colours as stored in notes (true colours). */
object QuickInk {
    val Black = Argb(0xFF1C1B19.toInt())
    val Red = Argb(0xFFD93F3F.toInt())
    val Blue = Argb(0xFF2F6BDE.toInt())
    val Green = Argb(0xFF2E9A5B.toInt())
    val all = listOf(Black, Red, Blue, Green)
}

/**
 * How a stored ink colour looks on the page in the current theme, for swatches and dots in the
 * chrome: the canvas's own dark-mode transform, so a swatch matches the ink it draws
 * (docs/adr/2026-11-24-2329-dark-mode-ink-lightness-band.md).
 */
fun inkForDisplay(ink: Argb, dark: Boolean): Color = Color(if (dark) CanvasInk.darkMode(ink.value) else ink.value)

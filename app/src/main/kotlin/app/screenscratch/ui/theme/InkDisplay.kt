package app.screenscratch.ui.theme

import androidx.compose.ui.graphics.Color
import app.screenscratch.core.model.Argb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The four quick ink colours as stored in notes (true colours). */
object QuickInk {
    val Black = Argb(0xFF1C1B19.toInt())
    val Red = Argb(0xFFD93F3F.toInt())
    val Blue = Argb(0xFF2F6BDE.toInt())
    val Green = Argb(0xFF2E9A5B.toInt())
    val all = listOf(Black, Red, Blue, Green)
}

/**
 * How a stored ink colour looks on the page in the current theme, for swatches and dots in
 * the chrome. Dark mode inverts near-greys and lightens chromatic colours
 * (docs/adr/2026-11-24-1742-dark-mode-ink-invert-and-lighten.md); the four quick colours map
 * exactly to the design's dark tints. The canvas owns the authoritative transform; this one
 * only has to agree with it closely enough for a swatch.
 */
fun inkForDisplay(ink: Argb, dark: Boolean): Color {
    if (!dark) return Color(ink.value)
    when (ink) {
        QuickInk.Black -> return InkColors.Dark.black
        QuickInk.Red -> return InkColors.Dark.red
        QuickInk.Blue -> return InkColors.Dark.blue
        QuickInk.Green -> return InkColors.Dark.green
    }
    val r = ink.red / 255f
    val g = ink.green / 255f
    val b = ink.blue / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    val d = hi - lo
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        hi == r -> 60f * (((g - b) / d).mod(6f))
        hi == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    val l2 = if (s < 0.15f) (1f - l).coerceIn(0.08f, 0.92f) else l + (1f - l) * 0.35f
    return Color.hsl(h, s.coerceIn(0f, 1f), l2.coerceIn(0f, 1f), ink.alpha / 255f)
}

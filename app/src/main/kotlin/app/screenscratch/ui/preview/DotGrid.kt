package app.screenscratch.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.screenscratch.ui.theme.colors

/** A blank page with the design's dot grid: 1.2 dp dots centred in 28 dp cells. Stands in for the canvas. */
@Composable
fun DotGrid(modifier: Modifier = Modifier, spacing: Dp = 28.dp, radius: Dp = 1.2.dp) {
    val c = colors
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.bg)
        val step = spacing.toPx()
        val r = radius.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(c.dot, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

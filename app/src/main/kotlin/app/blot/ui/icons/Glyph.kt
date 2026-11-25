package app.blot.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A stroke icon from the design: SVG path data in a [viewportWidth] × [viewportHeight] box,
 * drawn with round caps and joins. [dashed] paths are drawn with the design's `3 3` dash.
 */
@Immutable
class Glyph(
    val paths: List<String>,
    val dashed: List<String> = emptyList(),
    val viewportWidth: Float = 24f,
    val viewportHeight: Float = 24f,
) {
    constructor(vararg paths: String) : this(paths.toList())

    /** Builds the [ImageVector] at [strokeWidth] viewport units. */
    fun toImageVector(strokeWidth: Float = 1.8f, width: Dp = 24.dp, height: Dp = 24.dp): ImageVector {
        val b = ImageVector.Builder(
            defaultWidth = width,
            defaultHeight = height,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        for (d in paths) b.stroked(addPathNodes(d), strokeWidth)
        for (d in dashed) b.stroked(dashNodes(d, 3f, 3f), strokeWidth)
        return b.build()
    }

    private fun ImageVector.Builder.stroked(nodes: List<PathNode>, width: Float) {
        addPath(
            pathData = nodes,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    companion object {
        /** SVG `<circle>` as path data. */
        fun circle(cx: Float, cy: Float, r: Float) =
            "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

        /** SVG `<ellipse>` as path data. */
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
            "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0"

        /** SVG `<rect>` with corner radius [r] as path data. */
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
                "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}Z"
    }
}

/**
 * Splits path [d] into [on]-long dashes separated by [off] gaps, as polylines sampled every
 * half unit: ImageVector has no dash effect.
 */
private fun dashNodes(d: String, on: Float, off: Float): List<PathNode> {
    val path = PathParser().parsePathString(d).toPath()
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    val nodes = ArrayList<PathNode>()
    var start = 0f
    while (start < length) {
        val end = min(start + on, length)
        val steps = max(1, ceil((end - start) / 0.5f).toInt())
        for (i in 0..steps) {
            val p = measure.getPosition(start + (end - start) * i / steps)
            nodes += if (i == 0) PathNode.MoveTo(p.x, p.y) else PathNode.LineTo(p.x, p.y)
        }
        start += on + off
    }
    return nodes
}

/** Draws [glyph] at [size], tinted [tint]. */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Float = 1.8f,
    height: Dp = size,
) {
    val vector = remember(glyph, strokeWidth, size, height) { glyph.toImageVector(strokeWidth, size, height) }
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size, height),
        colorFilter = ColorFilter.tint(tint),
    )
}

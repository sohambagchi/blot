package app.blot.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.blot.core.model.Background
import app.blot.core.model.Pattern
import app.blot.ui.components.Rule
import app.blot.ui.components.Segment
import app.blot.ui.components.Segmented
import app.blot.ui.components.floatingSurface
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.KnobShadow
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors
import kotlin.math.roundToInt

/** The background panel's choices and how one leads to the next. */
object BackgroundChoices {
    /** The panel's pattern choices in order; null is no pattern. */
    val patterns: List<Pattern?> = listOf(null, Pattern.DOTS, Pattern.GRID, Pattern.RULED)

    /** Small, medium and large as a factor of the pattern's default spacing. */
    val presetFactors = listOf(0.75f, 1f, 1.5f)

    fun name(p: Pattern?): String = when (p) {
        null -> "None"
        Pattern.DOTS -> "Dots"
        Pattern.GRID -> "Grid"
        Pattern.RULED -> "Lines"
    }

    /** Small, medium and large spacings for [p], in whole canvas units. */
    fun presets(p: Pattern): List<Float> = presetFactors.map { (Background.defaultSpacing(p) * it).roundToInt().toFloat() }

    /** The index of [b]'s spacing in its pattern's [presets], or -1 for a custom spacing. */
    fun presetIndex(b: Background): Int = presets(b.pattern).indexOf(b.spacing)

    /**
     * The background after choosing [pattern] while [current] is shown: the same size preset
     * (medium for a custom spacing or no pattern before), or null for no pattern.
     */
    fun choose(current: Background?, pattern: Pattern?): Background? {
        if (pattern == null) return null
        if (current?.pattern == pattern) return current
        val preset = current?.let { presetIndex(it) }?.takeIf { it >= 0 } ?: 1
        return Background(pattern, presets(pattern)[preset])
    }

    /** [spacing] snapped to whole units within the slider's range. */
    fun clampSpacing(spacing: Float): Float =
        spacing.roundToInt().toFloat().coerceIn(Background.MIN_SPACING, Background.MAX_SPACING)
}

/**
 * The background panel under the corner pill (the design's Layers panel): pattern choice with
 * small previews, size presets and a spacing slider. Every change applies at once.
 */
@Composable
internal fun BackgroundPanel(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val c = colors
    val bg = state.background
    Column(
        modifier
            .width(260.dp)
            .floatingSurface(RoundedCornerShape(Radii.menu), Elevation.Menu)
            .padding(8.dp)
            .semantics { paneTitle = "Background" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Background", Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp), style = BlotType.smallStrong.copy(color = c.text))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (p in BackgroundChoices.patterns) {
                PatternTile(p, bg?.pattern == p, Modifier.weight(1f)) { actions.setBackground(BackgroundChoices.choose(bg, p)) }
            }
        }
        if (bg != null) {
            Rule(Modifier.padding(horizontal = 4.dp))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Size", Modifier.weight(1f), style = BlotType.captionStrong.copy(color = c.muted))
                BasicText("${bg.spacing.roundToInt()} dp", style = BlotType.caption.copy(color = c.muted))
            }
            val presets = BackgroundChoices.presets(bg.pattern)
            Segmented(
                listOf("Small", "Medium", "Large").mapIndexed { i, label -> Segment(presets[i], label) },
                bg.spacing,
                { actions.setBackground(bg.copy(spacing = it)) },
                Modifier.fillMaxWidth(),
                itemHeight = 32.dp,
            )
            SpacingSlider(bg.spacing, Modifier.padding(horizontal = 4.dp)) { actions.setBackground(bg.copy(spacing = it)) }
        }
    }
}

@Composable
private fun PatternTile(pattern: Pattern?, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    val name = BackgroundChoices.name(pattern)
    Column(
        modifier
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = if (pattern == null) "No background pattern" else "$name background"
                selected = chosen
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val mark = c.muted
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(shape)
                .background(c.bg)
                .border(if (chosen) 2.dp else 1.dp, if (chosen) c.text else c.border, shape),
        ) { patternPreview(pattern, mark) }
        BasicText(name, style = (if (chosen) BlotType.captionStrong else BlotType.caption).copy(color = if (chosen) c.text else c.muted))
    }
}

/** A few cells of [pattern] in [color], as the tile's picture. */
private fun DrawScope.patternPreview(pattern: Pattern?, color: Color) {
    val step = 8.dp.toPx()
    val line = 1.dp.toPx()
    when (pattern) {
        null -> Unit
        Pattern.DOTS -> {
            var y = step / 2 + (size.height % step) / 2
            while (y < size.height) {
                var x = step / 2 + (size.width % step) / 2
                while (x < size.width) {
                    drawCircle(color, 1.1.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Pattern.GRID -> {
            var x = (size.width % step) / 2
            while (x < size.width) {
                drawLine(color.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), line)
                x += step
            }
            var y = (size.height % step) / 2
            while (y < size.height) {
                drawLine(color.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), line)
                y += step
            }
        }
        Pattern.RULED -> {
            val rule = step * 1.25f
            var y = rule
            while (y < size.height - rule / 2) {
                drawLine(color.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), line)
                y += rule
            }
        }
    }
}

/** A slider over [Background.MIN_SPACING]–[Background.MAX_SPACING] in whole canvas units. */
@Composable
private fun SpacingSlider(value: Float, modifier: Modifier = Modifier, onValue: (Float) -> Unit) {
    val c = colors
    val emit by rememberUpdatedState(onValue)
    val current by rememberUpdatedState(value)
    val min = Background.MIN_SPACING
    val max = Background.MAX_SPACING
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = "Spacing"
                stateDescription = "${value.roundToInt()} dp"
                progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, steps = (max - min).toInt() - 1)
                setProgress { v ->
                    emit(BackgroundChoices.clampSpacing(v))
                    true
                }
            }
            .pointerInput(Unit) {
                val r = KNOB_RADIUS.toPx()
                fun at(x: Float) {
                    val f = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                    val v = BackgroundChoices.clampSpacing(min + f * (max - min))
                    if (v != current) emit(v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    at(down.position.x)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        at(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val r = KNOB_RADIUS.toPx()
        val track = 4.dp.toPx()
        val cy = size.height / 2
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val x = r + f * (size.width - 2 * r)
        drawRoundRect(c.border, Offset(r, cy - track / 2), Size(size.width - 2 * r, track), CornerRadius(track / 2))
        drawRoundRect(c.active, Offset(r, cy - track / 2), Size(x - r, track), CornerRadius(track / 2))
        drawCircle(KnobShadow, r + 0.5.dp.toPx(), Offset(x, cy + 1.dp.toPx()))
        drawCircle(c.surface, r, Offset(x, cy))
        drawCircle(c.border, r - 0.5.dp.toPx(), Offset(x, cy), style = Stroke(1.dp.toPx()))
    }
}

private val KNOB_RADIUS = 10.dp

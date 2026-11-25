package app.screenscratch.ui.editor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.screenscratch.core.model.Argb
import app.screenscratch.ui.components.Rule
import app.screenscratch.ui.components.SwitchTrack
import app.screenscratch.ui.components.floatingSurface
import app.screenscratch.ui.theme.Elevation
import app.screenscratch.ui.theme.Radii
import app.screenscratch.ui.theme.ScratchType
import app.screenscratch.ui.theme.colors

/** The design's monochrome palettes, dark to light. */
internal val Palettes: List<Pair<String, List<Long>>> = listOf(
    "Greys" to listOf(0xFF1F1F1F, 0xFF5C5C5C, 0xFF9A9A9A, 0xFFD6D6D6),
    "Sepia" to listOf(0xFF3B2716, 0xFF7A5938, 0xFFB08F68, 0xFFE2D1B8),
    "Reds" to listOf(0xFF8E1F1F, 0xFFD93F3F, 0xFFEE8A8A, 0xFFF8D2D2),
    "Oranges" to listOf(0xFF8A3D0C, 0xFFE0711F, 0xFFF2A869, 0xFFFBDDC2),
    "Ambers" to listOf(0xFF7A5A00, 0xFFD9A400, 0xFFF0CD5A, 0xFFFAEDB8),
    "Greens" to listOf(0xFF1D5C34, 0xFF2E9A5B, 0xFF79C894, 0xFFCDEBD6),
    "Teals" to listOf(0xFF0E4F52, 0xFF1F8C8F, 0xFF6CC2C2, 0xFFC8EBEA),
    "Blues" to listOf(0xFF1B3F8F, 0xFF2F6BDE, 0xFF82A9F0, 0xFFD2E0FA),
    "Purples" to listOf(0xFF43207A, 0xFF7A5CFF, 0xFFAE9AFF, 0xFFE1D9FF),
    "Pinks" to listOf(0xFF7E1A4D, 0xFFD6458C, 0xFFEC93BE, 0xFFF8D5E6),
)

/**
 * The wheel's hue and saturation and the strip's tone, kept across recompositions so that a
 * grey or black keeps the hue it was picked with.
 */
private class WheelPick(var h: Float, var s: Float, var tone: Float, var rgb: Int)

/** The colour panel: wheel, tone strip, hex field, widths, palettes and the two toggles. */
@Composable
internal fun ColourPanel(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val c = colors
    val rgb = state.color.value or (0xFF shl 24)
    val pick = remember { WheelPick(0f, 0f, 0.5f, 0) }
    if (pick.rgb != rgb) {
        val hsv = ColourMath.rgbToHsv(rgb)
        if (hsv.v > 0f && hsv.s > 0f) pick.h = hsv.h
        if (hsv.v > 0f) pick.s = hsv.s
        pick.tone = ColourMath.toneOf(hsv)
        pick.rgb = rgb
    }
    val choose = { newRgb: Int ->
        pick.rgb = newRgb
        actions.setColor(Argb(newRgb))
    }
    Column(
        modifier
            .width(360.dp)
            .floatingSurface(RoundedCornerShape(Radii.bar), Elevation.Menu)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColourWheel(pick.h, pick.s) { h, s ->
                pick.h = h
                pick.s = s
                if (pick.tone == 0f) pick.tone = 0.5f
                choose(ColourMath.withTone(h, s, pick.tone))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneStrip(pick.h, pick.s, pick.tone) { t ->
                    pick.tone = t
                    choose(ColourMath.withTone(pick.h, pick.s, t))
                }
                HexField(rgb) { parsed -> choose(parsed) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (w in EditorUiState.WidthPresets) WidthButton(w, state.strokeWidth == w) { actions.setStrokeWidth(w) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for ((name, swatches) in Palettes) {
                Column(Modifier.semantics { contentDescription = name }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (hex in swatches) {
                        val chosen = rgb == hex.toInt()
                        val shape = RoundedCornerShape(7.dp)
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(shape)
                                .background(Color(hex))
                                .border(if (chosen) 2.dp else 1.dp, if (chosen) c.text else c.border, shape)
                                .clickable(role = Role.Button) { choose(hex.toInt()) }
                                .semantics {
                                    contentDescription = ColourMath.toHex(hex.toInt())
                                    selected = chosen
                                },
                        )
                    }
                }
            }
        }
        Rule()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelToggle("Pressure", state.pressure, Modifier.weight(1f)) { actions.setPressure(!state.pressure) }
            PanelToggle("Pen only", state.penOnly, Modifier.weight(1f)) { actions.setPenOnly(!state.penOnly) }
        }
    }
}

/**
 * The HSV wheel: a sweep gradient for hue (red at 3 o'clock, clockwise) under a radial white
 * fade for saturation. Tapping or dragging reports hue and saturation.
 */
@Composable
internal fun ColourWheel(h: Float, s: Float, modifier: Modifier = Modifier, onPick: (Float, Float) -> Unit) {
    val pickNow by rememberUpdatedState(onPick)
    val hues = remember {
        listOf(Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000))
    }
    Canvas(
        modifier
            .size(128.dp)
            .semantics { contentDescription = "Colour wheel" }
            .pointerInput(Unit) {
                fun report(change: PointerInputChange) {
                    val r = size.width / 2f
                    val (hh, ss) = ColourMath.wheelHueSat(change.position.x - r, change.position.y - size.height / 2f, r)
                    pickNow(hh, ss)
                    change.consume()
                }
                awaitEachGesture {
                    report(awaitFirstDown())
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        report(change)
                    }
                }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(hues, center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
        val (dx, dy) = ColourMath.wheelOffset(h, s, r)
        val m = Offset(center.x + dx, center.y + dy)
        val ring = 7.dp.toPx()
        drawCircle(Color(0x59000000), ring + 0.5.dp.toPx(), m, style = Stroke(1.dp.toPx()))
        drawCircle(Color.White, ring - 1.dp.toPx(), m, style = Stroke(2.dp.toPx()))
    }
}

/** Black → wheel colour → white; the knob shows [tone]. */
@Composable
private fun ToneStrip(h: Float, s: Float, tone: Float, onTone: (Float) -> Unit) {
    val toneNow by rememberUpdatedState(onTone)
    val mid = Color(ColourMath.hsvToRgb(h, s, 1f))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { contentDescription = "Shade" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var change: PointerInputChange? = awaitFirstDown()
                    while (change != null && change.pressed) {
                        toneNow((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                        change = awaitPointerEvent().changes.firstOrNull()
                    }
                }
            },
    ) {
        val r = size.height / 2f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.Black, mid, Color.White)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        if (tone != 0.5f) {
            val x = (tone * size.width).coerceIn(r, size.width - r)
            drawCircle(Color.White, r - 1.dp.toPx(), Offset(x, r), style = Stroke(2.dp.toPx()))
        }
    }
}

/** The hex field; edits that parse set the colour, others wait until they do. */
@Composable
private fun HexField(rgb: Int, onColour: (Int) -> Unit) {
    val c = colors
    val focus = LocalFocusManager.current
    val field = rememberTextFieldState(ColourMath.toHex(rgb))
    var focused by remember { mutableStateOf(false) }
    val emit by rememberUpdatedState(onColour)
    LaunchedEffect(rgb, focused) {
        if (!focused) field.setTextAndPlaceCursorAtEnd(ColourMath.toHex(rgb))
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { t ->
            if (focused) ColourMath.parseHex(t)?.let(emit)
        }
    }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        state = field,
        inputTransformation = InputTransformation.maxLengthTrim(7),
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = ScratchType.monoSmall.copy(color = c.text),
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        onKeyboardAction = { focus.clearFocus() },
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(c.bg)
            .border(1.dp, c.border, shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "Hex colour" },
        decorator = { inner ->
            Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}

@Composable
private fun WidthButton(width: Float, chosen: Boolean, onClick: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .border(1.dp, if (chosen) c.text else c.border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "Stroke width ${width.toInt()}"
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((width * 2 + 2).dp).clip(CircleShape).background(c.text))
    }
}

@Composable
private fun PanelToggle(label: String, on: Boolean, modifier: Modifier, onFlip: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, c.border, shape)
            .clickable(role = Role.Switch, onClick = onFlip)
            .semantics { stateDescription = if (on) "On" else "Off" }
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(label, style = ScratchType.small.copy(color = c.text), maxLines = 1)
        SwitchTrack(on)
    }
}

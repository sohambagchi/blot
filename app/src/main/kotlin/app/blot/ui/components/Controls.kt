package app.blot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.blot.ui.icons.Glyph
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.KnobShadow
import app.blot.ui.theme.Radii
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.colors

/** A CSS-like `box-shadow: 0 offsetY blur colour` behind [shape]. */
fun Modifier.boxShadow(shape: Shape, elevation: Elevation, color: Color): Modifier =
    dropShadow(shape, Shadow(radius = elevation.blur, color = color, offset = DpOffset(0.dp, elevation.offsetY)))

/** The design's floating surface: shadow, surface fill and a 1 dp border. It is a [chromeSurface]. */
@Composable
fun Modifier.floatingSurface(shape: Shape, elevation: Elevation? = Elevation.Bar, fill: Color = colors.surface): Modifier {
    val c = colors
    val shadowed = if (elevation == null) this else boxShadow(shape, elevation, if (elevation.strong) c.shadowStrong else c.shadow)
    return shadowed.chromeSurface(shape).clip(shape).background(fill).border(1.dp, c.border, shape)
}

/** A square icon button as used across the design (44 dp touch target by default). */
@Composable
fun IconButton(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    height: Dp = size,
    iconSize: Dp = 22.dp,
    iconHeight: Dp = iconSize,
    strokeWidth: Float = 1.8f,
    radius: Dp = Radii.menuItem,
    background: Color = Color.Transparent,
    tint: Color = colors.text,
    enabled: Boolean = true,
    selected: Boolean? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .size(size, height)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (selected != null) this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint, size = iconSize, height = iconHeight, strokeWidth = strokeWidth)
        content?.invoke()
    }
}

/** The design's 34 × 20 switch track with a 16 dp knob. Purely visual; the row is the touch target. */
@Composable
fun SwitchTrack(checked: Boolean) {
    val c = colors
    Box(
        Modifier.size(34.dp, 20.dp).clip(RoundedCornerShape(Radii.switch)).background(if (checked) c.active else c.border),
    ) {
        Box(
            Modifier
                .offset(x = if (checked) 16.dp else 2.dp, y = 2.dp)
                .boxShadow(CircleShape, Elevation(1.dp, 2.dp), KnobShadow)
                .size(16.dp)
                .clip(CircleShape)
                .background(c.surface),
        )
    }
}

/** A labelled switch row: label (and optional hint) on the left, track on the right. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    minHeight: Dp = 44.dp,
) {
    val c = colors
    Row(
        modifier
            .heightIn(min = minHeight)
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics {
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) "On" else "Off"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(label, style = BlotType.body.copy(color = c.text))
            if (hint != null) BasicText(hint, style = BlotType.caption.copy(color = c.muted))
        }
        SwitchTrack(checked)
    }
}

/** One option of a [Segmented] control. */
data class Segment<T>(val value: T, val label: String, val enabled: Boolean = true)

/**
 * The design's segmented radio group: options on a [colors.bg] trough, the chosen one raised
 * on the surface colour. [raised] adds the export dialog's `0 1px 3px` shadow.
 */
@Composable
fun <T> Segmented(
    options: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    raised: Boolean = true,
    itemHeight: Dp = 40.dp,
) {
    val c = colors
    Row(
        modifier.clip(RoundedCornerShape(Radii.field)).background(c.bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (o in options) {
            val on = o.value == selected
            val shape = RoundedCornerShape(Radii.segment)
            Box(
                Modifier
                    .weight(1f)
                    .height(itemHeight)
                    .then(if (on && raised) Modifier.boxShadow(shape, Elevation.Segment, c.shadow) else Modifier)
                    .clip(shape)
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(enabled = o.enabled, role = Role.RadioButton) { onSelect(o.value) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                val fg = when {
                    !o.enabled -> c.muted.copy(alpha = 0.5f)
                    on -> c.text
                    else -> c.muted
                }
                BasicText(o.label, style = BlotType.bodyMedium.copy(color = fg), maxLines = 1)
            }
        }
    }
}

/** A 1 dp rule in the border colour. */
@Composable
fun Rule(modifier: Modifier = Modifier, vertical: Boolean = false, length: Dp? = null) {
    val c = colors
    val m = if (vertical) {
        Modifier.width(1.dp).then(if (length != null) Modifier.height(length) else Modifier)
    } else {
        Modifier.height(1.dp).then(if (length != null) Modifier.width(length) else Modifier.fillMaxWidth())
    }
    Spacer(modifier.then(m).background(c.border))
}

/** A text button with an outline, as in "Change…" and "Save to folder". */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    height: Dp = 44.dp,
    radius: Dp = Radii.field,
    horizontalPadding: Dp = 16.dp,
    textStyle: TextStyle = BlotType.bodyMedium,
    color: Color = colors.text,
) {
    val c = colors
    val shape = RoundedCornerShape(radius)
    ButtonRow(
        modifier.height(height).clip(shape).border(1.dp, c.border, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
    ) {
        if (glyph != null) GlyphIcon(glyph, color, size = 18.dp)
        BasicText(label, style = textStyle.copy(color = color), maxLines = 1)
    }
}

/** A filled primary button in the active colour, as in "Share…". */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    height: Dp = 44.dp,
    horizontalPadding: Dp = 18.dp,
) {
    val c = colors
    val shape = RoundedCornerShape(Radii.field)
    ButtonRow(
        modifier.height(height).clip(shape).background(c.active).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontalPadding),
    ) {
        if (glyph != null) GlyphIcon(glyph, c.activeText, size = 18.dp)
        BasicText(label, style = BlotType.bodyStrong.copy(color = c.activeText), maxLines = 1)
    }
}

@Composable
private fun ButtonRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Single-line text that ends in an ellipsis. */
@Composable
fun EllipsisText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

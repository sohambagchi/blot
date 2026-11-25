package app.blot.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blot.ui.components.Rule
import app.blot.ui.components.floatingSurface
import app.blot.ui.icons.BlotIcons
import app.blot.ui.icons.Glyph
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors
import kotlin.math.roundToInt

private object SelectionGlyphs {
    val BringForward = Glyph("M5 15h14", "M5 19h14", "M12 11V3", "m8 7 4-4 4 4")
    val SendBackward = Glyph("M5 5h14", "M5 9h14", "M12 13v8", "m8 17 4 4 4-4")
    val Group = Glyph(listOf("M7 7h5v5H7Z", "M12 12h5v5h-5Z"), dashed = listOf("M3 3h18v18H3Z"))
    val Ungroup = Glyph("M3 3h8v8H3Z", "M13 13h8v8h-8Z")
    val Duplicate = Glyph("M8 8h12v12H8Z", "M16 8V4H4v12h4")
}

/** The selection menu's items for [menu], in order: name, glyph, command, whether a rule comes first. */
internal fun selectionMenuItems(menu: SelectionMenu): List<SelectionMenuItem> = listOfNotNull(
    SelectionMenuItem("Bring forward", SelectionGlyphs.BringForward, SelectionCommand.BringForward),
    SelectionMenuItem("Send backward", SelectionGlyphs.SendBackward, SelectionCommand.SendBackward),
    when {
        menu.canUngroup -> SelectionMenuItem("Ungroup", SelectionGlyphs.Ungroup, SelectionCommand.Ungroup, ruleBefore = true)
        menu.canGroup -> SelectionMenuItem("Group", SelectionGlyphs.Group, SelectionCommand.Group, ruleBefore = true)
        else -> null
    },
    SelectionMenuItem("Duplicate", SelectionGlyphs.Duplicate, SelectionCommand.Duplicate, ruleBefore = !menu.canGroup && !menu.canUngroup),
    SelectionMenuItem("Delete", BlotIcons.Delete, SelectionCommand.Delete, ruleBefore = true),
)

internal data class SelectionMenuItem(val name: String, val glyph: Glyph, val command: SelectionCommand, val ruleBefore: Boolean = false)

/**
 * The long-press menu over the selection, when [EditorUiState.selectionMenu] is set: a tool-menu
 * panel above the selection's box (below it when there is no room), kept inside the window. A
 * tap outside it closes it. Lay it over the whole editor, in the canvas's coordinates.
 */
@Composable
internal fun SelectionMenuLayer(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val menu = state.selectionMenu ?: return
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.closeSelectionMenu() } })
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = { SelectionMenuPanel(menu, actions) },
        ) { measurables, constraints ->
            val panel = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            val d = density
            val margin = (MENU_MARGIN_DP * d).roundToInt()
            val gap = (MENU_GAP_DP * d).roundToInt()
            val a = menu.anchor
            val maxX = constraints.maxWidth
            val maxY = constraints.maxHeight
            val x = ((a.center.x * d).roundToInt() - panel.width / 2).coerceIn(margin, maxOf(margin, maxX - panel.width - margin))
            val above = (a.top * d).roundToInt() - gap - panel.height
            val below = (a.bottom * d).roundToInt() + gap
            val y = when {
                above >= margin -> above
                below + panel.height <= maxY - margin -> below
                else -> ((a.center.y * d).roundToInt() - panel.height / 2).coerceIn(margin, maxOf(margin, maxY - panel.height - margin))
            }
            layout(maxX, maxY) { panel.place(x, y) }
        }
    }
}

@Composable
private fun SelectionMenuPanel(menu: SelectionMenu, actions: EditorActions) {
    val c = colors
    Column(
        Modifier
            .width(MENU_WIDTH_DP.dp)
            .floatingSurface(RoundedCornerShape(Radii.menu), Elevation.Menu)
            .padding(6.dp)
            .semantics { contentDescription = "Selection" },
    ) {
        for (item in selectionMenuItems(menu)) {
            if (item.ruleBefore) Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            val tint = if (item.command == SelectionCommand.Delete) c.danger else c.text
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.menuItem))
                    .background(Color.Transparent)
                    .clickable(role = Role.Button) { actions.selectionCommand(item.command) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { GlyphIcon(item.glyph, tint, size = 20.dp) }
                BasicText(item.name, style = BlotType.body.copy(color = tint))
            }
        }
    }
}

private const val MENU_WIDTH_DP = 200
private const val MENU_MARGIN_DP = 12f
private const val MENU_GAP_DP = 12f

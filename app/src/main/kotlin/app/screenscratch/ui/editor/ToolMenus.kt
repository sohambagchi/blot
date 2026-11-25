package app.screenscratch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.screenscratch.ui.components.Rule
import app.screenscratch.ui.components.floatingSurface
import app.screenscratch.ui.icons.Glyph
import app.screenscratch.ui.icons.GlyphIcon
import app.screenscratch.ui.icons.ScratchIcons
import app.screenscratch.ui.theme.Elevation
import app.screenscratch.ui.theme.Radii
import app.screenscratch.ui.theme.colors

/** One entry of a tool menu: a choice (radio) or an on/off option (checkbox). */
internal sealed interface MenuItem {
    val name: String
    val glyph: Glyph
    val dividerBefore: Boolean

    data class Choice(
        override val name: String,
        override val glyph: Glyph,
        val chosen: Boolean,
        val pick: () -> Unit,
        override val dividerBefore: Boolean = false,
    ) : MenuItem

    data class Option(
        override val name: String,
        override val glyph: Glyph,
        val on: Boolean,
        val flip: () -> Unit,
        override val dividerBefore: Boolean = false,
    ) : MenuItem
}

internal data class ToolGroup(
    val menu: ToolMenu,
    val tool: Tool,
    val title: String,
    val currentName: String,
    val glyph: Glyph,
    val items: List<MenuItem>,
)

internal fun penGlyph(mode: PenMode) = when (mode) {
    PenMode.Pen -> ScratchIcons.Pen
    PenMode.Hand -> ScratchIcons.Hand
    PenMode.Pointer -> ScratchIcons.Pointer
}

/** The five menu-bearing tool groups, with their items as in the design's `defs`. */
internal fun toolGroups(s: EditorUiState, a: EditorActions): List<ToolGroup> {
    val penNames = mapOf(PenMode.Pen to "Pen", PenMode.Hand to "Hand: pan and zoom", PenMode.Pointer to "Pointer: select objects")
    val eraserNames = mapOf(EraserMode.WholeStroke to "Erase whole strokes", EraserMode.PartOfStroke to "Erase part of a stroke")
    val eraserGlyphs = mapOf(EraserMode.WholeStroke to ScratchIcons.Eraser, EraserMode.PartOfStroke to ScratchIcons.EraseCut)
    val selectNames = mapOf(SelectMode.Lasso to "Lasso select", SelectMode.Rectangle to "Rectangle select")
    val selectGlyphs = mapOf(SelectMode.Lasso to ScratchIcons.Lasso, SelectMode.Rectangle to ScratchIcons.Marquee)
    val shapes = listOf(
        Triple(ShapeKind.Rectangle, "Rectangle", ScratchIcons.Rect),
        Triple(ShapeKind.RoundedRectangle, "Rounded rectangle", ScratchIcons.RoundedRect),
        Triple(ShapeKind.Ellipse, "Ellipse", ScratchIcons.Ellipse),
        Triple(ShapeKind.Triangle, "Triangle", ScratchIcons.Triangle),
        Triple(ShapeKind.Diamond, "Diamond", ScratchIcons.Diamond),
        Triple(ShapeKind.Hexagon, "Hexagon", ScratchIcons.Hexagon),
        Triple(ShapeKind.Parallelogram, "Parallelogram", ScratchIcons.Parallelogram),
        Triple(ShapeKind.Cylinder, "Cylinder", ScratchIcons.Cylinder),
    )
    val shapeDividers = setOf(ShapeKind.Triangle, ShapeKind.Parallelogram)
    val lines = listOf(
        Triple(LineKind.Line, "Line", ScratchIcons.Line),
        Triple(LineKind.Arrow, "Arrow", ScratchIcons.Arrow),
        Triple(LineKind.DoubleArrow, "Double arrow", ScratchIcons.DoubleArrow),
        Triple(LineKind.Elbow, "Elbow connector", ScratchIcons.Elbow),
        Triple(LineKind.Curve, "Curved arrow", ScratchIcons.Curve),
    )
    val shape = shapes.first { it.first == s.shape }
    val line = lines.first { it.first == s.line }
    return listOf(
        ToolGroup(
            ToolMenu.Pen, Tool.Pen, "Pen tools", penNames.getValue(s.penMode), penGlyph(s.penMode),
            PenMode.entries.mapIndexed { i, m ->
                MenuItem.Choice(penNames.getValue(m), penGlyph(m), m == s.penMode, { a.setPenMode(m) }, dividerBefore = i > 0)
            },
        ),
        ToolGroup(
            ToolMenu.Eraser, Tool.Eraser, "Eraser", eraserNames.getValue(s.eraserMode), eraserGlyphs.getValue(s.eraserMode),
            EraserMode.entries.mapIndexed { i, m ->
                MenuItem.Choice(eraserNames.getValue(m), eraserGlyphs.getValue(m), m == s.eraserMode, { a.setEraserMode(m) }, dividerBefore = i > 0)
            },
        ),
        ToolGroup(
            ToolMenu.Select, Tool.Select, "Select", selectNames.getValue(s.selectMode), selectGlyphs.getValue(s.selectMode),
            SelectMode.entries.mapIndexed { i, m ->
                MenuItem.Choice(selectNames.getValue(m), selectGlyphs.getValue(m), m == s.selectMode, { a.setSelectMode(m) }, dividerBefore = i > 0)
            },
        ),
        ToolGroup(
            ToolMenu.Shape, Tool.Shape, "Shapes", shape.second, shape.third,
            shapes.map { (k, name, g) ->
                MenuItem.Choice(name, g, k == s.shape, { a.setShape(k) }, dividerBefore = k in shapeDividers)
            },
        ),
        ToolGroup(
            ToolMenu.Line, Tool.Line, "Lines and arrows", line.second, line.third,
            lines.map { (k, name, g) ->
                MenuItem.Choice(name, g, k == s.line, { a.setLine(k) }, dividerBefore = k == LineKind.Elbow)
            } + listOf(
                MenuItem.Option("Snap to shapes", ScratchIcons.SnapShape, s.snapToShapes, { a.setSnapToShapes(!s.snapToShapes) }, dividerBefore = true),
                MenuItem.Option("Snap angles to 15°", ScratchIcons.SnapAngle, s.snapAngles, { a.setSnapAngles(!s.snapAngles) }),
            ),
        ),
    )
}

/** A tool menu: a column under a horizontal toolbar, a row beside a vertical one. */
@Composable
internal fun ToolMenuPanel(group: ToolGroup, row: Boolean, modifier: Modifier = Modifier) {
    val c = colors
    val content: @Composable () -> Unit = {
        for (item in group.items) {
            if (item.dividerBefore) {
                if (row) Rule(Modifier.padding(horizontal = 3.dp), vertical = true, length = 28.dp)
                else Rule(Modifier.padding(vertical = 3.dp), length = 28.dp)
            }
            MenuButton(item)
        }
    }
    val m = modifier
        .floatingSurface(RoundedCornerShape(Radii.menu), Elevation.Menu)
        .padding(7.dp)
        .semantics { contentDescription = group.title }
    if (row) {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    } else {
        Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun MenuButton(item: MenuItem) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    val (bg, fg) = when (item) {
        is MenuItem.Choice -> if (item.chosen) c.active to c.activeText else Color.Transparent to c.text
        is MenuItem.Option -> Color.Transparent to if (item.on) c.text else c.muted
    }
    val ring = item is MenuItem.Option && item.on
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(bg)
            .then(if (ring) Modifier.border(1.5.dp, c.text, shape) else Modifier)
            .clickable(role = if (item is MenuItem.Option) Role.Checkbox else Role.RadioButton) {
                when (item) {
                    is MenuItem.Choice -> item.pick()
                    is MenuItem.Option -> item.flip()
                }
            }
            .semantics {
                contentDescription = item.name
                when (item) {
                    is MenuItem.Choice -> selected = item.chosen
                    is MenuItem.Option -> stateDescription = if (item.on) "On" else "Off"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(item.glyph, fg)
    }
}

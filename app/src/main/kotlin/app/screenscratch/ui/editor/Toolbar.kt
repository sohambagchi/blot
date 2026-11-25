package app.screenscratch.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.screenscratch.core.model.Argb
import app.screenscratch.ui.components.Rule
import app.screenscratch.ui.components.floatingSurface
import app.screenscratch.ui.icons.GlyphIcon
import app.screenscratch.ui.icons.ScratchIcons
import app.screenscratch.ui.theme.Radii
import app.screenscratch.ui.theme.QuickInk
import app.screenscratch.ui.theme.colors
import app.screenscratch.ui.theme.inkForDisplay
import kotlin.math.roundToInt

/** Gap between the toolbar and the colour panel. */
private const val PANEL_GAP = 8f
/** Distance from a tool button's leading edge to its menu's (the design's `56px`). */
private const val MENU_OFFSET = 56f

/**
 * The floating toolbar with its menus and colour panel, laid out over a [widthDp] × [heightDp]
 * window. Draws nothing outside the bar and its open panels, so the canvas below keeps its touches.
 * The bar is moved off [obstacles] (dp rectangles in the same space, e.g. the corner pills).
 */
@Composable
internal fun ToolbarLayer(state: EditorUiState, actions: EditorActions, widthDp: Float, heightDp: Float, obstacles: List<Rect> = emptyList()) {
    val vertical = ToolbarGeometry.isVertical(state.toolbarPlacement, widthDp, heightDp)
    val towards = ToolbarGeometry.opensTowards(state.toolbarPlacement, widthDp, heightDp)
    var drag by remember { mutableStateOf<Offset?>(null) }
    val geo = remember { BarGeometry() }
    val anchors = remember { mutableStateMapOf<ToolMenu, Rect>() }
    val density = LocalDensity.current.density
    val groups = toolGroups(state, actions)
    val current by rememberUpdatedState(state)
    val avoid by rememberUpdatedState(obstacles)

    Box(Modifier.fillMaxSize().onGloballyPositioned { geo.layerOrigin = it.positionInRoot() }) {
        if (state.openMenu != null) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.openMenu(null) } })
        }
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                ToolBar(
                    state, actions, groups, vertical,
                    onAnchor = { menu, bounds ->
                        val local = bounds.translate(-geo.layerOrigin)
                        if (anchors[menu] != local) anchors[menu] = local
                    },
                    onDragStart = {
                        actions.openMenu(null)
                        drag = geo.topLeft
                    },
                    onDrag = { delta -> drag = (drag ?: geo.topLeft) + delta / density },
                    onDragEnd = {
                        drag?.let { d ->
                            actions.moveToolbar(
                                ToolbarGeometry.drop(d.x, d.y, geo.size.x, geo.size.y, widthDp, heightDp, vertical),
                            )
                        }
                        drag = null
                    },
                    onToggleCollapsed = { actions.setToolbarCollapsed(!current.toolbarCollapsed) },
                )
                if (state.colourPanelOpen && !state.toolbarCollapsed) ColourPanel(state, actions)
                val menu = state.openMenu
                if (menu != null && anchors[menu] != null) {
                    ToolMenuPanel(groups.first { it.menu == menu }, row = towards == Edge.Left || towards == Edge.Right)
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val bar = measurables[0].measure(loose)
            val d = density
            val bw = bar.width / d
            val bh = bar.height / d
            val (bx, by) = drag?.let { it.x to it.y }
                ?: ToolbarGeometry.topLeft(current.toolbarPlacement, bw, bh, widthDp, heightDp, avoid)
            geo.topLeft = Offset(bx, by)
            geo.size = Offset(bw, bh)
            val panelIndex = if (current.colourPanelOpen && !current.toolbarCollapsed) 1 else -1
            val panel = if (panelIndex > 0) measurables[1].measure(loose) else null
            val menuIndex = if (panelIndex > 0) 2 else 1
            val menu = measurables.getOrNull(menuIndex)?.measure(loose)
            val maxX = constraints.maxWidth
            val maxY = constraints.maxHeight
            val margin = (ToolbarGeometry.MARGIN * d).roundToInt()
            fun clampX(x: Int, w: Int) = x.coerceIn(margin, maxOf(margin, maxX - w - margin))
            fun clampY(y: Int, h: Int) = y.coerceIn(margin, maxOf(margin, maxY - h - margin))
            layout(maxX, maxY) {
                val px = (bx * d).roundToInt()
                val py = (by * d).roundToInt()
                bar.place(px, py)
                val gap = (PANEL_GAP * d).roundToInt()
                panel?.let { p ->
                    when (towards) {
                        Edge.Bottom -> p.place(clampX(px + (bar.width - p.width) / 2, p.width), py + bar.height + gap)
                        Edge.Top -> p.place(clampX(px + (bar.width - p.width) / 2, p.width), py - gap - p.height)
                        Edge.Right -> p.place(px + bar.width + gap, clampY(py + bar.height - p.height, p.height))
                        Edge.Left -> p.place(px - gap - p.width, clampY(py + bar.height - p.height, p.height))
                    }
                }
                val anchor = current.openMenu?.let { anchors[it] }
                if (menu != null && anchor != null) {
                    val off = (MENU_OFFSET * d).roundToInt()
                    val ax = anchor.left.roundToInt()
                    val ay = anchor.top.roundToInt()
                    val cx = anchor.center.x.roundToInt()
                    val cy = anchor.center.y.roundToInt()
                    when (towards) {
                        Edge.Bottom -> menu.place(clampX(cx - menu.width / 2, menu.width), ay + off)
                        Edge.Top -> menu.place(clampX(cx - menu.width / 2, menu.width), ay + anchor.height.roundToInt() - off - menu.height)
                        Edge.Right -> menu.place(ax + off, clampY(cy - menu.height / 2, menu.height))
                        Edge.Left -> menu.place(ax + anchor.width.roundToInt() - off - menu.width, clampY(cy - menu.height / 2, menu.height))
                    }
                }
            }
        }
    }
}

/** Where the bar was last laid out, in dp; read by the drag callbacks, never by composition. */
private class BarGeometry {
    var topLeft = Offset.Zero
    var size = Offset.Zero
    var layerOrigin = Offset.Zero
}

@Composable
private fun ToolBar(
    state: EditorUiState,
    actions: EditorActions,
    groups: List<ToolGroup>,
    vertical: Boolean,
    onAnchor: (ToolMenu, Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onToggleCollapsed: () -> Unit,
) {
    val c = colors
    val collapsed = state.toolbarCollapsed
    val items: @Composable () -> Unit = {
        DragHandle(vertical, !collapsed, onToggleCollapsed, onDragStart, onDrag, onDragEnd)
        val shown = if (collapsed) groups.take(3) else groups
        for (g in shown) {
            if (g.menu == ToolMenu.Shape) BarDivider(vertical)
            val active = state.tool == g.tool
            val open = state.openMenu == g.menu
            ToolButton(
                label = "${g.title}: ${g.currentName}",
                active = active,
                open = open,
                onClick = { if (active) actions.openMenu(if (open) null else g.menu) else actions.selectTool(g.tool) },
                modifier = Modifier.onGloballyPositioned { onAnchor(g.menu, it.boundsInRoot()) },
                menuDot = if (collapsed && g.menu == ToolMenu.Pen) null else true,
                inkDot = if (collapsed && g.menu == ToolMenu.Pen) inkForDisplay(state.color, c.dark) else null,
            ) { tint -> GlyphIcon(g.glyph, tint) }
        }
        if (!collapsed) {
            ToolButton("Text box", state.tool == Tool.Text, false, { actions.selectTool(Tool.Text) }, menuDot = null) { tint ->
                GlyphIcon(ScratchIcons.Text, tint)
            }
            ToolButton("Insert image", state.tool == Tool.Image, false, { actions.selectTool(Tool.Image) }, menuDot = null) { tint ->
                GlyphIcon(ScratchIcons.Image, tint)
            }
            BarDivider(vertical)
            val names = listOf("Black", "Red", "Blue", "Green")
            QuickInk.all.forEachIndexed { i, ink ->
                Swatch(ink, "${names[i]} ink", state.color == ink) { actions.setColor(ink) }
            }
            val open = state.colourPanelOpen
            val chevron = when {
                vertical && open -> ScratchIcons.ChevronLeft
                vertical -> ScratchIcons.ChevronRight
                open -> ScratchIcons.ChevronUp
                else -> ScratchIcons.ChevronDown
            }
            val shape = RoundedCornerShape(Radii.toolButton)
            Box(
                Modifier
                    .size(if (vertical) 44.dp else 36.dp, if (vertical) 36.dp else 44.dp)
                    .clip(shape)
                    .background(if (open) c.hover else Color.Transparent)
                    .clickable(role = Role.Button) { actions.setColourPanelOpen(!open) }
                    .semantics {
                        contentDescription = "More colours and stroke width"
                        stateDescription = if (open) "Expanded" else "Collapsed"
                    },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(chevron, c.muted, size = 18.dp, strokeWidth = 2f)
            }
        }
    }
    val m = Modifier.floatingSurface(RoundedCornerShape(Radii.bar)).padding(7.dp)
    if (vertical) {
        Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    } else {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { items() }
    }
}

@Composable
private fun BarDivider(vertical: Boolean) {
    if (vertical) Rule(Modifier.padding(vertical = 4.dp), length = 24.dp)
    else Rule(Modifier.padding(horizontal = 4.dp), vertical = true, length = 24.dp)
}

/**
 * The drag handle: a tap collapses or expands the toolbar, a drag past the touch slop moves it.
 * Expanding happens only here, so the bar never opens under the pen mid-stroke.
 */
@Composable
private fun DragHandle(
    vertical: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = colors
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        Modifier
            .size(if (vertical) 44.dp else 28.dp, if (vertical) 28.dp else 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragging = false
                    var travelled = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            change?.consume()
                            if (dragging) end() else if (change != null) tap()
                            break
                        }
                        val delta = change.positionChange()
                        travelled += delta
                        if (!dragging && travelled.getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            start()
                        }
                        if (dragging) {
                            move(delta)
                            change.consume()
                        }
                    }
                }
            }
            .semantics {
                contentDescription = "Toolbar handle: drag to move, tap to collapse or expand"
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                onClick { tap(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (vertical) GlyphIcon(ScratchIcons.GripHorizontal, c.muted, size = 24.dp, height = 12.dp, strokeWidth = 3f)
        else GlyphIcon(ScratchIcons.GripVertical, c.muted, size = 12.dp, height = 24.dp, strokeWidth = 3f)
    }
}

/**
 * A 44 dp tool button. [menuDot] marks a tool with a menu (the design's small dot in the
 * corner); [inkDot] replaces it with the current ink colour on the collapsed pen.
 */
@Composable
internal fun ToolButton(
    label: String,
    active: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuDot: Boolean? = true,
    inkDot: Color? = null,
    icon: @Composable (Color) -> Unit,
) {
    val c = colors
    val shape = RoundedCornerShape(Radii.toolButton)
    val fg = if (active) c.activeText else c.text
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .background(
                when {
                    active -> c.active
                    open -> c.hover
                    else -> Color.Transparent
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = active
            },
    ) {
        Box(Modifier.align(Alignment.Center)) { icon(fg) }
        if (inkDot != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset((-4).dp, (-4).dp).size(8.dp)
                    .drawBehind {
                        drawCircle(c.surface, radius = size.minDimension / 2 + 1.5.dp.toPx())
                        drawCircle(inkDot)
                    },
            )
        } else if (menuDot == true) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset((-6).dp, (-6).dp).size(4.dp).clip(CircleShape)
                    .background(fg.copy(alpha = 0.6f)),
            )
        }
    }
}

/** A quick-colour swatch: a 22 dp dot, ringed when chosen. */
@Composable
private fun Swatch(ink: Argb, label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = colors
    val color = inkForDisplay(ink, c.dark)
    Box(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = chosen
            }
            .drawBehind {
                val r = 11.dp.toPx()
                if (chosen) {
                    drawCircle(color, r + 4.dp.toPx())
                    drawCircle(c.surface, r + 2.dp.toPx())
                }
                drawCircle(color, r)
            },
    )
}

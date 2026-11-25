package app.blot.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.blot.ui.components.EllipsisText
import app.blot.ui.components.IconButton
import app.blot.ui.components.Rule
import app.blot.ui.components.floatingSurface
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.icons.BlotIcons
import app.blot.ui.library.PageGlyph
import app.blot.ui.library.PageKind
import app.blot.ui.library.label
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.colors
import kotlinx.coroutines.delay

/** Window width below which the phone viewer replaces the tablet chrome. */
val PhoneMaxWidth = 600.dp

/** How long [EditorUiState.message] stays up. */
const val MessageMillis = 4_000L

/**
 * The editor screen: [content] (the canvas) fills the window and the chrome floats over it,
 * inside the safe-drawing insets. Picks the tablet chrome or the phone viewer by window width.
 */
@Composable
fun Editor(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth < PhoneMaxWidth) PhoneEditorChrome(state, actions, content = content)
        else EditorChrome(state, actions, content = content)
    }
}

/**
 * The tablet editor chrome over [content]: back pill with the note title, the floating toolbar
 * with its menus and colour panel, the undo/redo/export pill, page and zoom chips with the page
 * menu, the message bar, and the export dialog. The toolbar keeps clear of the corner pills.
 */
@Composable
fun EditorChrome(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = colors
    val density = LocalDensity.current.density
    val obstacles = remember { mutableStateMapOf<String, Rect>() }
    val record = { key: String -> Modifier.onGloballyPositioned { obstacles[key] = it.boundsInParent().let { r -> Rect(r.left / density, r.top / density, r.right / density, r.bottom / density) } } }
    Box(modifier.fillMaxSize()) {
        content()
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val full = !state.toolbarCollapsed
            Row(
                Modifier
                    .padding(16.dp)
                    .then(record("back"))
                    .height(52.dp)
                    .floatingSurface(RoundedCornerShape(Radii.pill))
                    .padding(start = if (full) 5.dp else 4.dp, end = if (full) 15.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(BlotIcons.Back, "Back to library", actions::back)
                if (full) TitleBlock(state, Modifier.widthIn(max = 280.dp))
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .then(record("undo"))
                    .height(54.dp)
                    .floatingSurface(RoundedCornerShape(Radii.pill))
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    BlotIcons.Undo, "Undo", actions::undo, iconSize = 20.dp,
                    tint = if (state.canUndo) c.text else c.muted, enabled = state.canUndo,
                )
                if (full) {
                    IconButton(
                        BlotIcons.Redo, "Redo", actions::redo, iconSize = 20.dp,
                        tint = if (state.canRedo) c.text else c.muted, enabled = state.canRedo,
                    )
                    IconButton(
                        BlotIcons.Share, "Export and share", { actions.setExportOpen(true) }, iconSize = 20.dp,
                        background = if (state.exportOpen) c.hover else Color.Transparent,
                    )
                }
            }
            if (state.pageMenuOpen) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.setPageMenuOpen(false) } })
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).then(record("chips")),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(state.pageLabel, actions::openPageSettings, selected = state.pageMenuOpen)
                Chip("${state.zoomPercent}%", actions::resetZoom)
            }
            if (state.pageMenuOpen) {
                PageMenu(state, actions, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + 36.dp + 8.dp))
            }
            ToolbarLayer(state, actions, maxWidth.value, maxHeight.value, obstacles.values.toList())
            MessageBar(state, actions, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        }
        if (state.exportOpen) ExportDialog(state, actions)
    }
}

/** Title over folder name, as in the back pill and the phone's top bar. */
@Composable
internal fun TitleBlock(state: EditorUiState, modifier: Modifier = Modifier) {
    val c = colors
    val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    Column(modifier) {
        EllipsisText(state.noteTitle, BlotType.bodyStrong.copy(color = c.text, lineHeight = 1.2.em, lineHeightStyle = tight))
        if (state.folderName.isNotEmpty()) {
            EllipsisText(state.folderName, BlotType.caption.copy(color = c.muted, lineHeight = 1.2.em, lineHeightStyle = tight))
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit, selected: Boolean = false) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    Box(
        Modifier
            .height(36.dp)
            .floatingSurface(shape, elevation = null, fill = if (selected) c.hover else c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = BlotType.captionMedium.copy(color = c.muted))
    }
}

/** The page chip's menu: page kind, orientation, and adding or removing pages. */
@Composable
internal fun PageMenu(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val c = colors
    Column(
        modifier
            .width(240.dp)
            .floatingSurface(RoundedCornerShape(Radii.menu), Elevation.Menu)
            .padding(6.dp),
    ) {
        for (kind in listOf(PageKind.Infinite, PageKind.Screen, PageKind.A4, PageKind.Letter)) {
            PageMenuRow(kind.label(), state.pageKind == kind, { actions.setPageKind(kind) }) { PageGlyph(kind, it, small = true) }
        }
        if (state.pageKind != PageKind.Infinite) {
            Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            if (state.pageKind != PageKind.Screen) {
                PageMenuRow("Landscape", state.pageLandscape, { actions.setPageLandscape(!state.pageLandscape) }) {
                    GlyphIcon(BlotIcons.ScreenPage, it, size = 16.dp, strokeWidth = 2f)
                }
            }
            PageMenuRow("Add page", false, actions::addPage) { GlyphIcon(BlotIcons.Plus, it, size = 16.dp, strokeWidth = 2f) }
            if (state.pageCount > 1) {
                PageMenuRow("Remove last page", false, actions::removePage) { GlyphIcon(BlotIcons.Delete, it, size = 16.dp, strokeWidth = 2f) }
            }
        }
    }
}

@Composable
private fun PageMenuRow(label: String, chosen: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (chosen) c.selected else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = chosen }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(c.text) }
        BasicText(label, style = (if (chosen) BlotType.bodyStrong else BlotType.body).copy(color = c.text))
    }
}

/** [EditorUiState.message] as a bar that dismisses itself after [MessageMillis], or when tapped. */
@Composable
internal fun MessageBar(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier) {
    val message = state.message ?: return
    val dismiss by rememberUpdatedState(actions::dismissMessage)
    LaunchedEffect(message) {
        delay(MessageMillis)
        dismiss()
    }
    Notice(message, dismiss, modifier)
}

/** A dark rounded notice with [text]; tapping it calls [onTap]. */
@Composable
fun Notice(text: String, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    Box(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .floatingSurface(RoundedCornerShape(Radii.field), Elevation.Menu, fill = c.active)
            .clickable(role = Role.Button, onClick = onTap)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicText(text, style = BlotType.body.copy(color = c.activeText))
    }
}

package app.screenscratch.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.screenscratch.ui.components.EllipsisText
import app.screenscratch.ui.components.IconButton
import app.screenscratch.ui.components.floatingSurface
import app.screenscratch.ui.icons.ScratchIcons
import app.screenscratch.ui.theme.Radii
import app.screenscratch.ui.theme.ScratchType
import app.screenscratch.ui.theme.colors

/** Window width below which the phone viewer replaces the tablet chrome. */
val PhoneMaxWidth = 600.dp

/**
 * The editor screen: [content] (the canvas) fills the window and the chrome floats over it.
 * Picks the tablet chrome or the phone viewer by window width.
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
 * with its menus and colour panel, the undo/redo/export pill, page and zoom chips, and the
 * export dialog.
 */
@Composable
fun EditorChrome(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        content()
        val full = !state.toolbarCollapsed
        Row(
            Modifier
                .padding(16.dp)
                .height(52.dp)
                .floatingSurface(RoundedCornerShape(Radii.pill))
                .padding(start = if (full) 5.dp else 4.dp, end = if (full) 15.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(ScratchIcons.Back, "Back to library", actions::back)
            if (full) TitleBlock(state)
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .height(54.dp)
                .floatingSurface(RoundedCornerShape(Radii.pill))
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                ScratchIcons.Undo, "Undo", actions::undo, iconSize = 20.dp,
                tint = if (state.canUndo) c.text else c.muted, enabled = state.canUndo,
            )
            if (full) {
                IconButton(
                    ScratchIcons.Redo, "Redo", actions::redo, iconSize = 20.dp,
                    tint = if (state.canRedo) c.text else c.muted, enabled = state.canRedo,
                )
                IconButton(
                    ScratchIcons.Share, "Export and share", { actions.setExportOpen(true) }, iconSize = 20.dp,
                    background = if (state.exportOpen) c.hover else Color.Transparent,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(state.pageLabel, actions::openPageSettings)
            Chip("${state.zoomPercent}%", actions::resetZoom)
        }
        ToolbarLayer(state, actions, maxWidth.value, maxHeight.value)
        if (state.exportOpen) ExportDialog(state, actions)
    }
}

/** Title over folder name, as in the back pill and the phone's top bar. */
@Composable
internal fun TitleBlock(state: EditorUiState, modifier: Modifier = Modifier) {
    val c = colors
    val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    Column(modifier) {
        EllipsisText(state.noteTitle, ScratchType.bodyStrong.copy(color = c.text, lineHeight = 1.2.em, lineHeightStyle = tight))
        if (state.folderName.isNotEmpty()) {
            EllipsisText(state.folderName, ScratchType.caption.copy(color = c.muted, lineHeight = 1.2.em, lineHeightStyle = tight))
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val c = colors
    val shape = RoundedCornerShape(Radii.menuItem)
    Box(
        Modifier
            .height(36.dp)
            .floatingSurface(shape, elevation = null)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = ScratchType.captionMedium.copy(color = c.muted))
    }
}

package app.blot.ui.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.blot.ui.components.IconButton
import app.blot.ui.components.floatingSurface
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.icons.BlotIcons
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors

/**
 * The phone viewer (`Phone.dc.html`): a top bar with back, title, background and export, and a bottom pill
 * with pan, pen, lasso, text and image. Meant for viewing and light edits without a pen.
 */
@Composable
fun PhoneEditorChrome(state: EditorUiState, actions: EditorActions, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val titleEdit = remember { TitleEdit() }
    Box(modifier.fillMaxSize().commitTitleOnTapElsewhere(state.titleEditing, titleEdit)) {
        content()
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { PhoneBars(state, actions, titleEdit) }
        SelectionMenuLayer(state, actions)
        if (state.exportOpen) ExportDialog(state, actions)
    }
}

@Composable
private fun BoxScope.PhoneBars(state: EditorUiState, actions: EditorActions, edit: TitleEdit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(54.dp)
            .floatingSurface(RoundedCornerShape(Radii.pill), elevation = null)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(BlotIcons.Back, "Back to library", actions::back)
        TitleBlock(state, actions, edit, Modifier.weight(1f))
        IconButton(
            BlotIcons.Background, "Background", { actions.setBackgroundPanelOpen(!state.backgroundPanelOpen) }, iconSize = 20.dp,
            background = if (state.backgroundPanelOpen) colors.hover else Color.Transparent, selected = state.backgroundPanelOpen,
        )
        IconButton(BlotIcons.Share, "Export and share", { actions.setExportOpen(true) }, iconSize = 20.dp)
    }
    val pan = state.tool == Tool.Pen && state.penMode == PenMode.Hand
    val pen = state.tool == Tool.Pen && state.penMode == PenMode.Pen
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp)
            .height(58.dp)
            .floatingSurface(RoundedCornerShape(Radii.bar), Elevation.PhonePill)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton("Pan and zoom", pan, false, { actions.setPenMode(PenMode.Hand) }, menuDot = null) { GlyphIcon(BlotIcons.Move, it) }
        ToolButton("Pen", pen, false, { actions.setPenMode(PenMode.Pen) }, menuDot = null) { GlyphIcon(BlotIcons.Pen, it) }
        ToolButton("Pointer: lasso select", state.tool == Tool.Select, false, { actions.setSelectMode(SelectMode.Lasso) }, menuDot = null) {
            GlyphIcon(BlotIcons.LassoPhone, it)
        }
        ToolButton("Text box", state.tool == Tool.Text, false, { actions.selectTool(Tool.Text) }, menuDot = null) {
            GlyphIcon(BlotIcons.Text, it)
        }
        ToolButton("Insert image", state.tool == Tool.Image, false, { actions.selectTool(Tool.Image) }, menuDot = null) {
            GlyphIcon(BlotIcons.Image, it)
        }
    }
    MessageBar(state, actions, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    if (state.backgroundPanelOpen) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.setBackgroundPanelOpen(false) } })
        BackgroundPanel(state, actions, Modifier.align(Alignment.TopEnd).padding(top = 12.dp + 54.dp + 8.dp, end = 12.dp))
    }
}

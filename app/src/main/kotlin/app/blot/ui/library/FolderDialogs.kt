package app.blot.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blot.ui.components.IconButton
import app.blot.ui.components.ModalCard
import app.blot.ui.components.OutlineButton
import app.blot.ui.components.PrimaryButton
import app.blot.ui.icons.BlotIcons
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors

/** Names a new folder or renames one: a focused name field, the reason a name was refused, and Cancel and Create or Rename. */
@Composable
internal fun FolderNameDialog(dialog: FolderDialog, actions: LibraryActions) {
    val c = colors
    val (title, initial, error) = when (dialog) {
        is FolderDialog.Rename -> Triple("Rename folder", dialog.name, dialog.error)
        is FolderDialog.New -> Triple("New folder", dialog.name, dialog.error)
        is FolderDialog.Delete -> return
    }
    val field = rememberTextFieldState(initial, TextRange(0, initial.length))
    val focus = remember { FocusRequester() }
    val close = { actions.setFolderDialog(null) }
    val submit = {
        val name = field.text.toString()
        if (dialog is FolderDialog.Rename) actions.renameFolder(dialog.id, name) else actions.createFolder(name)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalCard(close, 420.dp, title) {
        Column(Modifier.padding(start = 21.dp, end = 21.dp, top = 9.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, Modifier.weight(1f).semantics { heading() }, style = BlotType.title.copy(color = c.text))
                IconButton(BlotIcons.Close, "Close", close, Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted)
            }
            val shape = RoundedCornerShape(Radii.field)
            BasicTextField(
                state = field,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = BlotType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Folder name" },
                decorator = { inner ->
                    Box(
                        Modifier
                            .height(44.dp)
                            .background(c.surface, shape)
                            .border(1.dp, if (error != null) c.danger else c.border, shape)
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (field.text.isEmpty()) BasicText("Folder name", style = BlotType.body.copy(color = c.muted), maxLines = 1)
                        inner()
                    }
                },
            )
            if (error != null) {
                BasicText(
                    error,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = BlotType.caption.copy(color = c.danger),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlineButton("Cancel", close)
                PrimaryButton(if (dialog is FolderDialog.Rename) "Rename" else "Create", submit)
            }
        }
    }
}

/** Asks before deleting a folder, saying that its notes move to the top level and none is deleted. */
@Composable
internal fun DeleteFolderDialog(dialog: FolderDialog.Delete, actions: LibraryActions) {
    val c = colors
    val close = { actions.setFolderDialog(null) }
    val title = "Delete “${dialog.name}”?"
    val body = when (dialog.count) {
        0 -> "The folder is empty. Deleting it removes only the folder."
        1 -> "Its note moves to the top level of your notes folder. No note is deleted."
        else -> "Its ${dialog.count} notes move to the top level of your notes folder. No note is deleted."
    }
    ModalCard(close, 420.dp, title) {
        Column(Modifier.padding(start = 21.dp, end = 21.dp, top = 9.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, Modifier.weight(1f).semantics { heading() }, style = BlotType.title.copy(color = c.text), maxLines = 2)
                IconButton(BlotIcons.Close, "Close", close, Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted)
            }
            BasicText(body, style = BlotType.body.copy(color = c.muted, lineHeight = BlotType.body.fontSize * 1.5f))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlineButton("Cancel", close)
                val shape = RoundedCornerShape(Radii.field)
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(shape)
                        .background(c.danger)
                        .clickable(role = Role.Button) { actions.deleteFolder(dialog.id) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Delete folder", style = BlotType.bodyStrong.copy(color = c.activeText), maxLines = 1)
                }
            }
        }
    }
}

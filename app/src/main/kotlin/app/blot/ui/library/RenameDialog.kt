package app.blot.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blot.core.model.NoteTitle
import app.blot.ui.components.IconButton
import app.blot.ui.components.ModalCard
import app.blot.ui.components.OutlineButton
import app.blot.ui.components.PrimaryButton
import app.blot.ui.icons.BlotIcons
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors

/** Renames [note]: a title field, focused with its text selected, and Cancel and Rename. */
@Composable
internal fun RenameDialog(note: NoteCard, actions: LibraryActions) {
    val c = colors
    val initial = if (note.untitled) "" else note.title
    val field = rememberTextFieldState(initial, TextRange(0, initial.length))
    val focus = remember { FocusRequester() }
    val close = { actions.setRenaming(null) }
    val rename = { actions.renameNote(note.id, field.text.toString()) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalCard(close, 420.dp, "Rename note") {
        Column(Modifier.padding(start = 21.dp, end = 21.dp, top = 9.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Rename note", Modifier.weight(1f).semantics { heading() }, style = BlotType.title.copy(color = c.text))
                IconButton(BlotIcons.Close, "Close", close, Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted)
            }
            val shape = RoundedCornerShape(Radii.field)
            BasicTextField(
                state = field,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = BlotType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.text),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onKeyboardAction = { rename() },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Note title" },
                decorator = { inner ->
                    Box(
                        Modifier
                            .height(44.dp)
                            .background(c.surface, shape)
                            .border(1.dp, c.border, shape)
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (field.text.isEmpty()) BasicText(NoteTitle.UNTITLED, style = BlotType.body.copy(color = c.muted), maxLines = 1)
                        inner()
                    }
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlineButton("Cancel", close)
                PrimaryButton("Rename", rename)
            }
        }
    }
}

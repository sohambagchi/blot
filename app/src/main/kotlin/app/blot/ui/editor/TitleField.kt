package app.blot.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blot.core.model.NoteTitle
import app.blot.ui.theme.colors

/** Links the title field to taps elsewhere in the editor, which commit it. One per chrome. */
internal class TitleEdit {
    /** The current gesture began on the field. */
    var onField = false

    /** Commits the open field; set while one is shown. */
    var commit: () -> Unit = {}
}

/**
 * While [editing], a pointer going down anywhere outside the title field commits the title. It
 * watches the final pass and consumes nothing, so the tap or stroke still does what it does: a
 * pen writing on the canvas right away leaves a new note untitled.
 */
internal fun Modifier.commitTitleOnTapElsewhere(editing: Boolean, edit: TitleEdit): Modifier =
    if (!editing) this
    else pointerInput(edit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!edit.onField) edit.commit()
            edit.onField = false
        }
    }

/**
 * The note title as a single-line field, focused and with its text selected. Done, Enter or a
 * tap elsewhere commits it through [EditorActions.setTitle]; Back or Escape cancels.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TitleField(title: String, actions: EditorActions, edit: TitleEdit, style: TextStyle, modifier: Modifier = Modifier) {
    val c = colors
    val field = rememberTextFieldState(title, TextRange(0, title.length))
    val focus = remember { FocusRequester() }
    val flags = remember { FieldFlags() }
    val commit = {
        if (!flags.finished) {
            flags.finished = true
            actions.setTitle(field.text.toString())
        }
    }
    val cancel = {
        if (!flags.finished) {
            flags.finished = true
            actions.setTitleEditing(false)
        }
    }
    edit.commit = commit
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        state = field,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = style,
        cursorBrush = SolidColor(c.text),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { commit() },
        modifier = modifier
            .pointerInput(edit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    edit.onField = true
                }
            }
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused) flags.focused = true else if (flags.focused) commit()
            }
            // Back reaches the field before the keyboard closes; it cancels rather than just hiding it.
            .onInterceptKeyBeforeSoftKeyboard { e ->
                if (e.key != Key.Back) return@onInterceptKeyBeforeSoftKeyboard false
                if (e.type == KeyEventType.KeyUp) cancel()
                true
            }
            .onPreviewKeyEvent { e ->
                if (e.key != Key.Escape) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) cancel()
                true
            }
            .semantics { contentDescription = "Note title" },
        decorator = { inner ->
            Box(
                Modifier.background(c.hover, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (field.text.isEmpty()) BasicText(NoteTitle.UNTITLED, style = style.copy(color = c.muted), maxLines = 1)
                inner()
            }
        },
    )
}

/** A field ends once, by commit or cancel; losing focus commits only after it had focus. */
private class FieldFlags {
    var finished = false
    var focused = false
}

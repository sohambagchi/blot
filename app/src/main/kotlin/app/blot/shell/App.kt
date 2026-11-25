package app.blot.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import app.blot.canvas.NoteCanvas
import app.blot.core.model.PageSetup
import app.blot.ui.editor.Editor
import app.blot.ui.library.Library
import app.blot.ui.theme.BlotTheme
import app.blot.ui.theme.ThemeSetting
import app.blot.ui.theme.colors
import app.blot.canvas.Tool as CanvasTool

/** The whole app: the library, or the editor over the canvas, in the chosen theme. [onDark] hears the resolved theme. */
@Composable
fun BlotApp(app: AppModel, onDark: (Boolean) -> Unit = {}) {
    val dark = when (app.themeSetting) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    LaunchedEffect(dark) {
        app.applyDark(dark)
        onDark(dark)
    }
    BlotTheme(dark) {
        when (val s = app.screen) {
            Screen.Library -> LibraryScreen(app)
            is Screen.Editor -> EditorScreen(s.session)
        }
    }
}

@Composable
private fun LibraryScreen(app: AppModel) {
    val state = app.library
    BackHandler(enabled = state.settingsOpen || state.cardMenu != null || state.newNoteOpen) {
        when {
            state.settingsOpen -> app.setSettingsOpen(false)
            state.cardMenu != null -> app.setCardMenu(null)
            else -> app.setNewNoteOpen(false)
        }
    }
    Box(Modifier.fillMaxSize().background(colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Library(state, app)
    }
}

@Composable
private fun EditorScreen(session: EditorSession) {
    val editor = session.editor
    val state = editor.uiState
    BackHandler {
        when {
            state.exportOpen -> editor.setExportOpen(false)
            state.pageMenuOpen -> editor.setPageMenuOpen(false)
            state.openMenu != null -> editor.openMenu(null)
            state.colourPanelOpen -> editor.setColourPanelOpen(false)
            else -> editor.back()
        }
    }
    LaunchedEffect(session) {
        // CanvasController's first fit puts an infinite canvas at the origin; an existing note's
        // ink may be elsewhere. Two frames: the canvas view has its size by then.
        withFrameNanos {}
        withFrameNanos {}
        val note = session.controller.note.value
        if (note.page == PageSetup.Infinite && note.elements.isNotEmpty()) session.controller.fit()
    }
    Editor(state, editor) {
        NoteCanvas(
            session.controller,
            Modifier.fillMaxSize().onSizeChanged { editor.canvasSizePx = it }.reportWriting(session),
            session.textRenderer,
            session.images,
        )
    }
}

/**
 * Tells the chrome about writing (pen down and every move while the pen tool draws) so the
 * toolbar can auto-collapse. Watches the initial pass and consumes nothing, so the canvas view
 * still gets every event.
 */
private fun Modifier.reportWriting(session: EditorSession) = pointerInput(session) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = session.controller
            if (c.tool != CanvasTool.PEN) continue
            val writing = e.changes.firstOrNull { it.pressed && (it.type == PointerType.Stylus || !c.penOnly) } ?: continue
            session.editor.onWritingActivity(writing.uptimeMillis)
        }
    }
}

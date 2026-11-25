package app.blot.ui.preview

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.blot.core.model.Argb
import app.blot.ui.editor.Editor
import app.blot.ui.editor.EditorUiState
import app.blot.ui.editor.InMemoryEditor
import app.blot.ui.editor.PenMode
import app.blot.ui.editor.Tool
import app.blot.ui.library.FakeLibrary
import app.blot.ui.library.Library
import app.blot.ui.theme.BlotTheme
import app.blot.ui.theme.ThemeSetting
import app.blot.ui.theme.colors
import app.blot.ui.theme.inkForDisplay

/**
 * Shows the library and editor chrome with fake data, before they are wired to the note store
 * and the canvas. The "canvas" is a dot grid that draws plain strokes and reports writing, so
 * auto-collapse can be tried with the pen.
 */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UiPreview() }
    }
}

@Composable
private fun UiPreview() {
    var openNote by remember { mutableStateOf<String?>(null) }
    val library = remember { FakeLibrary(onOpen = { openNote = it.title }) }
    val dark = when (library.state.settings.theme) {
        ThemeSetting.System -> isSystemInDarkTheme()
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
    }
    LaunchedEffect(dark) { library.dark = dark }
    BlotTheme(dark) {
        Box(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) { PreviewScreens(library, openNote) { openNote = it } }
    }
}

@Composable
private fun PreviewScreens(library: FakeLibrary, openNote: String?, setOpen: (String?) -> Unit) {
    val dark = colors.dark
    if (openNote == null) {
        Library(library.state, library)
        return
    }
    val editor = remember(openNote) {
        InMemoryEditor(
            EditorUiState(noteTitle = openNote, folderName = library.state.heading, pageLabel = "Infinite canvas"),
            onBack = { setOpen(null) },
        )
    }
    BackHandler { setOpen(null) }
    Editor(editor.state, editor) { ScribbleCanvas(editor, dark) }
}

/** A dot grid that keeps plain pen strokes in memory; enough to try the chrome. */
@Composable
private fun ScribbleCanvas(editor: InMemoryEditor, dark: Boolean) {
    val strokes = remember { mutableStateListOf<Triple<Path, Argb, Float>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val state = editor.state
    Box(Modifier.fillMaxSize()) {
        DotGrid()
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val s = editor.state
                    val draws = s.tool == Tool.Pen && s.penMode == PenMode.Pen &&
                        (!s.penOnly || down.type == PointerType.Stylus)
                    if (!draws) return@awaitEachGesture
                    editor.onWritingActivity(SystemClock.uptimeMillis())
                    val points = mutableListOf(down.position)
                    live = points.toList()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        points += change.position
                        live = points.toList()
                        change.consume()
                        editor.onWritingActivity(SystemClock.uptimeMillis())
                    }
                    val path = Path()
                    points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    strokes += Triple(path, editor.state.color, editor.state.strokeWidth)
                    live = emptyList()
                }
            },
        ) {
            for ((path, ink, width) in strokes) {
                drawPath(path, inkForDisplay(ink, dark), style = Stroke(width.dp.toPx() * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (live.size > 1) {
                val path = Path()
                live.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, inkForDisplay(state.color, dark), style = Stroke(state.strokeWidth.dp.toPx() * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

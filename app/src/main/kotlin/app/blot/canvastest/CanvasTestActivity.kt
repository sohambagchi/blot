package app.blot.canvastest

import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blot.canvas.CanvasController
import app.blot.canvas.EraserMode
import app.blot.canvas.FrontBufferContent
import app.blot.canvas.InkCanvasView
import app.blot.canvas.LineArrows
import app.blot.canvas.NoteCanvas
import app.blot.canvas.SelectMode
import app.blot.canvas.Tool
import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.ShapeKind
import app.blot.core.model.format.NoteJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Debug screen for trying the inking canvas on a device: a full-screen canvas, a plain toolbar,
 * and the latest pen-down latency and frame times. The note is kept in app-private storage so it
 * survives restarts, until the real storage layer arrives.
 */
class CanvasTestActivity : ComponentActivity() {
    lateinit var controller: CanvasController
        private set

    private var persist = true
    private val noteFile by lazy { File(filesDir, "canvas-test/${NoteJson.FILE_NAME}") }
    private var frameMs by mutableFloatStateOf(Float.NaN)

    private val frameMetrics = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6f
        frameMs = if (frameMs.isNaN()) ms else frameMs * 0.9f + ms * 0.1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        persist = !intent.getBooleanExtra(EXTRA_FRESH, false)
        controller = CanvasController(if (persist) loadNote() else CanvasController.newNote())
        controller.penOnly = hasStylus()
        controller.darkMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setContent { Screen() }
    }

    override fun onResume() {
        super.onResume()
        window.addOnFrameMetricsAvailableListener(frameMetrics, Handler(Looper.getMainLooper()))
    }

    override fun onPause() {
        window.removeOnFrameMetricsAvailableListener(frameMetrics)
        super.onPause()
    }

    override fun onStop() {
        if (persist) save(controller.note.value)
        super.onStop()
    }

    /** The canvas view, for tests. */
    val canvasView: InkCanvasView?
        get() = find(window.decorView)

    private fun find(v: View): InkCanvasView? = when (v) {
        is InkCanvasView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }

    private fun hasStylus(): Boolean {
        val im = getSystemService(InputManager::class.java)
        return im.inputDeviceIds.any { id ->
            im.getInputDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true
        }
    }

    private fun loadNote(): Note = try {
        if (noteFile.exists()) NoteJson.decode(noteFile.readText()) else CanvasController.newNote()
    } catch (e: Exception) {
        Log.w(TAG, "couldn't read $noteFile; starting a new note", e)
        CanvasController.newNote()
    }

    @Synchronized
    private fun save(note: Note) {
        try {
            noteFile.parentFile?.mkdirs()
            val tmp = File(noteFile.path + ".tmp")
            tmp.writeText(NoteJson.encode(note))
            if (!tmp.renameTo(noteFile)) Log.w(TAG, "couldn't replace $noteFile")
        } catch (e: Exception) {
            Log.w(TAG, "couldn't save $noteFile", e)
        }
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun Screen() {
        val c = controller
        val dark = c.darkMode
        val panel = if (dark) Color(0xFF1A1A1C) else Color(0xFFF2F2F4)
        val ink = if (dark) Color(0xFFE8E8E8) else Color(0xFF111111)
        val muted = if (dark) Color(0xFF9E9E9E) else Color(0xFF666666)
        val text = TextStyle(color = ink, fontSize = 14.sp)
        if (persist) {
            LaunchedEffect(c) {
                c.note.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { withContext(Dispatchers.IO) { save(it) } }
            }
        }
        Column(Modifier.fillMaxSize().background(panel).safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Canvas test", style = text.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                for (argb in PEN_COLORS) {
                    val selected = c.color.value == argb
                    Box(
                        Modifier.size(22.dp)
                            .border(if (selected) 3.dp else 1.dp, if (selected) ink else muted)
                            .padding(4.dp)
                            .background(Color(argb))
                            .clickable { c.color = Argb(argb); if (c.tool == Tool.ERASER || c.tool == Tool.PAN) c.tool = Tool.PEN },
                    )
                }
                for (t in Tool.entries) {
                    Action(t.label, text, selected = c.tool == t) { c.tool = t }
                }
                when (c.tool) {
                    Tool.ERASER -> Action(if (c.eraserMode == EraserMode.PARTIAL) "Erase: part" else "Erase: whole", text) {
                        c.eraserMode = if (c.eraserMode == EraserMode.PARTIAL) EraserMode.WHOLE_STROKE else EraserMode.PARTIAL
                    }
                    Tool.SELECT -> {
                        Action(if (c.selectMode == SelectMode.LASSO) "Lasso" else "Rectangle", text) {
                            c.selectMode = if (c.selectMode == SelectMode.LASSO) SelectMode.RECTANGLE else SelectMode.LASSO
                        }
                        if (c.hasSelection) {
                            Action("Delete", text) { c.deleteSelection() }
                            Action("Duplicate", text) { c.duplicateSelection() }
                            Action("Front", text) { c.bringToFront() }
                            Action("Forward", text) { c.bringForward() }
                            Action("Backward", text) { c.sendBackward() }
                            Action("Back", text) { c.sendToBack() }
                        }
                    }
                    Tool.SHAPE -> {
                        Action(if (c.shapeKind == ShapeKind.RECTANGLE) "Rectangle" else "Ellipse", text) {
                            c.shapeKind = if (c.shapeKind == ShapeKind.RECTANGLE) ShapeKind.ELLIPSE else ShapeKind.RECTANGLE
                        }
                        Action("Fill: ${onOff(c.shapeFill != null)}", text) { c.shapeFill = if (c.shapeFill == null) Argb(FILL) else null }
                        Action("Square: ${onOff(c.constrainShapes)}", text) { c.constrainShapes = !c.constrainShapes }
                    }
                    Tool.LINE -> {
                        Action("Arrows: ${c.lineArrows.name.lowercase()}", text) {
                            c.lineArrows = LineArrows.entries[(c.lineArrows.ordinal + 1) % LineArrows.entries.size]
                        }
                        Action("Snap 15°: ${onOff(c.snapAngles)}", text) { c.snapAngles = !c.snapAngles }
                    }
                    else -> Unit
                }
                Action("Undo", text.copy(color = if (c.canUndo) ink else muted)) { c.undo() }
                Action("Redo", text.copy(color = if (c.canRedo) ink else muted)) { c.redo() }
                Action("Pen only: ${onOff(c.penOnly)}", text) { c.penOnly = !c.penOnly }
                Action("Clear", text) { c.clear() }
                val pages = c.note.value.page is PageSetup.Pages
                Action("Page: ${if (pages) "A4" else "infinite"}", text) {
                    c.setPage(if (pages) PageSetup.Infinite else PageSetup.Pages.a4(count = A4_PAGES))
                }
                Action("Fit", text) { c.fit() }
                Action("Dark: ${onOff(c.darkMode)}", text) { c.darkMode = !c.darkMode }
                Action("Buffer: ${if (c.frontBufferContent == FrontBufferContent.SCENE) "scene" else "ink only"}", text) {
                    c.frontBufferContent = if (c.frontBufferContent == FrontBufferContent.SCENE) {
                        FrontBufferContent.INK_ONLY
                    } else {
                        FrontBufferContent.SCENE
                    }
                }
            }
            Stats(text.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted))
            NoteCanvas(c, Modifier.weight(1f).fillMaxWidth())
        }
    }

    @Composable
    private fun Stats(style: TextStyle) {
        val s = controller.stats
        val v = controller.viewport
        BasicText(
            buildString {
                append("pen-down→ink ${ms(s.penDownToInkMs)} (dispatch ${ms(s.penDownDispatchMs)})")
                append(" · →display ${ms(s.penDownToPresentMs)}")
                append(" · scene in buffer ${ms(s.sceneRenderMs)}${if (s.afterPan) " (after pan)" else ""}")
                append(" · view draw ${ms(s.sceneDrawMs)} (${s.drawnElements} el)")
                append(" · frame ${ms(frameMs)}")
                append(" · zoom ${"%.0f".format(Locale.ROOT, v.zoom * 100)}%")
                append(" · ${if (s.frontBuffered) "front buffer" else "view ink"}")
            },
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = style,
        )
    }

    @Composable
    private fun Action(label: String, style: TextStyle, selected: Boolean = false, onClick: () -> Unit) {
        BasicText(
            label,
            Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
            style = if (selected) style.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline) else style,
        )
    }

    private fun onOff(b: Boolean) = if (b) "on" else "off"
    private fun ms(v: Float) = if (v.isNaN()) "–" else String.format(Locale.ROOT, "%.1f ms", v)

    private val Tool.label: String
        get() = when (this) {
            Tool.PEN -> "Pen"
            Tool.ERASER -> "Eraser"
            Tool.PAN -> "Pan"
            Tool.SELECT -> "Select"
            Tool.SHAPE -> "Shape"
            Tool.LINE -> "Line"
            Tool.TEXT -> "Text"
        }

    companion object {
        /** Boolean extra: start from an empty note and don't save it. For tests. */
        const val EXTRA_FRESH = "fresh"
        private const val TAG = "CanvasTest"
        private const val SAVE_DEBOUNCE_MS = 500L
        private const val A4_PAGES = 3
        private const val FILL = 0xFFFFF59D.toInt()
        /** Black, red, blue, green. */
        private val PEN_COLORS = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E5BD8.toInt(), 0xFF2E7D32.toInt())
    }
}

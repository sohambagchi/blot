package app.screenscratch.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Measurements from the canvas view, as Compose state. Times are milliseconds; NaN means not
 * measured yet. Written on the UI thread.
 */
class CanvasStats {
    /** Pen-down event time to the end of the first front-buffer draw of that stroke. */
    var penDownToInkMs by mutableFloatStateOf(Float.NaN)
        internal set

    /** Pen-down event time to the front buffer's first frame being handed to the display. */
    var penDownToPresentMs by mutableFloatStateOf(Float.NaN)
        internal set

    /** Time the first front-buffer draw spent re-rendering the note, or NaN if it didn't. */
    var sceneRenderMs by mutableFloatStateOf(Float.NaN)
        internal set

    /** Whether the viewport changed between the previous stroke and the latest one. */
    var afterPan by mutableStateOf(false)
        internal set

    /** Duration of the canvas view's latest `onDraw`. */
    var sceneDrawMs by mutableFloatStateOf(Float.NaN)
        internal set

    /** Elements the latest `onDraw` drew. */
    var drawnElements by mutableIntStateOf(0)
        internal set

    /** Whether ink goes through `LowLatencyCanvasView` (false: drawn by the canvas view itself). */
    var frontBuffered by mutableStateOf(false)
        internal set

    var strokes by mutableIntStateOf(0)
        internal set
}

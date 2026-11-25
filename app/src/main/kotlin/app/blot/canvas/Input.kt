package app.blot.canvas

/** What a pen drag does. */
enum class Tool {
    PEN,

    /** Erases as [CanvasController.eraserMode] says. */
    ERASER,
    PAN,

    /**
     * The pointer: lasso or rectangle selection, by [CanvasController.selectMode]. Drags on a
     * selection move, scale or rotate it; only this tool changes a selection.
     */
    SELECT,

    /** Draws a [CanvasController.shapeKind] by dragging from corner to corner. */
    SHAPE,

    /** Draws a straight line by dragging, with [CanvasController.lineArrows]. */
    LINE,

    /** A tap creates a text box, or edits the one under it. */
    TEXT,
}

/** What the eraser takes. */
enum class EraserMode {
    /** Whole strokes it touches. */
    WHOLE_STROKE,

    /** Only the parts of strokes it passes over; the rest are split into new strokes. */
    PARTIAL,
}

/** How [Tool.SELECT] picks elements when dragged off a selection. */
enum class SelectMode { LASSO, RECTANGLE }

/** Which ends of a line [Tool.LINE] draws with an arrowhead. */
enum class LineArrows { NONE, END, BOTH }

/** The kind of pointer, from `MotionEvent.getToolType`. */
enum class PointerKind { STYLUS, ERASER, FINGER, MOUSE, UNKNOWN }

/** Stylus side-button bits of [InputEvent.buttons]. */
object StylusButtons {
    /** `MotionEvent.BUTTON_STYLUS_PRIMARY`. */
    const val PRIMARY = 1

    /** `MotionEvent.BUTTON_STYLUS_SECONDARY`. */
    const val SECONDARY = 2
}

/**
 * One position of a pointer, in view pixels.
 *
 * @property pressure 0–1.
 * @property tilt radians from perpendicular, 0–π/2.
 * @property orientation radians, −π–π, as Android's `AXIS_ORIENTATION`.
 * @property timeMs `SystemClock.uptimeMillis` time base.
 */
data class InputSample(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val timeMs: Long = 0L,
)

/** A pointer in an [InputEvent], with its batched samples oldest first; the last is current. */
class InputPointer(val id: Int, val kind: PointerKind, val samples: List<InputSample>) {
    init {
        require(samples.isNotEmpty()) { "pointer $id has no samples" }
    }

    val current: InputSample get() = samples.last()
}

/** The input actions the router distinguishes; `MotionEvent` actions map onto them. */
enum class InputAction { DOWN, POINTER_DOWN, MOVE, POINTER_UP, UP, CANCEL, HOVER, BUTTON }

/**
 * A platform-free copy of a `MotionEvent`, so input routing can be unit-tested.
 *
 * @property actionPointerId the pointer that went down or up, for those actions.
 * @property buttons [StylusButtons] bits held during the event.
 */
class InputEvent(
    val action: InputAction,
    val pointers: List<InputPointer>,
    val actionPointerId: Int = pointers.firstOrNull()?.id ?: -1,
    val buttons: Int = 0,
) {
    fun pointer(id: Int): InputPointer? = pointers.firstOrNull { it.id == id }
}

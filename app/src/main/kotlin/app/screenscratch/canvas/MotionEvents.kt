package app.screenscratch.canvas

import android.view.MotionEvent

/** [MotionEvent]'s tool type as a [PointerKind]. */
fun pointerKind(toolType: Int): PointerKind = when (toolType) {
    MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
    MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER
    MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
    MotionEvent.TOOL_TYPE_MOUSE -> PointerKind.MOUSE
    else -> PointerKind.UNKNOWN
}

/** The stylus side buttons held in [MotionEvent.getButtonState], as [StylusButtons] bits. */
fun stylusButtons(buttonState: Int): Int =
    (if (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) StylusButtons.PRIMARY else 0) or
        (if (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) StylusButtons.SECONDARY else 0)

/** A copy of this event for [InputRouter], with every pointer's historical samples; null for actions it ignores. */
fun MotionEvent.toInputEvent(): InputEvent? {
    val action = when (actionMasked) {
        MotionEvent.ACTION_DOWN -> InputAction.DOWN
        MotionEvent.ACTION_POINTER_DOWN -> InputAction.POINTER_DOWN
        MotionEvent.ACTION_MOVE -> InputAction.MOVE
        MotionEvent.ACTION_POINTER_UP -> InputAction.POINTER_UP
        MotionEvent.ACTION_UP -> InputAction.UP
        MotionEvent.ACTION_CANCEL -> InputAction.CANCEL
        MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> InputAction.HOVER
        MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> InputAction.BUTTON
        else -> return null
    }
    val pointers = List(pointerCount) { p ->
        val samples = ArrayList<InputSample>(historySize + 1)
        for (h in 0 until historySize) {
            samples += InputSample(
                x = getHistoricalX(p, h),
                y = getHistoricalY(p, h),
                pressure = getHistoricalPressure(p, h),
                tilt = getHistoricalAxisValue(MotionEvent.AXIS_TILT, p, h),
                orientation = getHistoricalOrientation(p, h),
                timeMs = getHistoricalEventTime(h),
            )
        }
        samples += InputSample(
            x = getX(p),
            y = getY(p),
            pressure = getPressure(p),
            tilt = getAxisValue(MotionEvent.AXIS_TILT, p),
            orientation = getOrientation(p),
            timeMs = eventTime,
        )
        InputPointer(getPointerId(p), pointerKind(getToolType(p)), samples)
    }
    return InputEvent(action, pointers, getPointerId(actionIndex), stylusButtons(buttonState))
}

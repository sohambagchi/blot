package app.blot.canvas

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

/** The selection menu's long press through [InkCanvasView]'s touch handling, on the main looper's clock. */
@RunWith(RobolectricTestRunner::class)
class LongPressViewTest {
    private val box = Shape("r", ShapeKind.RECTANGLE, Rect(100f, 100f, 200f, 100f), Argb.BLACK, 2f)
    private val controller = CanvasController(Note("n", created = Instant.EPOCH, modified = Instant.EPOCH, elements = listOf(box)))
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val view = InkCanvasView(activity, controller, useFrontBuffer = false).also {
        activity.setContentView(it)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private var downTime = 0L

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun touch(action: Int, x: Float, y: Float, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.5f })
        val e = MotionEvent.obtain(downTime, now, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    /** View pixels of canvas point ([x], [y]). */
    private fun at(x: Float, y: Float) = controller.viewport.toScreenX(x) to controller.viewport.toScreenY(y)

    private fun selectBox() {
        controller.tool = Tool.SELECT
        controller.select(setOf("r"))
    }

    @Test
    fun holdingStillForTheTimeoutOpensTheMenuAndMovesNothing() {
        selectBox()
        val (x, y) = at(200f, 150f)
        touch(MotionEvent.ACTION_DOWN, x, y)
        advance(LongPress.TIMEOUT_MS - 50)
        assertFalse(controller.selectionMenuOpen)
        advance(60)
        assertTrue(controller.selectionMenuOpen)
        // The rest of the press is swallowed: no move happens.
        touch(MotionEvent.ACTION_MOVE, x + 80f, y)
        touch(MotionEvent.ACTION_UP, x + 80f, y)
        assertEquals(box, controller.note.value.elements.single())
        assertTrue(controller.selectionMenuOpen)
        // The next touch on the canvas only closes it.
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(MotionEvent.ACTION_UP, 10f, 10f)
        assertFalse(controller.selectionMenuOpen)
        assertEquals(setOf("r"), controller.selection)
    }

    @Test
    fun aDragBeforeTheTimeoutMovesTheSelectionInstead() {
        selectBox()
        val (x, y) = at(200f, 150f)
        touch(MotionEvent.ACTION_DOWN, x, y)
        advance(100)
        touch(MotionEvent.ACTION_MOVE, x + 60f, y)
        advance(600)
        assertFalse(controller.selectionMenuOpen)
        touch(MotionEvent.ACTION_UP, x + 60f, y)
        val moved = controller.note.value.elements.single() as Shape
        assertEquals(box.frame.x + 60f / controller.viewport.scale, moved.frame.x, 0.01f)
    }

    @Test
    fun aFingerHoldOpensItWithPenOnlyToo() {
        selectBox()
        controller.penOnly = true
        val (x, y) = at(200f, 150f)
        touch(MotionEvent.ACTION_DOWN, x, y, MotionEvent.TOOL_TYPE_FINGER)
        advance(LongPress.TIMEOUT_MS + 10)
        assertTrue(controller.selectionMenuOpen)
        touch(MotionEvent.ACTION_UP, x, y, MotionEvent.TOOL_TYPE_FINGER)
    }

    @Test
    fun outsideTheSelectionOrWithAnotherToolNothingOpens() {
        selectBox()
        val (x, y) = at(600f, 600f)
        touch(MotionEvent.ACTION_DOWN, x, y)
        advance(1_000)
        assertFalse(controller.selectionMenuOpen)
        touch(MotionEvent.ACTION_UP, x, y)

        controller.tool = Tool.PEN
        val (bx, by) = at(200f, 150f)
        touch(MotionEvent.ACTION_DOWN, bx, by)
        advance(1_000)
        assertFalse(controller.selectionMenuOpen)
        touch(MotionEvent.ACTION_UP, bx, by)
    }
}

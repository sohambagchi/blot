package app.blot.canvas

import app.blot.canvas.InputAction.DOWN
import app.blot.canvas.InputAction.HOVER
import app.blot.canvas.InputAction.MOVE
import app.blot.canvas.InputAction.POINTER_DOWN
import app.blot.canvas.InputAction.POINTER_UP
import app.blot.canvas.InputAction.UP
import app.blot.canvas.PointerKind.FINGER
import app.blot.canvas.PointerKind.STYLUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pen-input rules for every tool, with the side buttons swapped or not and "Pen only" on or
 * off: the pointer button switches to the pointer for good, the erase button erases while held,
 * and fingers pan and zoom with "Pen only", or use the tool without it.
 */
class PenButtonsMatrixTest {
    private class Recorder(override var tool: Tool, override val penOnly: Boolean, override val swapButtons: Boolean) : InputTarget {
        val calls = ArrayList<String>()
        override fun switchToPointer() {
            tool = Tool.SELECT
            calls += "pointer"
        }
        override fun inkStart(mode: InkMode, pointer: PointerKind, sample: InputSample) { calls += "ink:$mode" }
        override fun inkMove(samples: List<InputSample>) { calls += "inkMove" }
        override fun inkEnd() { calls += "inkEnd" }
        override fun inkCancel() { calls += "inkCancel" }
        override fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float) {
            calls += if (factor != 1f) "zoom" else "pan"
        }
        override fun transformEnd() { calls += "transformEnd" }
        override fun editStart(sample: InputSample) { calls += "edit" }
        override fun editMove(samples: List<InputSample>) { calls += "editMove" }
        override fun editEnd() { calls += "editEnd" }
        override fun editCancel() { calls += "editCancel" }
    }

    private class Case(val tool: Tool, val penOnly: Boolean, val swap: Boolean) {
        val t = Recorder(tool, penOnly, swap)
        val router = InputRouter(t)
        val pointerButton = if (swap) StylusButtons.PRIMARY else StylusButtons.SECONDARY
        val eraseButton = if (swap) StylusButtons.SECONDARY else StylusButtons.PRIMARY

        fun ev(action: InputAction, vararg pointers: InputPointer, actionId: Int = pointers.first().id, buttons: Int = 0) =
            router.onEvent(InputEvent(action, pointers.toList(), actionId, buttons))

        fun p(id: Int, kind: PointerKind, x: Float, y: Float = 0f) = InputPointer(id, kind, listOf(InputSample(x, y)))

        fun drag(kind: PointerKind, buttons: Int = 0) {
            ev(DOWN, p(0, kind, 10f), buttons = buttons)
            ev(MOVE, p(0, kind, 40f), buttons = buttons)
            ev(UP, p(0, kind, 40f), buttons = buttons)
        }

        override fun toString() = "tool=$tool penOnly=$penOnly swap=$swap"
    }

    private fun cases() = Tool.entries.flatMap { tool ->
        listOf(false, true).flatMap { penOnly -> listOf(false, true).map { swap -> Case(tool, penOnly, swap) } }
    }

    /** The first thing a single-pointer gesture does with [tool] and no buttons. */
    private fun roleOf(tool: Tool) = when (tool) {
        Tool.PEN -> "ink:DRAW"
        Tool.ERASER -> "ink:ERASE"
        Tool.PAN -> "pan"
        Tool.SELECT, Tool.SHAPE, Tool.LINE, Tool.TEXT -> "edit"
    }

    @Test
    fun aPlainStylusUsesTheTool() {
        for (c in cases()) {
            c.drag(STYLUS)
            assertEquals("$c", roleOf(c.tool), c.t.calls.first())
            assertEquals("$c", c.tool, c.t.tool)
        }
    }

    @Test
    fun thePointerButtonWhileHoveringSwitchesToThePointerAndItStaysAfterRelease() {
        for (c in cases()) {
            c.ev(HOVER, c.p(0, STYLUS, 5f), buttons = c.pointerButton)
            c.ev(HOVER, c.p(0, STYLUS, 5f))
            assertEquals("$c", listOf("pointer"), c.t.calls)
            assertEquals("$c", Tool.SELECT, c.t.tool)
            c.t.calls.clear()
            c.drag(STYLUS)
            assertEquals("$c", "edit", c.t.calls.first())
            assertEquals("$c", Tool.SELECT, c.t.tool)
        }
    }

    @Test
    fun thePointerButtonWhileTouchingSwitchesForTheNextGesture() {
        for (c in cases()) {
            c.ev(DOWN, c.p(0, STYLUS, 10f))
            c.ev(MOVE, c.p(0, STYLUS, 20f), buttons = c.pointerButton)
            c.ev(MOVE, c.p(0, STYLUS, 30f))
            c.ev(UP, c.p(0, STYLUS, 30f))
            assertEquals("$c", roleOf(c.tool), c.t.calls.first { it != "pointer" })
            assertTrue("$c", "pointer" in c.t.calls)
            // The running gesture was not ended by the press.
            assertEquals("$c ${c.t.calls}", 1, c.t.calls.count { it == "inkEnd" || it == "editEnd" || it == "transformEnd" })
            assertEquals("$c", Tool.SELECT, c.t.tool)
        }
    }

    @Test
    fun theEraseButtonErasesWhileHeldAndTheToolComesBack() {
        for (c in cases()) {
            // Held from hover through the stroke.
            c.ev(HOVER, c.p(0, STYLUS, 5f), buttons = c.eraseButton)
            c.drag(STYLUS, buttons = c.eraseButton)
            assertEquals("$c", listOf("ink:ERASE", "inkMove", "inkEnd"), c.t.calls)
            c.ev(HOVER, c.p(0, STYLUS, 5f))
            c.t.calls.clear()
            // Released mid-stroke: the tool takes over for the rest of it.
            c.ev(DOWN, c.p(0, STYLUS, 10f), buttons = c.eraseButton)
            c.ev(MOVE, c.p(0, STYLUS, 20f))
            assertEquals("$c", "ink:ERASE", c.t.calls.first())
            c.ev(UP, c.p(0, STYLUS, 25f))
            if (c.tool != Tool.ERASER) assertEquals("$c ${c.t.calls}", roleOf(c.tool), c.t.calls[c.t.calls.indexOf("inkEnd") + 1])
            assertEquals("$c", c.tool, c.t.tool)
        }
    }

    @Test
    fun oneFingerPansWithPenOnlyAndUsesTheToolWithout() {
        for (c in cases()) {
            c.drag(FINGER)
            val expected = if (c.penOnly) "pan" else roleOf(c.tool)
            assertEquals("$c", expected, c.t.calls.first())
        }
    }

    @Test
    fun twoFingersAlwaysPinch() {
        for (c in cases()) {
            c.ev(DOWN, c.p(0, FINGER, 100f))
            c.ev(POINTER_DOWN, c.p(0, FINGER, 100f), c.p(1, FINGER, 200f), actionId = 1)
            c.ev(MOVE, c.p(0, FINGER, 50f), c.p(1, FINGER, 250f))
            c.ev(POINTER_UP, c.p(0, FINGER, 50f), c.p(1, FINGER, 250f), actionId = 1)
            c.ev(UP, c.p(0, FINGER, 50f))
            assertTrue("$c ${c.t.calls}", "zoom" in c.t.calls)
            // Fingers never leave ink or an edit behind.
            assertTrue("$c ${c.t.calls}", "inkEnd" !in c.t.calls && "editEnd" !in c.t.calls)
        }
    }

    @Test
    fun fingersNeverDrawOrSelectWithPenOnly() {
        for (c in cases().filter { it.penOnly }) {
            c.drag(FINGER)
            c.ev(DOWN, c.p(0, FINGER, 0f))
            c.ev(UP, c.p(0, FINGER, 0f))
            assertTrue("$c ${c.t.calls}", c.t.calls.none { it.startsWith("ink") || it.startsWith("edit") })
        }
    }

    @Test
    fun fingerEventsDoNotPressTheButtons() {
        for (c in cases()) {
            c.ev(DOWN, c.p(0, FINGER, 0f), buttons = c.pointerButton or c.eraseButton)
            c.ev(UP, c.p(0, FINGER, 0f))
            assertTrue("$c ${c.t.calls}", "pointer" !in c.t.calls)
        }
    }
}

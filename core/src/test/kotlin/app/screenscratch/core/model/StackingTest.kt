package app.screenscratch.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StackingTest {
    // A box element: x, y position with a 10×10 frame.
    private fun box(id: String, x: Float, y: Float = 0f) =
        TextBox(id, Rect(x, y, 10f, 10f), id, 12f, Argb.BLACK)

    private fun List<Element>.ids() = joinToString(",") { it.id }

    // a, b, c overlap each other at the origin; far is elsewhere.
    private val a = box("a", 0f)
    private val b = box("b", 5f)
    private val c = box("c", 2f)
    private val far = box("far", 1000f)

    @Test
    fun emptySelectionChangesNothing() {
        val list = listOf(a, b, c)
        for (op in ops) assertEquals(list, op(list, emptySet()))
    }

    @Test
    fun unknownIdsAreIgnored() {
        val list = listOf(a, b, c)
        for (op in ops) assertEquals(list, op(list, setOf("nope")))
        assertEquals("b,c,a", list.bringToFront(setOf("a", "nope")).ids())
    }

    @Test
    fun alreadyAtFrontOrBackChangesNothing() {
        val list = listOf(a, b, c)
        assertEquals(list, list.bringToFront(setOf("c")))
        assertEquals(list, list.bringForward(setOf("c")))
        assertEquals(list, list.bringToFront(setOf("b", "c")))
        assertEquals(list, list.sendToBack(setOf("a")))
        assertEquals(list, list.sendBackward(setOf("a")))
        assertEquals(list, list.sendToBack(setOf("a", "b")))
    }

    @Test
    fun toFrontAndBackKeepRelativeOrderOfNonContiguousSelection() {
        val e = box("e", 3f)
        val list = listOf(a, b, c, far, e)
        assertEquals("a,c,e,b,far", list.bringToFront(setOf("far", "b")).ids())
        assertEquals("b,far,a,c,e", list.sendToBack(setOf("far", "b")).ids())
    }

    @Test
    fun forwardStepsPastNextOverlappingElementOnly() {
        // far sits between a and c but doesn't overlap a, so a steps past both.
        assertEquals("far,c,a,b", listOf(a, far, c, b).bringForward(setOf("a")).ids())
        assertEquals("a,c,b,far", listOf(a, b, far, c).sendBackward(setOf("c")).ids())
    }

    @Test
    fun forwardWithNothingOverlappingAboveChangesNothing() {
        val list = listOf(a, far)
        assertEquals(list, list.bringForward(setOf("a")))
        assertEquals(list, list.sendBackward(setOf("far")))
    }

    @Test
    fun forwardRegroupsNonContiguousSelectionAboveTarget() {
        val x = box("x", 500f)
        val y = box("y", 2f) // overlaps a
        val z = box("z", 4f)
        val list = listOf(a, x, b, y, z)
        assertEquals("x,y,a,b,z", list.bringForward(setOf("a", "b")).ids())
        assertEquals("a,x,b,z,y", list.bringForward(setOf("y")).ids())
    }

    @Test
    fun backwardRegroupsNonContiguousSelectionBelowTarget() {
        val x = box("x", 500f)
        val list = listOf(a, b, x, c)
        // Bottommost selected is b; a overlaps the selection and is the target.
        assertEquals("b,c,a,x", list.sendBackward(setOf("b", "c")).ids())
    }

    @Test
    fun repeatedForwardReachesTheFront() {
        var list: List<Element> = listOf(a, b, c)
        list = list.bringForward(setOf("a"))
        assertEquals("b,a,c", list.ids())
        list = list.bringForward(setOf("a"))
        assertEquals("b,c,a", list.ids())
        assertEquals(list, list.bringForward(setOf("a")))
    }

    private val ops: List<(List<Element>, Set<String>) -> List<Element>> = listOf(
        { l, s -> l.bringToFront(s) },
        { l, s -> l.bringForward(s) },
        { l, s -> l.sendBackward(s) },
        { l, s -> l.sendToBack(s) },
    )
}

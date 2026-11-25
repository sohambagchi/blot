package app.blot.core.edit

import app.blot.core.model.Argb
import app.blot.core.model.Element
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.bringForward
import app.blot.core.model.bringToFront
import app.blot.core.model.sendBackward
import app.blot.core.model.sendToBack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {
    /** Overlapping squares, so every restack has a target. */
    private fun sq(id: String, group: String? = null) =
        Shape(id, ShapeKind.RECTANGLE, Rect(0f, 0f, 10f, 10f), Argb.BLACK, 1f, group = group)

    private val ids: (List<Element>) -> List<String> = { l -> l.map { it.id } }

    @Test
    fun selectingAMemberSelectsTheGroup() {
        val els = listOf(sq("a", "g"), sq("b"), sq("c", "g"), sq("d", "h"))
        assertEquals(setOf("a", "c"), Groups.expand(els, setOf("a")))
        assertEquals(setOf("a", "b", "c"), Groups.expand(els, setOf("b", "c")))
        assertEquals(setOf("b"), Groups.expand(els, setOf("b")))
    }

    @Test
    fun unitsCountGroupsOnce() {
        val els = listOf(sq("a", "g"), sq("b"), sq("c", "g"), sq("d", "h"))
        assertEquals(1, Groups.units(els, setOf("a", "c")))
        assertEquals(2, Groups.units(els, setOf("a", "b", "c")))
        assertEquals(3, Groups.units(els, setOf("a", "b", "c", "d")))
        assertEquals("g", Groups.soleGroup(els, setOf("a", "c")))
        assertNull(Groups.soleGroup(els, setOf("a", "b", "c")))
        assertNull(Groups.soleGroup(els, setOf("b")))
    }

    @Test
    fun groupingGathersMembersWhereTheTopmostWas() {
        val els = listOf(sq("a"), sq("x"), sq("b"), sq("y"), sq("c"), sq("z"))
        val out = Groups.group(els, setOf("a", "b", "c"), "g")
        assertEquals(listOf("x", "y", "a", "b", "c", "z"), ids(out))
        assertEquals(listOf(null, null, "g", "g", "g", null), out.map { it.group })
    }

    @Test
    fun groupingGroupsMergesThemIntoOneNewGroup() {
        val els = listOf(sq("a", "g1"), sq("b", "g1"), sq("c", "g2"), sq("d"))
        val out = Groups.group(els, setOf("a", "b", "c", "d"), "new")
        assertEquals(setOf("new"), out.map { it.group }.toSet())
    }

    @Test
    fun ungroupingClearsTheGroup() {
        val els = listOf(sq("a", "g"), sq("b", "g"), sq("c", "h"))
        val out = Groups.ungroup(els, setOf("a", "b"))
        assertEquals(listOf(null, null, "h"), out.map { it.group })
        assertEquals(ids(els), ids(out))
    }

    @Test
    fun duplicatingAGroupGivesTheCopiesANewGroup() {
        val els = listOf(sq("a", "g"), sq("b", "g"), sq("c"), sq("d", "h"))
        var n = 0
        val (out, copies) = SelectionEdits.duplicate(els, setOf("a", "b", "c", "d"), 5f, 5f) { "n${n++}" }
        val byId = out.associateBy { it.id }
        val ga = byId.getValue(copies[0]).group
        assertEquals(ga, byId.getValue(copies[1]).group)
        assertNotEquals("g", ga)
        assertNull(byId.getValue(copies[2]).group)
        val gd = byId.getValue(copies[3]).group
        assertNotEquals("h", gd)
        assertNotEquals(ga, gd)
        assertEquals(out.size, out.map { it.id }.toSet().size)
    }

    private fun assertContiguous(els: List<Element>) {
        for (g in els.mapNotNull { it.group }.toSet()) {
            val idx = els.indices.filter { els[it].group == g }
            assertEquals("group $g in ${ids(els)}", idx.last() - idx.first() + 1, idx.size)
        }
    }

    @Test
    fun restackingStepsPastWholeGroups() {
        val els = listOf(sq("s"), sq("a", "g"), sq("b", "g"), sq("t"))
        // A loose element brought forward goes above the whole group, not between its members.
        val fwd = els.bringForward(setOf("s"))
        assertEquals(listOf("a", "b", "s", "t"), ids(fwd))
        assertContiguous(fwd)
        val back = els.sendBackward(setOf("t"))
        assertEquals(listOf("s", "t", "a", "b"), ids(back))
        assertContiguous(back)
        // A selected group moves as one run.
        val gFwd = els.bringForward(setOf("a", "b"))
        assertEquals(listOf("s", "t", "a", "b"), ids(gFwd))
        val gBack = els.sendBackward(setOf("a", "b"))
        assertEquals(listOf("a", "b", "s", "t"), ids(gBack))
        assertContiguous(els.bringToFront(setOf("a", "b")))
        assertContiguous(els.sendToBack(setOf("a", "b")))
    }

    @Test
    fun aGroupMovedPastAnotherGroupStaysWhole() {
        val els = listOf(sq("a", "g"), sq("b", "g"), sq("c", "h"), sq("d", "h"))
        val out = els.bringForward(setOf("a", "b"))
        assertEquals(listOf("c", "d", "a", "b"), ids(out))
        assertContiguous(out)
        assertTrue(out.sendBackward(setOf("a", "b")) == els)
    }
}

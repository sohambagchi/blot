package app.blot.core.edit

import app.blot.core.model.Element

/**
 * Groups of elements, by [Element.group]. A group's members are selected and edited together and
 * lie in one contiguous run of the stacking order; groups don't nest. Ids not in the list are
 * ignored.
 */
object Groups {
    /** [ids] plus every element that shares a group with one of them. */
    fun expand(elements: List<Element>, ids: Set<String>): Set<String> {
        if (ids.isEmpty()) return ids
        val groups = elements.mapNotNullTo(HashSet()) { e -> e.group.takeIf { e.id in ids } }
        if (groups.isEmpty()) return ids
        val out = HashSet(ids)
        for (e in elements) if (e.group in groups) out += e.id
        return out
    }

    /** How many groups and ungrouped elements the elements with [ids] make up. */
    fun units(elements: List<Element>, ids: Set<String>): Int {
        val groups = HashSet<String>()
        var loose = 0
        for (e in elements) {
            if (e.id !in ids) continue
            val g = e.group
            if (g == null) loose++ else groups += g
        }
        return groups.size + loose
    }

    /** The group id when the elements with [ids] are all in one group, otherwise null. */
    fun soleGroup(elements: List<Element>, ids: Set<String>): String? {
        val selected = elements.filter { it.id in ids }
        val g = selected.firstOrNull()?.group ?: return null
        return g.takeIf { selected.all { it.group == g } }
    }

    /**
     * [elements] with those in [ids] put in the one group [group], which replaces any groups
     * they were in. The members are moved into one run, in their order, where the topmost of
     * them was.
     */
    fun group(elements: List<Element>, ids: Set<String>, group: String): List<Element> {
        val top = elements.indexOfLast { it.id in ids }
        if (top < 0) return elements
        val members = elements.filter { it.id in ids }.map { it.withGroup(group) }
        val out = ArrayList<Element>(elements.size)
        for ((i, e) in elements.withIndex()) {
            if (e.id !in ids) out += e
            if (i == top) out += members
        }
        return out
    }

    /** [elements] with those in [ids] taken out of their groups. */
    fun ungroup(elements: List<Element>, ids: Set<String>): List<Element> =
        elements.map { if (it.id in ids) it.withGroup(null) else it }
}

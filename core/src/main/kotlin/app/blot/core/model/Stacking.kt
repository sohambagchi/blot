package app.blot.core.model

/*
 * Stacking-order commands on a selection. Each takes elements in stacking order (index 0 at the
 * back) and returns the reordered list; selected elements keep their order relative to each
 * other. Ids not in the list are ignored, so an empty or unmatched selection changes nothing.
 *
 * Forward and backward step past the next *overlapping* element rather than one index: in a
 * note of many small strokes the element one index away usually doesn't overlap the selection,
 * so a one-index step would show no change.
 */

/** Moves the selected elements to the front, above everything else. */
fun List<Element>.bringToFront(selection: Set<String>): List<Element> {
    val (selected, rest) = partition { it.id in selection }
    return rest + selected
}

/** Moves the selected elements to the back, below everything else. */
fun List<Element>.sendToBack(selection: Set<String>): List<Element> {
    val (selected, rest) = partition { it.id in selection }
    return selected + rest
}

/**
 * Moves the selection up past the next element that overlaps it.
 *
 * The target is the lowest unselected element above the topmost selected one whose bounds
 * intersect any selected element's bounds. The selection is regrouped, in order, directly above
 * the target. With no target, nothing changes.
 */
fun List<Element>.bringForward(selection: Set<String>): List<Element> {
    val top = indexOfLast { it.id in selection }
    if (top < 0) return this
    val boxes = selectedBounds(selection)
    val target = (top + 1 until size).firstOrNull { i -> overlapsAny(this[i], boxes) } ?: return this
    val selected = filter { it.id in selection }
    val out = ArrayList<Element>(size)
    for (i in indices) {
        if (this[i].id !in selection) out += this[i]
        if (i == target) out += selected
    }
    return out
}

/**
 * Moves the selection down past the next element that overlaps it.
 *
 * The target is the highest unselected element below the bottommost selected one whose bounds
 * intersect any selected element's bounds. The selection is regrouped, in order, directly below
 * the target. With no target, nothing changes.
 */
fun List<Element>.sendBackward(selection: Set<String>): List<Element> {
    val bottom = indexOfFirst { it.id in selection }
    if (bottom < 0) return this
    val boxes = selectedBounds(selection)
    val target = (bottom - 1 downTo 0).firstOrNull { i -> overlapsAny(this[i], boxes) } ?: return this
    val selected = filter { it.id in selection }
    val out = ArrayList<Element>(size)
    for (i in indices) {
        if (i == target) out += selected
        if (this[i].id !in selection) out += this[i]
    }
    return out
}

private fun List<Element>.selectedBounds(selection: Set<String>): List<Rect> =
    filter { it.id in selection }.map { it.bounds }

private fun overlapsAny(e: Element, boxes: List<Rect>): Boolean {
    val b = e.bounds
    return boxes.any { it.intersects(b) }
}

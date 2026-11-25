package app.blot.canvas

import app.blot.core.ink.StrokeOutliner
import app.blot.core.model.Stroke
import java.util.concurrent.ConcurrentHashMap

/**
 * Stroke outlines (perfect-freehand polygons, x0, y0, x1, y1, … in canvas units) by stroke id.
 * An entry is reused while the note holds the same [Stroke] object; strokes are immutable, so a
 * changed stroke is a new object and is outlined again. Safe to use from several threads: the
 * UI thread and the ink render thread share one cache.
 */
class OutlineCache {
    private class Entry(val stroke: Stroke, val xy: FloatArray)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val outliner = ThreadLocal.withInitial { StrokeOutliner() }
    private val input = ThreadLocal.withInitial { FloatArray(256) }

    /** [stroke]'s outline. The array must not be modified. */
    fun outline(stroke: Stroke): FloatArray {
        entries[stroke.id]?.let { if (it.stroke === stroke) return it.xy }
        val xy = compute(stroke)
        entries[stroke.id] = Entry(stroke, xy)
        return xy
    }

    /** Drops entries for strokes not in [ids]. */
    fun retain(ids: Set<String>) {
        entries.keys.retainAll(ids)
    }

    val size: Int get() = entries.size

    private fun compute(stroke: Stroke): FloatArray {
        val n = stroke.points.size
        var buf = input.get()!!
        if (buf.size < n * 3) {
            buf = FloatArray(n * 3 * 2)
            input.set(buf)
        }
        for ((i, p) in stroke.points.withIndex()) {
            buf[i * 3] = p.x
            buf[i * 3 + 1] = p.y
            buf[i * 3 + 2] = if (stroke.pressureReported) p.pressure else Float.NaN
        }
        val o = outliner.get()!!
        o.outline(buf, n, InkStyle.options(stroke))
        return o.toFloatArray()
    }
}

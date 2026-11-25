package app.blot.canvas

import android.graphics.Path
import android.graphics.RectF
import app.blot.core.geometry.PathData
import app.blot.core.geometry.PathSegment

private val arcBox = ThreadLocal.withInitial { RectF() }

/** This path as an `android.graphics.Path`, written into [out] (rewound first). */
fun PathData.toPath(out: Path = Path()): Path {
    out.rewind()
    val box = arcBox.get()!!
    for (sp in subpaths) {
        out.moveTo(sp.start.x, sp.start.y)
        for (seg in sp.segments) {
            when (seg) {
                is PathSegment.LineTo -> out.lineTo(seg.end.x, seg.end.y)
                is PathSegment.QuadTo -> out.quadTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y)
                is PathSegment.CubicTo -> out.cubicTo(seg.c1.x, seg.c1.y, seg.c2.x, seg.c2.y, seg.end.x, seg.end.y)
                is PathSegment.ArcTo -> {
                    box.set(seg.cx - seg.rx, seg.cy - seg.ry, seg.cx + seg.rx, seg.cy + seg.ry)
                    out.arcTo(box, Math.toDegrees(seg.start.toDouble()).toFloat(), Math.toDegrees(seg.sweep.toDouble()).toFloat(), false)
                }
            }
        }
        if (sp.closed) out.close()
    }
    return out
}

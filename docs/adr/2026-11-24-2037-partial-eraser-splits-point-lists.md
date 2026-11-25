# Partial eraser splits strokes' point lists

**Status:** accepted; amended by [2026-11-25-0002-eraser-takes-shapes-and-lines-whole](2026-11-25-0002-eraser-takes-shapes-and-lines-whole.md)

**Context:** The eraser can take whole strokes or only the parts it passes over. Skia rasterises `PorterDuff.CLEAR` in PDFs, so erasing has to change geometry, and strokes are stored as raw samples ([2026-11-24-1928-note-json-format](2026-11-24-1928-note-json-format.md)).
**Decision:**
- A stroke the eraser covers part of is replaced, at its place in the stacking order, by one new stroke per surviving run of samples, each with a fresh id and its times counted from its own first sample. Ported from xnotes' area eraser (MIT).
- The eraser is the capsule swept between two eraser samples. It reaches half the stroke's width past the samples, so it cuts where it visibly touches the ink.
- Each cut ends on a point interpolated onto the eraser's edge and snapped to the `note.json` grid. An eraser passing between two far-apart samples still cuts there.
- Only strokes are cut. Shapes, lines, text and images are left alone, as with the whole-stroke eraser.
- One eraser gesture is one undo step, however many cuts it makes.
**Alternatives:**
- Drop only the samples within the eraser circle, as xnotes does: cuts then depend on how densely the pen sampled, and a fast eraser can pass between samples without cutting.
- Keep the stroke and store erased ranges or masks: every renderer and exporter would have to honour them.
**Consequences:** A piece of a stroke with simulated pressure starts its taper again at the cut. Erasing many times leaves many small strokes; the file grows by one header per piece.

# In-progress ink as pressure-width capsules

**Status:** accepted

**Context:** While the pen is down, ink goes into `LowLatencyCanvasView`'s single, persistent buffer ([2026-11-24-1909-ink-through-lowlatencycanvasview](2026-11-24-1909-ink-through-lowlatencycanvasview.md)). Nothing drawn there can be taken back without redrawing what lies under it. At pen-up the canvas view shows the stroke's perfect-freehand outline, and the switch should not be visible.
**Decision:**
- Each new sample adds one piece: the convex hull of a circle at the previous sample and one at the new sample.
- The radii come from perfect-freehand's own radius formula with the stroke's width and thinning, so the width matches the final outline at every sample. A pen that reports no pressure follows perfect-freehand's simulated pressure.
- No streamline or smoothing: the ink reaches the latest sample.
- Samples are snapped to the `note.json` grid as they arrive, so the outline drawn at pen-up is the one a reload draws.
- `input-motionprediction` is not used.
**Alternatives:**
- Re-outlining a sliding tail with perfect-freehand on every event: closer to the final shape, but streamline makes the tip trail the pen, which adds latency. Each refill also blends the anti-aliased edges again, and the old tail's end cap can't be removed.
- Round-capped line segments: beads appear wherever the width changes.
- Predicted points: a misprediction stays in the persistent buffer until pen-up.
**Consequences:**
- At pen-up only perfect-freehand's streamlining changes. On 240 Hz pen input that is a sub-pixel shift; on sparse input it rounds sharp corners. `SceneRenderTest` overlays both (`live-vs-final.png`).
- Translucent ink, such as a highlighter, would darken where pieces overlap, so it will need its own in-progress drawing.

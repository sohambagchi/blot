# Ink through LowLatencyCanvasView

**Status:** accepted

**Context:** The stroke-engine ADR picked graphics-core's front buffer without naming one of its two renderers. On the TB-J616F both show ink once the app cuts the `SurfaceView`'s hole; by feel in the pen test, `LowLatencyCanvasView` was the most responsive pad mode and `CanvasFrontBufferedRenderer` was too slow. See [2026-11-24-1905-tb-j616f-measurements](../analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md).
**Decision:** In-progress strokes are drawn with `LowLatencyCanvasView`. It renders the visible part of the note into its single buffer when asked (`onRedrawRequested`), draws new ink straight into that buffer, and shows the result as a bitmap once the pen lifts. Panning, zooming, selection and other edits are drawn by our own canvas `View`, which then has `LowLatencyCanvasView` re-render the note at the new viewport before the next stroke.
**Alternatives:** `CanvasFrontBufferedRenderer` — redraws the whole note every frame, which suits pan and zoom, but was too slow on the tablet, and on Android 12 needs a hand-cut hole and never reports its first frame. Ordinary `View` drawing — works everywhere, but has the most latency.
**Consequences:**
- The canvas keeps two render paths in step: our `View` and `LowLatencyCanvasView`'s buffer.
- Re-rendering the note at pen-down after a pan adds work at the start of a stroke; measure it on the tablet.
- `LowLatencyCanvasView` takes a different path on emulators, so its front-buffer path is only exercised on real devices.
- `CanvasFrontBufferedRenderer` stays the fallback.

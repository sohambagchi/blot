# Stroke outlines: perfect-freehand ported to Kotlin

- `:core` turns pen points (x, y, pressure) into a pressure-shaped outline polygon in `app.screenscratch.core.ink`, ported from perfect-freehand 1.2.3 (MIT, tag `v1.2.3`, commit `f56f097`) with the same options and defaults.
- `getStroke` returns outline points and `getStrokeFlat` returns them as x0, y0, x1, y1, … for a `Path`. `StrokeOutliner` reuses its buffers, so re-outlining an in-progress stroke on each input event allocates nothing.
- The output matches upstream's within 1e-3: every upstream test input and both tablet strokes with the default options, seven of them under 21 other option sets, and 840 random option combinations. The expectations come from running the npm package (`core/src/test/js/parity.mjs`).
- On the host JVM, outlining the 620-point tablet stroke takes about 0.015 ms after warm-up.
- Fixtures: upstream's test inputs and the two TB-J616F strokes, under `core/src/test/resources/ink/`.

# Own stroke engine on graphics-core

**Status:** accepted

**Context:** Pen strokes need low latency, pressure-shaped outlines, partial erase and a storage format we control.
**Decision:** Port perfect-freehand (MIT) to Kotlin in `:core` to turn sampled points into outline polygons. Draw the in-progress stroke with `androidx.graphics:graphics-core`'s front-buffered renderer and extend it with `androidx.input:input-motionprediction`. Store raw points (x, y, pressure, time), not outlines.
**Alternatives:** Jetpack Ink 1.0 — ~1.5 MB native code per ABI, couples storage to its protobuf, partial erase only in 1.1 alphas, and draws finished strokes with `Path` on API < 34 anyway.
**Consequences:** We own stroke quality and must tune it on the real pen. Ink stays the fallback: switching would replace rendering only, since stored points don't change. Pressure falls back to speed-simulated when the device reports none.

# The front buffer holds only ink by default

**Status:** accepted, to be confirmed on the TB-J616F

**Context:** [2026-11-24-1909-ink-through-lowlatencycanvasview](2026-11-24-1909-ink-through-lowlatencycanvasview.md) re-renders the visible note into `LowLatencyCanvasView`'s buffer and inks on top. The buffer's `SurfaceView` sits above the window, so the editor chrome over the canvas disappears while a stroke is drawn. The canvas can instead keep only the ink in a transparent buffer over the canvas view.
**Decision:** The app starts in ink-only mode. A hidden developer switch under Settings → About (long-press) puts the whole note back in the buffer.
**Alternatives:** Whole note in the buffer, as the ink ADR has it: no compositing of the ink over the view, but the chrome hides mid-stroke and the note is re-rendered at pen-down after every pan; on the emulator it reached the display about 30 ms later.
**Consequences:** The chrome stays visible while writing. Which mode feels faster on the tablet's real front-buffer path is still unmeasured; if the whole-note mode wins there, flip the default.

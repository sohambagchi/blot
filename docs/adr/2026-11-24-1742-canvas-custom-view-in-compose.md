# Canvas is a custom View inside Compose

**Status:** accepted

**Context:** Front-buffered inking needs a `SurfaceView`; Compose's `Canvas` cannot draw to a front buffer.
**Decision:** The canvas is a custom `View` hosted with `AndroidView`. The control bar, menus, colour picker, settings and library are Compose, overlaid on it. Text boxes and images are drawn by the canvas; an editor appears only while a text box is being edited.
**Alternatives:** All-Compose canvas — simpler, but noticeably more pen latency.
**Consequences:** Canvas drawing code is plain `android.graphics`, shared with PNG and PDF export.

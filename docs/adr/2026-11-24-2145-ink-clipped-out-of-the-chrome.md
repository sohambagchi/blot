# In-progress ink is clipped out of the chrome

**Status:** accepted

**Context:** The front buffer's `SurfaceView` sits above the whole window ([2026-11-24-1909-ink-through-lowlatencycanvasview](2026-11-24-1909-ink-through-lowlatencycanvasview.md)), so a stroke drawn across the colour panel, a menu or a pill showed on top of it until pen-up, then went behind it. In whole-note mode ([2026-11-24-2043-front-buffer-holds-ink-only-by-default](2026-11-24-2043-front-buffer-holds-ink-only-by-default.md)) the opaque buffer hid the chrome entirely mid-stroke.
**Decision:**
- Every floating surface (`floatingSurface`, and the modal scrim) reports its rounded rectangle in window pixels to a `ChromeBounds` registry while laid out. The editor turns them into `CanvasController.inkExclusions`, in the canvas view's pixels, grown out to whole pixels.
- The render thread clips every ink draw with `clipOutPath` of those rounded rectangles, and wipes them to transparent whenever the scene is drawn into the buffer or the list changes. The window, chrome included, shows through there, in both front-buffer modes.
- A change mid-stroke redraws the buffer; a change between strokes clears it, since the last stroke was clipped to the old areas.
- Floating surfaces also take every touch that lands on them, so a pen-down in their padding goes to the chrome, not the canvas.
**Alternatives:**
- Lower the front buffer below the window (`setZOrderMediaOverlay`) and cut holes for the canvas: the window's opaque background would hide the ink, and `LowLatencyCanvasView` sets the Z-order itself.
- Hide or dim the chrome while writing: the colour panel and menus are often open while writing.
- Exclude bounding boxes only: ink would stop short of the pills' rounded corners, then appear there at pen-up.
**Consequences:**
- New chrome over the canvas is covered by building it on `floatingSurface` or `chromeSurface`; anything drawn another way still shows ink over it mid-stroke.
- Shadows are not excluded: in-progress ink passes over a surface's shadow and goes under it at pen-up, and in whole-note mode the buffer's scene covers the shadows while a stroke is drawn.
- Whole-note mode now shows the chrome while writing, which removes that mode's visible drawback; which mode is faster is still unmeasured on the tablet.

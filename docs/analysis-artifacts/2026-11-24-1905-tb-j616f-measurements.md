# TB-J616F measurements

**Question:** What do the Lenovo TB-J616F and its pen actually report, and does front-buffered inking work on it?

**Answer:** The pen gives 4096 pressure levels, tilt, orientation, hover and two side buttons at about 240 Hz, and has no eraser end. The screen is 204 dpi (1569×941 dp), not the assumed 240 dpi (1333×800 dp). Both graphics-core front-buffer renderers work once the app cuts the `SurfaceView`'s hole in the window itself.

Sources: pen test reports `reports/pen-report-20261124-184928.json` (first run) and `reports/pen-report-20261124-190623.json` (second run, with the hole cut and every pad mode judged by eye).

## Device

| | |
|---|---|
| SoC | MediaTek MT8185, Android 12 (API 31) |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `armeabi` |
| Screen | 2000×1200 px, 204 dpi (density 1.275) → 1569×941 dp landscape; 60 Hz only |
| Pen device | `himax-pen`, sources touchscreen + stylus, separate from the `himax-touchscreen` finger device |

## Pen

- **Pressure:** the full 0–1 range in steps of 1/4095, so 4096 levels.
- **Tilt:** 0–90° axis; 17–56° seen while writing. **Orientation:** ±180° axis, about ±80° seen.
- **Hover:** `AXIS_DISTANCE` 0–1, 1884 hover samples.
- **Rate:** about 237 Hz in a 1.1 s stroke, so about 4 samples per 60 Hz frame. Timestamps are whole milliseconds, so the intervals jitter between 3, 4, 6 and 7 ms.
- **Buttons:** `BUTTON_STYLUS_PRIMARY` and `BUTTON_STYLUS_SECONDARY` are both reported. No `TOOL_TYPE_ERASER` was seen.
- **Fingers:** a separate tool type, at about 90 Hz with coarse pressure (about 200 levels).
- **Prediction:** `input-motionprediction` predicted 87–89% of moves, with a mean lead of 18–19 ms (about one frame).

## Front buffer

- On the tablet, `CanvasFrontBufferedRenderer` drew and reported frames presented, but nothing was visible, with or without rotation. The ordinary `View` pad drew and rotated correctly.
- The Android 12 emulator reproduces it:
  - A plain `SurfaceView` and `LowLatencyCanvasView` show their ink.
  - `CanvasFrontBufferedRenderer` shows nothing, in both 1.0.4 and 1.1.0-beta01.
  - With `setZOrderOnTop(true)` its ink appears, so its layers render but sit behind the window.
- The window only cuts a `SurfaceView`'s transparent hole once the surface reports its first frame finished. On Android 12, `CanvasFrontBufferedRenderer` never reports it. That is also why a window holding one from its first frame waits forever for its first draw and takes no input.
- Fix in the pen test: the pad clears the pixels behind itself (`BlendMode.Clear` in Compose), cutting the hole itself. With that, all four pad modes show ink on the API 31 and API 35 emulators, including after rotation. On the tablet, every pad mode was judged visible: `View`, `SurfaceView`, `CanvasFrontBufferedRenderer` and `LowLatencyCanvasView`.
- By feel on the tablet: `LowLatencyCanvasView` was the most responsive, `CanvasFrontBufferedRenderer` too slow, and the plain `SurfaceView` pad (redrawn whole per segment on the UI thread) showed ink only after the pen lifted. `LowLatencyCanvasView` wins; the canvas uses it ([2026-11-24-1909-ink-through-lowlatencycanvasview](../adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md)).

# Pen test screen

- The main screen has a "Pen test" entry; the debug APK can be sideloaded and run without adb.
- The pen test shows live pen axes and, per tool type, the pressure range and an estimate of its levels, tilt, orientation, hover distance, buttons seen and sample rate. It also shows motion-prediction lead.
- The pad starts as an ordinary `View`. "Pad" cycles through `View`, plain `SurfaceView`, `CanvasFrontBufferedRenderer` and `LowLatencyCanvasView`, and "Ink visible? Yes / No" records the verdict for each in the report.
- Front-buffered ink shows on Android 12: the pad cuts its own transparent hole in the window. If a `CanvasFrontBufferedRenderer` stroke isn't presented within 3 s, the screen falls back to the ordinary pad and records the front buffer as stalled.
- The tablet emulator now uses the measured 204 dpi (1569×941 dp); see [2026-11-24-1905-tb-j616f-measurements](../analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md).
- "Share report" and "Save report" export everything as JSON, adding device, display and input-device ranges and the last stroke's samples.
- Dependencies added: `androidx.graphics:graphics-core` 1.0.4, `androidx.input:input-motionprediction` 1.0.1.
- Tests: `PenStats` unit tests, plus on-device tests that inject a stylus stroke and check the front-buffer fallback. They pass on the API 31 and API 35 tablet emulators, with a stroke through every pad mode.

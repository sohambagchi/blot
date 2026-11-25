Console: https://claude.ai/artifact/J59pWtPJ2HmmSFPT5TUmix

# Index

## Analysis

- [2026-11-24-1905-tb-j616f-measurements](analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md) — what the tablet and pen report; front buffer works once the app cuts the SurfaceView's hole
- [2026-11-24-1551-component-reuse-research](analysis-artifacts/2026-11-24-1551-component-reuse-research.md) — which libraries to reuse vs. reimplement

## Decisions

- [2026-11-24-1928-note-json-format](adr/2026-11-24-1928-note-json-format.md) — note.json layout, element types and the point codec
- [2026-11-24-1934-note-directory-named-by-id](adr/2026-11-24-1934-note-directory-named-by-id.md) — note folders are named by a stable id, never by title
- [2026-11-24-1934-note-json-wins-over-tmp](adr/2026-11-24-1934-note-json-wins-over-tmp.md) — save and recovery rules for note.json and its tmp
- [2026-11-24-1912-pen-input-controls](adr/2026-11-24-1912-pen-input-controls.md) — side buttons cycle pen/pan/select and erase; "Pen only" in the colour panel
- [2026-11-24-1909-ink-through-lowlatencycanvasview](adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md) — strokes ink through LowLatencyCanvasView; our View handles pan, zoom and edits
- [2026-11-24-1742-own-stroke-engine](adr/2026-11-24-1742-own-stroke-engine.md) — perfect-freehand port on graphics-core; Jetpack Ink as fallback
- [2026-11-24-1742-canvas-custom-view-in-compose](adr/2026-11-24-1742-canvas-custom-view-in-compose.md) — front-buffered canvas View, Compose chrome
- [2026-11-24-1742-storage-saf-directory-per-note](adr/2026-11-24-1742-storage-saf-directory-per-note.md) — SAF folder, one directory per note, library index
- [2026-11-24-1742-markdown-commonmark-staticlayout](adr/2026-11-24-1742-markdown-commonmark-staticlayout.md) — one markdown renderer for screen, PNG and PDF
- [2026-11-24-1742-mit-licence](adr/2026-11-24-1742-mit-licence.md) — MIT; GPL code read for approach only
- [2026-11-24-1551-separate-build-sdk-from-emulator-sdk](adr/2026-11-24-1551-separate-build-sdk-from-emulator-sdk.md) — own build SDK (compileSdk 37.2) beside android-emu's emulator SDK

## Changes

- [2026-11-24-1934-note-store](changelog/2026-11-24-1934-note-store.md) — SAF note storage with an incremental library index
- [2026-11-24-1931-markdown-renderer](changelog/2026-11-24-1931-markdown-renderer.md) — markdown text boxes for screen, PNG and PDF
- [2026-11-24-1928-note-model-and-format](changelog/2026-11-24-1928-note-model-and-format.md) — core note model and note.json format
- [2026-11-24-1927-stroke-outline](changelog/2026-11-24-1927-stroke-outline.md) — perfect-freehand ported to Kotlin
- [2026-11-24-1808-pen-test-screen](changelog/2026-11-24-1808-pen-test-screen.md) — pen test screen and shareable JSON report
- [2026-11-24-1742-backend-decisions](changelog/2026-11-24-1742-backend-decisions.md) — backend decided, tablet emulator on Android 12
- [2026-11-24-1551-dev-shell-and-test-harness](changelog/2026-11-24-1551-dev-shell-and-test-harness.md) — Nix dev shell, Gradle skeleton, tablet AVD, tests passing

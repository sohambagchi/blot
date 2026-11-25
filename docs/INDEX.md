Console: https://claude.ai/artifact/J59pWtPJ2HmmSFPT5TUmix

# Index

## Analysis

- [2026-11-24-1905-tb-j616f-measurements](analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md) — what the tablet and pen report; front buffer works once the app cuts the SurfaceView's hole
- [2026-11-24-1551-component-reuse-research](analysis-artifacts/2026-11-24-1551-component-reuse-research.md) — which libraries to reuse vs. reimplement

## Decisions

- [2026-11-24-2236-format-2-shape-kinds-and-line-routes](adr/2026-11-24-2236-format-2-shape-kinds-and-line-routes.md) — format 2 adds shape kinds and line routes; notes are written at the lowest version they need
- [2026-11-24-2213-fixed-signing-key-outside-the-repo](adr/2026-11-24-2213-fixed-signing-key-outside-the-repo.md) — builds sign with ~/.android/blot.jks so updates install over each other
- [2026-11-24-2149-pen-buttons-pointer-and-eraser](adr/2026-11-24-2149-pen-buttons-pointer-and-eraser.md) — secondary pen button switches to the pointer; primary held erases; fingers pan in pen-only mode
- [2026-11-24-2149-rotation-and-groups-in-note-json](adr/2026-11-24-2149-rotation-and-groups-in-note-json.md) — optional rotation on text boxes and images, optional group ids, no format bump
- [2026-11-24-2158-text-box-maths-ported-typesetter](adr/2026-11-24-2158-text-box-maths-ported-typesetter.md) — LaTeX maths in text boxes through a ported iosMath typesetter and a bundled Latin Modern Math subset
- [2026-11-24-2145-ink-clipped-out-of-the-chrome](adr/2026-11-24-2145-ink-clipped-out-of-the-chrome.md) — in-progress ink is clipped out of the chrome's reported rectangles
- [2026-11-24-2125-rename-to-blot](adr/2026-11-24-2125-rename-to-blot.md) — the app is called Blot, ID app.blot
- [2026-11-24-2037-lasso-selects-what-it-touches](adr/2026-11-24-2037-lasso-selects-what-it-touches.md) — the lasso selects anything it encloses or touches
- [2026-11-24-2037-partial-eraser-splits-point-lists](adr/2026-11-24-2037-partial-eraser-splits-point-lists.md) — the partial eraser cuts strokes into new strokes
- [2026-11-24-2037-text-boxes-edited-in-place](adr/2026-11-24-2037-text-boxes-edited-in-place.md) — text boxes are edited as markdown source over the canvas
- [2026-11-24-2043-front-buffer-holds-ink-only-by-default](adr/2026-11-24-2043-front-buffer-holds-ink-only-by-default.md) — the low-latency layer holds only new ink, pending the tablet's confirmation
- [2026-11-24-2043-thumbnails-outside-the-library-index](adr/2026-11-24-2043-thumbnails-outside-the-library-index.md) — thumbnails cached app-private, not in the library index
- [2026-11-24-2023-export-pages-and-bounds](adr/2026-11-24-2023-export-pages-and-bounds.md) — PDF page per note page; PNG and SVG as one file; content bounds plus margin
- [2026-11-24-2023-svg-embeds-images](adr/2026-11-24-2023-svg-embeds-images.md) — SVG embeds images as data URIs and lays text out as positioned lines
- [2026-11-24-2005-in-progress-ink-as-pressure-capsules](adr/2026-11-24-2005-in-progress-ink-as-pressure-capsules.md) — in-progress ink drawn as pressure capsules matching the final outline
- [2026-11-24-1928-note-json-format](adr/2026-11-24-1928-note-json-format.md) — note.json layout, element types and the point codec
- [2026-11-24-1934-note-directory-named-by-id](adr/2026-11-24-1934-note-directory-named-by-id.md) — note folders are named by a stable id, never by title
- [2026-11-24-1934-note-json-wins-over-tmp](adr/2026-11-24-1934-note-json-wins-over-tmp.md) — save and recovery rules for note.json and its tmp
- [2026-11-24-1909-ink-through-lowlatencycanvasview](adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md) — strokes ink through LowLatencyCanvasView; our View handles pan, zoom and edits
- [2026-11-24-1742-own-stroke-engine](adr/2026-11-24-1742-own-stroke-engine.md) — perfect-freehand port on graphics-core; Jetpack Ink as fallback
- [2026-11-24-1742-canvas-custom-view-in-compose](adr/2026-11-24-1742-canvas-custom-view-in-compose.md) — front-buffered canvas View, Compose chrome
- [2026-11-24-1742-storage-saf-directory-per-note](adr/2026-11-24-1742-storage-saf-directory-per-note.md) — SAF folder, one directory per note, library index
- [2026-11-24-1742-markdown-commonmark-staticlayout](adr/2026-11-24-1742-markdown-commonmark-staticlayout.md) — one markdown renderer for screen, PNG and PDF
- [2026-11-24-1742-mit-licence](adr/2026-11-24-1742-mit-licence.md) — MIT; GPL code read for approach only
- [2026-11-24-1551-separate-build-sdk-from-emulator-sdk](adr/2026-11-24-1551-separate-build-sdk-from-emulator-sdk.md) — own build SDK (compileSdk 37.2) beside android-emu's emulator SDK

## Changes

- [2026-11-24-2236-shapes-and-line-routes](changelog/2026-11-24-2236-shapes-and-line-routes.md) — every shape kind and elbow and curved lines draw
- [2026-11-24-2149-pointer-input](changelog/2026-11-24-2149-pointer-input.md) — pointer tool, pen buttons, rotate and scale with readouts, press-and-hold menu, groups
- [2026-11-24-2158-text-box-maths](changelog/2026-11-24-2158-text-box-maths.md) — text boxes fit their markdown and render $…$ and $$…$$ maths
- [2026-11-24-2145-titles-and-ink-off-the-chrome](changelog/2026-11-24-2145-titles-and-ink-off-the-chrome.md) — notes can be named and renamed; ink never draws over the chrome
- [2026-11-24-2125-rename-to-blot](changelog/2026-11-24-2125-rename-to-blot.md) — Screen Scratch renamed to Blot
- [2026-11-24-2037-editing-tools](changelog/2026-11-24-2037-editing-tools.md) — partial eraser, lasso select, shapes, lines and arrows, text boxes
- [2026-11-24-2043-app-shell](changelog/2026-11-24-2043-app-shell.md) — library, editor, storage and canvas wired into one app
- [2026-11-24-2023-export](changelog/2026-11-24-2023-export.md) — PNG, PDF and SVG export with share and save to folder
- [2026-11-24-2005-inking-canvas](changelog/2026-11-24-2005-inking-canvas.md) — inking canvas on LowLatencyCanvasView, with pan, zoom, eraser and undo
- [2026-11-24-2004-editor-ui](changelog/2026-11-24-2004-editor-ui.md) — editor chrome, library, settings and phone layout from the design
- [2026-11-24-1934-note-store](changelog/2026-11-24-1934-note-store.md) — SAF note storage with an incremental library index
- [2026-11-24-1931-markdown-renderer](changelog/2026-11-24-1931-markdown-renderer.md) — markdown text boxes for screen, PNG and PDF
- [2026-11-24-1928-note-model-and-format](changelog/2026-11-24-1928-note-model-and-format.md) — core note model and note.json format
- [2026-11-24-1927-stroke-outline](changelog/2026-11-24-1927-stroke-outline.md) — perfect-freehand ported to Kotlin
- [2026-11-24-1808-pen-test-screen](changelog/2026-11-24-1808-pen-test-screen.md) — pen test screen and shareable JSON report
- [2026-11-24-1742-backend-decisions](changelog/2026-11-24-1742-backend-decisions.md) — backend decided, tablet emulator on Android 12
- [2026-11-24-1551-dev-shell-and-test-harness](changelog/2026-11-24-1551-dev-shell-and-test-harness.md) — Nix dev shell, Gradle skeleton, tablet AVD, tests passing

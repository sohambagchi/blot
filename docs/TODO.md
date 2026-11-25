# TODO

- [ ] 1 Core document model and `note.json` format in `:core`: elements in stacking order, strokes as delta-encoded points, shapes, text boxes, images, page settings
- [ ] 2 Port perfect-freehand to `:core`, with tests
- [ ] 3 Canvas `View`: inking through `LowLatencyCanvasView` with motion prediction, pan and zoom, "Pen only" and side buttons ([2026-11-24-1912-pen-input-controls](adr/2026-11-24-1912-pen-input-controls.md)) — details: [2026-11-24-1909-ink-through-lowlatencycanvasview](adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md)
  - [ ] 3.1 Re-render the note into `LowLatencyCanvasView` after a pan or edit; measure the delay at pen-down on the tablet
  - [ ] 3.2 Check on the tablet whether `LowLatencyCanvasView` needs the hand-cut `SurfaceView` hole, and whether it can sit in the window's first frame — details: [2026-11-24-1905-tb-j616f-measurements](analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md)
- [ ] 4 SAF `NoteStore`: folder picker, directory per note, tmp-then-replace saves, library index
- [ ] 5 Tools: partial and whole-stroke eraser, lasso select/move/scale, stacking-order commands, shapes, lines and arrows, text boxes
- [ ] 6 Markdown text boxes, image import and paste
- [ ] 7 Export PNG, PDF and SVG; share
- [ ] 8 Settings screen: theme, swap the pen's side buttons
- [ ] 9 Iterate the UI in the Claude Design canvas: https://claude.ai/artifact/EfhVLmtXRNi457wy83HBCP
- [ ] 10 Public open-source release prep — details: [2026-11-24-1551-public-release](todo/2026-11-24-1551-public-release.md)
- [ ] 11 Layers: deferred; stacking order until then (the design's layer panel is not built)

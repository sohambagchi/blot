# TODO

- [ ] 1 Canvas `View`: inking through `LowLatencyCanvasView` with motion prediction, pan and zoom, "Pen only" and side buttons ([2026-11-24-1912-pen-input-controls](adr/2026-11-24-1912-pen-input-controls.md)) — details: [2026-11-24-1909-ink-through-lowlatencycanvasview](adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md)
  - [ ] 1.1 Re-render the note into `LowLatencyCanvasView` after a pan or edit; measure the delay at pen-down on the tablet
  - [ ] 1.2 Check on the tablet whether `LowLatencyCanvasView` needs the hand-cut `SurfaceView` hole, and whether it can sit in the window's first frame — details: [2026-11-24-1905-tb-j616f-measurements](analysis-artifacts/2026-11-24-1905-tb-j616f-measurements.md)
- [ ] 2 Wire the app together: library on the SAF note store, editor on the canvas, saves through `NoteJson`, thumbnails in the library index
- [ ] 3 Tools: partial eraser, lasso select/move/scale, stacking-order commands, shapes, lines and arrows, text-box editing
- [ ] 4 Image import and paste into markdown text boxes
- [ ] 5 Export PNG, PDF and SVG; share
- [ ] 6 Iterate the UI in the Claude Design canvas: https://claude.ai/artifact/EfhVLmtXRNi457wy83HBCP
- [ ] 7 Public open-source release prep — details: [2026-11-24-1551-public-release](todo/2026-11-24-1551-public-release.md)
- [ ] 8 Layers: deferred; stacking order until then (the design's layer panel is not built)

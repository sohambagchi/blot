# TODO

- [ ] 1 Try the app on the TB-J616F — details: [2026-11-24-1909-ink-through-lowlatencycanvasview](adr/2026-11-24-1909-ink-through-lowlatencycanvasview.md)
  - [ ] 1.1 Compare the two front-buffer modes (whole note vs ink only): feel, latency overlay, and whether chrome over the canvas stays visible while writing
  - [ ] 1.2 Check the pen-up switch, the first stroke after a pan, side buttons, palm resting, rotation and dark-mode colours
  - [ ] 1.3 Draw each new shape and elbow and curved lines with the pen: curve smoothing, near-level elbows straightening, arrowheads on tight curves
  - [ ] 1.4 Backgrounds: dark-mode pattern colours, and the GPU timings `BackgroundSceneTimingTest` logs under `BackgroundTiming`
  - [ ] 1.5 Folders on the real storage provider: moves leave no hidden `.moving`/`.moved` directories, a case-only rename, deleting a folder with notes, and how folders look in a file manager and Syncthing
- [ ] 2 Image import and paste into markdown text boxes
- [ ] 3 Iterate the UI in the Claude Design canvas: https://claude.ai/artifact/EfhVLmtXRNi457wy83HBCP
- [ ] 4 Public open-source release prep — details: [2026-11-24-1551-public-release](todo/2026-11-24-1551-public-release.md)
- [ ] 5 Layers: deferred; stacking order until then (the pill's Layers slot holds the Background panel)

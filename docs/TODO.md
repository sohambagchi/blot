# TODO

- [ ] 1 Settle backend decisions with the user. Proposals: [2026-11-24-1551-component-reuse-research](analysis-artifacts/2026-11-24-1551-component-reuse-research.md)
  - [ ] 1.1 Stroke engine: own perfect-freehand port on graphics-core, or Jetpack Ink
  - [ ] 1.2 Canvas host: custom View inside Compose chrome
  - [ ] 1.3 Markdown: commonmark-java + own StaticLayout renderer
  - [ ] 1.4 Storage: all-files access + one directory per note, or SAF
  - [ ] 1.5 Project licence, which decides what code we may port
  - [ ] 1.6 Ink colours in dark mode: invert black ink, or keep true colours
- [ ] 2 Measure the TB-J616F: ABI list, density, pen axes (pressure, tilt, hover, barrel button, eraser tool type), front-buffer rendering — blocked: tablet connected over adb
- [ ] 3 Add an API 31 tablet image to android-emu so tests run on the tablet's Android version — blocked: user OK to change ~/dev/android-emu
- [ ] 4 Iterate the UI in the Claude Design canvas: https://claude.ai/artifact/EfhVLmtXRNi457wy83HBCP
- [ ] 5 Public open-source release prep — details: [2026-11-24-1551-public-release](todo/2026-11-24-1551-public-release.md)

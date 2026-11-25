# Notes can have a dot, grid or ruled background

- A "Background" button with a small grid icon takes the corner pill's hidden Layers slot (and sits in the phone's top bar), opening a panel: None, Dots, Grid or Lines with small previews, Small / Medium / Large, and a slider from 8 to 96 dp, all applied as you change them
- Dots and grid default to 28 dp apart, ruled lines to 45 dp (college ruled); new notes start with the last pattern chosen, none at first
- The canvas draws the pattern under everything, scaling with zoom: across the whole view on an infinite canvas, inside each page on pages, where ruled lines keep a one-line margin top and bottom. Marks closer than 6 px thin out to every other cell, and lines stay one crisp pixel
- Colours come from the design's dot and border tokens, in light and dark, and never go through the ink's dark-mode transform; library thumbnails show the pattern too
- Measured on the host (Robolectric's Skia raster, 2000 × 1200 px at the tablet's density) for a full-screen pattern at 100% / 20%: none 0.5 / 0.4 ms, ruled 0.5 / 0.5 ms, grid 0.8 / 1.6 ms, dots 2.4 / 3.2 ms; small dots are drawn square, which brought dots at 20% from 104 ms to 8 ms. `BackgroundSceneTimingTest` logs the same cases on a device's GPU
- The export dialog's "Dot grid" is now "Background pattern", shown only for notes that have one, and exports the note's own pattern and spacing in PNG, PDF and SVG (dots as an SVG `<pattern>`, lines as one `<path>` per page)
- `note.json` gains an optional `background` object at the end; format stays 1 and notes without one are written byte for byte as before
- Changing the background is saved but isn't an undo step
- Rationale: see ADR [2026-11-24-2244-note-background-patterns](../adr/2026-11-24-2244-note-background-patterns.md)

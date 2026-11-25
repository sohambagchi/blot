# Note background patterns

**Status:** accepted

**Context:** In place of a layers panel the user wants dot, grid or ruled backgrounds with a default size and some control over it, without snapping. Notes had no background beyond the paper colour, and exports had a fixed "Dot grid" switch. [2026-11-24-1928-note-json-format](2026-11-24-1928-note-json-format.md) lets new optional fields in without a format bump.
**Decision:**
- **Model:** `Note.background` is an optional `{"pattern": "dots"|"grid"|"ruled", "spacing": <canvas units>}`, the last key of `note.json`, left out for a plain background, so existing files re-encode byte for byte. `format` stays 1. A missing `spacing` reads as the pattern's default; a non-positive spacing or unknown pattern rejects the file.
- **Defaults:** dots and grid 28 units (the design's dot spacing), ruled 45 (college ruled, 9/32 in). The panel offers small, medium and large at 0.75×, 1× and 1.5× of the default, rounded to whole units, and a slider over 8–96. New notes start with the pattern last chosen in any note (kept in the app settings); the first default is none.
- **Not an undo step:** like the title, changing the background is saved but not undoable, so dragging the slider doesn't fill the history; undo and redo keep the current background.
- **Geometry:** `PatternMarks` in `:core` gives the marks that fall in a visible rectangle. Dots sit in cell centres, as the old export dot grid did. Cells start at the canvas origin, or at each page's corner on pages. A page leaves out grid lines on its edges, and its ruled lines start one spacing below the top and stop at least one spacing above the bottom.
- **Drawing:** `SceneRenderer` draws the pattern after the pages and before the elements, in canvas units, with one `drawPoints` or `drawLines` per page. Marks closer than 6 screen pixels are thinned to every 2nd, 4th… cell, anchored to the origin so they don't shift while panning; beyond 64 cells apart nothing is drawn. Lines snap to whole pixels, dots under 2.5 px are drawn square, and colours come from the design's `dot` and `border` tokens (slightly lifted in dark mode so they read on the dark page), never through the ink transform. The front buffer gets it only as part of the scene it re-renders; thumbnails draw it too, export previews don't.
- **UI:** the design's hidden Layers button in the corner pill becomes "Background", with a small grid icon, opening a panel styled as the Layers panel. The phone's top bar has the same button.
- **Export:** "Dot grid" becomes "Background pattern", shown only when the note has one. PNG and PDF draw it through the canvas's painter; SVG writes dots as one `<pattern>` fill per page and grid or ruled lines as one `<path>` per page.
**Alternatives:**
- A per-app background instead of per note: ruled paper suits some notes and not others.
- A background element in `elements`: it would move, select and erase like content.
- SVG lines as a `<pattern>` too: a tile can't leave out a page's edges or keep ruled margins, and its edge lines get cut in half.
- Fading the pattern out when zoomed out: thinning keeps the page readable as paper at any zoom.
**Consequences:**
- An older app ignores the field and drops it when it saves the note.
- Turning the background on or off can't be undone; choosing "None" is the way back.
- Exports drop the "Dot grid" option for notes without a pattern; a note that wants dots now picks them in the panel.

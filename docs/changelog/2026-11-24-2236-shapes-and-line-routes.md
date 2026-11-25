# Every shape kind, elbow connectors and curved arrows

- The shape menu draws what it shows: rounded rectangle, triangle, diamond, hexagon, parallelogram and cylinder join rectangle and ellipse, each shaped like its toolbar glyph
- Every shape fills, outlines, turns, scales, moves, duplicates and groups as rectangles do, and the square-frame option works for all of them
- Taps, the lasso and the rectangle select pick a shape by its outline, or its inside when filled, not by its frame
- Elbow connectors run across, down and across (or down, across and down for tall drags) with rounded corners and an arrowhead along the last segment; "Snap angles to 15°" makes nearly level or plumb ones straight
- Curved arrows follow the pen: the drag is simplified to a few points and drawn as a smooth curve, its arrowhead along the curve's end; a straight drag bows gently
- The canvas, drag previews, thumbnails, PNG, PDF and SVG draw the same outlines, in dark mode too; SVG writes new shapes and every line as `<path>`
- "Only rectangles and ellipses for now" and "Elbow and curved lines aren't available yet" are gone
- `note.json` format 2 holds the new kinds and line routes; notes without them are still written as format 1, byte for byte as before
- Rationale: see ADR [2026-11-24-2236-format-2-shape-kinds-and-line-routes](../adr/2026-11-24-2236-format-2-shape-kinds-and-line-routes.md)

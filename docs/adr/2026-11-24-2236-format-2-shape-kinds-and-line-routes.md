# Format 2: more shape kinds and line routes, written only when used

**Status:** accepted

**Context:** The toolbar offers eight shapes and five line kinds; `note.json` format 1 has only `rectangle` and `ellipse` and straight lines ([2026-11-24-1928-note-json-format](2026-11-24-1928-note-json-format.md)). A new enum value must bump `format`, because format-1 readers refuse unknown enum values, and a line route changes what `points` means.
**Decision:**
- `format` 2 adds:
  - `shape.kind`: `roundedRectangle`, `triangle`, `diamond`, `hexagon`, `parallelogram`, `cylinder`. Each is inscribed in `frame`, with proportions from the toolbar glyphs: corner radius 2/7 of the shorter side, at most 32; triangle apex at the top middle; diamond on the edge midpoints; hexagon with flat top and bottom inset by a quarter of the width; parallelogram's top edge shifted right by 2/9 of the width; cylinder caps as wide as the frame, each cap's vertical radius 5/28 of the width but at most a quarter of the height, the bottom cap's back half hidden. `rotation`, `fill` and `width` mean what they do for rectangles; outlines have mitred joins.
  - `line.route`: `elbow` or `curve`, left out for `straight`. An elbow is straight segments through `points` with corners rounded to max(12, 4 × width), each corner taking at most half of either segment. A curve is a centripetal Catmull–Rom spline through `points`, ends extended by mirrored phantom points. Arrowheads point along the last segment, or the curve's tangent at its end.
- A note is written with the lowest format that holds it: 2 only when it has one of the new kinds or a non-straight route, otherwise 1. Readers accept 1 and 2.
- The canvas draws an elbow from its drag as horizontal–vertical–horizontal (or vertical–horizontal–vertical when the drag is taller than wide) with the middle segment halfway; a curve keeps the drag's samples simplified by Ramer–Douglas–Peucker to at most 8 points, bowed by 12% of its length when the drag was straight.
**Alternatives:**
- Always write format 2: simpler, but a note with only rectangles would stop opening in a format-1 build for no reason.
- Store an elbow as its two ends and route it when drawing: turning it with the pointer would then re-route it level instead of turning it.
- Store a curve as Bézier control points: exact for other tools, but moving one point would no longer keep the curve smooth, and the file would carry three times the points.
- A generic `path` shape with SVG path data: any outline, but nothing to change kind or frame on later.
**Consequences:** A format-1 build refuses a note with a new shape or route, and the store falls back to `note.json.tmp` if that is older. Format-1 notes are written byte for byte as before. An elbow turned by the pointer keeps its right angles but is no longer level.

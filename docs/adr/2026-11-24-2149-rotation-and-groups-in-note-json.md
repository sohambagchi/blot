# Rotated text boxes and images, and groups, in note.json

**Status:** accepted

**Context:** The pointer turns any selection ([2026-11-24-2149-pen-buttons-pointer-and-eraser](2026-11-24-2149-pen-buttons-pointer-and-eraser.md)). Shapes already have `rotation`; text boxes and images don't, and strokes and lines can turn their points. Elements also need grouping. [2026-11-24-1928-note-json-format](2026-11-24-1928-note-json-format.md) allows new optional fields without a format bump.
**Decision:**
- `text` and `image` get an optional `rotation`: radians, clockwise on screen, about the frame's centre, as on `shape`. It is written only when non-zero, so unrotated notes are byte-identical to before.
- Every element gets an optional `group`: a string id, omitted when the element is in no group.
- `format` stays 1.
- Turning a stroke or line turns its points (and a stroke's pen orientation); shapes, text boxes and images move their frame's centre and add the turn to `rotation`, kept in (−π, π].
- Bounds, hit tests, the lasso, the canvas, thumbnails, PNG and PDF use the turned frame; SVG wraps the element in `<g transform="rotate(…)">`.
- Groups: selecting any member selects the group, and it moves, scales, rotates, duplicates, deletes and restacks as one. Members lie in one contiguous run of the stacking order: grouping gathers them where the topmost selected element was, and restacking steps past whole groups. Groups don't nest; grouping groups merges them into a new one. Duplicates get a new group id.
**Alternatives:**
- A `group` element holding its members: nests, but changes the element list's shape and needs a format bump.
- Baking a text box's turn into a transform matrix: shears and flips the app never makes.
**Consequences:** An older app reads a rotated text box or image unturned, and drops `rotation` and `group` when it saves the note. A turned text box keeps its top-left corner when its height changes after editing.

# Text boxes are edited in place as markdown source

**Status:** accepted

**Context:** Text boxes hold CommonMark ([2026-11-24-1742-markdown-commonmark-staticlayout](2026-11-24-1742-markdown-commonmark-staticlayout.md)). The canvas draws them through an injected `TextBoxRenderer`, and the markdown renderer is wired in separately.
**Decision:**
- A tap with the text tool on empty canvas makes a 320-unit-wide box; a drag sets the width. A tap on a box edits it.
- Editing happens in a plain `EditText` laid over the box at the box's position, width and font size, showing the markdown source. The canvas hides the box while it is edited.
- A touch anywhere else on the canvas, Back, a tool change or undo commits the edit as one undo step. A box left blank is removed; a new one is never added.
- On commit the frame's height becomes the renderer's measured height (`TextBoxRenderer.measureHeight`), so the box grows and shrinks to fit. A renderer that doesn't measure keeps the frame's height.
**Alternatives:**
- A dialog or bottom sheet: takes the text away from where it sits on the page.
- Live rendered markdown while typing (WYSIWYG): a rich-text editor of our own, out of proportion for now.
**Consequences:** While typing, the text shows as plain source in the platform font; the rendered markdown appears on commit, so line breaks can move. The new box isn't in the note until it is committed, so a crash mid-edit loses the typing.

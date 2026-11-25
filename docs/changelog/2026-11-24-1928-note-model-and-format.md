# Note model and note.json format

- `:core` has the document model in `app.screenscratch.core.model`. A `Note` has an id, title, created and modified times, a page setup (infinite, or Screen, A4 or Letter pages) and elements in stacking order: `Stroke`, `Shape`, `Line`, `TextBox` and `Image`. Each element has a stable id and axis-aligned bounds.
- Bring to front, Bring forward, Send backward and Send to back work on a selection of ids and keep the selection's relative order. Forward and backward step past the next element that overlaps the selection, not one index.
- `NoteJson` reads and writes `note.json`. It ignores unknown fields and refuses newer formats. Stroke points are stored as base64 delta varints (`PointCodec`), 12× smaller than plain JSON on the tablet's strokes: about 8.6 bytes per point.
- Dependency added: `kotlinx-serialization-json` 1.13.0 and the Kotlin serialization plugin.
- Tests cover JSON round trips of every element type, the point codec's error bounds and malformed input, encoded size against real tablet strokes, stacking edge cases and unknown fields.
- Rationale: see ADR [2026-11-24-1928-note-json-format](../adr/2026-11-24-1928-note-json-format.md)

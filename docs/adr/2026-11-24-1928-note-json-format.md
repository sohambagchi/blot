# note.json format

**Status:** accepted

**Context:** Each note is a directory with `note.json` and `assets/` ([2026-11-24-1742-storage-saf-directory-per-note](2026-11-24-1742-storage-saf-directory-per-note.md)). The format must be compact for strokes, readable for everything else, and specified well enough for another reader. Code: `app.screenscratch.core.model` and `.model.format` in `:core`.

**Decision:**
- **File:** one UTF-8 JSON object, 2-space indented. Keys:
  - `format`: integer, first key, currently 1 (`FORMAT_VERSION`). Readers refuse a newer format.
  - `id` (string), `title` (string, may be empty), `created` and `modified` (ISO-8601 UTC strings).
  - `page`: `{"type": "infinite"}`, or `{"type": "pages", "paper": "screen"|"a4"|"letter", "width", "height", "count"}`. Page `i` has its top-left corner at `(0, i × (height + 24))`. `width` and `height` are authoritative: A4 is 1322.83 × 1870.87, Letter 1360 × 1760 (portrait), and a screen page is the screen's size in dp when the note was made.
  - `elements`: array in stacking order, back first. Each has `type` and a unique `id`.
- **Units:** canvas units, 1 dp (1/160 inch) at 100% zoom, with y pointing down. Angles are in radians, clockwise on screen. Colours are `"#AARRGGBB"` true sRGB. Dark mode transforms them at draw time and never writes them back.
- **Elements** (`Rect` is `{"x", "y", "width", "height"}`, top-left origin):
  - `stroke`: `points` (blob below), `color`, `width` (nominal diameter), `pen` (`pen`|`pencil`|`highlighter`), `pressureReported` (false means the stroke engine simulates pressure).
  - `shape`: `kind` (`rectangle`|`ellipse`), `frame`, `color`, `width` (outline; 0 for none), optional `fill`, `rotation` about the frame centre.
  - `line`: `points` (`[{"x", "y"}, …]`, at least 2), `color`, `width`, `startArrow` and `endArrow` (`none`|`arrow`|`triangle`). An arrowhead reaches at most max(8, 4 × width) from its end point.
  - `text`: `frame`, `markdown` (CommonMark), `fontSize`, `color`.
  - `image`: `asset` (`{"sha256", "extension"}`, which is the file `assets/<sha256>.<extension>`; lower-case hex and extension), `frame`.
- **Stroke points:** base64 (RFC 4648, padded) of unsigned LEB128 varints:
  - A channel mask (bit 0 time, 1 x, 2 y, 3 pressure, 4 tilt, 5 orientation), then the point count.
  - Then, per point and per channel in bit order, the zig-zag delta (`(n << 1) ^ (n >> 63)`) from the previous point's quantised value. The first point's delta is from 0.
  - A channel is left out exactly when all its values are 0, and it then decodes as 0. Unknown mask bits and trailing bytes are errors.
- **Quantisation** (the decoded error is at most half a step):
  - x, y: 1/16 canvas unit. The TB-J616F pen resolves about 1 px, which is 0.78 dp, so 1/16 stays below the pen's resolution up to about 12× zoom. Measured on the tablet's strokes, 1/32 would cost 9% more bytes and 1/8 would save 5%.
  - Pressure: 0–1 as 0–4095. That is 12 bits, the pen's own 4096 levels, so it is lossless on this pen.
  - Tilt (0–π/2) and orientation (−π–π, 0 pointing up as in Android's `AXIS_ORIENTATION`): 1/4096 turn, about 0.088°. This costs nearly nothing over 1/1024 turn, since the deltas stay within one varint byte.
  - Time: whole milliseconds since the stroke's first sample, which is what the pen reports.
- **Compatibility:** readers ignore unknown keys. New optional fields keep `format`. A new element type, enum value or required field, or a changed meaning or encoding, bumps it.
- **Validation:** a missing or newer `format`, unknown `type` or enum value, malformed blob, colour, instant or asset name, or a duplicate element id rejects the whole file with `NoteFormatException`. The store then falls back to `note.json.tmp`.

**Alternatives:**
- Points as JSON arrays: 12× larger on the tablet's strokes (~8.6 base64 bytes per point vs ~107).
- Protobuf or CBOR for the whole file: smaller still, but not readable or hand-editable, and it adds a dependency.
- Absolute coordinates without deltas: about 3 bytes per coordinate instead of 1–2.
- Colours as signed ints: compact but unreadable.
- Device pixels as the unit: depends on the device, so notes would change size between the tablet and the phone.

**Consequences:**
- Coordinates and pressure snap to the grid on save. Encoding is idempotent, so repeated saves don't drift.
- An older app opening a note with newer optional fields drops them when it saves.
- The canvas, stroke engine, exporters and `NoteStore` share one coordinate space and one file reader.

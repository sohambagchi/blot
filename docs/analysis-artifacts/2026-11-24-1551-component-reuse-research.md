# Component reuse research

**Question:** Which existing open-source components can we reuse for the pen-notes app without compromising requirements or pulling in unneeded features, and where should we write our own (citing the source we learned from)?

**Answer (proposed, pending review):** Reuse a few small platform libraries: graphics-core, input-motionprediction, commonmark-java and kotlinx-serialization. Write our own stroke engine, eraser, selection, markdown renderer, SVG writer, colour wheel and storage layer. These are each a few hundred lines, and the best references are MIT/Apache code we can port with a citation. Keep Jetpack Ink as a fallback if our strokes look worse than its `pressurePen`.

Checked 2026-11-24. Items marked *unverified* still need checking on the real TB-J616F.

## Adopt as dependencies

| Component | Version / licence | Why it's worth having | Cost |
|---|---|---|---|
| `androidx.graphics:graphics-core` | 1.0.4, Apache-2.0 | Front-buffered low-latency drawing (`CanvasFrontBufferedRenderer`, `LowLatencyCanvasView`). Needs API 29+, so it works on API 31 | ~0.25 MB `.so` per ABI |
| `androidx.input:input-motionprediction` | 1.0.1, Apache-2.0 | Stroke-tip prediction. Uses a built-in Kalman predictor on API 31 and the system predictor on 34+ | ~35 KB, pure Java |
| `org.commonmark:commonmark` + GFM extensions | 0.33.0, BSD-2 | Markdown parser (AST only) | 217 KB, no dependencies |
| `kotlinx-serialization-json` | 1.13.0, Apache-2.0 | Note metadata | ~300 KB |
| `androidx.activity` `PickVisualMedia`, Compose `dragAndDropTarget` / `contentReceiver`, `FileProvider` | platform / AndroidX | Image import, paste, drop and share | already in the app |

## Write our own (cite the reference in code comments)

| Piece | Reference to port or study (licence) | Notes |
|---|---|---|
| Stroke outline (pressure → polygon) | [perfect-freehand](https://github.com/steveruizok/perfect-freehand) (MIT), ~300 lines | Simulates pressure from speed when the device reports none |
| Partial and whole-stroke eraser | [xnotes](https://github.com/shardulvs/xnotes-android) `EraseSession.kt` (MIT) | Split the stroke's point list where the eraser hits. Keeps storage as plain points |
| Lasso selection, move/scale | Excalidraw `lasso/` (MIT), xnotes `SelectionMath.kt` (MIT) | Point-in-polygon tests over bounding boxes |
| Shapes, lines, arrows | Excalidraw `linearElementEditor.ts`, `binding.ts` (MIT) | Arrow binding to shapes can come later |
| Infinite canvas camera and page layouts | xnotes `core/infinite/` (MIT). Rnote `layout.rs` / `camera.rs` (GPL: approach only) | |
| Spatial index | [RBush](https://github.com/mourner/rbush) (MIT), or a uniform tile grid | Probably a tile grid first: simpler, and it suits an infinite canvas |
| Undo/redo | Excalidraw `history.ts` (MIT): delta-based | |
| Markdown renderer | commonmark AST → `SpannableStringBuilder` → `StaticLayout.draw(canvas)`. Markwon's span approach (Apache-2.0, unmaintained since 2021) | One renderer for screen, PNG and PDF. Tables can wait |
| SVG export | xnotes `Svg.kt` (MIT) | Serialise the model directly. Text as `<text>`/`<tspan>` per layout line, with an optional outline-text mode. No `foreignObject` |
| PDF / PNG export | Platform `PdfDocument` (stays vector through Skia) / `Bitmap` | Erasing must change geometry: Skia rasterises `PorterDuff.CLEAR` blending |
| HSV colour wheel | — | ~200 lines with `sweepGradient` + `radialGradient`, so no dependency |
| Storage layer | xnotes, Markor (Apache-2.0) for folder handling | See storage below |

## Considered and rejected (for now)

- **Jetpack Ink 1.0** (Apache-2.0, minSdk 23).
  - What it offers: good brushes (`pressurePen`, `highlighter`), geometry for lasso and whole-stroke erase, and low latency on API 29–32 through graphics-core.
  - Why not:
    - It adds ~1.5 MB of native code per ABI.
    - Storage would be tied to its protobuf format.
    - Partial erase is only in the experimental 1.1 alphas.
    - On API < 34 (the tablet) it renders finished strokes with `Path` anyway.
  - It stays the fallback if our strokes look worse.
- **Markwon / compose-markdown:** unmaintained, or tied to TextView. **mikepenz renderer / compose-richtext:** Compose only, so no vector PDF or SVG.
- **skydoves colorpicker-compose** (1.3.0, Apache-2.0, 148 KB) is acceptable but unnecessary. **godaddy picker** is stale.
- **davidmoten rtree2 / JTS:** too large and too general for our hit-testing.
- **Copyleft apps** (Notable, Saber, Rnote, Fossify: GPL-3.0; Xournal++: GPL-2.0; Stylus Labs Write: AGPL-3.0): read them for approach, don't copy code, unless the project itself becomes GPL. **tldraw** is source-available only, so copy nothing from it.

## Storage and file format

- **Folder access:** `MANAGE_EXTERNAL_STORAGE` with plain `java.io.File` on a user-chosen folder (e.g. `Documents/ScreenScratch`). It's fast, simple and visible to Syncthing. Put it behind a `NoteStore` interface so a Storage Access Framework (SAF) implementation can be added later.
- **SAF:** it works, but `DocumentFile` makes one IPC call per getter, so it's slow once there are hundreds of notes. App-specific external storage was ruled out: Syncthing can't see it and it's deleted on uninstall.
- **Note on disk:** one directory per note: a single mutable `note.json` plus `assets/` named by content hash. Each save changes one file, which syncs cleanly. Write atomically (temp file + rename). SQLite is fine only as a rebuildable index.
- **Point encoding:** JSON for metadata, and each stroke's points as a base64 blob of delta-encoded, quantised varints (x, y, pressure, t). That's roughly 5× smaller than naive JSON and still easy to debug.
- **Interchange:** Xournal++ `.xopp` (paged handwriting) and Excalidraw JSON (shapes and arrows) are good *export* targets later. Neither fits as the primary format.

## Device facts to confirm on the TB-J616F

- `getprop ro.product.cpu.abilist`: is the userspace 64-bit? This decides which ABIs we ship.
- `wm density`: the emulator assumes 240 (2000×1200 → 1333×800 dp).
- `getevent -lp` plus a MotionEvent axis logger. We need:
  - the real pressure resolution (Lenovo says 4096 levels)
  - whether `AXIS_TILT` and `AXIS_DISTANCE` (hover) are reported
  - whether the barrel button sends `BUTTON_STYLUS_PRIMARY`
  - whether the pen reports `TOOL_TYPE_ERASER`
- Whether front-buffer rendering works on the MediaTek GPU.
- Palm rejection on Android 12: `FLAG_CANCELED` only exists from API 33. On the tablet we'll ink only for `TOOL_TYPE_STYLUS`, use fingers for pan and zoom, and drop the stroke on `ACTION_CANCEL`.

## Sources

The research covered xnotes, Jetpack Ink and Cahier, Excalidraw, perfect-freehand, Rnote, Xournal++, Saber, Notable, Stylus Labs Write, Markor, commonmark-java, Markwon, AndroidX release notes, AOSP `PdfDocument.cpp` and the Play all-files-access policy. URLs are inline above where we would port code.

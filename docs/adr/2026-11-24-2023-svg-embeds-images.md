# SVG embeds images as data URIs

**Status:** accepted

**Context:** Image elements and markdown images refer to files in the note's `assets/`. An SVG can either embed them or link to them.
**Decision:** Embed each asset's original bytes, unchanged, as a base64 `data:` URI in `xlink:href`. The MIME type comes from the file extension. Text stays `<text>`/`<tspan>`, with each run's `textLength` set to the width the app measured. No `foreignObject` is used.
**Alternatives:** Link to `assets/<hash>.<ext>`, which gives a smaller file but breaks as soon as the SVG is shared on its own. Re-encode images as PNG, which loses JPEG compression and gains nothing.
**Consequences:** SVGs are self-contained but about a third larger than their images. Assets in formats that browsers don't render (e.g. HEIC) won't show. Text stays selectable, but a viewer without Roboto substitutes another font, and `textLength` keeps line widths as laid out.

# Export pages and bounds

**Status:** accepted

**Context:** A note is either an infinite canvas or a stack of pages. Each export format must choose what one file holds and how big it is.
**Decision:**
- **PDF:** one PDF page per note page, sized in points (canvas units × 72/160), so A4 comes out as 595 × 842. An infinite canvas, a visible area or a selection becomes a single page sized to its content. That page is scaled down if a side would pass 14,400 pt, the largest page PDF viewers accept.
- **PNG and SVG:** a single file. Page notes keep their pages stacked as in the editor, with the 24 dp gap between them in the desk colour (or transparent). A page range picks the pages to include. PNG is downscaled when the image would exceed 2²⁵ pixels.
- An infinite canvas exports its content bounds plus a 24 dp margin. A selection exports only the selected elements, framed the same way. A visible area exports exactly that rectangle. Elements are clipped to each page.
**Alternatives:** Tile an infinite canvas onto A4 pages, which splits drawings at arbitrary lines; a vector page of any size zooms instead. Export one PNG per page, which needs several files, `ACTION_SEND_MULTIPLE` and a folder picker instead of one `CreateDocument`; choosing a one-page range gives a single page instead.
**Consequences:** Every export is one file, so share and save behave the same for all formats. Long page notes give tall PNGs at a reduced scale. Printing an infinite canvas needs the viewer's fit-to-page.

# Markdown renderer for text boxes

- `app.screenscratch.markdown.MarkdownLayout` parses markdown with commonmark and lays it out with `StaticLayout` at a given width, theme and density. It caches the layout until an input changes.
- It renders headings, emphasis, strong, strikethrough, inline code, fenced and indented code blocks, block quotes, nested bullet, ordered and task lists, links, thematic breaks, line breaks and images. Tables are not supported yet.
- Images `![alt](assets/<hash>.<ext>)` come from an injected `MarkdownImageLoader` and are scaled to the box width. If the loader returns null, a placeholder shows the alt text.
- Spans only shape the text. Bars, bullets, checkboxes, backgrounds, rules and images are computed once as a list of `MarkdownDecoration`s. `draw(canvas)` paints that list and then the text, so the same code serves screen, PNG and PDF. SVG export can read `lines` (baseline, left edge, styled runs) together with `decorations`.
- `MarkdownTheme.Light` and `MarkdownTheme.Dark` follow the app palette.
- Soft line breaks render as spaces, as CommonMark specifies. A visible line break needs two trailing spaces or a blank line.
- Dependencies added: `org.commonmark:commonmark` 0.33.0 (217 KB), `commonmark-ext-gfm-strikethrough` 0.33.0 (13 KB) and `commonmark-ext-task-list-items` 0.33.0 (12 KB), all BSD-2 with no other dependencies.
- Tests: Robolectric tests with native graphics cover each construct's spans, nested list indents, image placeholders, height growth, layout caching and run styles. Another test renders a sample note to `app/build/markdown/sample-light.png` and `sample-dark.png` and checks its pixels. A recorded `Picture` replays pixel-identical to a direct draw. Robolectric has no `PdfDocument`, so `MarkdownPdfTest` (instrumented) covers the PDF page and the hardware canvas. It compiles but has not yet been run on a device.

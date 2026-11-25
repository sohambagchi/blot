# Markdown via commonmark-java and StaticLayout

**Status:** accepted

**Context:** Text boxes render markdown on screen and in PNG, PDF and SVG exports.
**Decision:** Parse with `org.commonmark:commonmark` (+ GFM extensions). Our renderer turns the AST into a `SpannableStringBuilder` drawn with `StaticLayout`, used for screen, PNG and PDF. SVG export writes each laid-out line as `<text>`. Images are `![](assets/<hash>.<ext>)`, resolved against the note's `assets/`; image import and paste insert such a text box.
**Alternatives:** Compose renderers (mikepenz, compose-richtext) — screen only, no vector PDF or SVG. Markwon — unmaintained since 2021 and tied to `TextView`.
**Consequences:** We maintain the span mapping. Tables come later.

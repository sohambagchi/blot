# Text-box line breaks follow CommonMark

**Status:** accepted

**Context:** In a text box, a single Enter could either start a new line or join the lines, as CommonMark specifies.
**Decision:** Keep CommonMark: a single newline renders as a space, a blank line starts a new paragraph, and a hard break (two trailing spaces or a backslash) breaks the line.
**Alternatives:** Treat every newline as a hard break — closer to plain-text editing, but text boxes would render differently from the same markdown elsewhere.
**Consequences:** The markdown renderer needs no change, and text boxes export to other markdown tools unchanged.

# Thumbnails live outside the library index

**Status:** accepted

**Context:** [2026-11-24-1742-storage-saf-directory-per-note](2026-11-24-1742-storage-saf-directory-per-note.md) has the library index cache thumbnails. The index built in `:core` holds titles and times only, and the card also needs the page kind and count, which the index doesn't know.
**Decision:** Thumbnails are PNGs in the app's cache directory, keyed by note id, the note's modified time and the theme, with the page kind and count beside them. A save draws the new one; the library draws missing ones in the background from `note.json`. The notes folder never holds thumbnails.
**Alternatives:** Thumbnails inside the index file — one large JSON rewritten on every save. Thumbnails in each note's folder — synced to other devices and visible to the user.
**Consequences:** Clearing the cache costs one read and render per note. Each theme has its own thumbnail, drawn when first needed.

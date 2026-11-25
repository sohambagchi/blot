# Library folders are subdirectories of the notes folder

**Status:** accepted

**Context:** The library's design has folders with counts. Notes live one directory each under the user's folder ([2026-11-24-1742-storage-saf-directory-per-note](2026-11-24-1742-storage-saf-directory-per-note.md)), named by id ([2026-11-24-1934-note-directory-named-by-id](2026-11-24-1934-note-directory-named-by-id.md)), and the folder is meant to read sensibly in a file manager or sync tool.
**Decision:**
- A folder is a subdirectory of the notes folder, named exactly as the user named it (trimmed). Names must be non-empty, free of `/ \ : * ? " < > |` and control characters, not start or end with a dot, fit in 255 bytes, and differ from other folders ignoring case.
- One level only. A top-level directory with `note.json` or `note.json.tmp` is a note; any other top-level directory (not starting with `.`) is a folder. Inside a folder, only note directories count; anything else is left alone.
- A note directory keeps its id as its name wherever it lives, so ids, thumbnails and the index survive a move. The store finds a note by id across the root and every folder.
- Deleting a folder moves its notes to the top level first, then removes the folder only if nothing but hidden files is left. It never deletes a note.
- Moving uses `DocumentsContract.moveDocument` when the provider flags `FLAG_SUPPORTS_MOVE`. Otherwise the store copies the note into a hidden `.<id>.moving`, checks names and sizes against the original, renames the original to a hidden `.<id>.moved`, renames the copy to `<id>`, then deletes the original. The next listing settles any interrupted step: with the note visible, the hidden leftovers go; without it, the checked copy, or else the original, takes the name back.
- The library index keys entries by path (`<id>` or `<folder>/<id>`). Each refresh lists the root once and each folder once, and reads only note directories whose time changed.
**Alternatives:**
- Folders as a tag in `note.json` or the index: nothing to move, but invisible in a file manager and lost with the index.
- Nested folders: more listings per refresh and a tree in the UI, for little gain in a notes app.
- Deleting a folder with its notes: one tap from losing work.
- Copy then delete without the hidden names: an interruption could leave two visible copies of one id, or a half-deleted original next to the only full copy.
**Consequences:** Any directory a user makes beside the notes shows up as a folder. A folder's own time says nothing about the notes inside it, so every refresh lists every folder. A case-only rename goes through a temporary name, since FAT and Android's emulated storage ignore case.

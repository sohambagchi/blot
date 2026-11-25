# Notes stored through SAF, one directory per note

**Status:** accepted

**Context:** Notes must live in a user-visible folder on device storage.
**Decision:**
- The user picks a root folder with `ACTION_OPEN_DOCUMENT_TREE`; the app keeps the persisted URI permission.
- Each note is a directory holding `note.json` and `assets/` (files named by content hash).
- Stroke points are delta-encoded, quantised varints, base64 inside `note.json`.
- Directory listings use one `DocumentsContract` child-documents query each, never per-file `DocumentFile` getters. An app-private library index caches titles, thumbnails and modified times, and is rebuilt from the folder.
- Saves write `note.json.tmp`, then replace `note.json`. SAF has no atomic rename-over, so loading falls back to `note.json.tmp` when `note.json` is missing or unreadable.
- All access goes through a `NoteStore` interface.
**Alternatives:** `MANAGE_EXTERNAL_STORAGE` with `java.io.File` — faster and simpler, but Play rejects it for note apps and it grants far more access than needed. App-specific external storage — invisible to Syncthing, deleted on uninstall.
**Consequences:** No broad storage permission. Every file operation is IPC to the documents provider, so the library depends on the index for speed.

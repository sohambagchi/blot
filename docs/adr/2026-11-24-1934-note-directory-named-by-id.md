# Note directories are named by a stable id, not the title

**Status:** accepted

**Context:** Each note is a directory under the user's folder ([2026-11-24-1742-storage-saf-directory-per-note](2026-11-24-1742-storage-saf-directory-per-note.md)). Links between notes, the library index and open editors all need a reference that survives the note being renamed. Over SAF, renaming a directory changes its document id, and two devices syncing through Syncthing can create notes at the same moment.
**Decision:**
- A note's id is its directory name: UTC creation time plus 32 random bits, e.g. `20261124-193412-9f3c01ab`. Directories are never renamed, so the id never changes.
- The title lives only in `note.json` and in the app-private library index.
- The app treats any directory under the root holding a `note.json` or `note.json.tmp` as a note, whatever its name. Names starting with `.` are skipped (Syncthing's `.stfolder`, `.stversions`).
**Alternatives:** Title-derived names (`Meeting notes/`) are friendlier in a file manager. But a rename would move the directory and break every reference, titles collide, and they need escaping for FAT. A title slug after the id goes stale on rename. A plain UUID is unique too, but doesn't sort by creation date.
**Consequences:** Browsing the folder outside the app shows ids, not titles. Every operation finds a note directory by name in the latest root listing, because SAF document ids are provider-specific and can't be built from a path.

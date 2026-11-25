# A parseable note.json wins over note.json.tmp

**Status:** accepted

**Context:** SAF can't rename over an existing file, so a save writes `note.json.tmp`, deletes `note.json`, then renames the tmp ([2026-11-24-1742-storage-saf-directory-per-note](2026-11-24-1742-storage-saf-directory-per-note.md)). After a crash both files may exist. Syncthing also syncs `*.tmp` files, so a tmp can arrive from another device next to a newer `note.json`.
**Decision:**
- On read, use `note.json` if it exists and the caller's parser accepts it. Otherwise try `note.json.tmp`. The caller signals "unreadable" by throwing from the parse function it passes to `readNote`.
- `readNote` returns whether the backup was used, and never repairs the folder by itself. The next save overwrites the tmp and replaces `note.json`.
- Assets get the same treatment: written as `<hash>.<ext>.tmp`, then renamed. A file under its final hash name is always complete.
**Alternatives:**
- Prefer a parseable tmp, since it's usually the newer copy. That's wrong when the tmp is stale or came in through sync.
- Compare modified times. That depends on clocks across devices.
- Repair on read. That writes from what should be a read-only call, and may destroy a `note.json` that only failed because it comes from a newer app version.
**Consequences:** A crash between writing the tmp and deleting `note.json` loses that one save: the previous version loads. The window is one provider call. The parser must also throw for a `note.json` written by a newer `FORMAT_VERSION`; if the backup parses, the app then shows older content. Saving that would overwrite the newer note, so the caller has to handle that case.

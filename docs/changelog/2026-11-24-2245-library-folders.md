# Folders in the library

- The library's left column lists real folders, subdirectories of the notes folder, with note counts; "All notes" shows every note, and selecting a folder shows only its notes
- "New folder" asks for a name and says why one is refused (taken, a `/` or other character FAT rejects, a leading dot, too long); the new folder is selected
- A folder's menu (the kebab on the selected folder, or a long press on any folder; a long press on a chip on phones) renames or deletes it; deleting asks first, moves its notes to the top level and never deletes a note
- A note card's menu has "Move to…", listing the top level and the other folders
- New notes are created in the selected folder; the editor's corner pill names the note's folder, or the notes folder for top-level notes
- Moves keep the note's id, so thumbnails, search, rename, export and delete carry on; the provider moves when it can, otherwise the note is copied, checked and only then removed, and an interrupted move is settled on the next listing
- The library index lists the root once and each folder once per refresh, and reads only notes that changed
- Rationale: see ADR [2026-11-24-2228-folders-are-subdirectories](../adr/2026-11-24-2228-folders-are-subdirectories.md)

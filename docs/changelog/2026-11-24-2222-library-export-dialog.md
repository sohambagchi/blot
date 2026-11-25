# Exporting from the library uses the editor's export dialog

- A note card's menu is Rename, Export…, Delete; Export… opens the same export and share dialog as the editor's export button, with a preview of the note
- The dialog offers format, scale, background, light colours and dot grid; the area choice is hidden because the library exports the whole note
- `ExportSheet` is the shared dialog; the editor's `ExportDialog` and the library both call it

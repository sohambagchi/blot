# Notes have titles, and ink stays behind the chrome

- Tapping the title in the editor's back pill, or the phone's top bar, edits it in place: Done, Enter or a tap elsewhere keeps it; Back or Escape cancels
- A new note opens with the cursor in the empty title ("Untitled" as placeholder); writing on the canvas right away leaves it untitled
- The library card menu has Rename, opening a small dialog; the card, the index and search follow
- A title change is saved by autosave and moves the modified time like any edit, but is not an undo step
- Exports are named after the title; an untitled note exports as "Blot" and its creation time, e.g. "Blot 2026-11-24 2105.png", and characters file systems reject are replaced
- A stroke across the colour panel, a menu, the toolbar, a pill, the page chips, the message bar or the export dialog no longer draws over it while the pen is down, and doesn't flash over it after pen-up; whole-note front-buffer mode shows the chrome while writing too
- A pen-down in a floating surface's padding goes to the chrome instead of starting a stroke underneath
- `InkOverChromeTest` reads the screen back mid-stroke on the Android 12 emulator: no ink inside the open colour panel in either front-buffer mode, and ink over it when the exclusions are removed
- Rationale: see ADR [2026-11-24-2145-ink-clipped-out-of-the-chrome](../adr/2026-11-24-2145-ink-clipped-out-of-the-chrome.md)

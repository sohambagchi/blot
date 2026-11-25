# Backend decisions settled; tablet emulator on Android 12

- Licence, stroke engine, canvas host, markdown, storage, dark-mode ink, stacking order and input policy are decided; implementation tasks are on the TODO list.
- `LICENSE` (MIT) added.
- `emu-tablet` boots `tab-31`, Android 12 like the TB-J616F; `EMU_API=35` gives the API 35 variant. `emu-up` still defaults to `emu-35`.
- The dev shell's emulator SDK carries API 35 and 31 images, built through android-emu's new `lib.mkEmu`.
- Rationale: see ADRs [2026-11-24-1742-mit-licence](../adr/2026-11-24-1742-mit-licence.md), [2026-11-24-1742-own-stroke-engine](../adr/2026-11-24-1742-own-stroke-engine.md), [2026-11-24-1742-canvas-custom-view-in-compose](../adr/2026-11-24-1742-canvas-custom-view-in-compose.md), [2026-11-24-1742-markdown-commonmark-staticlayout](../adr/2026-11-24-1742-markdown-commonmark-staticlayout.md), [2026-11-24-1742-storage-saf-directory-per-note](../adr/2026-11-24-1742-storage-saf-directory-per-note.md), [2026-11-24-1742-dark-mode-ink-invert-and-lighten](../adr/2026-11-24-1742-dark-mode-ink-invert-and-lighten.md), [2026-11-24-1742-z-order-instead-of-layers](../adr/2026-11-24-1742-z-order-instead-of-layers.md), [2026-11-24-1742-pen-only-inking-setting](../adr/2026-11-24-1742-pen-only-inking-setting.md)

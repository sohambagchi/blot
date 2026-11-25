# Pen input controls: side buttons and the "Pen only" toggle

**Status:** accepted

**Context:** The TB-J616F's pen has two side buttons (`BUTTON_STYLUS_PRIMARY`, `BUTTON_STYLUS_SECONDARY`) and no eraser end. Android 12 can't flag palm touches. The design canvas puts the finger toggle in the colour panel. Supersedes [2026-11-24-1742-pen-only-inking-setting](2026-11-24-1742-pen-only-inking-setting.md).
**Decision:**
- One side button cycles the pen's tool: pen → pan → select → pen. The toolbar shows the current one.
- The other side button erases while it's held, then returns to the previous tool.
- Primary erases and secondary cycles. A setting swaps them.
- "Pen only" is a toggle in the colour panel. When on, only `TOOL_TYPE_STYLUS` draws, fingers pan and zoom, and `ACTION_CANCEL` drops the stroke. It defaults to on when an input device reports `SOURCE_STYLUS` (the tablet) and off otherwise (the phone).
**Alternatives:** Toggle-style eraser button — easy to forget it's on. Finger toggle only in settings — too far away for switching mid-session.
**Consequences:** Button state is read from `MotionEvent.buttonState` on every sample. A press while hovering, as well as while touching, counts. The settings screen keeps only the button swap and the theme.

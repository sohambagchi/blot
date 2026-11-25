# Pen buttons: one switches to the pointer, one erases while held

**Status:** accepted

**Context:** The TB-J616F's pen has two side buttons and no eraser end. Cycling pen → pan → select with one of them ([2026-11-24-1912-pen-input-controls](2026-11-24-1912-pen-input-controls.md)) made the user count presses, and selecting was split between a pen mode and the toolbar's select button. Supersedes that ADR.
**Decision:**
- One pointer (select) tool, with Lasso and Rectangle as its options. The pen tool's modes are Pen and Hand.
- Secondary button pressed, hovering or touching: the tool becomes the pointer and stays it after release, until the user picks another tool. A press mid-stroke takes effect at the next pen-down.
- Primary button held: erases, by the eraser's mode, while held; release returns to the tool. "Swap side buttons" swaps the two roles.
- "Pen only" on: the buttons as above, and fingers only navigate (one finger pans, two pinch); fingers never draw or select. Off: one finger uses the current tool, two pan and zoom.
- Only the pointer moves, scales or rotates a selection: drag inside to move, a corner handle scales uniformly, one rotate handle above the box turns it about its centre. Readouts beside the handle show the turn ("+15°", "−7°") or the scale ("1x", "0.95x", at most two decimals). "Snap angles to 15°" snaps the resulting angle. Each gesture is one undo step. Leaving the pointer clears the selection.
- A lone shape, text box or image is selected by its own turned frame; any other selection by its bounds.
- Holding still (400 ms, within touch slop) on the selection, pen or finger, opens a menu of bring forward, send backward, group or ungroup, duplicate and delete. A tap elsewhere closes it.
**Alternatives:**
- Keep cycling with a toast for the mode: still needs counting, and pan is a finger's job.
- Pointer only while the button is held, like the eraser: a lasso then needs the button held through the whole drag.
- A rotate handle per corner: four more targets near the scale handles, easy to grab the wrong one with a pen.
**Consequences:** The toolbar shows the pointer as active after a button press. The handles, readouts and menu are fixed sizes on screen. With "Pen only" on, a finger long press on the selection still opens the menu, but a finger drag pans rather than moves it.

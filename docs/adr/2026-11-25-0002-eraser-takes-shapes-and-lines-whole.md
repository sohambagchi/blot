# The eraser takes shapes and lines whole

**Status:** accepted

**Context:** Both eraser modes left shapes and lines alone ([2026-11-24-2037-partial-eraser-splits-point-lists](2026-11-24-2037-partial-eraser-splits-point-lists.md)); a pen user expects the eraser to remove whatever ink it touches.
**Decision:** In both modes, a shape or line the eraser's capsule touches is removed whole: a shape at its outline, or anywhere inside when filled; a line along its drawn path (elbows and curves included). Strokes still erase whole or in part by mode. Text boxes and images are never erased. The test is `Selection.inkTouches`, beside the tap test it shares geometry with.
**Alternatives:** Cutting shapes and lines in part mode — a cut rectangle is no longer a rectangle, and the note format has no partial shapes.
**Consequences:** Erasing across a diagram can take a whole shape where it used to leave it; one undo step brings it back, as for strokes.

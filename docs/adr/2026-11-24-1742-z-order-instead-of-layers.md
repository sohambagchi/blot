# Stacking order instead of layers

**Status:** accepted

**Context:** Layers were an open question; overlapping strokes, shapes, images and text still need an order.
**Decision:** Elements are kept in a stacking order with Bring forward, Send backward, Bring to front and Send to back on the selection. No layer panel.
**Alternatives:** Full layers (visibility, locking, panel) — UI weight for a feature not yet needed.
**Consequences:** The format stores elements in z-order; layers can be added later as a grouping on top of it.

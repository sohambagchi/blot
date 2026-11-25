# Lasso selects what it encloses or touches

**Status:** accepted

**Context:** The select tool's lasso and rectangle need a membership rule, and a tap needs a hit test. Bounds alone select strokes the lasso never came near.
**Decision:**
- An element is selected when part of what it draws lies inside the loop (even-odd), or comes within its ink's reach of the loop's line. What it draws is a stroke's sample path, a shape's or line's centre line, or a text box's or image's frame. The rule is xnotes' `SelectionMath` (MIT). Bounds are only a pre-filter, as in Excalidraw's lasso.
- A rectangle selection uses the same rule with the rectangle as the loop.
- A tap picks the topmost element within 6 dp of its ink: a stroke's filled outline, a shape's or line's outline, the inside of a filled shape, or a text box's or image's frame.
- A loop drawn inside a large box or text frame doesn't select the box.
**Alternatives:**
- Select only elements wholly inside the loop (Excalidraw's "contain" mode): a stroke the loop clips by a pixel is left out, which is fiddly with a pen.
- Select by the share of samples inside: needs a threshold, and still drops strokes the loop crosses.
**Consequences:** A loop that grazes a neighbouring word selects it, and there is no way yet to take one element out of a selection; the loop has to be redrawn.

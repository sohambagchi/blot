# Dark mode inverts and lightens ink at draw time

**Status:** accepted

**Context:** Black ink is invisible on a dark page; saturated red, blue and green lose contrast.
**Decision:** Notes store true colours. In dark mode the page is dark, near-black ink draws as near-white (and near-white as near-black), and chromatic colours are lightened for contrast. Imported images are never altered. Exports use stored colours on a light page unless a dark export is chosen.
**Alternatives:** Keep true colours — forces a light page in dark mode.
**Consequences:** One colour transform used by the canvas and by dark exports; stored notes never change with the theme.

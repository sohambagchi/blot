# Dark mode maps ink lightness into a band, and the colour panel shows drawn colours

**Status:** accepted

**Context:** Supersedes [2026-11-24-1742-dark-mode-ink-invert-and-lighten](2026-11-24-1742-dark-mode-ink-invert-and-lighten.md). That transform raised every chromatic ink to at least OKLab L 0.75, so all dark colours drew at one lightness: navy #1B3F8F and blue #2F6BDE drew almost alike (ΔE 0.003). The colour panel showed stored colours, so the colour picked often wasn't the colour drawn.
**Decision:**
- Notes still store true colours and dark mode still maps them at draw time, through one function (`InkColors.darkMode`) used by the canvas, thumbnails, dark exports, toolbar swatches and the colour panel. Images are never mapped.
- Near-greys still reverse lightness as `1 − L²`, and greyish colours still blend between the grey and colour rules by chroma (0.03–0.08).
- Chromatic colours keep their hue and map lightness in order: `L' = 0.48 + 0.45·L`, the band 0.48–0.93. Chroma is reduced only to fit sRGB.
- The band passes through (0.6, 0.75), so the quick red, blue and green draw where they did. Its low end keeps the darkest chromatic sRGB colour (#000023) at 3.2:1 contrast on the dark page #1E1E20. The darkest palette chip (#43207A) gets 4.6:1.
- In dark mode the colour panel's wheel, tone strip and palette chips show drawn colours. The hex field keeps the stored hex, with "draws as #XXXXXX" under it.
**Alternatives:** A curve that stretches the dark end — it can't keep the quick colours near 0.75 and the light end near its own lightness. A band starting near 0.4 — dark navies fall under 3:1.
**Consequences:** Lightness steps between chromatic inks shrink to 45 % but keep their order, so palette chips stay distinct in dark mode (smallest pairwise ΔE 0.028). Mid-light tints (#EE8A8A, #82A9F0) draw a little lighter than before and very light chromatic inks a little darker. Pale tints with chroma under 0.08 still follow the grey rule, so they draw dark, and the panel now shows that.

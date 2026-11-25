# Bundle a Latin subset of Inter

**Status:** accepted

**Context:** The design canvas sets every label in Inter. Android's system sans (Roboto) has different widths and heights, so pills, segments and menus would drift from the design. Inter 4.1 is SIL OFL 1.1 with no reserved font name, but the variable TTF is 880 KB.
**Decision:** Ship static 400, 500 and 600 instances cut down to Latin, Latin-1, Latin Extended-A, general punctuation, arrows and a few maths signs: 54 KB per weight. `scripts/subset-inter.sh` rebuilds them from nixpkgs' Inter. The OFL text ships as `res/raw/licence_inter_ofl.txt` and appears under Settings → About.
**Alternatives:** The full variable font — 880 KB for scripts the chrome never shows. The system sans — free, but sizes no longer match the design. Downloadable Google Fonts — needs Play Services and a network on first run.
**Consequences:** Characters outside the subset, such as a Greek or CJK note title, fall back to the system font mid-string. Monospace labels (paths, the hex field) use the system monospace.

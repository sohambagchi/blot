#!/usr/bin/env bash
# Regenerates app/src/main/resources/app/blot/markdown/math/blot-math.otf: Latin Modern Math 1.959
# (GUST Font License, an instance of LPPL 1.3c) cut down to the characters text-box maths uses,
# renamed "Blot Math" as the licence requests of derived fonts, and its licence text for About.
# The subsetter keeps the MATH table and every size variant and assembly part it refers to.
set -euo pipefail
cd "$(dirname "$0")/.."
lm=$(nix build --inputs-from . nixpkgs#lmmath.src --print-out-paths --no-link)
latex=$(nix build --inputs-from . nixpkgs#texlive.pkgs.latex.texdoc --print-out-paths --no-link)
fonttools=$(nix build --inputs-from . nixpkgs#python3Packages.fonttools --print-out-paths --no-link)
py=$(nix build --inputs-from . nixpkgs#python3 --print-out-paths --no-link)/bin/python3
export PYTHONPATH="$(echo "$fonttools"/lib/python3*/site-packages)"
out=app/src/main/resources/app/blot/markdown/math
mkdir -p "$out" app/src/main/res/raw
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Text and symbols: ASCII, Latin-1, dotless i/j, combining accents, Greek, punctuation and
# primes, letterlike symbols, arrows, operators, delimiters and big operators.
# Alphabets: bold, italic, bold italic, script, fraktur, double-struck, sans-serif, monospace,
# Greek bold/italic/bold italic, and the styled digits.
unicodes="U+0020-007E,U+00A0-00FF,U+0131,U+0237,U+0300-030C,U+0338,U+0391-03A9,U+03B1-03C9,\
U+03D1,U+03D5,U+03D6,U+03F0,U+03F1,U+03F4,U+03F5,U+2016,U+2020-2022,U+2026,U+2032-2037,U+2044,\
U+2057,U+20D6-20D7,U+20E1,U+2100-214F,U+2190-21FF,U+2200-22FF,U+2308-230B,U+2322-2323,\
U+2329-232A,U+23B4-23B5,U+23DC-23E1,U+25A0-25A1,U+25B3,U+25BD,U+25CB,U+2660-2667,U+27C2,U+27E8-27FF,\
U+2A00-2A0C,U+2A3F,\
U+1D400-1D4CF,U+1D504-1D56B,U+1D5A0-1D5D3,U+1D670-1D6A3,U+1D6A4-1D755,U+1D7CE-1D7FF"

"$py" -P -m fontTools.subset "$lm/otf/latinmodern-math.otf" --unicodes="$unicodes" \
  --layout-features='' --drop-tables+=GSUB,GPOS,GDEF,FFTM --no-hinting --no-glyph-names \
  --notdef-outline --name-IDs='*' --name-languages='*' --output-file="$tmp/subset.otf"

"$py" -P - "$tmp/subset.otf" "$out/blot-math.otf" <<'EOF'
import sys
from fontTools.ttLib import TTFont
f = TTFont(sys.argv[1])
name = f["name"]
note = ("Blot Math is a modified version of Latin Modern Math 1.959: a subset of its characters, "
        "with OpenType layout tables, hinting and glyph names removed, made for the Blot app by "
        "scripts/subset-math-font.sh. Its authors do not support this version. The original is at "
        "https://www.gust.org.pl/projects/e-foundry/lm-math and on CTAN.")
for rec in list(name.names):
    if rec.nameID in (1, 3, 4, 6, 16, 17, 10):
        name.removeNames(nameID=rec.nameID)
name.setName("Blot Math", 1, 3, 1, 0x409)
name.setName("Blot Math Regular; subset of Latin Modern Math 1.959", 3, 3, 1, 0x409)
name.setName("Blot Math Regular", 4, 3, 1, 0x409)
name.setName("BlotMath-Regular", 6, 3, 1, 0x409)
name.setName(note, 10, 3, 1, 0x409)
cff = f["CFF "].cff
cff.fontNames = ["BlotMath-Regular"]
top = cff.topDictIndex[0]
top.FullName = "Blot Math Regular"
top.FamilyName = "Blot Math"
f.save(sys.argv[2])
EOF

{
  cat <<'EOF'
Blot Math, the font text-box maths is drawn in, is a modified version of Latin Modern Math 1.959
by Boguslaw Jackowski, Piotr Strzelczyk and Piotr Pianowski: a subset of its characters, with
OpenType layout tables, hinting and glyph names removed, and renamed as the GUST Font License
requests. Its authors do not support this modified version. The complete, unmodified font is at
https://www.gust.org.pl/projects/e-foundry/lm-math and on CTAN (fonts/lm-math).

Copyright 2012--2014 for the Latin Modern math extensions by B. Jackowski, P. Strzelczyk and
P. Pianowski (on behalf of TeX Users Groups). This work can be freely used and distributed under
the GUST Font License (GFL), which is an instance of the LaTeX Project Public License (LPPL).
Both follow.

EOF
  cat "$lm/doc/GUST-FONT-LICENSE.txt"
  printf '\n\n'
  cat "$latex/doc/latex/base/lppl.txt"
} > app/src/main/res/raw/licence_latin_modern_math.txt
chmod u+w "$out/blot-math.otf" app/src/main/res/raw/licence_latin_modern_math.txt
ls -l "$out/blot-math.otf"

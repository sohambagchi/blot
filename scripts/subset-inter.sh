#!/usr/bin/env bash
# Regenerates app/src/main/res/font/inter_*.ttf: static 400/500/600 instances of Inter 4.1
# (SIL OFL 1.1, no reserved font name) cut down to Latin, punctuation and the arrows and
# maths signs the UI uses. Characters outside the subset fall back to the system font.
set -euo pipefail
cd "$(dirname "$0")/.."
inter=$(nix build --inputs-from . nixpkgs#inter.src --print-out-paths --no-link)
fonttools=$(nix build --inputs-from . nixpkgs#python3Packages.fonttools --print-out-paths --no-link)
py=$(nix build --inputs-from . nixpkgs#python3 --print-out-paths --no-link)/bin/python3
export PYTHONPATH="$(echo "$fonttools"/lib/python3*/site-packages)"
out=app/src/main/res/font
mkdir -p "$out" app/src/main/res/raw
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
unicodes="U+0020-007E,U+00A0-017F,U+2010-2027,U+2030-203A,U+2190-2193,U+2212,U+2215,U+226A-226B,U+2264-2265,U+20AC"
for pair in 400:regular 500:medium 600:semibold; do
  wght=${pair%%:*}
  name=${pair##*:}
  "$py" -m fontTools.varLib.instancer "$inter/InterVariable.ttf" "wght=$wght" "opsz=14" -q -o "$tmp/$name.ttf"
  "$py" -m fontTools.subset "$tmp/$name.ttf" --unicodes="$unicodes" \
    --layout-features='kern,liga,calt,ccmp,locl,mark,mkmk,tnum' --no-hinting --desubroutinize \
    --output-file="$out/inter_$name.ttf"
done
cp "$inter/LICENSE.txt" app/src/main/res/raw/licence_inter_ofl.txt
chmod u+w app/src/main/res/raw/licence_inter_ofl.txt
ls -l "$out"/inter_*.ttf

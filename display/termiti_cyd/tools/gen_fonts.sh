#!/usr/bin/env bash
# Přegeneruje src/fonts/*.c. Montserrat (OFL) se stahuje z github.com/JulietaUla/Montserrat
# (fonts/ttf/Montserrat-{Medium,SemiBold,Bold}.ttf), Cinzel bere z aplikace.
# Použití: tools/gen_fonts.sh <složka s Montserrat TTF>
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
TTF="$1"
OUT="$HERE/../src/fonts"
CINZEL="$HERE/../../../app/src/main/res/font/cinzel.ttf"
mkdir -p "$OUT"
conv() { python "$HERE/ttf2lvgl.py" "$@"; }

conv "$TTF/Montserrat-Medium.ttf"   12 font_text_12 "$OUT/font_text_12.c" --fallback lv_font_montserrat_12
conv "$TTF/Montserrat-Medium.ttf"   14 font_text_14 "$OUT/font_text_14.c" --fallback lv_font_montserrat_14
conv "$TTF/Montserrat-SemiBold.ttf" 16 font_semi_16 "$OUT/font_semi_16.c" --fallback lv_font_montserrat_16
conv "$TTF/Montserrat-Bold.ttf"     40 font_num_40  "$OUT/font_num_40.c"  --chars "0123456789-–?:"
conv "$CINZEL"                      24 font_title_24 "$OUT/font_title_24.c" --chars "TERMIT" --weight 700

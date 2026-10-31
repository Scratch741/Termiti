"""Převod TTF → LVGL 9 font (lv_font_fmt_txt, 4 bpp, bez komprese a kerningu).

Vestavěné fonty LVGL nemají českou diakritiku, proto si generujeme vlastní.
Použití:
  python ttf2lvgl.py FONT.ttf VELIKOST JMENO VYSTUP.c [--chars "..."] [--fallback lv_font_montserrat_14]
                        [--weight 700]
Bez --chars se bere ASCII + Latin-1 + Latin Extended-A + pár typografických znaků.
"""
import argparse
from PIL import Image, ImageDraw, ImageFont

DEFAULT_RANGES = [(0x20, 0x7E), (0xA0, 0x17F), (0x2013, 0x2014), (0x2022, 0x2022), (0x2026, 0x2026)]


def ranges_from_chars(chars):
    cps = sorted(set(ord(c) for c in chars) | {0x20})
    out = []
    for cp in cps:
        if out and cp == out[-1][1] + 1:
            out[-1] = (out[-1][0], cp)
        else:
            out.append((cp, cp))
    return out


def render_glyph(font, ch):
    """Vrátí (šířka, výška, ofs_x, ofs_y, adv_w*16, 4bit pixely po řádcích)."""
    adv16 = round(font.getlength(ch) * 16)
    x0, y0, x1, y1 = font.getbbox(ch, anchor='ls')
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return 0, 0, 0, 0, adv16, []
    img = Image.new('L', (w, h), 0)
    ImageDraw.Draw(img).text((-x0, -y0), ch, font=font, fill=255, anchor='ls')
    trim = img.getbbox()
    if trim is None:
        return 0, 0, 0, 0, adv16, []
    img = img.crop(trim)
    w, h = img.size
    left, top = x0 + trim[0], y0 + trim[1]
    bottom = top + h                      # od účaří, kladné dolů
    px = [(v + 8) >> 4 if v < 248 else 15 for v in img.tobytes()]
    # LVGL: ofs_y = spodní hrana boxu nad účařím (kladné nahoru)
    return w, h, left, -bottom, adv16, px


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('ttf')
    ap.add_argument('size', type=int)
    ap.add_argument('name')
    ap.add_argument('out')
    ap.add_argument('--chars')
    ap.add_argument('--fallback')
    ap.add_argument('--weight', type=float)
    a = ap.parse_args()

    font = ImageFont.truetype(a.ttf, a.size)
    if a.weight is not None:
        font.set_variation_by_axes([a.weight])
    ascent, descent = font.getmetrics()
    ranges = ranges_from_chars(a.chars) if a.chars else DEFAULT_RANGES

    bitmap = bytearray()
    dscs = ['    {.bitmap_index = 0, .adv_w = 0, .box_w = 0, .box_h = 0, .ofs_x = 0, .ofs_y = 0} /* id 0 */']
    cmaps = []
    gid = 1
    for start, end in ranges:
        cmaps.append((start, end - start + 1, gid))
        for cp in range(start, end + 1):
            w, h, ox, oy, adv16, px = render_glyph(font, chr(cp))
            if px and (len(px) % 2):
                px.append(0)
            idx = len(bitmap)
            for i in range(0, len(px), 2):
                bitmap.append((px[i] << 4) | px[i + 1])
            assert idx < (1 << 20) and adv16 < (1 << 12) and w < 256 and h < 256
            dscs.append(f'    {{.bitmap_index = {idx}, .adv_w = {adv16}, .box_w = {w}, .box_h = {h}, '
                        f'.ofs_x = {ox}, .ofs_y = {oy}}} /* U+{cp:04X} */')
            gid += 1

    hexrows = []
    for i in range(0, len(bitmap), 24):
        hexrows.append('    ' + ', '.join(f'0x{b:02x}' for b in bitmap[i:i + 24]))
    cmap_src = ',\n'.join(
        f'    {{.range_start = {s}, .range_length = {n}, .glyph_id_start = {g}, .unicode_list = NULL, '
        f'.glyph_id_ofs_list = NULL, .list_length = 0, .type = LV_FONT_FMT_TXT_CMAP_FORMAT0_TINY}}'
        for s, n, g in cmaps)
    fallback_decl = f'LV_FONT_DECLARE({a.fallback})\n' if a.fallback else ''
    fallback = f'&{a.fallback}' if a.fallback else 'NULL'

    src = f'''/* Vygenerováno tools/ttf2lvgl.py – neupravovat ručně.
 * Zdroj: {a.ttf.replace(chr(92), '/').split('/')[-1]}, {a.size} px, 4 bpp */
#include <lvgl.h>

{fallback_decl}
static LV_ATTRIBUTE_LARGE_CONST const uint8_t glyph_bitmap[] = {{
{(',' + chr(10)).join(hexrows) if hexrows else '    0x00'}
}};

static const lv_font_fmt_txt_glyph_dsc_t glyph_dsc[] = {{
{(',' + chr(10)).join(dscs)}
}};

static const lv_font_fmt_txt_cmap_t cmaps[] = {{
{cmap_src}
}};

static const lv_font_fmt_txt_dsc_t font_dsc = {{
    .glyph_bitmap = glyph_bitmap,
    .glyph_dsc = glyph_dsc,
    .cmaps = cmaps,
    .kern_dsc = NULL,
    .kern_scale = 0,
    .cmap_num = {len(cmaps)},
    .bpp = 4,
    .kern_classes = 0,
    .bitmap_format = 0,
}};

const lv_font_t {a.name} = {{
    .get_glyph_dsc = lv_font_get_glyph_dsc_fmt_txt,
    .get_glyph_bitmap = lv_font_get_bitmap_fmt_txt,
    .line_height = {ascent + descent},
    .base_line = {descent},
    .subpx = LV_FONT_SUBPX_NONE,
    .underline_position = -1,
    .underline_thickness = 1,
    .dsc = &font_dsc,
    .fallback = {fallback},
    .user_data = NULL,
}};
'''
    with open(a.out, 'w', encoding='utf-8', newline='\n') as f:
        f.write(src)
    print(f'{a.name}: {gid - 1} glyfů, {len(bitmap)} B, výška řádku {ascent + descent}')


if __name__ == '__main__':
    main()

"""Přehled k výběru: STARÉ (současná grafika ve hře) vedle všech nových variant.

  python tools/regen_review.py kampan_goblini [kampan_goblini_b …]

Pro každou položku dávky najde varianty ve všech zadaných dávkách
(~/Pictures/Generated/karty/<art>/<art>_<dávka>_vN.png) a uloží
design/regen/<první dávka>.jpg – čísla variant jdou souvisle přes všechny dávky
(1–4 = první kolo, 5–6 = druhé kolo…), takže stačí napsat „goblin_troll 5“.
"""
import json, os, sys, glob
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.expanduser('~/Pictures/Generated/karty')
DRAW = os.path.join(ROOT, 'app/src/main/res/drawable')
OUT = os.path.join(ROOT, 'design/regen')


def font(sz):
    for f in ['C:/Windows/Fonts/segoeuib.ttf', 'C:/Windows/Fonts/arialbd.ttf']:
        if os.path.exists(f):
            return ImageFont.truetype(f, sz)
    return ImageFont.load_default()


def fit(path, w, h):
    im = Image.open(path).convert('RGB'); im.thumbnail((w, h), Image.LANCZOS)
    o = Image.new('RGB', (w, h), (28, 24, 22)); o.paste(im, ((w - im.width) // 2, (h - im.height) // 2))
    return o


def main(batches):
    first = json.load(open(os.path.join(ROOT, 'tools/art_batches', batches[0] + '.json'), encoding='utf-8'))
    rows = []
    for it in first:
        art = it['art']; paths = []
        for b in batches:
            paths += sorted(glob.glob(os.path.join(GEN, art, f'{art}_{b}_v*.png')),
                            key=lambda p: int(p.rsplit('_v', 1)[1].split('.')[0]))
        old = (glob.glob(os.path.join(DRAW, art + '.png')) + glob.glob(os.path.join(DRAW, art + '.jpg')) +
               glob.glob(os.path.join(DRAW, art + '.webp')))
        rows.append((it.get('title', art), art, old[0] if old else None, paths))
    def cell(it):
        w, h = it.get('size', [512, 512]); H = 240
        return (int(H * w / h), H)
    cells = [cell(it) for it in first]
    lab = 230; pad = 8
    width = max(lab + (1 + len(r[3])) * (c[0] + pad) for r, c in zip(rows, cells))
    sheet = Image.new('RGB', (width, sum(c[1] + pad for c in cells) + 40), (16, 13, 12))
    d = ImageDraw.Draw(sheet); f = font(20); fs = font(15)
    d.text((lab, 8), 'STARÉ', fill=(160, 150, 140), font=f)
    y = 40
    for (title, art, old, paths), (W, H) in zip(rows, cells):
        d.text((8, y + H // 2 - 22), title, fill=(226, 188, 118), font=f)
        d.text((8, y + H // 2 + 4), art, fill=(150, 140, 130), font=fs)
        if old:
            sheet.paste(fit(old, W, H), (lab, y))
            d.rectangle((lab, y, lab + W - 1, y + H - 1), outline=(90, 80, 70), width=2)
        else:
            d.text((lab + 10, y + H // 2 - 10), '(nic)', fill=(120, 110, 100), font=f)
        for i, p in enumerate(paths):
            x = lab + (i + 1) * (W + pad)
            sheet.paste(fit(p, W, H), (x, y))
            d.rectangle((x, y, x + 30, y + 28), fill=(0, 0, 0)); d.text((x + 8, y + 2), str(i + 1), fill='white', font=f)
        y += H + pad
    os.makedirs(OUT, exist_ok=True)
    dest = os.path.join(OUT, batches[0] + '.jpg')
    sheet.save(dest, quality=88)
    print(dest, sheet.size)


if __name__ == '__main__':
    main(sys.argv[1:])

"""Náhled pozadí bojiště tak, jak ho uvidí hráč: ořez jako ContentScale.Crop na plochu
bojiště (~625×190 dp na S23 na šířku), hrad 110×165 dp + hradba 42×58 dp vlevo i vpravo.
  python tools/battlefield_mock.py <dávka> [<dávka>…]  → design/regen/bojiste_mock.jpg"""
import os, sys, glob, json
from PIL import Image, ImageDraw, ImageFont
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.expanduser('~/Pictures/Generated/karty'); DRAW = os.path.join(ROOT, 'app/src/main/res/drawable')
K = 2  # px na dp
FW, FH = 625 * K, 190 * K
SKINS = {'castle_background': ('castle_player', 'wall_player2'), 'castle_background_citadela': ('castle_citadela', 'wall_citadela'),
         'castle_background_goblin': ('castle_player_4', 'wall_goblin'), 'castle_background_swamp': ('castle_baziny', 'wall_baziny'),
         'castle_background_vulcan': ('castle_drak', 'wall_drak'), 'castle_background_winter': ('castle_hory', 'wall_hory')}

def field(bg_path, art, hp=0.85):
    bg = Image.open(bg_path).convert('RGB'); k = max(FW / bg.width, FH / bg.height)
    bg = bg.resize((round(bg.width * k), round(bg.height * k)), Image.LANCZOS)
    x0, y0 = (bg.width - FW) // 2, (bg.height - FH) // 2
    f = bg.crop((x0, y0, x0 + FW, y0 + FH)).convert('RGBA')
    cs, ws = SKINS[art]
    c = Image.open(os.path.join(DRAW, cs + '.png')).convert('RGBA'); w = Image.open(os.path.join(DRAW, ws + '.png')).convert('RGBA')
    cw, ch = 110 * K, 165 * K; kc = min(cw / c.width, ch / c.height); c = c.resize((round(c.width * kc), round(c.height * kc)), Image.LANCZOS)
    w = w.resize((42 * K, 58 * K), Image.LANCZOS)
    sink = lambda im, frac: im.crop((0, 0, im.width, round(im.height * frac)))
    cc = sink(c, hp); wc = sink(w, hp)
    pad = 8 * K; cx = pad + (cw - c.width) // 2
    f.alpha_composite(cc, (cx, FH - cc.height)); f.alpha_composite(wc, (pad + cw + 3 * K, FH - wc.height))
    cm, wm = cc.transpose(Image.FLIP_LEFT_RIGHT), wc.transpose(Image.FLIP_LEFT_RIGHT)
    f.alpha_composite(cm, (FW - pad - cw + (cw - c.width) // 2, FH - cm.height)); f.alpha_composite(wm, (FW - pad - cw - 3 * K - 42 * K, FH - wm.height))
    return f.convert('RGB')

def main(batches):
    items = [i for i in json.load(open(os.path.join(ROOT, 'tools/art_batches', batches[0] + '.json'), encoding='utf-8')) if i['art'] in SKINS]
    S = 0.5; tw, th = int(FW * S), int(FH * S); cols = 3; lab = 40
    rows = []
    for it in items:
        art = it['art']; paths = [os.path.join(DRAW, art + '.png')]
        for b in batches: paths += sorted(glob.glob(os.path.join(GEN, art, f'{art}_{b}_v*.png')), key=lambda p: int(p.rsplit('_v', 1)[1][:-4]))
        rows.append((it['title'], paths))
    nrows = sum((len(p) + cols - 1) // cols for _, p in rows)
    sheet = Image.new('RGB', (cols * (tw + 8) + 8, nrows * (th + 8) + len(rows) * lab), (16, 13, 12))
    d = ImageDraw.Draw(sheet)
    try: font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 22)
    except Exception: font = ImageFont.load_default()
    y = 0
    for (title, paths), it in zip(rows, items):
        d.text((10, y + 8), title, fill=(226, 188, 118), font=font); y += lab
        for i, p in enumerate(paths):
            x = 8 + (i % cols) * (tw + 8)
            sheet.paste(field(p, it['art']).resize((tw, th), Image.LANCZOS), (x, y))
            d.rectangle((x, y, x + 64, y + 30), fill=(0, 0, 0)); d.text((x + 6, y + 2), 'STARÉ' if i == 0 else str(i), fill='white', font=font)
            if i % cols == cols - 1 or i == len(paths) - 1: y += th + 8
    out = os.path.join(ROOT, 'design/regen/bojiste_mock.jpg'); sheet.save(out, quality=88); print(out, sheet.size)

if __name__ == '__main__':
    main(sys.argv[1:])

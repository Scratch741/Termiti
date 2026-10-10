"""Sjednocení tlačítek menu (button_N.png) na jeden rám a jeden tón + nová tlačítka s ikonou.

Původní tlačítka 6-9 sdílejí pixelově shodný rám, 1-5 jsou jiné rendery (jiný odstín,
jinak posazený medailon). Skript:
  * vezme rám z button_6 a z medailonu vyrobí prázdný disk (minimum přes 6-9),
  * z každého původního tlačítka vyřízne vnitřek medailonu, srovná tón ikony a vsadí ho do rámu,
  * z herních ikon (trofej, lebka, svitek, věž s lešením) a kreslených kostek udělá nová tlačítka 10-14.

Zdroj: tools/menu_buttons_src/button_1..9.png (původní soubory, aby šel skript pouštět znovu).
Použití: python tools/make_menu_buttons.py [výstupní složka]   (default res/drawable)
"""
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'tools', 'menu_buttons_src')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable')
OUT = sys.argv[1] if len(sys.argv) > 1 else RES

W, H = 662, 107
CX, CY = 73.5, 51.5      # střed medailonu společného rámu
R_IN = 43.0              # vnitřek disku (uvnitř bronzového prstence)
ICON_BOX = 60            # do jak velkého čtverce se vsazují nové ikony


def rgb2hsv(rgb):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx = rgb.max(-1); mn = rgb.min(-1); d = mx - mn
    dd = np.where(d > 1e-6, d, 1)
    rc = (mx - r) / dd; gc = (mx - g) / dd; bc = (mx - b) / dd
    h = np.where(mx == r, bc - gc, np.where(mx == g, 2 + rc - bc, 4 + gc - rc)) / 6 % 1
    h = np.where(d > 1e-6, h, 0)
    s = np.where(mx > 1e-6, d / np.where(mx > 1e-6, mx, 1), 0)
    return h * 360, s, mx


def hsv2rgb(h, s, v):
    h = (h % 360) / 60
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p = v * (1 - s); q = v * (1 - s * f); t = v * (1 - s * (1 - f))
    r = np.choose(i, [v, q, p, p, t, v])
    g = np.choose(i, [t, v, v, q, p, p])
    b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1)


def smooth(x, lo, hi):
    t = np.clip((x - lo) / (hi - lo), 0, 1)
    return t * t * (3 - 2 * t)


def load(i):
    return np.asarray(Image.open(os.path.join(SRC, 'button_%d.png' % i)).convert('RGBA')).astype(np.float64) / 255


def save(a, name):
    Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(
        os.path.join(OUT, name + '.png'), optimize=True)


YY, XX = np.mgrid[0:H, 0:W].astype(np.float64)


def find_disc(a):
    """Střed a poloměr medailonu: hledá kružnici, kde tmavý vnitřek přechází do světlého prstence."""
    lum = a[..., :3].mean(-1) * a[..., 3]
    ang = np.linspace(0, 2 * np.pi, 180, endpoint=False)
    best = (-1, 0, 0, 0)
    for cx in np.arange(60, 90, 1.0):
        for cy in np.arange(48, 57, 1.0):
            for r in np.arange(38, 50, 1.0):
                def ring(rr):
                    x = np.clip(np.rint(cx + rr * np.cos(ang)).astype(int), 0, W - 1)
                    y = np.clip(np.rint(cy + rr * np.sin(ang)).astype(int), 0, H - 1)
                    return lum[y, x].mean()
                sc = ring(r + 2) - ring(r - 2)
                if sc > best[0]:
                    best = (sc, cx, cy, r)
    return best[1:]


def sample(a, cx, cy, scale):
    """Převzorkuje `a` tak, aby bod (cx,cy) padl na (CX,CY) a měřítko bylo `scale`."""
    im = Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGBA')
    # affine: výstupní (x,y) -> vstupní
    m = (1 / scale, 0, cx - CX / scale, 0, 1 / scale, cy - CY / scale)
    return np.asarray(im.transform((W, H), Image.AFFINE, m, Image.BICUBIC)).astype(np.float64) / 255


# ── společný rám a prázdný disk ──────────────────────────────────────────────
same = [load(i) for i in (6, 7, 8, 9)]
base = same[0].copy()
stack = np.stack(same)
darkest = stack[..., :3].sum(-1).argmin(0)
blank = np.take_along_axis(stack, darkest[None, ..., None], 0)[0]
blur = np.asarray(Image.fromarray((blank * 255 + 0.5).astype(np.uint8), 'RGBA')
                  .filter(ImageFilter.GaussianBlur(2.2))).astype(np.float64) / 255
RR = np.sqrt((XX - CX) ** 2 + (YY - CY) ** 2)
inner = 1 - smooth(RR, R_IN - 5, R_IN - 1)          # 1 uvnitř disku
base[..., :3] = base[..., :3] * (1 - inner[..., None]) + blur[..., :3] * inner[..., None]

# ── cílový tón ikon: průměr přes 6-9 ─────────────────────────────────────────
def icon_stats(a, cx=CX, cy=CY, r=R_IN):
    rr = np.sqrt((XX - cx) ** 2 + (YY - cy) ** 2)
    h, s, v = rgb2hsv(a[..., :3])
    m = (rr < r - 5) & (v > 0.22)
    return np.median(h[m]), np.median(s[m]), np.percentile(v[m], 90)


T_H, T_S, T_V = np.mean([icon_stats(a) for a in same], axis=0)


def tone(a, mask):
    """Srovná tón ikony (odstín, sytost, jas světel) na cílové hodnoty."""
    h, s, v = rgb2hsv(a[..., :3])
    m = (mask > 0.5) & (v > 0.22)
    if m.sum() < 50:
        return a
    mh, ms, mv = np.median(h[m]), np.median(s[m]), np.percentile(v[m], 90)
    w = smooth(v, 0.08, 0.22) * mask                # tmavé pozadí disku se nemění
    hn = h + (T_H - mh) * w
    sn = np.clip(s * (1 + (T_S / ms - 1) * w), 0, 1)
    vn = np.clip(v * (1 + (T_V / mv - 1) * w), 0, 1)
    out = a.copy()
    out[..., :3] = hsv2rgb(hn, sn, vn)
    return out


feather = 1 - smooth(RR, R_IN - 6, R_IN - 2)        # kde se bere vnitřek z původního tlačítka

report = []
for i in range(1, 10):
    a = load(i)
    cx, cy, r = (CX, CY, R_IN) if i >= 6 else find_disc(a)
    moved = sample(a, cx, cy, R_IN / r)
    moved = tone(moved, feather)
    out = base.copy()
    out[..., :3] = base[..., :3] * (1 - feather[..., None]) + moved[..., :3] * feather[..., None]
    save(out, 'button_%d' % i)
    report.append('button_%d disc (%.0f, %.0f) r=%.0f' % (i, cx, cy, r))


# ── nové ikony ───────────────────────────────────────────────────────────────
def relief(img, box=ICON_BOX, gamma=1.0):
    """Barevná herní ikona -> bledě bronzový reliéf ve stylu ikon na tlačítkách."""
    im = img.convert('RGBA')
    bb = im.getchannel('A').point(lambda p: 255 if p > 24 else 0).getbbox()
    im = im.crop(bb)
    k = box / max(im.size)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    a = np.asarray(im).astype(np.float64) / 255
    lum = a[..., 0] * 0.30 + a[..., 1] * 0.59 + a[..., 2] * 0.11
    m = a[..., 3] > 0.5
    lo, hi = np.percentile(lum[m], 2), np.percentile(lum[m], 97)
    g = np.clip((lum - lo) / max(hi - lo, 1e-6), 0, 1) ** gamma
    v = 0.10 + (T_V + 0.06 - 0.10) * g
    s = T_S * (1.15 - 0.35 * g)
    rgb = hsv2rgb(np.full_like(v, T_H), s, v)
    return np.dstack([rgb, a[..., 3]])


def place(icon, dy=0.0):
    """Vsadí ikonu (RGBA pole) do prázdného medailonu, s měkkým stínem pod ní."""
    out = base.copy()
    ih, iw = icon.shape[:2]
    x0 = int(round(CX - iw / 2)); y0 = int(round(CY - ih / 2 + dy))
    layer = np.zeros((H, W, 4)); layer[y0:y0 + ih, x0:x0 + iw] = icon
    sh = np.asarray(Image.fromarray((layer[..., 3] * 255).astype(np.uint8))
                    .filter(ImageFilter.GaussianBlur(1.6))).astype(np.float64) / 255
    sh = np.roll(sh, 1, axis=0) * 0.55 * feather
    out[..., :3] *= (1 - sh[..., None])
    al = (layer[..., 3] * feather)[..., None]
    out[..., :3] = out[..., :3] * (1 - al) + layer[..., :3] * al
    return out


def dice_art(size=512):
    """Dvě kostky v nadhledu (kreslené ve vysokém rozlišení, pak se zmenší)."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    def die(ox, oy, e, faces):
        # izometrická krychle: vrchní, levá a pravá stěna
        hx, hy = e * 0.866, e * 0.5
        top = [(ox, oy), (ox + hx, oy + hy), (ox, oy + 2 * hy), (ox - hx, oy + hy)]
        left = [(ox - hx, oy + hy), (ox, oy + 2 * hy), (ox, oy + 2 * hy + e), (ox - hx, oy + hy + e)]
        right = [(ox + hx, oy + hy), (ox, oy + 2 * hy), (ox, oy + 2 * hy + e), (ox + hx, oy + hy + e)]
        edge = (40, 40, 40, 255)
        for poly, shade in ((top, 235), (left, 150), (right, 95)):
            d.polygon(poly, fill=(shade, shade, shade, 255))
            d.line(poly + [poly[0]], fill=edge, width=max(2, size // 90), joint='curve')

        def pips(origin, ux, uy, n, shade):
            spots = {1: [(.5, .5)], 2: [(.27, .27), (.73, .73)], 3: [(.25, .25), (.5, .5), (.75, .75)],
                     4: [(.27, .27), (.73, .27), (.27, .73), (.73, .73)],
                     5: [(.25, .25), (.75, .25), (.5, .5), (.25, .75), (.75, .75)],
                     6: [(.27, .22), (.73, .22), (.27, .5), (.73, .5), (.27, .78), (.73, .78)]}[n]
            pr = e * 0.085
            for u, w in spots:
                px = origin[0] + ux[0] * u + uy[0] * w
                py = origin[1] + ux[1] * u + uy[1] * w
                d.ellipse((px - pr, py - pr * 0.8, px + pr, py + pr * 0.8), fill=(shade, shade, shade, 255))

        pips((ox - hx, oy + hy), (hx, -hy), (hx, hy), faces[0], 45)      # vrch
        pips((ox - hx, oy + hy), (hx, hy), (0, e), faces[1], 30)         # levá
        pips((ox, oy + 2 * hy), (hx, -hy), (0, e), faces[2], 215)        # pravá

    e = size * 0.27
    die(size * 0.66, size * 0.06, e, (5, 3, 6))
    die(size * 0.34, size * 0.36, e * 1.12, (2, 6, 4))
    return im


def game_icon(name):
    return Image.open(os.path.join(RES, name + '.png'))


new = {
    'button_10': (relief(dice_art(), box=64, gamma=0.9), 0),            # super náhodný mód
    'button_11': (relief(game_icon('trophy_icon'), box=62, gamma=0.8), 0),   # žebříček
    'button_12': (relief(game_icon('scroll_icon'), box=62, gamma=1.3), 0),   # kampaň / záznam hry
    'button_13': (relief(game_icon('skull_icon'), box=60, gamma=0.9), 0),    # roguelike
    'button_14': (relief(game_icon('stavba_icon'), box=66, gamma=0.6), 0),   # constructed
}
for name, (icon, dy) in new.items():
    save(place(icon, dy), name)

print('target icon tone: hue %.1f sat %.2f v90 %.2f' % (T_H, T_S, T_V))
print('\n'.join(report))

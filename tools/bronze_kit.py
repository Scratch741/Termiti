"""Detailní návrh sady UI prvků „bronz a tmavé dřevo" – knihovna prvků + ukázka obrazovky.

Kreslí se procedurálně: dřevo z deformovaného šumu (letokruhy, póry, prkna), bronz jako
vícepásmové zkosení s jemným škrábáním kovu, rohové kování s nýty.

Použití: python tools/bronze_kit.py design/bronze_wood   (vytvoří bronze_kit.png a bronze_profile_mock.png)
Návrh, zatím neimplementováno – viz design/bronze_wood/README.md.
"""
import sys, os, colorsys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTDIR = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))).replace('\\', '/') + '/'
CINZEL = ROOT + 'app/src/main/res/font/cinzel.ttf'
SANS, SANSB = 'C:/Windows/Fonts/segoeui.ttf', 'C:/Windows/Fonts/segoeuib.ttf'
rng = np.random.default_rng(11)

GOLD  = (232, 196, 103)
CREAM = (237, 224, 196)
MUTED = (150, 132, 108)
GREEN = (120, 214, 120)


def hsv(h, s, v):
    return np.array(colorsys.hsv_to_rgb(h / 360, s, v), dtype=np.float32)


def noise(w, h, sx, sy, octaves=4, seed=None):
    r = np.random.default_rng(seed) if seed is not None else rng
    acc = np.zeros((h, w), np.float32); amp, tot = 1.0, 0.0
    for o in range(octaves):
        gw = max(2, int(w / sx * 2 ** o) + 2); gh = max(2, int(h / sy * 2 ** o) + 2)
        g = (r.random((gh, gw)) * 255).astype(np.uint8)
        acc += np.asarray(Image.fromarray(g).resize((w, h), Image.BICUBIC), np.float32) / 255 * amp
        tot += amp; amp *= 0.5
    return acc / tot


def wood(w, h, horizontal=True, planks=0, tone=1.0, seed=None):
    """Tmavé dřevo: letokruhy deformované šumem, jemné póry, volitelně prkna se spárami."""
    if not horizontal:
        return np.transpose(wood(h, w, True, planks, tone, seed), (1, 0, 2))
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    # rovná kresba: dlouhá vlákna po délce prkna, jen mírně zvlněná, + vzácné tmavší letokruhy
    warp = noise(w, h, 600, 80, 2, seed) * 7
    streak = noise(w, h, 420, 2.2, 4, seed)
    rings = (0.5 + 0.5 * np.sin((y + warp) * 0.21 + noise(w, h, 300, 40, 2, seed) * 3)) ** 6
    pores = noise(w, h, 14, 1.2, 2, seed)
    t = np.clip(0.55 * streak + 0.22 * pores + 0.23 * (1 - rings) - 0.05, 0, 1)
    c_dark, c_mid, c_lite = hsv(20, 0.70, 0.12), hsv(22, 0.66, 0.22), hsv(26, 0.58, 0.33)
    col = np.where(t[..., None] < 0.5,
                   c_dark + (c_mid - c_dark) * (t[..., None] / 0.5),
                   c_mid + (c_lite - c_mid) * ((t[..., None] - 0.5) / 0.5))
    if planks:
        ph = h / planks
        seam = np.abs(((y + 0.5) % ph) - 0.5) < 1.2
        col[seam] *= 0.35
        col[np.abs(((y + 2.5) % ph) - 0.5) < 0.8] *= 1.18      # světlá hrana pod spárou
    return np.clip(col * tone, 0, 1)


def sdf_rrect(w, h, r, inset=0.0):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32) + 0.5
    hx, hy = w / 2 - inset - r, h / 2 - inset - r
    qx = np.abs(x - w / 2) - hx; qy = np.abs(y - h / 2) - hy
    return np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - r


def bronze_band(d, h, rim, bright=1.0):
    """Bronzový lem: tmavá linka – světlá hrana – tělo – stín – tmavá linka; světlo shora."""
    gy, gx = np.gradient(d)
    ln = np.hypot(gx, gy) + 1e-6
    light = (gx * 0.55 + gy * 0.83) / ln          # normála obrysu ven · směr ke světlu (shora)
    t = np.clip(-d / rim, 0, 1)
    top = hsv(40, 0.50, 0.98) * bright; mid = hsv(34, 0.62, 0.66) * bright; low = hsv(28, 0.72, 0.30)
    # profil: vnější polovina lemu odráží světlo, vnitřní je ve stínu (zaoblená lišta)
    prof = np.cos((t - 0.35) * np.pi) * 0.5 + 0.5
    k = np.clip(0.45 + 0.55 * (-light) * (1 - 2 * np.abs(t - 0.5)) * 0.8 + 0.25 * prof, 0, 1)
    col = np.where(k[..., None] > 0.6, mid + (top - mid) * ((k[..., None] - 0.6) / 0.4), low + (mid - low) * (k[..., None] / 0.6))
    scratch = noise(d.shape[1], d.shape[0], 30, 2, 2)
    col *= (0.92 + 0.16 * scratch[..., None])
    col *= np.linspace(1.06, 0.82, d.shape[0])[:, None, None]
    line = (t < 0.14) | (t > 0.86)
    col[line] = col[line] * 0.40
    return np.clip(col, 0, 1)


def plate(w, h, r=10, rim=5, fill=None, inset_shadow=14, inset_dark=0.45, bright=1.0, alpha_fill=1.0):
    d = sdf_rrect(w, h, r)
    if fill is None:
        fill = wood(w, h)
    sh = np.clip((-d - rim) / inset_shadow, 0, 1) ** 0.7
    f = fill * (inset_dark + (1 - inset_dark) * sh)[..., None]
    band = bronze_band(d, h, rim, bright)
    isrim = (d <= 0) & (d > -rim)
    rgb = np.where(isrim[..., None], band, f)
    a = np.clip(0.5 - d, 0, 1)
    a = np.where(isrim | (d > -0.5), a, a * alpha_fill)
    return Image.fromarray((np.dstack([rgb, a]) * 255).astype(np.uint8), 'RGBA')


def rivet(img, cx, cy, r=4):
    dr = ImageDraw.Draw(img)
    dr.ellipse([cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1], fill=(40, 24, 12, 255))
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(150, 104, 48, 255))
    dr.ellipse([cx - r + 1, cy - r + 1, cx + r - 2, cy + r - 2], fill=(214, 170, 92, 255))
    dr.ellipse([cx - r + 2, cy - r + 2, cx, cy], fill=(250, 226, 160, 255))


def corner_brackets(img, size=26, rim=5, inset=4):
    """Bronzové rohové kování (L) s nýtem ve všech čtyřech rozích."""
    w, h = img.size
    piece = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    m = np.zeros((size, size), np.float32)
    m[:rim + 2, :] = 1; m[:, :rim + 2] = 1
    # zkosený konec ramen
    for i in range(size):
        if i > size - 6:
            m[:rim + 2, i] = 0; m[i, :rim + 2] = 0
    d = -m * (rim + 2) + 0.5
    band = bronze_band(np.where(m > 0, -np.minimum(np.indices((size, size))[0], np.indices((size, size))[1]) - 1.0, 1.0), size, rim + 2, 1.05)
    rgba = np.dstack([band, m])
    piece = Image.fromarray((rgba * 255).astype(np.uint8), 'RGBA')
    rivet(piece, rim // 2 + 4, rim // 2 + 4, 3)
    for rot, (x, y) in [(0, (inset, inset)), (270, (w - size - inset, inset)), (90, (inset, h - size - inset)), (180, (w - size - inset, h - size - inset))]:
        img.alpha_composite(piece.rotate(rot), (x, y))


def drop_shadow(dst, img, x, y, off=4, blur=5, strength=0.75):
    a = img.getchannel('A').filter(ImageFilter.GaussianBlur(blur)).point(lambda p: int(p * strength))
    sh = Image.new('RGBA', img.size, (0, 0, 0, 255)); sh.putalpha(a)
    dst.alpha_composite(sh, (x + off // 2, y + off))
    dst.alpha_composite(img, (x, y))


def text_c(dr, box, txt, font, fill, shadow=True, dy=-2):
    x0, y0, x1, y1 = box
    b = dr.textbbox((0, 0), txt, font=font)
    x = x0 + (x1 - x0 - (b[2] - b[0])) / 2 - b[0]; y = y0 + (y1 - y0 - (b[3] - b[1])) / 2 - b[1] + dy
    if shadow:
        dr.text((x + 1, y + 2), txt, font=font, fill=(10, 6, 3))
    dr.text((x, y), txt, font=font, fill=fill)


# ── Jednotlivé prvky ───────────────────────────────────────────────────────────
def header_plate(w, h=52, label='', bright=1.0):
    """Destička nadpisu: světlejší dřevo, bronzový lem, kulaté koncovky s nýty."""
    img = plate(w, h, r=h // 2 - 4, rim=4, fill=wood(w, h, tone=1.35, seed=3), inset_shadow=10, inset_dark=0.55, bright=bright)
    rivet(img, h // 2 - 2, h // 2, 4); rivet(img, w - h // 2 + 1, h // 2, 4)
    if label:
        text_c(ImageDraw.Draw(img), (0, 0, w, h), label, ImageFont.truetype(SANSB, int(h * 0.42)), CREAM)
    return img


def panel(w, h, title=None):
    img = plate(w, h, r=14, rim=8, fill=wood(w, h, planks=max(1, h // 120), tone=0.95, seed=5), inset_shadow=26, inset_dark=0.30)
    corner_brackets(img, 44, 6, 3)
    if title:
        tp = header_plate(min(w - 120, 360), 50, title)
        img.alpha_composite(tp, ((w - tp.width) // 2, 12))
    return img


def slot(size=92, state='empty', icon=None):
    fill = np.full((size, size, 3), 0.05, np.float32) + noise(size, size, 20, 20, 2)[..., None] * 0.05
    img = plate(size, size, r=7, rim=6, fill=fill, inset_shadow=16, inset_dark=0.15)
    if icon is not None:
        ic = icon.copy(); ic.thumbnail((int(size * 0.62),) * 2, Image.LANCZOS)
        if state == 'locked':
            ic = Image.fromarray((np.asarray(ic) * [0.35, 0.35, 0.35, 1]).astype(np.uint8), 'RGBA')
        img.alpha_composite(ic, ((size - ic.width) // 2, (size - ic.height) // 2))
    dr = ImageDraw.Draw(img)
    if state == 'selected':
        glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(glow).rounded_rectangle([3, 3, size - 4, size - 4], 7, outline=(255, 214, 110, 255), width=3)
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(2)))
        img.alpha_composite(glow)
    if state == 'locked':
        lock = Image.open(ROOT + 'app/src/main/res/drawable/lock_icon.png').convert('RGBA'); lock.thumbnail((26, 26))
        img.alpha_composite(lock, (size - 30, size - 32))
    return img


def button(w, h, label, state='normal', font=None):
    bright = {'normal': 1.0, 'pressed': 0.85, 'disabled': 0.55}[state]
    tone = {'normal': 1.15, 'pressed': 0.80, 'disabled': 0.70}[state]
    img = plate(w, h, r=min(12, h // 4), rim=5, fill=wood(w, h, tone=tone, seed=7), inset_shadow=12,
                inset_dark=0.35 if state == 'pressed' else 0.55, bright=bright)
    col = {'normal': GOLD, 'pressed': (200, 166, 88), 'disabled': (120, 106, 84)}[state]
    text_c(ImageDraw.Draw(img), (0, 2 if state == 'pressed' else 0, w, h), label,
           font or ImageFont.truetype(CINZEL, int(h * 0.42)), col)
    return img


def tab(w, h, label, active):
    img = plate(w, h, r=8, rim=4, fill=wood(w, h, tone=1.3 if active else 0.75, seed=9), inset_shadow=8,
                inset_dark=0.6 if active else 0.4, bright=1.0 if active else 0.7)
    text_c(ImageDraw.Draw(img), (0, 0, w, h), label, ImageFont.truetype(SANSB, int(h * 0.40)), GOLD if active else MUTED)
    return img


def rows(w, items, row_h=36, selected=None):
    """Řádky tabulky: střídavé tmavé pruhy, bronzová vlasová linka, zlatý popisek."""
    h = row_h * len(items)
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    f, fb = ImageFont.truetype(SANS, int(row_h * 0.52)), ImageFont.truetype(SANSB, int(row_h * 0.52))
    for i, (k, v, c) in enumerate(items):
        y = i * row_h
        base = wood(w, row_h, tone=0.62 if i % 2 else 0.80, seed=20 + i)
        if selected == i:
            base = np.clip(base * 1.25 + np.array([0.05, 0.03, 0.0]), 0, 1)
        img.alpha_composite(Image.fromarray((np.dstack([base, np.ones((row_h, w))]) * 255).astype(np.uint8), 'RGBA'), (0, y))
        dr.line([(0, y + row_h - 1), (w, y + row_h - 1)], fill=(92, 62, 30, 255))
        dr.text((14, y + row_h * 0.17), k, font=f, fill=(222, 186, 104))
        tw = dr.textbbox((0, 0), v, font=fb)[2]
        dr.text((w - 14 - tw, y + row_h * 0.17), v, font=fb, fill=c)
        if selected == i:
            dr.rectangle([0, y, 3, y + row_h - 2], fill=(240, 200, 110, 255))
    return img


def separator(w):
    h = 12
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    fade = np.clip(np.minimum(x, w - x) / (w * 0.18), 0, 1)
    d = np.abs(y - h / 2) - 2.0
    band = bronze_band(d, h, 2.0)
    a = (np.clip(0.5 - d, 0, 1) * fade)
    img = Image.fromarray((np.dstack([band, a]) * 255).astype(np.uint8), 'RGBA')
    # kosočtverec uprostřed
    dr = ImageDraw.Draw(img)
    cx = w // 2
    dr.polygon([(cx - 9, h // 2), (cx, 0), (cx + 9, h // 2), (cx, h - 1)], fill=(214, 168, 88, 255), outline=(60, 36, 14, 255))
    dr.polygon([(cx - 4, h // 2), (cx, 2), (cx + 2, h // 2 - 1)], fill=(252, 228, 160, 255))
    return img


def scrollbar(h, thumb=(0.25, 0.45)):
    w = 16
    img = plate(w, h, r=7, rim=3, fill=np.full((h, w, 3), 0.06, np.float32), inset_shadow=6, inset_dark=0.4)
    t0, t1 = int(h * thumb[0]), int(h * thumb[1])
    th = plate(w - 6, t1 - t0, r=5, rim=3, fill=wood(w - 6, t1 - t0, tone=2.2, seed=31), inset_shadow=4, bright=1.15)
    img.alpha_composite(th, (3, t0))
    return img


def bg(w, h):
    n = noise(w, h, 300, 300, 3)
    arr = np.dstack([n * 16 + 16, n * 12 + 12, n * 9 + 9, np.full_like(n, 255)]).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def label(dr, xy, txt, size=16):
    dr.text(xy, txt, font=ImageFont.truetype(SANS, size), fill=(170, 150, 120))


icons = {n: Image.open(ROOT + f'app/src/main/res/drawable/{n}.png').convert('RGBA')
         for n in ['card_icon', 'goldcoin_icon', 'trophy_icon', 'scroll_icon', 'shield_icon', 'skull_icon', 'diamond_icon', 'star_icon']}

# ── List 1: knihovna prvků ──────────────────────────────────────────────────────
W, H = 1800, 1180
sheet = bg(W, H)
dr = ImageDraw.Draw(sheet)
text_c(dr, (0, 10, W, 70), 'Bronz a tmavé dřevo – knihovna prvků', ImageFont.truetype(CINZEL, 38), GOLD)

p = panel(560, 560, 'Panel')
drop_shadow(sheet, p, 40, 100, 6, 8)
label(dr, (40, 668), 'Panel: prkna, rohové kování s nýty, destička s nadpisem')
inner = rows(480, [('Výhry', '314', GREEN), ('Online výhry', '35', GREEN), ('Odehráno', '423', CREAM),
                   ('Úspěšnost', '74 %', (232, 196, 90)), ('Prach', '11 715', (180, 160, 230)), ('Úroveň', '31', GOLD)], 40, selected=2)
sheet.alpha_composite(inner, (80, 190))
sheet.alpha_composite(separator(480), (80, 446))
for i, (n, st) in enumerate([('card_icon', 'empty'), ('goldcoin_icon', 'selected'), ('trophy_icon', 'empty'), ('skull_icon', 'locked')]):
    drop_shadow(sheet, slot(96, st, icons[n]), 80 + i * 124, 486, 3, 3)

x0 = 650
text_c(dr, (x0, 100, x0 + 500, 130), 'Destičky nadpisů', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
drop_shadow(sheet, header_plate(480, 54, 'Statistiky'), x0 + 10, 140)
drop_shadow(sheet, header_plate(300, 44, 'Denní questy'), x0 + 10, 210)
drop_shadow(sheet, header_plate(200, 38, 'Hradby'), x0 + 330, 214)

text_c(dr, (x0, 280, x0 + 500, 310), 'Tlačítka – normální / stisknuté / vypnuté', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
for i, st in enumerate(['normal', 'pressed', 'disabled']):
    drop_shadow(sheet, button(480, 62, 'Hrát', st), x0 + 10, 320 + i * 80)

text_c(dr, (x0, 570, x0 + 500, 600), 'Malá tlačítka', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
for i, (lab, st) in enumerate([('+', 'normal'), ('−', 'normal'), ('‹', 'normal'), ('›', 'pressed'), ('?', 'disabled'), ('×', 'normal')]):
    drop_shadow(sheet, button(58, 58, lab, st, ImageFont.truetype(SANSB, 30)), x0 + 10 + i * 80, 610, 3, 3)

x1 = 1200
text_c(dr, (x1, 100, x1 + 560, 130), 'Záložky – aktivní / neaktivní', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
for i, (lab, act) in enumerate([('Přehled', True), ('Vzhled', False), ('Schopnosti', False)]):
    sheet.alpha_composite(tab(170, 46, lab, act), (x1 + 10 + i * 182, 140))
text_c(dr, (x1, 220, x1 + 560, 250), 'Patice – prázdná / vybraná / zamčená', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
for i, (n, st) in enumerate([(None, 'empty'), ('diamond_icon', 'selected'), ('shield_icon', 'locked'), ('star_icon', 'empty')]):
    drop_shadow(sheet, slot(112, st, icons[n] if n else None), x1 + 10 + i * 136, 262, 3, 3)
text_c(dr, (x1, 410, x1 + 560, 440), 'Oddělovač a rolovací lišta', ImageFont.truetype(SANSB, 20), MUTED, shadow=False)
sheet.alpha_composite(separator(520), (x1 + 20, 460))
sheet.alpha_composite(scrollbar(200), (x1 + 20, 500))
sheet.alpha_composite(rows(470, [('Rychlá magie', 'x2', GOLD), ('Prokletí', 'x1', GOLD), ('Vojenská základna', 'x2', GOLD),
                                 ('Odstřelovač', 'x2', GOLD), ('Přímý zásah', 'x2', GOLD)], 40), (x1 + 60, 500))

text_c(dr, (0, 720, W, 760), 'Barvy a materiály', ImageFont.truetype(SANSB, 22), MUTED, shadow=False)
sw = [('Dřevo tmavé', hsv(20, 0.70, 0.12)), ('Dřevo střední', hsv(22, 0.66, 0.22)), ('Dřevo světlé', hsv(26, 0.58, 0.33)),
      ('Bronz stín', hsv(28, 0.72, 0.30)), ('Bronz tělo', hsv(34, 0.62, 0.66)), ('Bronz lesk', hsv(40, 0.50, 0.98)),
      ('Text popisek', np.array((222, 186, 104)) / 255), ('Text hodnota', np.array(CREAM) / 255), ('Nadpis zlatý', np.array(GOLD) / 255)]
for i, (n, c) in enumerate(sw):
    x = 60 + i * 190
    dr.rounded_rectangle([x, 770, x + 160, 850], 8, fill=tuple((c * 255).astype(int)), outline=(90, 60, 30))
    label(dr, (x, 858), n, 15)
    label(dr, (x, 878), '#%02X%02X%02X' % tuple((c * 255).astype(int)), 14)
text_c(dr, (0, 930, W, 1160),
       '', ImageFont.truetype(SANS, 18), MUTED, shadow=False)
notes = ['• Dřevo: tmavý ořech, letokruhy vodorovně; panely skládané z prken se spárou (hloubka bez dalších rámečků).',
         '• Bronz: tenký lem 4–8 px, světlo vždy shora; tmavé linky po obou stranách lemu oddělí prvek od pozadí.',
         '• Nadpisy: světlejší destička s kulatými konci a nýty – hlavní orientační bod každého panelu.',
         '• Stisknuté tlačítko: tmavší dřevo, ztlumený bronz, text o 2 px níž. Vypnuté: vybledlé, bez lesku.',
         '• Vybraná patice / řádek: zlatá záře a světlý proužek vlevo – nikdy jiná barva než zlato.']
for i, t in enumerate(notes):
    label(dr, (60, 930 + i * 34), t, 18)
sheet.convert('RGB').save(os.path.join(OUTDIR, 'bronze_kit.png'))

# ── List 2: ukázka obrazovky Profil (návrhové rozlišení 1560×720) ─────────────
W2, H2 = 1560, 720
scr = Image.open(ROOT + 'app/src/main/res/drawable/menu_bg.png').convert('RGBA').resize((W2, H2), Image.LANCZOS)
scr = Image.fromarray((np.asarray(scr).astype(np.float32) * [0.55, 0.55, 0.55, 1]).astype(np.uint8), 'RGBA')
d2 = ImageDraw.Draw(scr)
main = panel(980, 640)
drop_shadow(scr, main, 290, 50, 6, 8)
text_c(d2, (290, 62, 1270, 112), 'Profil', ImageFont.truetype(CINZEL, 40), GOLD)
scr.alpha_composite(separator(820), (370, 116))
for i, (lab, act) in enumerate([('Přehled', True), ('Vzhled', False), ('Schopnosti', False), ('Debug', False)]):
    scr.alpha_composite(tab(200, 44, lab, act), (350 + i * 218, 140))
scr.alpha_composite(header_plate(860, 46, 'Statistiky'), (350, 204))
scr.alpha_composite(rows(420, [('Výhry', '314', GREEN), ('Online výhry', '35', GREEN), ('Odehráno', '423', CREAM)], 38), (350, 262))
scr.alpha_composite(rows(420, [('Úspěšnost', '74 %', (232, 196, 90)), ('Úroveň', '31', GOLD), ('Prach', '11 715', (180, 160, 230))], 38), (790, 262))
scr.alpha_composite(header_plate(860, 46, 'Denní questy'), (350, 392))
quests = [('Vyhraj 3 hry', '2 / 3', GOLD), ('Zahraj 15 útočných karet', '15 / 15', GREEN), ('Vyhraj online', '0 / 1', MUTED)]
scr.alpha_composite(rows(860, quests, 40, selected=1), (350, 450))
drop_shadow(scr, button(220, 52, 'Převzít'), 990, 590, 3, 4)
for i, n in enumerate(['trophy_icon', 'goldcoin_icon', 'diamond_icon']):
    drop_shadow(scr, slot(74, 'empty', icons[n]), 350 + i * 92, 580, 3, 3)
# levá a pravá deska jako v menu
lp = panel(220, 420); drop_shadow(scr, lp, 40, 160, 5, 6)
av = Image.open(ROOT + 'app/src/main/res/drawable/player_icon_1.png').convert('RGBA').resize((110, 110), Image.LANCZOS)
drop_shadow(scr, slot(130, 'selected', None), 85, 190, 3, 3); scr.alpha_composite(av, (95, 200))
text_c(d2, (40, 335, 260, 370), 'Scrrr', ImageFont.truetype(SANSB, 24), CREAM)
scr.alpha_composite(rows(176, [('Lv.', '31', GOLD), ('Zlato', '530', GOLD), ('Gemy', '24', (126, 200, 227))], 36), (62, 380))
rp = panel(220, 420); drop_shadow(scr, rp, 1300, 160, 5, 6)
for i, lab in enumerate(['Obchod', 'Nastavení', 'Zpět']):
    drop_shadow(scr, button(170, 56, lab, 'normal', ImageFont.truetype(CINZEL, 20)), 1325, 210 + i * 86, 3, 4)
scr.convert('RGB').save(os.path.join(OUTDIR, 'bronze_profile_mock.png'))
print('ok')

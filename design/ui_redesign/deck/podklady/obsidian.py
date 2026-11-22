"""Tvorba balíčku – návrh Obsidián & bronz (překreslení celé obrazovky, 2340×1080 = telefon)."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance, ImageChops

ROOT = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/'
DR = ROOT + 'app/src/main/res/drawable/'
S = 'C:/Users/Scratch/AppData/Local/Temp/claude/ea80e39437178693/c6655303-cca4-4e10-a4b0-2c34c7a925eb/scratchpad/'
G = 'C:/Users/Scratch/Pictures/Generated/ui/deckbuilder/'
W, H = 2340, 1080
SS = 4  # supersampling for masks

CINZEL = ROOT + 'app/src/main/res/font/cinzel.ttf'
SANS_B = 'C:/Windows/Fonts/segoeuib.ttf'
SANS = 'C:/Windows/Fonts/segoeui.ttf'
SANS_SB = 'C:/Windows/Fonts/seguisb.ttf'

# ── paleta ──────────────────────────────────────────────────────────────
OBS_TOP = (24, 21, 25)
OBS_BOT = (12, 10, 13)
BR_L = (222, 178, 112)
BR = (168, 122, 64)
BR_D = (96, 66, 34)
BR_DD = (58, 40, 22)
TXT = (234, 222, 200)
TXT_M = (150, 136, 116)
GOLD = (226, 188, 118)
RES = {'magie': (88, 138, 214), 'utok': (196, 70, 58), 'kamen': (156, 150, 140), 'chaos': (226, 128, 46)}

shot = Image.open(S + 'db_main.png').convert('RGBA')


def font(p, s): return ImageFont.truetype(p, s)


def vgrad(w, h, c0, c1, a0=255, a1=255):
    t = np.linspace(0, 1, h)[:, None, None]
    c0 = np.array(list(c0) + [a0], float); c1 = np.array(list(c1) + [a1], float)
    arr = (c0 * (1 - t) + c1 * t).repeat(w, axis=1)
    return Image.fromarray(arr.astype(np.uint8), 'RGBA')


def hgrad(w, h, c0, c1, a0=255, a1=255):
    return vgrad(h, w, c0, c1, a0, a1).transpose(Image.Transpose.ROTATE_90).transpose(Image.Transpose.FLIP_TOP_BOTTOM)


def rr_mask(w, h, r, inset=0):
    m = Image.new('L', (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle((inset * SS, inset * SS, (w - inset) * SS - 1, (h - inset) * SS - 1),
                                        max(0, r - inset) * SS, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def ring(w, h, r, t, inset=0):
    return ImageChops.subtract(rr_mask(w, h, r, inset), rr_mask(w, h, r, inset + t))


def paste_masked(c, img, mask, xy):
    layer = img.copy(); layer.putalpha(ImageChops.multiply(img.getchannel('A'), mask))
    c.alpha_composite(layer, xy)


def drop_shadow(c, box, r=10, blur=18, alpha=200, dy=8):
    x0, y0, x1, y1 = box
    sh = Image.new('RGBA', (W, H)); ImageDraw.Draw(sh).rounded_rectangle((x0, y0 + dy, x1, y1 + dy), r, fill=(0, 0, 0, alpha))
    c.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))


def diamond(c, cx, cy, s, col=BR_L, dark=BR_D):
    d = ImageDraw.Draw(c)
    d.polygon([(cx, cy - s), (cx + s, cy), (cx, cy + s), (cx - s, cy)], fill=dark)
    s2 = s * 0.55
    d.polygon([(cx, cy - s2), (cx + s2, cy), (cx, cy + s2), (cx - s2, cy)], fill=col)


def bronze_border(c, box, r, t=2.5, bright=1.0):
    """Bronzový rám: vnější tmavá linka, bronz s přechodem shora dolů, jemný vnitřní odlesk."""
    x0, y0, x1, y1 = box; w, h = x1 - x0, y1 - y0
    k = bright
    top = tuple(min(255, int(v * k)) for v in BR_L); bot = tuple(min(255, int(v * k)) for v in BR_D)
    paste_masked(c, Image.new('RGBA', (w, h), (0, 0, 0, 230)), ring(w, h, r, 1.2), (x0, y0))
    paste_masked(c, vgrad(w, h, top, bot), ring(w, h, r, t, 1.0), (x0, y0))
    paste_masked(c, Image.new('RGBA', (w, h), (255, 236, 200, 26)), ring(w, h, r, 1, 1.0 + t), (x0, y0))


def obsidian_fill(c, box, r, a=238, top=OBS_TOP, bot=OBS_BOT, sheen=True):
    x0, y0, x1, y1 = box; w, h = x1 - x0, y1 - y0
    paste_masked(c, vgrad(w, h, top, bot, a, a), rr_mask(w, h, r), (x0, y0))
    if sheen:   # diagonální skelný lesk v horní třetině
        sh = Image.new('L', (w, h), 0); d = ImageDraw.Draw(sh)
        d.polygon([(0, 0), (w * 0.55, 0), (w * 0.25, h * 0.5), (0, h * 0.5)], fill=16)
        sh = sh.filter(ImageFilter.GaussianBlur(max(6, h // 6)))
        layer = Image.new('RGBA', (w, h), (255, 245, 230, 0)); layer.putalpha(ImageChops.multiply(sh, rr_mask(w, h, r)))
        c.alpha_composite(layer, (x0, y0))


def panel(c, box, r=12, corners=True, a=232):
    drop_shadow(c, box, r)
    obsidian_fill(c, box, r, a)
    bronze_border(c, box, r)
    if corners:
        x0, y0, x1, y1 = box
        for cx, cy in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
            diamond(c, cx, cy, 9)


def text(c, xy, t, f, fill, anchor='la', shadow=True):
    d = ImageDraw.Draw(c)
    if shadow: d.text((xy[0] + 2, xy[1] + 2), t, font=f, fill=(0, 0, 0, 200), anchor=anchor)
    d.text(xy, t, font=f, fill=fill, anchor=anchor)


def engraved(c, xy, t, f, fill=GOLD, anchor='la'):
    """Ražený nápis: tmavý stín dole, jemný světlý okraj nahoře."""
    d = ImageDraw.Draw(c)
    d.text((xy[0], xy[1] + 2), t, font=f, fill=(0, 0, 0, 220), anchor=anchor)
    d.text((xy[0], xy[1] - 1), t, font=f, fill=(255, 240, 210, 60), anchor=anchor)
    d.text(xy, t, font=f, fill=fill, anchor=anchor)


def button(c, box, label=None, primary=False, icon=None, fsize=30, active=False):
    x0, y0, x1, y1 = box; w, h = x1 - x0, y1 - y0; r = 10
    drop_shadow(c, box, r, 10, 170, 5)
    if primary:
        paste_masked(c, vgrad(w, h, (150, 108, 58), (84, 56, 26)), rr_mask(w, h, r), (x0, y0))
        hl = vgrad(w, h // 2, (255, 230, 180), (255, 230, 180), 50, 0)
        paste_masked(c, hl, rr_mask(w, h // 2, r), (x0, y0))
        bronze_border(c, box, r, 2.5, 1.12)
    else:
        obsidian_fill(c, box, r, 245, (30, 26, 30), (14, 12, 15))
        bronze_border(c, box, r, 2.0, 1.25 if active else 0.85)
    tx = (x0 + x1) // 2; ty = (y0 + y1) // 2
    if icon is not None:
        ic = icon.copy(); ic.thumbnail((h - 30, h - 30), Image.LANCZOS)
        f = font(CINZEL, fsize); tw = ImageDraw.Draw(c).textlength(label, font=f)
        total = ic.width + 14 + tw; ix = int(tx - total / 2)
        c.alpha_composite(ic, (ix, ty - ic.height // 2))
        engraved(c, (ix + ic.width + 14, ty), label, f, GOLD, 'lm')
    elif label:
        f = font(CINZEL, fsize)
        if primary:
            d = ImageDraw.Draw(c)
            d.text((tx, ty + 1), label, font=f, fill=(255, 226, 170, 90), anchor='mm')
            d.text((tx, ty - 1), label, font=f, fill=(30, 18, 6), anchor='mm')
        else:
            engraved(c, (tx, ty), label, f, GOLD, 'mm')


def tile(c, box, icon=None, label=None, active=False, fsize=26):
    x0, y0, x1, y1 = box; w, h = x1 - x0, y1 - y0; r = 8
    if active:
        glow = Image.new('RGBA', (W, H)); ImageDraw.Draw(glow).rounded_rectangle((x0 - 4, y0 - 4, x1 + 4, y1 + 4), r + 4, fill=(226, 170, 90, 120))
        c.alpha_composite(glow.filter(ImageFilter.GaussianBlur(10)))
    obsidian_fill(c, box, r, 245, (28, 24, 28) if not active else (44, 34, 26), (12, 10, 13))
    bronze_border(c, box, r, 2.0, 1.3 if active else 0.62)
    if icon is not None:
        ic = icon.copy(); ic.thumbnail((int(w * 0.56), int(h * 0.56)), Image.LANCZOS)
        if not active: ic = ImageEnhance.Brightness(ic).enhance(0.8)
        c.alpha_composite(ic, ((x0 + x1 - ic.width) // 2, (y0 + y1 - ic.height) // 2))
    if label:
        text(c, ((x0 + x1) // 2, (y0 + y1) // 2 - 1), label, font(SANS_SB, fsize), GOLD if active else TXT_M, 'mm', False)


def hline(c, x0, x1, y, center_diamond=True, a=200):
    w = x1 - x0
    t = np.abs(np.linspace(-1, 1, w)); alpha = (np.clip(1 - t ** 3, 0, 1) * a).astype(np.uint8)
    for dy, col in ((0, BR), (1, (0, 0, 0))):
        line = Image.new('RGBA', (w, 1), col + (0,)); line.putalpha(Image.fromarray(alpha[None, :], 'L'))
        c.alpha_composite(line, (x0, y + dy))
    if center_diamond: diamond(c, (x0 + x1) // 2, y, 7)


def vline(c, x, y0, y1, a=200):
    h = y1 - y0
    t = np.abs(np.linspace(-1, 1, h)); alpha = (np.clip(1 - t ** 3, 0, 1) * a).astype(np.uint8)
    for dx, col in ((0, BR), (1, (0, 0, 0))):
        line = Image.new('RGBA', (1, h), col + (0,)); line.putalpha(Image.fromarray(alpha[:, None], 'L'))
        c.alpha_composite(line, (x + dx, y0))
    diamond(c, x, (y0 + y1) // 2, 8)


def medallion(c, cx, cy, r, label, fsize=26):
    box = (cx - r, cy - r, cx + r, cy + r); w = 2 * r
    m = Image.new('L', (w * SS, w * SS), 0); ImageDraw.Draw(m).ellipse((0, 0, w * SS - 1, w * SS - 1), fill=255)
    full = m.resize((w, w), Image.LANCZOS)
    m2 = Image.new('L', (w * SS, w * SS), 0); ImageDraw.Draw(m2).ellipse((3 * SS, 3 * SS, (w - 3) * SS - 1, (w - 3) * SS - 1), fill=255)
    inner = m2.resize((w, w), Image.LANCZOS)
    paste_masked(c, vgrad(w, w, BR_L, BR_D), full, box[:2])
    paste_masked(c, vgrad(w, w, (34, 28, 30), (12, 10, 12)), inner, box[:2])
    text(c, (cx, cy - 1), label, font(SANS_B, fsize), TXT, 'mm', False)


def icon(name, size=None):
    im = Image.open(DR + name).convert('RGBA')
    im = im.crop(im.getchannel('A').getbbox())
    if size: im.thumbnail((size, size), Image.LANCZOS)
    return im


def background():
    bg = Image.open(G + 'db_obsidian_2.png').convert('RGB').resize((W, H), Image.LANCZOS)
    g = ImageEnhance.Color(bg).enhance(0.15)        # pryč s modrozelenou → neutrální černé sklo
    g = ImageEnhance.Brightness(g).enhance(0.7)
    g = ImageEnhance.Contrast(g).enhance(1.35)
    arr = np.asarray(g).astype(float)
    arr = arr * np.array([1.0, 0.95, 0.98])          # nepatrně teplejší (fialovo-hnědá čerň)
    yy, xx = np.mgrid[0:H, 0:W]
    v = 1 - 0.55 * (((xx - W / 2) / (W / 1.6)) ** 2 + ((yy - H / 2) / (H / 1.2)) ** 2)
    arr = arr * np.clip(v, 0.35, 1)[..., None]
    # teplé bronzové světlo shora (velmi slabé)
    glow = np.exp(-(((xx - W * 0.3) / 900) ** 2 + ((yy + 200) / 500) ** 2)) * 18
    arr += glow[..., None] * np.array([1.0, 0.7, 0.38])
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGB').convert('RGBA')


def list_row(c, y, cost, name, art, count, res='magie', h=74):
    x0, x1 = 1432, 2290; box = (x0, y, x1, y + h); w = x1 - x0; r = 8
    drop_shadow(c, box, r, 8, 150, 4)
    obsidian_fill(c, box, r, 240, (26, 22, 26), (14, 12, 15), sheen=False)
    # art vpravo, rozmazaný přechod do obsidiánu
    a = Image.open(DR + art).convert('RGBA')
    aw = 330; a = a.resize((aw, aw), Image.LANCZOS).crop((0, aw // 2 - h // 2 - 20, aw, aw // 2 + h // 2 - 20))
    a = ImageEnhance.Brightness(a).enhance(0.85)
    fade = np.clip(np.linspace(0, 1.6, aw), 0, 1)
    al = Image.fromarray((fade[None, :].repeat(h, 0) * 255).astype(np.uint8), 'L')
    a.putalpha(al)
    ax = x1 - 110 - aw
    paste_masked(c, a, Image.new('L', (aw, h), 255), (ax, y))
    # barva suroviny: tenký proužek vlevo
    paste_masked(c, vgrad(5, h - 18, RES[res], tuple(v // 2 for v in RES[res])), Image.new('L', (5, h - 18), 255), (x0 + 8, y + 9))
    bronze_border(c, box, r, 1.6, 0.7)
    medallion(c, x0 + 52, y + h // 2, 25, str(cost), 26)
    text(c, (x0 + 96, y + h // 2), name, font(SANS_SB, 31), TXT, 'lm')
    # počet
    bx = (x1 - 92, y + 16, x1 - 18, y + h - 16)
    obsidian_fill(c, bx, 6, 250, (20, 16, 18), (10, 8, 10), False)
    bronze_border(c, bx, 6, 1.5, 0.9)
    text(c, ((bx[0] + bx[2]) // 2, (bx[1] + bx[3]) // 2 + 1), f'×{count}', font(SANS_B, 25), GOLD, 'mm', False)


def render():
    c = background()
    # ── horní lišta ──
    button(c, (40, 18, 250, 86), '‹  Zpět', fsize=30)
    res_icons = [icon('magie_icon.png'), icon('utok_icon.png'), icon('kamen_icon2.png'), icon('chaos_icon.png'), icon('mine_icon.png')]
    x = 330
    for i, ic in enumerate(res_icons):
        tile(c, (x, 18, x + 70, 88), ic, active=(i == 3)); x += 82
    x += 38
    for rr in ('common', 'rare', 'epic', 'legendary'):
        g = Image.open(DR + f'rarity_{rr}.png').convert('RGBA').crop((355, 4, 445, 94))
        tile(c, (x, 18, x + 70, 88), g); x += 82
    hline(c, 30, 1380, 112)
    vline(c, 1404, 30, 1050)
    # karty (beze změny – kreslí je hra)
    cards = shot.crop((95, 136, 1302, 946))
    drop_shadow(c, (99, 140, 1299, 942), 12, 30, 160, 12)
    c.alpha_composite(cards, (95, 132))
    # šipky stránek
    for cx, ch in ((40, '‹'), (1362, '›')):
        b = (cx - 26, 505, cx + 26, 575)
        obsidian_fill(c, b, 8, 245, (28, 24, 28), (12, 10, 13)); bronze_border(c, b, 8, 2.0, 0.8)
        engraved(c, (cx, 538), ch, font(CINZEL, 44), GOLD, 'mm')
    # spodní lišta
    hline(c, 30, 1380, 968, True, 170)
    x = 88
    for i, lab in enumerate(['0', '1', '2', '3', '4', '5', '6', '7+']):
        tile(c, (x, 992, x + 62, 1056), label=lab, active=(i == 2), fsize=27); x += 72
    sb = (700, 992, 1150, 1056)
    obsidian_fill(c, sb, 10, 250, (8, 7, 9), (18, 15, 18), False)   # vtlačené pole
    bronze_border(c, sb, 10, 1.8, 0.75)
    d = ImageDraw.Draw(c)
    d.ellipse((728, 1008, 756, 1036), outline=TXT_M, width=4); d.line((752, 1032, 764, 1044), fill=TXT_M, width=5)
    text(c, (786, 1024), 'Hledat…', font(SANS, 30), (120, 108, 94), 'lm', False)
    tile(c, (1170, 992, 1234, 1056), icon('lock_icon.png'))
    text(c, (1320, 1024), '1 / 24', font(SANS_SB, 28), TXT_M, 'mm', False)

    # ── pravá strana ──
    button(c, (1432, 18, 1860, 86), 'Chaos', icon=icon('chaos_icon.png'), fsize=34, active=True)
    button(c, (1876, 22, 1936, 82), '✎', fsize=26)
    ImageDraw.Draw(c)  # tužka Cinzelem nemá glyf → překreslit symbolovým fontem
    ob = (1878, 24, 1934, 80); obsidian_fill(c, ob, 9, 255, (30, 26, 30), (14, 12, 15)); bronze_border(c, ob, 9, 2.0, 0.85)
    text(c, (1906, 52), '✎', font('C:/Windows/Fonts/seguisym.ttf', 30), GOLD, 'mm', False)
    button(c, (2060, 18, 2290, 86), 'Šablony', primary=True, fsize=30)

    sp = (1432, 120, 2290, 492)
    panel(c, sp, 12)
    engraved(c, (1470, 168), 'SLOŽENÍ BALÍČKU', font(CINZEL, 27), GOLD, 'lm')
    text(c, (1846, 168), '30/30', font(SANS_B, 30), (170, 200, 120), 'rm', False)
    # ikony a pruhy
    stats = [('magie_icon.png', 11, 'magie', 1470, 255), ('utok_icon.png', 3, 'utok', 1670, 255),
             ('kamen_icon2.png', 4, 'kamen', 1470, 360), ('chaos_icon.png', 12, 'chaos', 1670, 360)]
    for ic, n, rk, x, y in stats:
        im = icon(ic, 40); c.alpha_composite(im, (x, y - im.height // 2))
        text(c, (x + 56, y), str(n), font(SANS_B, 40), TXT, 'lm')
        bar = (x, y + 40, x + 160, y + 50)
        obsidian_fill(c, bar, 5, 255, (8, 7, 8), (20, 17, 19), False); bronze_border(c, bar, 5, 1.0, 0.5)
        fw = max(10, int(160 * n / 12 * 0.62))
        fb = (x + 1, y + 41, x + 1 + fw, y + 49)
        paste_masked(c, hgrad(fw, 8, tuple(v // 2 for v in RES[rk]), RES[rk]), rr_mask(fw, 8, 4), fb[:2])
    vline(c, 1878, 140, 472, 160)
    engraved(c, (1906, 168), 'MANA KŘIVKA', font(CINZEL, 27), GOLD, 'lm')
    vals = [0, 5, 9, 7, 2, 2, 4, 1]; labs = ['0', '1', '2', '3', '4', '5', '6', '7+']
    base = 432; maxh = 170
    for i, (v, lb) in enumerate(zip(vals, labs)):
        cx = 1922 + i * 46
        if v:
            bh = int(maxh * v / 9); b = (cx - 15, base - bh, cx + 15, base)
            paste_masked(c, vgrad(30, bh, BR_L, BR_D), rr_mask(30, bh, 3), b[:2])
            paste_masked(c, Image.new('RGBA', (30, bh), (0, 0, 0, 255)), ring(30, bh, 3, 1), b[:2])
            text(c, (cx, base - bh - 18), str(v), font(SANS_B, 26), TXT, 'mm')
        text(c, (cx, base + 26), lb, font(SANS_SB, 24), TXT_M if v else (90, 80, 70), 'mm', False)
    hline(c, 1906, 2270, base + 1, False, 140)

    # nadpis skupiny
    im = icon('magie_icon.png', 30); c.alpha_composite(im, (1436, 540 - im.height // 2))
    engraved(c, (1478, 540), 'MAGIE  (11)', font(CINZEL, 26), GOLD, 'lm')
    f = font(CINZEL, 26); tw = ImageDraw.Draw(c).textlength('MAGIE  (11)', font=f)
    hline(c, int(1478 + tw + 20), 2290, 541, False, 150)
    rows = [(1, 'Nestabilní vír', 'art_nestabilni_vir.png', 2), (1, 'Rychlá magie', 'art_rychla_magie.png', 2),
            (2, 'Temný rituál', 'art_temny_ritual.png', 2), (3, 'Chaotický důl', 'art_chaoticky_dul.png', 1),
            (3, 'Entropie', 'art_entropie.png', 2), (5, 'Bouřlivá mysl', 'art_bourliva_mysl.png', 2)]
    y = 580
    for cost, name, art, n in rows:
        list_row(c, y, cost, name, art, n); y += 86
    # dolní okraj seznamu do ztracena
    fade = vgrad(900, 70, (0, 0, 0), (0, 0, 0), 0, 255)
    bgcrop = background().crop((1420, 1010, 2320, 1080)); bgcrop.putalpha(fade.getchannel('A'))
    c.alpha_composite(bgcrop, (1420, 1010))
    # posuvník
    paste_masked(c, Image.new('RGBA', (4, 900), (60, 46, 30, 200)), rr_mask(4, 900, 2), (2310, 130))
    paste_masked(c, vgrad(6, 260, BR_L, BR_D), rr_mask(6, 260, 3), (2309, 140))
    return c.convert('RGB')


if __name__ == '__main__':
    im = render()
    im.save(S + 'obsidian.png')
    print('ok')

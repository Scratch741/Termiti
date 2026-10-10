"""Barevné sjednocení hradů a hradeb hráče (castle_player*, wall_player*).

Skiny vznikaly postupně a kámen má pokaždé jiný odstín (tyrkysový, modrý, olivový, teple šedý),
sytost i jas. Skript přebarví KÁMEN na jednu společnou paletu – chladně šedý kámen s lehkým
tyrkysovým nádechem – a srovná jas a kontrast. Akcenty (svítící okna, dřevo dveří, prapory)
nechává být: mění se jen málo syté pixely.

Tematické skiny kampaně (baziny, citadela, drak, hory, goblin, castle_player_4 = goblinská chýše)
mají vlastní barevnost záměrně a skript se jich netýká.

Zdroj: tools/castle_src/*.png (původní soubory, aby šel skript pouštět znovu).
Použití: python tools/grade_castles.py [--stats] [výstupní složka]
"""
import os
import sys
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'tools', 'castle_src')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable')
args = [a for a in sys.argv[1:] if not a.startswith('--')]
OUT = args[0] if args else RES
STATS = '--stats' in sys.argv

CASTLES = ['castle_player'] + ['castle_player_%d' % i for i in (2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13)]
WALLS = ['wall_player%d' % i for i in (2, 3, 4, 5, 6)]

# Cílová paleta kamene
T_HUE = 172.0    # chladná šedá do tyrkysova
T_SAT = 0.19     # medián sytosti kamene
T_V50 = 0.24     # medián jasu
T_V95 = 0.55     # světla
HUE_KEEP = 0.35  # kolik místní odchylky odstínu zůstane (mech, stíny) – jinak by kámen zplacatěl


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


def circ_mean(h, w):
    a = np.deg2rad(h)
    return np.rad2deg(np.arctan2((np.sin(a) * w).sum(), (np.cos(a) * w).sum())) % 360


def stone_weight(h, s, v, ms=0.19):
    """1 = kámen (málo sytý), 0 = akcent (svítící okno, dřevo, prapor).
    ms = medián sytosti kamene v obrázku: u šedých hradů je i málo syté dřevo zřetelný akcent."""
    w = 1 - smooth(s, 0.42, 0.62)
    # teplé plochy zřetelně sytější než kámen = světlo v oknech a dřevo dveří
    warm = (smooth(h, 8, 18) * (1 - smooth(h, 52, 64)))
    w *= 1 - warm * smooth(s, ms * 1.5 + 0.05, ms * 2.2 + 0.11)
    return w


def stats(name):
    a = np.asarray(Image.open(os.path.join(SRC, name + '.png')).convert('RGBA')).astype(np.float64) / 255
    h, s, v = rgb2hsv(a[..., :3])
    m = (a[..., 3] > 0.5) & (stone_weight(h, s, v) > 0.5) & (v > 0.08)
    return circ_mean(h[m], (s * v)[m]), np.median(s[m]), np.median(v[m]), np.percentile(v[m], 95), a.shape


def grade(name):
    a = np.asarray(Image.open(os.path.join(SRC, name + '.png')).convert('RGBA')).astype(np.float64) / 255
    h, s, v = rgb2hsv(a[..., :3])
    m0 = (a[..., 3] > 0.5) & (stone_weight(h, s, v) > 0.5) & (v > 0.08)
    w = stone_weight(h, s, v, np.median(s[m0]))
    m = (a[..., 3] > 0.5) & (w > 0.5) & (v > 0.08)
    mh = circ_mean(h[m], (s * v)[m]); ms = np.median(s[m])
    v50 = np.median(v[m]); v95 = np.percentile(v[m], 95)
    # odstín: střed na cíl, místní odchylka zmenšená
    dh = (h - mh + 180) % 360 - 180
    # u téměř šedého kamene je odstín šum – po přisycení by dělal barevné fleky, proto ho
    # tím víc srovnáme na cíl a sytost zvedáme plošně (odmocnina tlumí rozdíly)
    keep = HUE_KEEP * min(1.0, ms / T_SAT) ** 2
    hn = T_HUE + dh * keep
    tsat = T_SAT * (0.72 + 0.28 * min(1.0, ms / T_SAT))   # původně šedé hrady o něco méně syté
    sn = np.clip(tsat * np.power(s / max(ms, 1e-3), 0.6), 0, 0.6)
    # jas: gama srovná medián, zisk světla (černá zůstává černá)
    gain = T_V95 / max(v95, 1e-3)
    gamma = np.log(T_V50 / gain) / np.log(max(v50, 1e-3)) if v50 < 1 else 1
    gamma = float(np.clip(gamma, 0.6, 1.6))
    vn = np.clip(gain * np.power(np.clip(v, 0, 1), gamma), 0, 1)
    graded = hsv2rgb(hn, sn, vn)
    # akcenty si drží barvu, jen jdou s jasem zbytku
    accent = hsv2rgb(h, s, np.clip(v * (0.5 + 0.5 * vn / np.maximum(v, 1e-3)), 0, 1))
    rgb = graded * w[..., None] + accent * (1 - w[..., None])
    out = np.dstack([rgb, a[..., 3]])
    Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(
        os.path.join(OUT, name + '.png'), optimize=True)


if STATS:
    extra = ['castle_player_4', 'castle_baziny', 'castle_citadela', 'castle_drak', 'castle_hory',
             'wall_player', 'wall_baziny', 'wall_citadela', 'wall_drak', 'wall_goblin', 'wall_hory']
    for n in CASTLES + WALLS + extra:
        if not os.path.exists(os.path.join(SRC, n + '.png')):
            continue
        mh, ms, v50, v95, shp = stats(n)
        print('%-18s %4dx%-4d hue %5.0f  sat %.2f  v50 %.2f  v95 %.2f' % (n, shp[1], shp[0], mh, ms, v50, v95))
else:
    for n in CASTLES + WALLS:
        grade(n)
    print('graded', len(CASTLES) + len(WALLS))

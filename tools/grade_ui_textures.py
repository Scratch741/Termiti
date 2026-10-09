"""Colour-grade the battle-screen UI textures toward one palette.

Target (measured on the originals, see texstat.py):
  big surfaces (leather, wood, panels)  -> warm brown, hue ~25 deg
  bright trims / glints                 -> bronze-gold, hue ~33 deg, a little less saturated
Only pixels of the brown/orange family are touched (hue 0..60); teal gems etc. stay.

Usage: python grade.py <src dir> <out dir>
"""
import sys, os
import numpy as np
from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]


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


def grade(name, src_hue, surf_hue, trim_hue, surf_sat, trim_sat, trim_lo=0.22, trim_hi=0.50, val=1.0):
    """src_hue = median hue of the texture's brown; it moves to surf_hue (dark) / trim_hue (bright)."""
    im = Image.open(os.path.join(SRC, name + '.png'))
    mode = im.mode
    a = np.asarray(im.convert('RGBA')).astype(np.float64) / 255
    h, s, v = rgb2hsv(a[..., :3])
    hw = np.where(h > 300, h - 360, h)                     # reds just below 0 stay next to oranges
    warm = smooth(hw, -30, -10) * (1 - smooth(hw, 55, 75))  # weight: 1 inside the brown family
    bright = smooth(v, trim_lo, trim_hi)                   # 0 = surface, 1 = trim / glint
    target = surf_hue + (trim_hue - surf_hue) * bright
    # keep 60 % of the local hue variation so the texture does not go flat
    h2 = target + (hw - src_hue) * 0.6
    s2 = s * (surf_sat + (trim_sat - surf_sat) * bright)
    hn = hw + (h2 - hw) * warm
    sn = np.clip(s + (s2 - s) * warm, 0, 1)
    vn = np.clip(v * (1 + (val - 1) * warm), 0, 1)
    rgb = hsv2rgb(hn, sn, vn)
    out = np.dstack([rgb, a[..., 3]])
    res = Image.fromarray((out * 255 + 0.5).astype(np.uint8), 'RGBA')
    if mode != 'RGBA':
        res = res.convert(mode)
    res.save(os.path.join(OUT, name + '.png'), optimize=True)


#      texture             src  surf trim  surfS trimS
grade('bg_top_bar',        17,  23,  33,   0.86, 0.74)
grade('hand_background',   17,  22,  27,   0.88, 0.86)
grade('bg_separator',      17,  24,  33,   0.92, 0.80)
grade('bg_side_panels',    30,  26,  33,   1.12, 0.95, val=1.06)
print('graded')

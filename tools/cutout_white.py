"""Vyřízne objekt z bílého/světlého pozadí (flood fill od okrajů) → PNG s průhledností,
ořízne na obsah a vloží do zadaného rozměru (Fit, průhledné okraje).
  python tools/cutout_white.py <vstup> <výstup> <šířka> <výška> [thresh=48] [pad=0] [stretch=0]"""
import sys
from PIL import Image, ImageDraw, ImageFilter

def cutout(src, dst, W, H, thresh=48, pad=0, stretch=False):
    im = Image.open(src).convert('RGB')
    w, h = im.size
    probe = im.copy(); KEY = (255, 0, 255)
    for x in range(0, w, 8):
        for y in (0, h - 1):
            if probe.getpixel((x, y)) != KEY and sum(probe.getpixel((x, y))) > 600:
                ImageDraw.floodfill(probe, (x, y), KEY, thresh=thresh)
    for y in range(0, h, 8):
        for x in (0, w - 1):
            if probe.getpixel((x, y)) != KEY and sum(probe.getpixel((x, y))) > 600:
                ImageDraw.floodfill(probe, (x, y), KEY, thresh=thresh)
    mask = Image.new('L', (w, h), 255)
    mp = mask.load(); pp = probe.load()
    for y in range(h):
        for x in range(w):
            if pp[x, y] == KEY: mp[x, y] = 0
    mask = mask.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    out = im.convert('RGBA'); out.putalpha(mask)
    out = out.crop(mask.point(lambda v: 255 if v > 20 else 0).getbbox())
    tw, th = W - 2 * pad, H - 2 * pad
    if stretch:  # rub karty: obsah přesně do poměru karty (mírné natažení, ~5 %)
        out = out.resize((tw, th), Image.LANCZOS)
    else:
        k = min(tw / out.width, th / out.height)
        out = out.resize((max(1, round(out.width * k)), max(1, round(out.height * k))), Image.LANCZOS)
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    canvas.paste(out, ((W - out.width) // 2, (H - out.height) // 2), out)
    canvas.save(dst, optimize=True)
    print(dst, canvas.size, 'obsah', out.size)

if __name__ == '__main__':
    a = sys.argv
    cutout(a[1], a[2], int(a[3]), int(a[4]), int(a[5]) if len(a) > 5 else 48, int(a[6]) if len(a) > 6 else 0,
           len(a) > 7 and a[7] == '1')

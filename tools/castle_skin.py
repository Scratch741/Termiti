"""Přemalování skinů hradů a hradeb (stejný tvar, lepší kresba) – pomocné kroky.

  python tools/castle_skin.py prep <out_dir> castle_player wall_player2 ...
      → <out_dir>/<skin>_grey.png: skin zvětšený na výšku 460 px na šedém plátně 512×512
        (vstup pro Qwen Edit „repaint this exact castle…, keep the plain grey background“).

  python tools/castle_skin.py final <skin> <pruhledny.png>
      → výsledek po comfy_remove_bg ořízne přesně na původní rámování skinu
        (stejný poměr i okraje jako původní PNG, jen ve vyšším rozlišení) a uloží
        do app/src/main/res/drawable/<skin>.png.

Spouštět z kořene repa. Postup a prompty: wiki/systems/art-pipeline.md (sekce Castle skins).
"""
import os, sys
from PIL import Image

D = 'app/src/main/res/drawable'
CANVAS, TARGET_H, BOTTOM = 512, 460, 20


def frame(skin):
    w0, h0 = Image.open(f'{D}/{skin}.png').size
    s = TARGET_H / h0
    w = round(w0 * s)
    x = (CANVAS - w) // 2
    y = CANVAS - TARGET_H - BOTTOM
    return (x, y, x + w, y + TARGET_H), s


def prep(out_dir, skins):
    os.makedirs(out_dir, exist_ok=True)
    for skin in skins:
        box, s = frame(skin)
        im = Image.open(f'{D}/{skin}.png').convert('RGBA')
        im = im.resize((box[2] - box[0], TARGET_H), Image.LANCZOS)
        bg = Image.new('RGBA', (CANVAS, CANVAS), (128, 128, 128, 255))
        bg.alpha_composite(im, box[:2])
        bg.convert('RGB').save(f'{out_dir}/{skin}_grey.png')
        print(out_dir + f'/{skin}_grey.png')


def final(skin, src):
    box, _ = frame(skin)
    im = Image.open(src).convert('RGBA')
    if im.size != (CANVAS, CANVAS):
        im = im.resize((CANVAS, CANVAS), Image.LANCZOS)
    out = im.crop(box)
    out.save(f'{D}/{skin}.png', optimize=True)
    print(f'{D}/{skin}.png', out.size)


if __name__ == '__main__':
    a = sys.argv[1:]
    if a and a[0] == 'prep':
        prep(a[1], a[2:])
    elif a and a[0] == 'final':
        final(a[1], a[2])
    else:
        print(__doc__)

from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance, ImageChops
import numpy as np

R = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/app/src/main/res'
D = R + '/drawable/'
S = 'C:/Users/Scratch/AppData/Local/Temp/claude/ea80e39437178693/c6655303-cca4-4e10-a4b0-2c34c7a925eb/scratchpad/'
G = 'C:/Users/Scratch/Pictures/Generated/ui/deckbuilder/'
OUT = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/design/ui_redesign/deck/'
W, H = 2340, 1080
shot = Image.open(S + 'db_main.png').convert('RGBA')
k = 1.17

def r(x0, y0, x1, y1): return tuple(round(v * k) for v in (x0, y0, x1, y1))

PROTECT = [r(85, 118, 327, 458), r(345, 118, 587, 458), r(605, 118, 849, 458), r(866, 118, 1110, 458),
           r(85, 465, 327, 805), r(345, 465, 587, 805), r(605, 465, 849, 805), r(866, 465, 1110, 805),
            r(668, 846, 1005, 910), r(1028, 846, 1090, 910),
           r(1115, 860, 1190, 900), r(4, 433, 62, 492), r(1136, 433, 1192, 492),
           r(1222, 120, 1958, 424), r(1222, 455, 1960, 923), r(1965, 110, 1985, 410)]
PROTECT += [r(x, 10, x + 66, 77) for x in (355, 433, 512, 590, 668, 780, 858, 937, 1015)]
PROTECT += [r(x, 846, x + 59, 910) for x in (75, 143, 211, 279, 347, 415, 482, 549)]
BUTTONS = {'zpet': r(75, 12, 243, 75), 'deck': r(1228, 14, 1560, 73), 'pen': r(1578, 16, 1640, 70),
           'sab': r(1790, 14, 1975, 73)}

FONT = R + '/font/cinzel.ttf'

def hslice(res, w, h):
    s = Image.open(res if '/' in res or res.endswith('_t.png') else D + res).convert('RGBA'); sw, sh = s.size
    cap = int(sh * 0.62); capd = round(cap * h / sh)
    if w < 2 * capd + 4: capd = w // 2 - 2
    o = Image.new('RGBA', (w, h))
    o.alpha_composite(s.crop((0, 0, cap, sh)).resize((capd, h), Image.LANCZOS), (0, 0))
    o.alpha_composite(s.crop((sw - cap, 0, sw, sh)).resize((capd, h), Image.LANCZOS), (w - capd, 0))
    o.alpha_composite(s.crop((cap, 0, sw - cap, sh)).resize((w - 2 * capd, h), Image.LANCZOS), (capd, 0))
    return o

def text(o, t, size, color, dx=0, font=None):
    d = ImageDraw.Draw(o); f = ImageFont.truetype(font or FONT, size)
    bb = d.textbbox((0, 0), t, font=f)
    x = (o.width - (bb[2] - bb[0])) // 2 - bb[0] + dx; y = (o.height - (bb[3] - bb[1])) // 2 - bb[1]
    d.text((x + 2, y + 2), t, font=f, fill=(0, 0, 0, 180)); d.text((x, y), t, font=f, fill=color)

def compose(bgname, btn, bright=0.95):
    bg = Image.open(G + bgname).convert('RGBA').resize((W, H), Image.LANCZOS)
    bg = ImageEnhance.Brightness(bg).enhance(bright)
    # soft darkening behind the card grid so the cards stay the focus
    sh = Image.new('L', (W, H), 0); ImageDraw.Draw(sh).rounded_rectangle(r(70, 105, 1125, 820), 40, fill=80)
    sh = sh.filter(ImageFilter.GaussianBlur(50))
    bg = Image.composite(Image.new('RGBA', (W, H), (0, 0, 0, 255)), bg, sh)
    # replace only the dark background pixels of the screenshot, keep the gold separators
    a = np.asarray(shot.convert('RGB')).astype(int)
    lum = a.max(axis=2)
    m = (lum < 70).astype(np.uint8) * 255
    mask = Image.fromarray(m, 'L')
    for b in list(PROTECT) + list(BUTTONS.values()):
        ImageDraw.Draw(mask).rectangle(b, fill=0)
    for b in BUTTONS.values():   # old buttons: fully replaced
        ImageDraw.Draw(mask).rectangle(b, fill=255)
    # panels (stats, deck list): dark parts become smoky glass so the background shows through
    pm = Image.new('L', (W, H), 0)
    for b in (r(1222, 120, 1958, 424), r(1222, 455, 1960, 923)):
        ImageDraw.Draw(pm).rectangle(b, fill=255)
    glass = Image.fromarray(((lum < 45) * 115).astype(np.uint8), 'L')
    pm = ImageChops.multiply(pm, glass)
    shot2 = Image.composite(bg, shot, pm)
    out = Image.composite(bg, shot2, mask)
    # panel behind deck list: slight dark glass so the right side reads as one block
    gl = Image.new('RGBA', (W, H)); ImageDraw.Draw(gl).rounded_rectangle(r(1212, 105, 1990, 918), 24, fill=(0, 0, 0, 0))
    # buttons
    x0, y0, x1, y1 = BUTTONS['zpet']; b = hslice(btn, x1 - x0, y1 - y0); text(b, 'Zpět', 30, (236, 222, 190)); out.alpha_composite(b, (x0, y0))
    x0, y0, x1, y1 = BUTTONS['deck']; b = hslice(btn, x1 - x0, y1 - y0)
    icon = shot.crop((1567, 32, 1605, 76)); b.alpha_composite(icon, (110, (y1 - y0 - 44) // 2)); text(b, 'Chaos', 32, (240, 205, 120), 25)
    out.alpha_composite(b, (x0, y0))
    x0, y0, x1, y1 = BUTTONS['pen']; b = hslice(btn, x1 - x0, y1 - y0); text(b, '✎', 28, (200, 185, 150), font='C:/Windows/Fonts/seguisym.ttf'); out.alpha_composite(b, (x0, y0))
    x0, y0, x1, y1 = BUTTONS['sab']; b = hslice(btn, x1 - x0, y1 - y0); text(b, 'Šablony', 28, (240, 205, 120)); out.alpha_composite(b, (x0, y0))
    return out.convert('RGB')

if __name__ == '__main__':
    import sys
    names = [('kamen', 'db_bg_kamen.png', 'btn_zelezo_2_t.png'), ('stul', 'db_bg_stul.png', 'btn_bronz_2_t.png'), ('knihovna', 'db_bg_knihovna.png', 'btn_kamen_1_t.png'), ('arkana', 'db_bg_arkana.png', 'btn_kamen_1_t.png')]
    for n, f, b in names:
        compose(f, S + b).save(S + f'dbbg_{n}.png'); print(n)

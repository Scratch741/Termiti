import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

D = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/app/src/main/res/drawable/'
G = 'C:/Users/Scratch/Pictures/Generated/ui/menu/'
S = 'C:/Users/Scratch/AppData/Local/Temp/claude/ea80e39437178693/c6655303-cca4-4e10-a4b0-2c34c7a925eb/scratchpad/'

src = sys.argv[1] if len(sys.argv) > 1 else 'menu_mage_v2.png'
scale = float(sys.argv[2]) if len(sys.argv) > 2 else 1.25
head_frac = float(sys.argv[3]) if len(sys.argv) > 3 else 0.22   # kde v okně má být hlava mága
head = (int(sys.argv[4]), int(sys.argv[5])) if len(sys.argv) > 5 else (520, 190)

bg = Image.open(D + 'menu_bg.png').convert('RGB')
X0, Y0, X1, Y1, CH = 368, 87, 1371, 831, 40
WW, WH = X1 - X0, Y1 - Y0

art = Image.open(G + src).convert('RGB')
art = art.resize((round(art.width * scale), round(art.height * scale)), Image.LANCZOS)
hx, hy = head[0] * scale, head[1] * scale
left = int(hx - WW / 2); top = int(hy - WH * head_frac)
top = max(0, min(top, art.height - WH)); left = max(0, min(left, art.width - WW))
win = art.crop((left, top, left + WW, top + WH))
print('crop', left, top, art.size)

# ztmavit spodek do tónu rámu a jemná vinětace do krajů okna
a = np.asarray(win).astype(float)
yy, xx = np.mgrid[0:WH, 0:WW]
v = 1 - 0.12 * (((xx - WW / 2) / (WW / 2)) ** 2) - 0.15 * np.clip((yy / WH - 0.75) / 0.25, 0, 1)
a = a * np.clip(v, 0.4, 1)[..., None]
# teplý nádech jako zbytek menu
a = a * np.array([1.04, 0.98, 0.94])
win = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

# maska okna se zkosenými rohy
m = Image.new('L', bg.size, 0)
ImageDraw.Draw(m).polygon([(X0 + CH, Y0), (X1 - CH, Y0), (X1, Y0 + CH), (X1, Y1 - CH), (X1 - CH, Y1), (X0 + CH, Y1), (X0, Y1 - CH), (X0, Y0 + CH)], fill=255)
m = m.filter(ImageFilter.GaussianBlur(1.2))
layer = bg.copy(); layer.paste(win, (X0, Y0))
out = Image.composite(layer, bg, m)

# vnitřní stín pod rámem (okno je zapuštěné)
sh = Image.new('L', bg.size, 0); ImageDraw.Draw(sh).rectangle((0, 0, bg.width, bg.height), fill=255)
inner = m.filter(ImageFilter.GaussianBlur(26))
shadow = np.asarray(m).astype(float) / 255 * (1 - np.asarray(inner).astype(float) / 255)
o = np.asarray(out).astype(float) * (1 - 0.85 * shadow[..., None])
out = Image.fromarray(np.clip(o, 0, 255).astype(np.uint8))
out.save(D + 'menu_bg_mage.jpg', quality=90)
p = out.copy(); p.thumbnail((1200, 700)); p.save(S + 'menu_mage_prev.png')
print('ok')

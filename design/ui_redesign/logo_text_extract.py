import sys, numpy as np
from PIL import Image, ImageFilter
src, out, prev = sys.argv[1:4]
lg = Image.open(src).convert('RGBA')
a = np.asarray(lg).astype(np.float32)
# oblast písmen (bez zdobeného rámu desky)
x0, y0, x1, y1 = 276, 120, 1112, 270
c = a[y0:y1, x0:x1]
lum = 0.299 * c[..., 0] + 0.587 * c[..., 1] + 0.114 * c[..., 2]
warm = c[..., 0] - c[..., 2]          # zlato: R výrazně víc než B
m = np.clip((lum - 16) / 20, 0, 1) * np.clip((warm - 13) / 14, 0, 1) * (c[..., 3] / 255)
# horní ozdoba desky uprostřed (nad K/M) a spodní lem desky nejsou písmena
yy, xx = np.mgrid[y0:y1, x0:x1]
m[(yy < 140) & (xx > 590) & (xx < 810)] = 0
m[yy > 258] = 0
# horní okraj desky má teplou texturu: nad úrovní písmen bereme jen jasné patky (vršek D)
m[(yy < 139) & (lum < 70)] = 0
m[(yy < 147) & (xx > 430)] = 0     # písmena vpravo od D začínají až na y≈150 (nad M je cíp ozdoby)
# tmavší hnědé zkosení bereme jen těsně u jasného těla písmen (do ~6 px),
# skvrny teplé textury desky v mezerách mezi písmeny tak vypadnou
body = Image.fromarray(((lum > 85) & (warm > 30)).astype(np.uint8) * 255)
near = np.asarray(body.filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.GaussianBlur(1.5))).astype(np.float32) / 255
m = m * np.clip(near * 1.5, 0, 1)
# odstranit drobné světlé tečky (škrábance desky): zahodit malé oddělené skvrny.
# (Dřív tu bylo zúžení a rozšíření masky – to ale usekávalo tenké patky D a spodek G.)
from collections import deque
b = m > 0.25
lab = np.zeros(b.shape, np.int32); keep = np.zeros(b.shape, bool); n = 0
for sy, sx in zip(*np.nonzero(b)):
    if lab[sy, sx]:
        continue
    n += 1; q = deque([(sy, sx)]); lab[sy, sx] = n; pts = []
    while q:
        y, x = q.popleft(); pts.append((y, x))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                ny, nx = y + dy, x + dx
                if 0 <= ny < b.shape[0] and 0 <= nx < b.shape[1] and b[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n; q.append((ny, nx))
    if len(pts) >= 150:
        ys, xs = zip(*pts); keep[list(ys), list(xs)] = True
grow = np.asarray(Image.fromarray(keep.astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(3))) > 0
m = m * grow
mask = Image.fromarray((m * 255).astype(np.uint8))
# uzavření: vyplní tmavé skvrnky uvnitř písmen a vrátí tmavé hrany zkosení (původní barvy)
mask = mask.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.GaussianBlur(0.7))
pad = 28
W, H = mask.width + 2 * pad, mask.height + 2 * pad
big = Image.new('L', (W, H), 0); big.paste(mask, (pad, pad))
shadow = big.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.GaussianBlur(9))
sh = np.asarray(shadow).astype(np.float32) / 255 * 0.85
rgb = np.zeros((H, W, 3), np.float32)
rgb[pad:-pad, pad:-pad] = c[..., :3]
al = np.asarray(big).astype(np.float32) / 255
# složení: písmena přes tmavý stín
outa = al + sh * (1 - al)
outrgb = (rgb * al[..., None] + np.array([8, 5, 4]) * (sh * (1 - al))[..., None]) / np.maximum(outa, 1e-6)[..., None]
res = np.dstack([outrgb, outa * 255]).clip(0, 255).astype(np.uint8)
im = Image.fromarray(res, 'RGBA')
im = im.crop(im.getbbox())
im.save(out, optimize=True)
print(im.size)
# náhledy: na purpurové a na pozadí s mágem
pv = Image.new('RGBA', (im.width + 40, im.height * 2 + 60), (255, 0, 255, 255))
pv.alpha_composite(im, (20, 20))
bg = Image.open(sys.argv[4]).convert('RGBA')
bg = bg.resize((2340, round(bg.height * 2340 / bg.width)))
cy = (bg.height - 1080) // 2 + 30
pv.paste(bg.crop((1196 - im.width // 2 - 20, cy, 1196 + im.width // 2 + 20, cy + im.height + 20)), (0, im.height + 40))
pv.alpha_composite(im, (20, im.height + 50))
pv.resize((pv.width // 2 * 1, pv.height // 2 * 1)).save(prev)

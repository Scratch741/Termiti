import numpy as np, sys
from PIL import Image, ImageFilter
from collections import deque
B = sys.argv[1]
o = np.asarray(Image.open('menu_bg_mage.jpg').convert('RGB')).astype(float)
c = np.asarray(Image.open('C:/Users/Scratch/Pictures/Generated/ui/menu_bg/menu_bg_bez_krystalu_v1.png').convert('RGB')).astype(float)
rects = [(578,98,44,100),(552,188,40,80),(550,298,46,100),(571,418,36,64),(1095,86,44,72),(1133,178,40,84),(1141,298,48,104),(1143,413,36,72)]
d = np.abs(o - c).max(2)
lum = o @ [0.299, 0.587, 0.114]
teal = o[..., 1] + o[..., 2] - 2 * o[..., 0]   # tyrkysová mlha: G+B výrazně nad R
A = np.zeros(d.shape)
for x, y, w, h in rects:
    sd = d[y:y+h, x:x+w]; st = teal[y:y+h, x:x+w]
    core = (sd > 40) & (st < 60)
    # části krystalu (souvislé plochy aspoň 25 px) -> konvexní obal = celý krystal
    lab = np.zeros(core.shape, int); pts_all = []; n = 0
    for sy, sx in zip(*np.nonzero(core)):
        if lab[sy, sx]: continue
        n += 1; q = deque([(sy, sx)]); lab[sy, sx] = n; pts = []
        while q:
            yy, xx = q.popleft(); pts.append((yy, xx))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = yy + dy, xx + dx
                    if 0 <= ny < h and 0 <= nx < w and core[ny, nx] and not lab[ny, nx]:
                        lab[ny, nx] = n; q.append((ny, nx))
        if len(pts) >= 25: pts_all += pts
    P = sorted(set((xx, yy) for yy, xx in pts_all))
    def cross(o_, a_, b_): return (a_[0]-o_[0])*(b_[1]-o_[1]) - (a_[1]-o_[1])*(b_[0]-o_[0])
    lo, up = [], []
    for p_ in P:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p_) <= 0: lo.pop()
        lo.append(p_)
    for p_ in reversed(P):
        while len(up) >= 2 and cross(up[-2], up[-1], p_) <= 0: up.pop()
        up.append(p_)
    hull = lo[:-1] + up[:-1]
    # krystal vpravo nahoře splývá s tmavým pozadím – obrys zadaný ručně
    if (x, y) == (1095, 86):
        hull = [(1106-x, 89-y), (1125-x, 87-y), (1131-x, 108-y), (1117-x, 152-y), (1103-x, 116-y)]
    from PIL import ImageDraw
    k = 4
    hm = Image.new('L', (w * k, h * k), 0)
    ImageDraw.Draw(hm).polygon([((xx + 0.5) * k, (yy + 0.5) * k) for xx, yy in hull], fill=255)
    filled = np.asarray(hm.resize((w, h), Image.LANCZOS)) > 100
    sm = Image.fromarray((filled * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.9))
    A[y:y+h, x:x+w] = np.asarray(sm) / 255
rgba = np.dstack([o, A * 255]).astype(np.uint8); rgba[rgba[..., 3] == 0] = 0
Image.fromarray(rgba, 'RGBA').save('menu_bg_crystals.png', optimize=True)
prev = Image.new('RGB', (8 * 70, 120), (255, 0, 255))
P = Image.open('menu_bg_crystals.png')
for i, (x, y, w, h) in enumerate(rects):
    t = Image.new('RGBA', (w, h), (255, 0, 255, 255)); t.alpha_composite(P.crop((x, y, x + w, y + h))); prev.paste(t.convert('RGB'), (i * 70, 5))
prev.resize((8 * 140, 240)).save(B + '/sprites.png')

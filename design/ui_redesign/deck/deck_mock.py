import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

R = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/app/src/main/res'
D = R + '/drawable/'
S = 'C:/Users/Scratch/AppData/Local/Temp/claude/ea80e39437178693/c6655303-cca4-4e10-a4b0-2c34c7a925eb/scratchpad/'
OUT = 'C:/Users/Scratch/AndroidStudioProjects/Termiti/design/ui_redesign/deck/'
W, H = 2340, 1080
shot = Image.open(S + 'db_main.png').convert('RGBA')
FONT = R + '/font/cinzel.ttf'

def font(sz): return ImageFont.truetype(FONT, sz)

def crop(b): return shot.crop(b)

GRID = crop((99, 140, 1299, 942))           # 8 cards
STATS = crop((1433, 146, 2287, 491))
LIST = crop((1433, 540, 2287, 1080))
FILT = crop((415, 14, 1270, 88))
MANA = crop((88, 992, 708, 1062))
SEARCH = crop((784, 992, 1174, 1062))

def nine(src, w, h, c, cd):
    s = Image.open(D + src).convert('RGBA'); sw, sh = s.size
    o = Image.new('RGBA', (w, h))
    xs = [0, c, sw - c, sw]; ys = [0, c, sh - c, sh]
    xd = [0, cd, w - cd, w]; yd = [0, cd, h - cd, h]
    for i in range(3):
        for j in range(3):
            p = s.crop((xs[i], ys[j], xs[i+1], ys[j+1])).resize((xd[i+1]-xd[i], yd[j+1]-yd[j]), Image.LANCZOS)
            o.alpha_composite(p, (xd[i], yd[j]))
    return o

def plaque(text, w, h=74, size=34):
    s = Image.open(D + 'dialog_title_plaque.png').convert('RGBA'); sw, sh = s.size
    cap = 165; capd = round(cap * h / sh)
    o = Image.new('RGBA', (w, h))
    o.alpha_composite(s.crop((0, 0, cap, sh)).resize((capd, h)), (0, 0))
    o.alpha_composite(s.crop((sw-cap, 0, sw, sh)).resize((capd, h)), (w-capd, 0))
    o.alpha_composite(s.crop((cap, 0, sw-cap, sh)).resize((w-2*capd, h)), (capd, 0))
    gold_text(o, text, size)
    return o

def gold_text(o, text, size, color=(240, 205, 120)):
    d = ImageDraw.Draw(o); f = font(size)
    bb = d.textbbox((0, 0), text, font=f)
    x = (o.width - (bb[2]-bb[0])) // 2 - bb[0]; y = (o.height - (bb[3]-bb[1])) // 2 - bb[1]
    d.text((x+2, y+2), text, font=f, fill=(0, 0, 0, 200))
    d.text((x, y), text, font=f, fill=color)

def button(res, text, w, h=80, size=28, color=(240, 225, 190)):
    b = Image.open(D + res).convert('RGBA').resize((w, h), Image.LANCZOS)
    gold_text(b, text, size, color)
    return b

def medal(icon, r=34, ring=(176, 128, 60)):
    o = Image.new('RGBA', (2*r+8, 2*r+8)); d = ImageDraw.Draw(o)
    d.ellipse((4, 6, 2*r+4, 2*r+6), fill=(0, 0, 0, 150))
    d.ellipse((2, 2, 2*r+2, 2*r+2), fill=ring)
    d.ellipse((7, 7, 2*r-3, 2*r-3), fill=(70, 46, 20))
    d.ellipse((10, 10, 2*r-6, 2*r-6), fill=(24, 16, 10))
    ic = icon.resize((2*r-16, 2*r-16), Image.LANCZOS)
    ic = ImageEnhance.Brightness(ic).enhance(1.6)
    m = Image.new('L', ic.size, 0); ImageDraw.Draw(m).ellipse((0, 0, ic.width-1, ic.height-1), fill=255)
    o.paste(ic, (10, 10), m)
    return o

def icons():
    # 9 filter icons from the top bar, centred in their slots
    xs = [449, 543, 635, 729, 820, 952, 1044, 1135, 1228]
    return [shot.crop((x-24, 28, x+24, 76)) for x in xs]

def key_icon(ic):
    # drop the dark slot background so only the glyph remains
    px = ic.copy(); data = px.load()
    for y in range(px.height):
        for x in range(px.width):
            r, g, b, a = data[x, y]
            if max(r, g, b) < 60: data[x, y] = (r, g, b, 0)
    return px

def bg(name, dark=0.45, blur=0):
    b = Image.open(D + name).convert('RGBA').resize((W, H), Image.LANCZOS)
    if blur: b = b.filter(ImageFilter.GaussianBlur(blur))
    return ImageEnhance.Brightness(b).enhance(dark)

def shadow(canvas, box, r=30, a=170):
    sh = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(sh)
    d.rounded_rectangle(box, 30, fill=(0, 0, 0, a))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(r)))

def right_panel(c, x0, y0, x1, y1, title):
    shadow(c, (x0+10, y0+20, x1+10, y1+20))
    c.alpha_composite(nine('dialog_frame.png', x1-x0, y1-y0, 170, 70), (x0, y0))
    inner_w = x1 - x0 - 110
    st = STATS.resize((inner_w, round(STATS.height * inner_w / STATS.width)), Image.LANCZOS)
    c.alpha_composite(st, (x0+55, y0+70))
    ly = y0 + 70 + st.height + 6
    li = LIST.resize((inner_w, round(LIST.height * inner_w / LIST.width)), Image.LANCZOS)
    li = li.crop((0, 0, inner_w, y1 - 50 - ly))
    c.alpha_composite(li, (x0+55, ly))
    p = plaque(title, 430)
    c.alpha_composite(p, ((x0+x1)//2 - 215, y0 - 34))

def variant_a():
    c = bg('bg_campaign.png', 0.6, 2)
    c.alpha_composite(button('button_iron.png', 'Zpět', 200), (30, 22))
    ics = icons()
    x = 280
    for i, ic in enumerate(ics):
        m = medal(ic, 34, (200, 150, 70) if i in (0, 3) else (120, 96, 66))
        c.alpha_composite(m, (x, 22)); x += 84 + (24 if i == 4 else 0)
    g = GRID.resize((1180, round(GRID.height*1180/GRID.width)), Image.LANCZOS)
    shadow(c, (110, 140, 1290, 920), 40, 140)
    c.alpha_composite(g, (110, 130))
    c.alpha_composite(MANA, (110, 985)); c.alpha_composite(SEARCH, (820, 985))
    right_panel(c, 1360, 70, 2320, 1065, 'Chaos')
    c.alpha_composite(button('button_bronze.png', 'Šablony', 220, 72, 26, (20, 11, 2)), (2090, 6))
    return c

def variant_b():
    c = bg('menu_bg.png', 0.55)
    # left: cards in a frame too
    shadow(c, (30, 90, 1330, 1060))
    c.alpha_composite(nine('dialog_frame.png', 1300, 970, 170, 70), (30, 90))
    g = GRID.resize((1150, round(GRID.height*1150/GRID.width)), Image.LANCZOS)
    c.alpha_composite(g, (105, 150))
    c.alpha_composite(MANA.resize((520, 59)), (110, 935)); c.alpha_composite(SEARCH.resize((330, 59)), (900, 935))
    c.alpha_composite(plaque('Sbírka', 380), (490, 58))
    c.alpha_composite(button('button_iron.png', 'Zpět', 190, 72, 26), (20, 6))
    f = FILT.resize((700, 61)); c.alpha_composite(f, (630, 4)) if False else None
    ics = icons()
    x = 880
    for i, ic in enumerate(ics[:5]):
        c.alpha_composite(medal(ic, 26), (x, 2)); x += 64
    right_panel(c, 1370, 90, 2320, 1060, 'Chaos')
    c.alpha_composite(button('button_bronze.png', 'Šablony', 210, 72, 26, (20, 11, 2)), (2105, 6))
    return c

def variant_c():
    # player bar on the left, cards in the middle without panel, list as a narrow column
    c = bg('bg_game.jpg', 0.5, 3)
    # vertical filter column with medallions
    shadow(c, (10, 10, 130, 1070))
    c.alpha_composite(nine('dialog_frame.png', 130, 1060, 170, 40), (5, 10))
    ics = icons()
    y = 120
    for i, ic in enumerate(ics):
        c.alpha_composite(medal(ic, 30, (200, 150, 70) if i == 3 else (120, 96, 66)), (31, y)); y += 76 + (20 if i == 4 else 0)
    c.alpha_composite(button('button_iron.png', '<', 100, 70, 30), (20, 30))
    g = GRID.resize((1260, round(GRID.height*1260/GRID.width)), Image.LANCZOS)
    c.alpha_composite(g, (160, 70))
    c.alpha_composite(MANA, (170, 1000)); c.alpha_composite(SEARCH, (900, 1000))
    right_panel(c, 1450, 60, 2330, 1070, 'Chaos')
    return c

v = {'a': variant_a, 'b': variant_b, 'c': variant_c}
for k in (sys.argv[1:] or 'abc'):
    v[k]().convert('RGB').save(S + f'deck_{k}.png')
    print(k)

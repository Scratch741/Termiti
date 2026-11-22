"""Dávková výroba artů karet bez Clauda – ComfyUI na LAN (Krea + reflektor).

Postup (podrobně ve wiki/systems/art-pipeline.md, sekce „Batch script"):

  1) Připrav dávku, např. tools/art_batches/dnes.json:
       [
         {"art": "art_rekrut", "title": "Rekrut",
          "prompt": "Medium shot ... Palette: ..., no teal. No text."},
         {"art": "art_drak", "title": "Drak", "prompt": "...", "count": 4}
       ]
     "art" = název drawable (bez .png). Styl darkmage se připojí sám
     ("styles": [...] ho přepíše, [] = bez stylu).

  2) python tools/art_batch.py gen tools/art_batches/dnes.json
       → varianty do Pictures/Generated/karty/<art>/ a přehled
         design/art_review/<dávka>.png (každá karta jeden řádek, čísla 1..N).
       Volitelně jen některé karty:  ... gen dnes.json art_drak art_rekrut

  3) python tools/art_batch.py apply tools/art_batches/dnes.json art_rekrut=2 art_drak=3
       → reflektor na vybranou variantu (bez něj: --no-light),
         res/drawable/<art>.png 512×512, server/art/<art>.webp 80×112,
         CardPresentation.kt: artScale = 0.80f bez starých posunů ořezu,
         přegeneruje card_data.json a připíše záznam do wiki/log.md.

Spouštět z kořene repa. Potřebuje jen Python + Pillow.
"""
import json, os, re, sys, time, uuid, random, datetime, subprocess, urllib.request
from PIL import Image, ImageDraw, ImageFont

HOST = os.environ.get('COMFY_HOST', 'http://192.168.125.140:8188')
ROOT = os.getcwd()
GEN = os.path.expanduser('~/Pictures/Generated/karty')
STYLES_FILE = os.path.expanduser('~/Pictures/ComfyWorkflow/styles.json')
KREA = os.path.join(ROOT, 'tools/comfy/krea_api.json')
LIGHT = os.path.join(ROOT, 'tools/comfy/art_reflektor_api.json')
REVIEW = os.path.join(ROOT, 'design/art_review')
PRES = os.path.join(ROOT, 'app/src/main/java/com/example/termiti/CardPresentation.kt')


# ── ComfyUI ───────────────────────────────────────────────────────────────────
def _post(path, data, ctype):
    req = urllib.request.Request(HOST + path, data, {'Content-Type': ctype})
    return json.load(urllib.request.urlopen(req, timeout=60))


def upload(path):
    b = uuid.uuid4().hex
    name = 'batch_' + os.path.basename(path)
    body = (f'--{b}\r\nContent-Disposition: form-data; name="image"; filename="{name}"\r\n'
            f'Content-Type: image/png\r\n\r\n').encode() + open(path, 'rb').read() + \
           f'\r\n--{b}\r\nContent-Disposition: form-data; name="overwrite"\r\n\r\ntrue\r\n--{b}--\r\n'.encode()
    return _post('/upload/image', body, 'multipart/form-data; boundary=' + b)['name']


def run(workflow, out_node, dest, timeout=600):
    pid = _post('/prompt', json.dumps({'prompt': workflow}).encode(), 'application/json')['prompt_id']
    t0 = time.time()
    while time.time() - t0 < timeout:
        h = json.load(urllib.request.urlopen(f'{HOST}/history/{pid}', timeout=30))
        if pid in h:
            st = h[pid].get('status', {})
            if st.get('status_str') == 'error':
                raise RuntimeError(f'ComfyUI chyba: {st}')
            if h[pid].get('outputs', {}).get(out_node):
                img = h[pid]['outputs'][out_node]['images'][0]
                q = f"{HOST}/view?filename={img['filename']}&subfolder={img['subfolder']}&type={img['type']}"
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                open(dest, 'wb').write(urllib.request.urlopen(q, timeout=60).read())
                return dest
        time.sleep(1.5)
    raise TimeoutError(pid)


def krea(prompt, seed, dest, aspect='1:1 (Square)', size=(512, 512), mp=1):
    w = json.load(open(KREA, encoding='utf-8'))
    for k in ['30:16', '30:17', '30:18', '30:19', '30:20', '30:21', '30:24', '30:27', '30:28']:
        w.pop(k, None)                                   # bez přepisu promptu přes Qwen
    w['30:6']['inputs']['text'] = prompt
    w['30:23']['inputs']['value'] = False                # bez LoRA darkbrush
    w['30:3']['inputs']['seed'] = seed
    w['49']['inputs']['aspect_ratio'] = aspect
    w['49']['inputs']['megapixels'] = mp                 # 1 MP (čtverec 1024×1024 → zmenší se na 512)
    w['29']['inputs']['filename_prefix'] = 'claude/batch'
    run(w, '29', dest)
    im = Image.open(dest).convert('RGB')
    tw, th = size                                       # ořez na cílový poměr (na střed) a zmenšení
    if im.width / im.height > tw / th:
        nw = round(im.height * tw / th); im = im.crop(((im.width - nw) // 2, 0, (im.width + nw) // 2, im.height))
    else:
        nh = round(im.width * th / tw); im = im.crop((0, (im.height - nh) // 2, im.width, (im.height + nh) // 2))
    if im.size != (tw, th) and (tw < im.width or th < im.height):
        im = im.resize((tw, th), Image.LANCZOS)
    im.save(dest)


def light(src, dest):
    w = json.load(open(LIGHT, encoding='utf-8'))
    w['1']['inputs']['image'] = upload(src)
    w['7']['inputs']['filename_prefix'] = 'claude/batch_light'
    run(w, '7', dest)


# ── pomocné ───────────────────────────────────────────────────────────────────
def load_batch(path):
    items = json.load(open(path, encoding='utf-8'))
    for it in items:
        it.setdefault('count', 4)
        it.setdefault('styles', ['darkmage'])
        it.setdefault('title', it['art'])
        # Volitelně pro ne-čtvercovou grafiku (pozadí, rub karty…): "aspect" = volba
        # ResolutionSelectoru ('16:9 (Widescreen)', '21:9 (Ultrawide)', '2:3 (Portrait Photo)'…),
        # "size" = [š, v] výsledku (ořez na poměr + zmenšení), "mp" = megapixely generování.
        it.setdefault('aspect', '1:1 (Square)')
        it.setdefault('size', [512, 512])
        it.setdefault('mp', 1)
    return items


def full_prompt(it, v=1):
    """Prompt varianty v (1..count). Položka může mít "alt": [další pojetí…] – varianty se
    mezi pojetími střídají (v1 = prompt, v2 = alt[0], …), aby se lišily víc než jen seedem."""
    styles = json.load(open(STYLES_FILE, encoding='utf-8')) if os.path.exists(STYLES_FILE) else {}
    extra = [styles[s]['text'] if isinstance(styles.get(s), dict) else styles.get(s, '') for s in it['styles']]
    prompts = [it['prompt']] + it.get('alt', [])
    return ', '.join([prompts[(v - 1) % len(prompts)]] + [e for e in extra if e])


def variant_path(it, tag, v):
    return os.path.join(GEN, it['art'], f'{it["art"]}_{tag}_v{v}.png')


def font(size):
    for f in ['C:/Windows/Fonts/segoeuib.ttf', 'C:/Windows/Fonts/arialbd.ttf']:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def sheet(rows, dest, sz=260, lab=190):
    cols = max(len(r[1]) for r in rows)
    o = Image.new('RGB', (lab + cols * (sz + 8), len(rows) * (sz + 8)), (18, 18, 18))
    d = ImageDraw.Draw(o); f = font(24)
    for r, (title, paths) in enumerate(rows):
        y = r * (sz + 8)
        d.text((8, y + sz // 2 - 14), title, fill='white', font=f)
        for v, p in enumerate(paths):
            x = lab + v * (sz + 8)
            if os.path.exists(p):
                im = Image.open(p).convert('RGB'); im.thumbnail((sz, sz))
                o.paste(im, (x + (sz - im.width) // 2, y + (sz - im.height) // 2))
            d.rectangle((x, y, x + 34, y + 36), fill='black'); d.text((x + 9, y + 3), str(v + 1), fill='white', font=f)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    o.save(dest)


# ── příkazy ───────────────────────────────────────────────────────────────────
def cmd_gen(batch, only):
    items = [it for it in load_batch(batch) if not only or it['art'] in only]
    tag = os.path.splitext(os.path.basename(batch))[0]
    rows = []
    for it in items:
        base = random.randint(1, 2**40)
        paths = []
        for v in range(1, it['count'] + 1):
            p = variant_path(it, tag, v)
            print(f'{it["title"]}: varianta {v}/{it["count"]}…', flush=True)
            krea(full_prompt(it, v), base + v, p, it['aspect'], tuple(it['size']), it['mp'])
            paths.append(p)
        it['seeds'] = [base + v for v in range(1, it['count'] + 1)]
        rows.append((it['title'], paths))
    out = os.path.join(REVIEW, f'{tag}.png')
    sheet(rows, out)
    # seedy si poznamenej vedle dávky (pro wiki / opakování)
    json.dump({it['art']: it.get('seeds') for it in items},
              open(os.path.splitext(batch)[0] + '.seeds.json', 'w', encoding='utf-8'), indent=1)
    print('Přehled:', out)


def fix_presentation(art):
    s = open(PRES, encoding='utf-8').read()
    i = s.find(f'R.drawable.{art},')
    if i < 0:
        print(f'  ! {art} není v CardPresentation.kt – uprav ručně'); return
    j = s.find('\n        "', i)
    j = len(s) if j < 0 else j
    seg = s[i:j]
    for key in ['artBiasX', 'artBiasY', 'listBiasX', 'listBiasY', 'listScale']:
        seg = re.sub(rf'\s*{key}\s*=\s*-?[\d.]+f\s*,', '', seg)
        seg = re.sub(rf',\s*{key}\s*=\s*-?[\d.]+f', '', seg)
    if re.search(r'artScale\s*=\s*[\d.]+f', seg):
        seg = re.sub(r'artScale\s*=\s*[\d.]+f', 'artScale = 0.80f', seg)
    else:
        seg = re.sub(r'(R\.drawable\.\w+,\s*"[^"]*")', r'\1,\n            artScale = 0.80f', seg, count=1)
    today = datetime.date.today().isoformat()
    seg = re.sub(r'// čtvercový art 512×512 \(\d{4}-\d\d-\d\d\)', f'// čtvercový art 512×512 ({today})', seg)
    s = s[:i] + seg + s[j:]
    open(PRES, 'w', encoding='utf-8', newline='').write(s)


def cmd_apply(batch, picks, use_light):
    items = {it['art']: it for it in load_batch(batch)}
    tag = os.path.splitext(os.path.basename(batch))[0]
    seeds = {}
    sp = os.path.splitext(batch)[0] + '.seeds.json'
    if os.path.exists(sp):
        seeds = json.load(open(sp, encoding='utf-8'))
    log = []
    for art, v in picks:
        it = items[art]
        src = variant_path(it, tag, v)
        final = src
        if use_light:
            final = os.path.join(GEN, art, f'{art}_{tag}_v{v}_light.png')
            print(f'{it["title"]}: reflektor…', flush=True)
            light(src, final)
        im = Image.open(final).convert('RGB').resize((512, 512), Image.LANCZOS)
        for ext in ('.jpg', '.jpeg', '.webp'):                # stejný název s jinou příponou = Duplicate resources
            old = os.path.join(ROOT, f'app/src/main/res/drawable/{art}{ext}')
            if os.path.exists(old):
                os.remove(old); print(f'  smazán starý {art}{ext}')
        im.save(os.path.join(ROOT, f'app/src/main/res/drawable/{art}.png'), optimize=True)
        w = round(512 * 80 / 112); x = (512 - w) // 2
        im.crop((x, 0, x + w, 512)).resize((80, 112), Image.LANCZOS) \
          .save(os.path.join(ROOT, f'server/art/{art}.webp'), 'WEBP', quality=85)
        fix_presentation(art)
        seed = (seeds.get(art) or [None] * v)[v - 1]
        log.append(f'- {it["title"]}: {it["prompt"][:160]}… (krea, varianta {v}'
                   f'{f", seed {seed}" if seed else ""}){" + reflektor" if use_light else ""}.')
        print(f'{it["title"]}: hotovo ({art})')
    subprocess.run(['node', 'tools/regen_card_data.js'], cwd=ROOT)
    today = datetime.date.today().isoformat()
    with open(os.path.join(ROOT, 'wiki/log.md'), 'a', encoding='utf-8', newline='') as f:
        f.write(f'\n## [{today}] art | dávka {tag} (tools/art_batch.py)\n\n' + '\n'.join(log) +
                '\n- 512×512 do `res/drawable`, miniatury `server/art/*.webp` 80×112, `artScale = 0.80f` bez posunů ořezu.\n')
    print('Hotovo. Zkontroluj ve hře, pak commit.')


def main(a):
    if len(a) < 2 or a[0] not in ('gen', 'apply'):
        print(__doc__); sys.exit(1)
    if a[0] == 'gen':
        cmd_gen(a[1], set(a[2:]))
    else:
        use_light = '--no-light' not in a
        picks = [(p.split('=')[0], int(p.split('=')[1])) for p in a[2:] if '=' in p]
        if not picks:
            print('Zadej výběr, např. art_rekrut=2'); sys.exit(1)
        cmd_apply(a[1], picks, use_light)


if __name__ == '__main__':
    main(sys.argv[1:])

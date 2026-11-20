"""Animace artu karty (1–3 s) – image-to-video Wan 2.2 I2V 14B v ComfyUI na LAN.

Postup a doporučení ve wiki/systems/card-animation.md.

  python tools/animate_card.py art_drak "the dragon slowly beats its wings, its tail sways"
  python tools/animate_card.py art_drak "..." --frames 33 --seed 7 --count 2
  python tools/animate_card.py cesta/k/obrazku.png "..." --mode free

  art = název drawable (app/src/main/res/drawable/<art>.png) nebo cesta k obrázku.
  Výstup: ~/Pictures/Generated/animace/<art>/<art>_s<seed>.webp (animovaný WebP),
  syrový výstup ComfyUI v <art>/_tmp/.

Režimy (--mode):
  pingpong (výchozí) – jeden běh WanImageToVideo, přehraný tam a zpět se zpomalením
           v obratech. Bez švu (jeden běh = stejné barvy i kresba), ale pohyb se vrací –
           hodí se na vratný pohyb (houpání, mávání, dýchání, záře), ne na proud ohně.
  free     – jen jeden běh, pohyb jedním směrem (při opakování skok).
  return   – POKUS, neschváleno: krok tam + druhý běh (WanFirstLastFrameToVideo) zpět,
           s 5 snímky kontextu na obou spojích a srovnáním histogramu. Spoje měří dobře,
           ale druhý běh se lokálně liší (sytost, obloha, kresba) – uživatel: „pořád to nesedí“.
Stejný první i poslední snímek v jednom běhu NEFUNGUJE – Wan se pak nehýbe, jen pulzuje jas.

Rychlost: když jsou na serveru LoRA lightx2v (4 kroky), použijí se samy
(4 kroky, cfg 1). Bez nich 20 kroků, cfg 3,5 – zhruba 5× pomalejší.

Spouštět z kořene repa. Potřebuje Python + Pillow + numpy.
"""
import argparse, json, os, random, sys, time, uuid, urllib.request

HOST = os.environ.get('COMFY_HOST', 'http://192.168.125.140:8188')
ROOT = os.getcwd()
DRAWABLE = os.path.join(ROOT, 'app/src/main/res/drawable')
OUT = os.path.expanduser('~/Pictures/Generated/animace')

HIGH = 'Wan2.2/wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors'
LOW = 'Wan2.2/wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors'
TE = 'umt5_xxl_fp8_e4m3fn_scaled.safetensors'
VAE = 'wan_2.1_vae.safetensors'
LORA_HIGH = 'wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors'
LORA_LOW = 'wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors'

# Kamera stojí, hýbou se jen detaily – karta nesmí „ujet“.
STYLE = ('The camera stays still, no zoom, no pan. Clear, continuous motion of the subject. '
         'Painterly dark fantasy illustration, keep the original art style and composition.')
NEGATIVE = ('camera movement, zoom, pan, shaking, morphing, distorted face, extra limbs, '
            'text, watermark, blurry, low quality, flicker, scene change, cartoon, photo, 3d render, '
            '色调艳丽，过曝，静态，细节模糊不清，字幕，画面，静止，最差质量，低质量，JPEG压缩残留，'
            '丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体')


def _get(path):
    return json.load(urllib.request.urlopen(HOST + path, timeout=60))


def _post(path, data, ctype):
    req = urllib.request.Request(HOST + path, data, {'Content-Type': ctype})
    return json.load(urllib.request.urlopen(req, timeout=60))


def upload(path):
    b = uuid.uuid4().hex
    name = 'anim_' + os.path.basename(path)
    body = (f'--{b}\r\nContent-Disposition: form-data; name="image"; filename="{name}"\r\n'
            f'Content-Type: image/png\r\n\r\n').encode() + open(path, 'rb').read() + \
           f'\r\n--{b}\r\nContent-Disposition: form-data; name="overwrite"\r\n\r\ntrue\r\n--{b}--\r\n'.encode()
    return _post('/upload/image', body, 'multipart/form-data; boundary=' + b)['name']


def has_lora():
    loras = _get('/models/loras')
    return LORA_HIGH in loras and LORA_LOW in loras


def _images(w, key, names, size):
    """Načte 1+ nahraných obrázků jako jednu dávku snímků (ImageBatch řetěz)."""
    names = [names] if isinstance(names, str) else list(names)
    out = None
    for i, n in enumerate(names):
        w[f'{key}_l{i}'] = {'class_type': 'LoadImage', 'inputs': {'image': n}}
        w[f'{key}_s{i}'] = {'class_type': 'ImageScale', 'inputs': {'image': [f'{key}_l{i}', 0], 'upscale_method': 'lanczos',
                                                                   'width': size, 'height': size, 'crop': 'center'}}
        cur = [f'{key}_s{i}', 0]
        if out is None:
            out = cur
        else:
            w[f'{key}_b{i}'] = {'class_type': 'ImageBatch', 'inputs': {'image1': out, 'image2': cur}}
            out = [f'{key}_b{i}', 0]
    return out


def workflow(image, prompt, seed, frames, size, fps, end_image, fast, prefix):
    """image / end_image = název nahraného obrázku nebo seznam (víc snímků = navázání pohybu).
    end_image=None → volný pohyb od image; jinak přechod image → end_image."""
    steps, cfg = (4, 1.0) if fast else (20, 3.5)
    half = steps // 2
    w = {
        'high': {'class_type': 'UNETLoader', 'inputs': {'unet_name': HIGH, 'weight_dtype': 'default'}},
        'low': {'class_type': 'UNETLoader', 'inputs': {'unet_name': LOW, 'weight_dtype': 'default'}},
        'clip': {'class_type': 'CLIPLoader', 'inputs': {'clip_name': TE, 'type': 'wan', 'device': 'default'}},
        'vae': {'class_type': 'VAELoader', 'inputs': {'vae_name': VAE}},
        'pos': {'class_type': 'CLIPTextEncode', 'inputs': {'clip': ['clip', 0], 'text': f'{prompt}. {STYLE}'}},
        'neg': {'class_type': 'CLIPTextEncode', 'inputs': {'clip': ['clip', 0], 'text': NEGATIVE}},
    }
    mh, ml = ['high', 0], ['low', 0]
    if fast:
        w['lh'] = {'class_type': 'LoraLoaderModelOnly', 'inputs': {'model': mh, 'lora_name': LORA_HIGH, 'strength_model': 1.0}}
        w['ll'] = {'class_type': 'LoraLoaderModelOnly', 'inputs': {'model': ml, 'lora_name': LORA_LOW, 'strength_model': 1.0}}
        mh, ml = ['lh', 0], ['ll', 0]
    w['sh'] = {'class_type': 'ModelSamplingSD3', 'inputs': {'model': mh, 'shift': 5.0 if fast else 8.0}}
    w['sl'] = {'class_type': 'ModelSamplingSD3', 'inputs': {'model': ml, 'shift': 5.0 if fast else 8.0}}
    common = {'positive': ['pos', 0], 'negative': ['neg', 0], 'vae': ['vae', 0],
              'width': size, 'height': size, 'length': frames, 'batch_size': 1}
    start = _images(w, 'st', image, size)
    if end_image:
        w['i2v'] = {'class_type': 'WanFirstLastFrameToVideo',
                    'inputs': {**common, 'start_image': start, 'end_image': _images(w, 'en', end_image, size)}}
    else:
        w['i2v'] = {'class_type': 'WanImageToVideo', 'inputs': {**common, 'start_image': start}}
    ks = {'noise_seed': seed, 'steps': steps, 'cfg': cfg, 'sampler_name': 'euler', 'scheduler': 'simple',
          'positive': ['i2v', 0], 'negative': ['i2v', 1]}
    w['k1'] = {'class_type': 'KSamplerAdvanced', 'inputs': {**ks, 'model': ['sh', 0], 'latent_image': ['i2v', 2],
               'add_noise': 'enable', 'start_at_step': 0, 'end_at_step': half, 'return_with_leftover_noise': 'enable'}}
    w['k2'] = {'class_type': 'KSamplerAdvanced', 'inputs': {**ks, 'model': ['sl', 0], 'latent_image': ['k1', 0],
               'add_noise': 'disable', 'start_at_step': half, 'end_at_step': 10000, 'return_with_leftover_noise': 'disable'}}
    w['dec'] = {'class_type': 'VAEDecode', 'inputs': {'samples': ['k2', 0], 'vae': ['vae', 0]}}
    # Bezztrátově – snímky se ještě skládají a teprve pak komprimují.
    w['save'] = {'class_type': 'SaveAnimatedWEBP', 'inputs': {'images': ['dec', 0], 'filename_prefix': prefix,
                 'fps': float(fps), 'lossless': True, 'quality': 100, 'method': 'default'}}
    return w


def frames_of(path):
    from PIL import Image
    im = Image.open(path)
    out = []
    for k in range(im.n_frames):
        im.seek(k)
        out.append(im.convert('RGB'))
    return out


CTX = 5   # snímků kontextu na obou koncích návratu (1 + 4 = celé latentní skupiny Wanu)


def stitch_loop(go, back, ctx=CTX):
    """Spojí krok tam + návrat do bezešvé smyčky.
    back začíná snímky go[-ctx:] a končí go[:ctx] (Wan je dostal jako začátek/konec),
    takže v obou spojích navazuje poloha i rychlost pohybu.
    1) Wan v režimu první+poslední snímek posouvá jas a kontrast (Drak: jas 85 → 100,
       černá 0 → 33) → histogram každého snímku návratu se srovná na referenci mezi go[-1] a go[0].
    2) Překryvy (ctx snímků na obou spojích) se plynule prolnou místo tvrdého střihu."""
    import numpy as np
    from PIL import Image
    A = lambda f: np.asarray(f, np.float32)
    go, back = [A(f) for f in go], [A(f) for f in back]
    n = len(back)
    # Srovnání histogramu po kanálech: tónová křivka snímku se přemapuje na rozložení
    # reference (kvantily lineárně mezi go[-1] a go[0]). Jen průměr+odchylka nestačily –
    # Wan v návratu posouvá černou (0 → 33) a kontrast „dýchal“.
    srt_a = [np.sort(go[-1][..., c].ravel()) for c in range(3)]
    srt_b = [np.sort(go[0][..., c].ravel()) for c in range(3)]

    def match(f, t):
        out = np.empty_like(f)
        for c in range(3):
            ch = f[..., c].ravel()
            ref = srt_a[c] * (1 - t) + srt_b[c] * t
            out[..., c].flat[np.argsort(ch, kind='stable')] = ref
        return out
    back = [match(f, k / (n - 1)) for k, f in enumerate(back)]
    w = [(k + 1) / (ctx + 1) for k in range(ctx)]            # váha „nové“ části v překryvu
    head = [back[n - ctx + k] * (1 - w[k]) + go[k] * w[k] for k in range(ctx)]        # konec návratu → začátek
    mid = [go[len(go) - ctx + k] * (1 - w[k]) + back[k] * w[k] for k in range(ctx)]   # konec kroku tam → návrat
    seq = head + go[ctx:len(go) - ctx] + mid + back[ctx:n - ctx]
    return [Image.fromarray(np.clip(f, 0, 255).astype(np.uint8)) for f in seq]


def pingpong(frames, out_per_way=None):
    """Tam a zpět s kosinovým zpomalením v obou obratech (mezisnímky = prolnutí sousedů).
    Drak: 33 snímků → 2×24, spoj 0,4, jas 86–88."""
    import numpy as np
    from PIL import Image
    F = [np.asarray(f, np.float32) for f in frames]
    n = len(F) - 1
    m = out_per_way or max(2, round(len(F) * 0.75))
    out = []
    for k in range(2 * m):
        u = k / m if k < m else 2 - k / m
        x = (1 - np.cos(np.pi * u)) / 2 * n
        i = int(x)
        t = x - i
        f = F[i] * (1 - t) + F[min(i + 1, n)] * t
        out.append(Image.fromarray(np.clip(f, 0, 255).astype(np.uint8)))
    return out


def save_webp(frames, fps, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    frames[0].save(dest, save_all=True, append_images=frames[1:], duration=round(1000 / fps),
                   loop=0, quality=90, method=6)


def run(w, dest, timeout):
    pid = _post('/prompt', json.dumps({'prompt': w}).encode(), 'application/json')['prompt_id']
    t0 = time.time()
    while time.time() - t0 < timeout:
        h = _get(f'/history/{pid}')
        if pid in h:
            st = h[pid].get('status', {})
            if st.get('status_str') == 'error':
                raise RuntimeError(f'ComfyUI chyba: {json.dumps(st)[:1500]}')
            out = h[pid].get('outputs', {}).get('save')
            if out:
                f = out['images'][0]
                q = f"/view?filename={f['filename']}&subfolder={f['subfolder']}&type={f['type']}"
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                open(dest, 'wb').write(urllib.request.urlopen(HOST + q, timeout=120).read())
                return time.time() - t0
        time.sleep(3)
    raise TimeoutError(pid)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('art', help='název drawable nebo cesta k obrázku')
    p.add_argument('prompt', help='co se má hýbat (anglicky, konkrétně, jemně)')
    p.add_argument('--frames', type=int, default=33, help='počet snímků, 4n+1 (33 ≈ 2 s při 16 fps)')
    p.add_argument('--fps', type=int, default=16)
    p.add_argument('--size', type=int, default=512, help='strana čtverce, násobek 16')
    p.add_argument('--seed', type=int)
    p.add_argument('--count', type=int, default=1, help='kolik variant (seed +1)')
    p.add_argument('--mode', choices=['pingpong', 'free', 'return'], default='pingpong',
                   help='pingpong = tam a zpět (výchozí), free = jedním směrem, return = pokus se 2 běhy')
    p.add_argument('--slow', action='store_true', help='nepoužít LoRA lightx2v ani když je k dispozici')
    a = p.parse_args()

    if (a.frames - 1) % 4:
        sys.exit('--frames musí být 4n+1 (17, 25, 33, 41, 49 …)')
    src = a.art if os.path.isfile(a.art) else os.path.join(DRAWABLE, a.art + '.png')
    if not os.path.isfile(src):
        sys.exit(f'Obrázek nenalezen: {src}')
    name = os.path.splitext(os.path.basename(src))[0]
    fast = not a.slow and has_lora()
    print(f'{name}: {a.frames} snímků, {a.size}px, {"4 kroky (lightx2v)" if fast else "20 kroků"}, '
          f'režim {a.mode}')
    image = upload(src)
    seed = a.seed if a.seed is not None else random.randint(1, 2**31)
    tmp = os.path.join(OUT, name, '_tmp')
    for i in range(a.count):
        s = seed + i
        dest = os.path.join(OUT, name, f'{name}_s{s}.webp')
        if a.mode in ('free', 'pingpong'):
            raw = os.path.join(tmp, f'free_s{s}.webp')
            t = run(workflow(image, a.prompt, s, a.frames, a.size, a.fps, None, fast, f'anim/{name}'), raw, 3600)
            fr = frames_of(raw)
            save_webp(pingpong(fr) if a.mode == 'pingpong' else fr, a.fps, dest)
        else:
            # Smyčka ve dvou krocích: 1) volný pohyb od artu, 2) návrat, který začíná posledními
            # CTX snímky kroku 1 a končí jeho prvními CTX snímky → navazuje poloha i rychlost.
            # (Stejný první i poslední snímek v jednom běhu = Wan se skoro nehýbe, jen pulzuje jas.)
            total = a.frames - 1                           # snímků ve smyčce
            g = total // 2
            g += (4 - g % 4) % 4                           # krok 1: g + 1 snímků (4n+1)
            b = total - (g + 1) + 2 * CTX
            b = b - 1 + (4 - (b - 1) % 4) % 4 + 1          # návrat: 4n+1
            raw1 = os.path.join(tmp, f'go_s{s}.webp')
            t = run(workflow(image, a.prompt, s, g + 1, a.size, a.fps, None, fast, f'anim/{name}'), raw1, 3600)
            go = frames_of(raw1)

            def up(frames, tag):
                names = []
                for k, f in enumerate(frames):
                    p_ = os.path.join(tmp, f'{tag}{k}_s{s}.png')
                    f.save(p_)
                    names.append(upload(p_))
                return names
            back_prompt = a.prompt + '. The motion continues smoothly and returns to the starting pose.'
            raw2 = os.path.join(tmp, f'back_s{s}.webp')
            t += run(workflow(up(go[-CTX:], 'ctxa'), back_prompt, s + 1000, b, a.size, a.fps, up(go[:CTX], 'ctxb'),
                              fast, f'anim/{name}'), raw2, 3600)
            save_webp(stitch_loop(go, frames_of(raw2)), a.fps, dest)
        print(f'  seed {s}: {dest}  ({t:.0f} s)')


if __name__ == '__main__':
    main()

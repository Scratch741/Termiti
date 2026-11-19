"""Lokální AI (Ollama na tomto PC) napíše prompty pro arty karet → dávka pro art_batch.py.

  python tools/art_plan.py <dávka> <karta> [<karta> …] [--gen] [--model mimo-v2.6]

  <karta> = id ("047") nebo český název ("Ogr", "Válečný pochod").
  Vytvoří tools/art_batches/<dávka>.json s prompty (můžeš je ručně upravit)
  a s --gen rovnou spustí  art_batch.py gen  (varianty + přehled v design/art_review/).
  Ollama běží na tomto PC, ComfyUI na stroji v LAN → GPU se nepřekrývají.
"""
import json, os, re, sys, subprocess, urllib.request

ROOT = os.getcwd()
OLLAMA = os.environ.get('OLLAMA_HOST', 'http://localhost:11434')

RULES = """You write ONE prompt for an AI image generator (Krea) for a card of a dark fantasy card game (painterly oil-painting style is added automatically – do not describe the style).
Rules:
- The card must be recognisable at a glance and different from other cards: choose ONE framing (extreme close-up/macro, close-up portrait, medium shot, wide shot, top-down, low angle, silhouette against light) and ONE dominant colour/light (green poison glow, red fire, cold blue moonlight, golden dawn, purple magic, misty grey, amber sunset, crimson…).
- Show what the card DOES. Prefer a key object, creature or a dramatic moment over a generic person; a person only when the person is the card.
- If there are several people, describe each one differently (age, hair, clothes) – no clones. No children.
- Concrete visible things only, 60-110 words, one paragraph, English, never mention the card name or game terms.
- The last sentence must be exactly: "Palette: <3-4 colours>, no teal. No text."
Answer with the prompt only."""


def cards():
    js = open(os.path.join(ROOT, 'server/game/cards.js'), encoding='utf-8').read()
    out = {}
    for m in re.finditer(r"\['(\w{3})','([^']+)',\s*\d+,'(\w+)'", js):
        out[m.group(1)] = {'id': m.group(1), 'cs': m.group(2), 'res': m.group(3)}
    kt = open(os.path.join(ROOT, 'app/src/main/java/com/example/termiti/CardPresentation.kt'), encoding='utf-8').read()
    for m in re.finditer(r'"(\w{3})" to CardPres\([\s\S]{0,400}?R\.drawable\.(\w+)', kt):
        if m.group(1) in out:
            out[m.group(1)]['art'] = m.group(2)
    en = json.load(open(os.path.join(ROOT, 'app/src/main/assets/lang/en.json'), encoding='utf-8')).get('cards', {})
    for cid, c in out.items():
        e = en.get(cid, {})
        c['en'] = e.get('name', c['cs']); c['desc'] = e.get('desc', '')
    return out


def find(all_cards, key):
    if key in all_cards:
        return all_cards[key]
    for c in all_cards.values():
        if c['cs'].lower() == key.lower() or c['en'].lower() == key.lower():
            return c
    sys.exit(f'Karta „{key}" nenalezena')


def ask(model, card):
    user = (f'Card: "{card["en"]}". Resource type: {card["res"]}. '
            f'Effect: {card["desc"].replace(chr(10), " ")}\nWrite the prompt.')
    body = {'model': model, 'stream': False, 'think': False,
            'messages': [{'role': 'system', 'content': RULES}, {'role': 'user', 'content': user}],
            'options': {'temperature': 0.8, 'num_predict': 320, 'repeat_penalty': 1.15}}
    r = urllib.request.urlopen(urllib.request.Request(OLLAMA + '/api/chat', json.dumps(body).encode(),
                                                      {'Content-Type': 'application/json'}), timeout=600)
    txt = json.load(r)['message']['content']
    txt = re.sub(r'<think>[\s\S]*?</think>', '', txt).replace('</think>', '\n')
    txt = re.sub(r'^\s*(Prompt:)?\s*"?', '', txt.strip())
    m = re.search(r'[\s\S]*?No text\.', txt)            # jen první prompt (model se umí zacyklit)
    txt = (m.group(0) if m else txt.split('\n\n')[0]).strip().strip('"').strip()
    if 'no teal' not in txt.lower():
        txt += ' Palette: muted colours, no teal. No text.'
    return re.sub(r'\s+', ' ', txt)


def main(a):
    model = 'mimo-v2.6'
    if '--model' in a:
        i = a.index('--model'); model = a[i + 1]; del a[i:i + 2]
    gen = '--gen' in a
    a = [x for x in a if x != '--gen']
    if len(a) < 2:
        print(__doc__); sys.exit(1)
    name, keys = a[0], a[1:]
    all_cards = cards()
    batch = []
    for k in keys:
        c = find(all_cards, k)
        if not c.get('art'):
            print(f'! {c["cs"]} nemá art v CardPresentation.kt – přeskočeno'); continue
        print(f'{c["cs"]}: píšu prompt ({model})…', flush=True)
        p = ask(model, c)
        print('   ', p)
        batch.append({'art': c['art'], 'title': c['cs'], 'prompt': p})
    os.makedirs(os.path.join(ROOT, 'tools/art_batches'), exist_ok=True)
    path = os.path.join(ROOT, 'tools/art_batches', name + '.json')
    json.dump(batch, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print('Dávka:', path)
    if gen:
        subprocess.run([sys.executable, os.path.join(ROOT, 'tools/art_batch.py'), 'gen', path], cwd=ROOT)


if __name__ == '__main__':
    main(sys.argv[1:])

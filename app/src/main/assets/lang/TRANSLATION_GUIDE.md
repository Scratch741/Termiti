# Termiti — Community Translation Guide

Want to translate Termiti into your language? Just create a new JSON file!

## Steps

1. Copy `en.json` and rename it to your language code (e.g. `de.json`, `pl.json`, `sk.json`)
2. Edit the `meta` block:
   ```json
   "meta": {
     "code": "de",
     "name": "Deutsch",
     "flag": "🇩🇪",
     "author": "Your Name",
     "version": 1
   }
   ```
3. Translate all values in the `strings` block — the UI text (keep the keys in English!)
4. Translate every card in the `cards` block — `name` + `desc` for each card id
5. Submit a Pull Request

## Rules

- **Do NOT change key names or card ids** — only translate the values
- **Keep `%s` and `%d` placeholders** exactly as they are (they are replaced at runtime)
- **Keep markdown markers** like `**...**` and tags like `[Combo]` — they are styled/parsed at runtime
- **Missing keys are fine** — any untranslated UI string or card falls back to Czech automatically
- Use `en.json` as your starting template; `cs.json` is the original Czech source

## Two blocks to translate

### `strings` — UI text
```json
"strings": {
  "ok": "OK",
  "cancel": "Abbrechen",
  "play": "SPIELEN"
}
```

### `cards` — every card's name + description (keyed by card id)
```json
"cards": {
  "001": { "name": "Schneller Schlag", "desc": "Greife den Gegner um 5 an. [Combo]" },
  "052": { "name": "Dämon",            "desc": "Direkter Treffer: Schloss -16. +2 Chaos." }
}
```
A card id missing from `cards` (or with an empty `name`/`desc`) falls back to
the built-in Czech text, so partial translations still work.

## Language codes

Use standard ISO 639-1 two-letter codes: `cs`, `en`, `de`, `pl`, `sk`, `fr`, `es`, `hu`, etc.

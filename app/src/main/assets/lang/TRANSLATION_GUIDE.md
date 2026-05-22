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
3. Translate all values in the `strings` block (keep the keys in English!)
4. Submit a Pull Request

## Rules

- **Do NOT change key names** — only translate the values
- **Keep `%s` and `%d` placeholders** exactly as they are (they are replaced at runtime)
- **Missing keys are fine** — the app falls back to Czech automatically
- Card names (Rychlý útok, Ogr, …) are not in these files — they are part of the card database

## Example

```json
{
  "meta": {
    "code": "de",
    "name": "Deutsch",
    "flag": "🇩🇪",
    "author": "Max Mustermann",
    "version": 1
  },
  "strings": {
    "ok": "OK",
    "cancel": "Abbrechen",
    "back": "← ZURÜCK",
    "settings": "⚙️  EINSTELLUNGEN",
    "play": "SPIELEN",
    ...
  }
}
```

## Language codes

Use standard ISO 639-1 two-letter codes: `cs`, `en`, `de`, `pl`, `sk`, `fr`, `es`, `hu`, etc.

# Changelog

Všechny významné změny v této hře jsou dokumentovány zde.
Formát vychází z [Keep a Changelog](https://keepachangelog.com/), verzování dle [SemVer](https://semver.org/) (`MAJOR.MINOR.PATCH`).

Hra je v **beta fázi** (verze `0.x`) — API a obsah se mohou měnit.

## Verzovací pravidla

- **MAJOR** — breaking změna, která vyžaduje nový klient i server současně.
- **MINOR** — nové karty, mechaniky, balanc, lokalizace (zpětně kompatibilní obsah).
- **PATCH** — opravy chyb, drobné úpravy.
- **`versionCode`** (Android) roste o 1 při **každém** vydaném buildu — jinak Android odmítne update.
- **`PROTOCOL_VERSION`** (klient `OnlineLobbyViewModel.kt` ↔ server `server.js`) je nezávislé
  na verzi hry; bump jen při změně, která rozbije kompatibilitu online protokolu nebo sdílených
  karetních dat. Při neshodě server odmítne připojení (`VERSION_MISMATCH`).

---

## [0.1.0] – 2026-05-29

První verzovaný build. Shrnutí dosavadního stavu hry:

### Hra
- Karetní hradní bitva: 4 typy zdrojů (Magie, Útok, Kameny, Chaos), hradby + hrad, doly.
- 176 karet včetně mechanik: doly, X-kost, combo, „toto kolo" buffy, rozhodovací karty,
  krádeže/ničení zdrojů a dolů, klonování, shapeshift, pasti.
- Ikonická legendární karta **Temný mág** (Darkmage), stejnojmenná se hrou.
- Offline hra proti AI (heuristický engine) i online multiplayer.

### Režimy
- Hra proti AI, Aréna (draft), online multiplayer s matchmakingem a ratingem, kampaň.
- Stavitel balíčků, obchod s balíčky karet, profil hráče.

### Lokalizace
- Plně lokalizovatelné UI **i karty** přes soubory `assets/lang/<code>.json`.
- Kompletní čeština (`cs`) a angličtina (`en`) — všech 176 karet (názvy + popisy).
- Komunitní překlady: stačí přidat nový jazykový soubor (viz `lang/TRANSLATION_GUIDE.md`).

### Infrastruktura
- Server-authoritative online hra (WebSocket lobby + game server, Node.js).
- Zavedeno verzování: SemVer, `versionCode`/`versionName`, `PROTOCOL_VERSION` handshake,
  git tagy, tento changelog.

[0.1.0]: https://github.com/Scratch741/Termiti/releases/tag/v0.1.0

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

## [0.2.0] – 2026-08-28

### Balanc
- Zlevněno šest chaosových karet: **Chaotická přeměna** 4→2, **Anarchie** 9→8,
  **Chaotický výbuch** 7→6, **Likvidace** 4→3, **Kletba cen** 3→2,
  **Osudová mince** 2→1.
- **Bouře chaosu** přesunuta z Legendární na Epickou (limit kopií 1→2). Její poměr
  20 poškození za 6 chaosu odpovídá vzácným kartám, ne legendárním.
- **Ohnivá koule** je nově základní karta — dostupná od začátku bez craftění.
  Ve startovním balíčku nahradila 2× Lupiče.

### Opravy
- **Přelíznutí** (líz s plnou rukou) se konečně zobrazuje: spálená karta se objeví
  uprostřed bojiště s plamenem. Offline se neukazovalo, když kartu vyvolal soupeř,
  online ji přebilo následující obnovení stavu.
- **Útočník vidí, které karty soupeři zničil.** Při více ztrátách naráz
  (Spálená knihovna, Prázdná mysl) byla dřív vidět jen poslední.
- **Skrytá bomba** hlásila dvě karty místo jedné (Bomba + Explodovaná bomba).
  Bomba zůstává v balíčku jako placeholder pro cílené odstranění, ale už není
  vidět jako událost.
- Online: soupeřova zahraná karta mohla zmizet i z logu, pokud hned ukončil tah
  a hráč si vzápětí lízl past.
- Karty se v historii zapisovaly dvakrát; líznutí ignorovalo pasivní schopnost
  na větší ruku a spálilo kartu o jednu dřív.
- Popisky 33 karet se rozcházely mezi vestavěným textem a jazykovými balíčky;
  tři z nich uváděly věcně špatné hodnoty.
- Offline log zobrazuje jméno hráče z profilu místo obecného „Hráč".

### Nové
- **Limity kopií se srovnávají samy.** Když se sníží limit karty, přebytečné kopie
  se rozeberou na prach, uložené balíčky se ořežou podle kolekce a hráč dostane
  v Stavitel balíčků přehled, o co přišel. Dřív to vyžadovalo ruční zásah v kódu.
- Mulligan v online hře ukazuje, kolik času zbývá soupeři na rozhodnutí.
- Kampaň: vycentrované názvy na kartách lokací i soupeřů, XP mezi zobrazenými
  odměnami.

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

[0.2.0]: https://github.com/Scratch741/Termiti/releases/tag/v0.2.0
[0.1.0]: https://github.com/Scratch741/Termiti/releases/tag/v0.1.0

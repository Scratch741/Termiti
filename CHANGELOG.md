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

## [0.4.1] – 2026-09-26

### Vylepšeno
- **Tvorba balíčku:** přepracovaný panel „Složení balíčku“ a „Mana křivka“ –
  vlastní tmavý podklad, zlaté nadpisy, větší ikony a čísla surovin, výraznější
  proužky, vyšší sloupce mana křivky s počtem karet nad každým a čitelnější osa.

## [0.4.0] – 2026-09-26

### Nové karty
- **Daň z chaosu** (4 chaos, epická) – poškodí hrad −7; máš-li víc chaosu
  než soupeř, ukradne navíc 4 hradu.
- **Chaotická šipka** (1 chaos, běžná, combo) – ukradne 3 hradu. První levný
  chaosový útok a první běžná chaosová karta, kterou lze vyrobit.

### Balanc
- **Chaotický důl** zlevněn ze 4 na 3 magie.
- **Šablony balíčků:** nový **Útočník2**, přepracovaný **Chaos**.
- **Magické bažiny:** upravené startovní suroviny, doly a cíle soupeřů.

### Změny
- **Šípy** přejmenovány na **Déšť šípů**.
- **Sjednocené popisy karet** – stejný efekt se všude píše stejně
  (např. „Poškodí hrad −14." místo „Přímý zásah: hrad −14, ignoruje hradby.").
- **Tvorba balíčku roluje plynule** – obrázky se už nedekódují zbytečně
  zvětšené a na hlavním vlákně (zaseknuté snímky 25 % → 9 %).

### Opravy
- AI už nehraje **Likvidaci** a podobné karty, když není z čeho vybírat
  (prázdný balíček nebo ruka soupeře), a takové karty zahazuje jako první.
- AI v koncovce **ukončí kolo a vyhraje**, když má vyšší hrad a oba balíčky
  jsou prázdné – dřív hrála dál a náskok zbytečně pouštěla.
- Po výbuchu pasti se zahraná karta na okamžik **nevracela zpět do ruky**.
- Kampaň: **„CHAOS to 1"** u soupeře dávalo dva chaosové doly místo jednoho.
- Tvorba balíčku v interním nástroji: seznam karet se už nesráží na nulu.

---

## [0.3.0] – 2026-09-25

### Nové
- **Nová kampaňová lokace „Magické bažiny"** mezi Goblinským táborem a
  Trpasličími horami: 10 soupeřů s vlastními ilustracemi, vlastním hradem,
  hradbou i pozadím bojiště. Soupeři nebijí silou — odsávají
  zdroje, pálí karty z ruky a sypou do balíčku krysy a bomby.
- **Angličtina v celé hře.** Multiplayer (lobby, přihlášení, chyby připojení),
  obchod, žebříček, herní dialogy, aréna, kampaň, profil, tvorba balíčku
  i roguelike — přes 180 textů, které byly dosud napsané natvrdo česky.
  Texty posílané serverem (online log, chybové hlášky) zůstávají česky.
- **„Prohlédnout hru" i v kampani.** Po bitvě se lze vrátit na dohranou desku
  a projít si log, poslední tahy i soupeřovu ruku.
- Profil → DEBUG: přepínač „Celá kampaň odemčena". Obchází jen zámky, skutečný
  postup ani odměny nemění, takže vypnutím se nic neztratí.

### Opravy
- **Pád hry hned po startu** (jen ve verzi 0.2.x z GitHubu po překladech) —
  Android odmítl načíst třídu s příliš mnoha parametry.
- **Vypnutá hudba se konečně vypne.** Hlasitost 0 jen ztišila přehrávač, který
  hrál dál — a některé telefony si ho pak samy zesílily.
- **Magický žolík** nenabízel kartu, kterou šlo vyhrát hru, pokud vyhrávala
  díky podmínce (např. Ostřelovač) nebo součtem dvou efektů.
- **AI zahazuje podle stejných pravidel jako hráč** — 1× za kolo a tah tím
  nekončí. Dřív po zahození přišla o celý tah. Navíc už pozná, že se některé
  karty (Zapomenutá poznámka) vyplatí spíš zahodit než zahrát.
- Kampaň: soupeři měli v balíčcích karty za chaos, na který neměli důl, takže
  je nikdy nezahráli. Opraveno v Magických bažinách.

---

## [0.2.1] – 2026-08-31

### Opravy
- Online: zahraná karta, která si sama vytáhla past (např. **Průzkumník**), už
  se nezatratila beze stopy — objeví se v odhazovacím balíčku i v logu.
- Online: tlačítko po zahrání combo karty teď správně píše „Ukončit combo"
  místo „Ukončit tah".
- Mulligan: tlačítka po odeslání volby jen zešednou, dřív úplně zmizela a
  panel se zmenšil i s kartami.
- Hrad soupeře v kampani (Trpasličí hory a další nové skiny) se už nevznáší
  nad okrajem bojiště.
- Oprava tří ilustrací soupeřů v kampani, kde ořez utínal hlavu.
- AI: karty stavějící hradby a přidávající suroviny se nezahazovaly zbytečně
  často — jejich hodnota se dřív neškálovala podle síly efektu.
- AI: v combo řetězu je pauza mezi kartami 1 s, ale po poslední kartě jen 0,5 s
  (dřív čekala zbytečně dlouho i na konci).
- Build z GitHubu už nevyžaduje podpisový klíč a je rovnou instalovatelný
  (debug build).

### Nové
- Nový vzhled hradu/hradby pro lokace Temná citadela a Dračí impérium,
  Trpasličí hory mají vlastní zimní pozadí bojiště.
- Mimo kampaň (vlastní balíček, super náhodné, roguelike) má soupeř náhodný
  vzhled hradu/hradby místo pořád stejného výchozího.
- Nový hráč dostává při založení účtu 500 zlaťáků místo 300.

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

[0.2.1]: https://github.com/Scratch741/Termiti/releases/tag/v0.2.1
[0.2.0]: https://github.com/Scratch741/Termiti/releases/tag/v0.2.0
[0.1.0]: https://github.com/Scratch741/Termiti/releases/tag/v0.1.0

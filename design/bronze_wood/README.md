# Bronz a tmavé dřevo – návrh UI (neimplementováno)

Návrh vzhledu panelů, nadpisů, tlačítek, záložek, patic a tabulek inspirovaný hnědými rámečky
s bronzovým lemem (styl, ne konkrétní grafika – cizí assety se nepoužívají). Vybraná varianta
ze tří (bronz + dřevo / zlato + kůže / železo + kámen), 2026-11-05.

- `kit.png` – knihovna prvků ve všech stavech + paleta a pravidla stylu
- `profile_mock.png` – ukázka obrazovky Profil v návrhovém rozlišení 1560×720
- generátor: `tools/bronze_kit.py` (`python tools/bronze_kit.py design/bronze_wood` přepíše
  `bronze_kit.png` a `bronze_profile_mock.png` v cílové složce)

## Pravidla stylu

- Dřevo: tmavý ořech, vlákna vodorovně; panely skládané z prken se spárou.
- Bronz: tenký lem 4–8 px, světlo vždy shora, tmavé linky po obou stranách lemu.
- Nadpisy: světlejší destička s kulatými konci a nýty.
- Stisknuté tlačítko: tmavší dřevo, ztlumený bronz, text o 2 px níž; vypnuté: vybledlé, bez lesku.
- Výběr (patice, řádek): zlatá záře / zlatý proužek vlevo – jiná barva než zlato se pro výběr nepoužívá.

| Barva | Hex |
|---|---|
| Dřevo tmavé / střední / světlé | `#1E1009` / `#382013` / `#543823` |
| Bronz stín / tělo / lesk | `#4C2F15` / `#A87B3F` / `#F9D07C` |
| Text popisek / hodnota / nadpis | `#DEBA68` / `#EDE0C4` / `#E8C467` |

## Otevřené body

- Kresleno procedurálně – na finální textury dokreslit nebo vygenerovat podle návrhu a barevně sladit.
- Rohové kování je málo výrazné, ve finále větší a zdobnější.
- Dřeva je v ukázce hodně; ve finále tmavší pozadí panelu, dřevo hlavně na nadpisy, tlačítka a řádky.
- Kandidátní obrazovky: Profil, Obchod, odměna v Roguelike, panel karty v Tvorbě balíčku.

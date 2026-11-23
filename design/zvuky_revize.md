# Revize zvuků karet (2026-11-23)

Zdroj podnětu: uživatel u Náhlé smrti slyšel „skoro svatbu“ místo dvou stejných úderů zvonu.
Revize vznikla na PC `uzivatel` bez zdrojových knihoven: rozbor hotových WAVů v `res/raw` (nástupy, výška tónu každého úderu, kdy přijde vrchol hlasitosti) a porovnání zdrojů z [[systems/card-sounds]] s názvem a efektem karty. **Nikdo to zatím neposlouchal.** Je to seznam podezřelých, ne verdikt. Opravy s novým zdrojem patří na PC se zdroji (`C:\Users\Scratch\…\zvuky\knihovny\_postup`, `map.tsv` + `render.py`), ideálně s posouzením přes MiMo-Audio.

## Hotovo
| ID | Karta | Co se stalo |
|---|---|---|
| `"101"` | Náhlá smrt | Byla „Church Bells – 3 Different“ (údery E6 → F5, tři různé zvony). Teď je první úder z původního zvuku zahraný **2×** (po 0,75 s) a oba doznívají; z dozvuku jsou vyfiltrované tóny druhého zvonu (F5 702 Hz a jeho partiály). Délka 1,93 s. První verze s výřezem do 0,92 s zněla „useknutě“. Skript `tools/nahla_smrt_2x.py`. Syntetický zvon uživatel odmítl („odporný“), syntézu nepoužívat. |

## Uživatel potvrdil jako špatné (2026-11-23)
| ID | Karta | Výrok | Zdroj |
|---|---|---|---|
| `"103"` | Magické rozdělení | „zní jak špatné Star Wars“ | Elemental Mutation – ELECMisc Impact Electric Tonal Deep Movement Hiss. Potřeba fantasy magie (třpyt, tonální kouzlo, rozštěpení na dva tóny), ne elektrika |
| `"004"` | Magie | „odporně, nemagicky, nedokážu to ani popsat“ | Fantasy Game 2 – MAGAngl Magic Light Spell Enchantment Potion Effect Tonal Br…; náběh, vrchol až v 1,0 s. Základní karta, hraje se často, chce krátký jasný magický třpyt s nástupem hned |
| `"C01"` | Chaotická jiskra | „jak starý rozbitý CRT televizor“ | InMotionAudio Arc – ArcDesign15 (elektrický oblouk) + praskání ohně; 50 % energie nad 6 kHz, žádný jasný nástup. Chce jednu ostrou magickou jiskru (lusknutí + krátký třpyt), ne bzučení elektřiny |

| `"C04"` | Krádež chaosu | „není nic slyšet“ | Sci-Fi Heavy Whoosh + otevření láhve. 85 % energie pod 200 Hz, na malém reproduktoru −35 LUFS (medián −14). Viz sekce „Neslyšitelné na telefonu“ |
| `"C28"` | Chaotická trofej | „jak bouchnutí do stolu s příbory“ | chainmail2 + Metal Old File Impact Tap Against Tire Iron. Trofej chce kořist: zlato, cinknutí poháru, temný akcent |
| `"011"` | Zásoby kamene | „jak prolítající UFO“ | RPG Sound Pack „spell“ (syntetický svist) + stone impact 015. Chce kámen: sypání štěrku, skládání kamenů na hromadu, bez kouzla. Podezřelé jsou i další karty s RPG „spell“/„magic1“: `"037"` Rychlá magie, `"137"` Zapomenutá poznámka |
| `"005"` | Posila hradu | „skřípající dveře“ | Creaking Door – Wooden Door Opening and Closing 09 + stone impact; kámen přijde až v 0,58 s, do té doby jen dveře. Stejné dveře mají i `"084"` Velká oprava a `"112"` Stavitel, bouchání dveří `"020"` Beranidlo, `"087"` Pevnost, `"136"` Poslední výpad |

## Proč to dopadlo takhle (poučení pro opravu)
Zvuky z 2026-11-22 vybírala jiná relace Clauda **podle názvů souborů v knihovnách, bez poslechu**. Lokální AI tehdy audio neuměla. Název „Wooden Door Opening“ nebo „Magic Light Spell“ ale neříká, jak to zní, a proto 8 z 8 poslechnutých karet nesedí. Další výměna stejným postupem dopadne stejně. Každý nový zdroj musí před použitím někdo poslechnout: MiMo-Audio, nebo uživatel z krátkého výběru 2–3 kandidátů.

## Neslyšitelné na telefonu (systémová chyba v `render.py`)
Hlasitost se srovnává na −12,5 LUFS **v celém pásmu**. U zvuků, kde je většina energie hluboko pod 200 Hz (dunění, boom), proto limit „spotřebuje“ basa, kterou reproduktor telefonu nezahraje, a slyšitelná část zůstane potichu. Měřeno po high-passu 300 Hz (přibližně malý reproduktor); medián všech zvuků −14 LUFS:

| ID | Karta | Telefon (LUFS) |
|---|---|---|
| `"C17"` | Telekineze | −43,8 (prakticky ticho) |
| `"140"` | Prastarý kolos | −38,2 |
| `"090"` | Rozšíření těžby | −37,3 |
| `"C04"` | Krádež chaosu | −35,1 |
| `"009"` | Pevné základy | −32,3 |
| `"082"` | Masivní zeď | −31,8 |
| `"035"` | Základní kámen | −30,1 |
| `"018"` | Mohutná věž | −29,3 |
| `"102"` | Kamenný příval | −26,7 |
| `"143"` | Velké zemětřesení | −25,9 |
| `"C14"` | Ničení kamenolomu | −24,5 |
| `"125"`, `"030"`, `"C08"` | Válečný zápal, Kamenný val, Zázrak chaosu | −24 |

Oprava v `render.py`: hlasitost měřit (nebo aspoň hlídat) i po high-passu ~300 Hz, a ke každému dunění přidat slyšitelnou vrstvu ve středech (kámen, praskání, kov), aby „boom“ byl slyšet i z telefonu. Basu pod ~60 Hz klidně odříznout.

## 0. Sci-fi a elektrické zdroje (stejné riziko jako Magické rozdělení)
| ID | Karta | Zdroj |
|---|---|---|
| `"001"` | Rychlý útok | úder pěstí + elektrické praskání (High Voltage) |
| `"037"` | Rychlá magie | Pure SciFi-Whoosh Fast |
| `"073"` | Škola magie | SCIMisc Ping (Parallax) |
| `"C27"` | Odraz magie | SCIMisc Zap (Parallax) |
| `"C04"`, `"C17"`, `"C18"` | Krádež chaosu, Telekineze, Krádež osudu | Sci-Fi Heavy Whoosh (3 karty stejný) |
| `"C07"` | Chaotický štít | Arc Power Up (elektrický oblouk) |
| `"C13"` | Sabotáž | Electrical Panel Pitchdown (rozvodna) |
| `"C09"` | Chaotická krize | electricity surge + nábojnice |
| `"113"` | Goblin šaman | příšera + elektrický oblouk |
| `"D02"` | Věštba | Water Sci Fi Hit + hrací skříňka |
| `"C01"`, `"C06"`, `"C29"` | Chaotická jiskra, Bouře chaosu, Bouřlivá mysl | elektřina; u jiskry a bouře může sedět, ověřit |

## 1. Zvony s různou výškou tónu („svatba“) – stejná vada jako Náhlá smrt
Zdroj `04 Church Bells, Near Distance, In Church Tower-3 Different` zní ve všech případech jako melodie více zvonů.
| ID | Karta | Naměřené údery | Návrh |
|---|---|---|---|
| `"C30"` | Chrám chaosu | E6 → G#5 → A#5 → E6, hraje melodii | jeden rozladěný/zkreslený zvon nebo zvon pozpátku + praskání |
| `"060"` | Chrám | F5 + C6, dva různé zvony, hlavní úder až v 1,1 s | jeden velký zvon, úder hned na začátku |
| `"031"` | Renovace | C6/F5, hlavní úder v 1,2 s | zvon ke stavbě hradu nesedí: zednické nářadí, kámen |
| `"097"` | Obnova království | E6 + D#4 současně | jeden slavnostní hluboký úder (zvon smí, ale jeden) |
| `"C11"` | Chaos a řád | D4 + H5 + boom | ok-ish, ověřit poslechem |

## 2. Veselé „UI“ zvuky v temném stylu (xylofon, kalimba, konfety, kreslený)
| ID | Karta | Problém |
|---|---|---|
| `"045"` | Očarované doly | Xylophone Ringtone |
| `"075"` | Rozmach těžby | Xylophone Ringtone |
| `"118"` | Magický žolík | Xylophone Ringtone, 3× stejný tón E6 |
| `"092"` | Strategická výstavba | Kalimba 3 Up |
| `"116"` | Chaotická replikace | Kalimba 3 Up |
| `"120"` | Hromadná sleva | Kalimba 3 Up |
| `"085"` | Královská obnova | Confetti Whoosh (slavnostní konfety) |
| `"C42"` | Klon | Cartoon Impacts – Vibrato Snap (kreslený) |
| `"039"`, `"044"`, `"C35"` | Stavební boom, Trifekta dolů, Chaotická přeměna | „Magic Creation 23“ z UI knihovny budovatelských her, ověřit |

## 3. Zdroj neodpovídá kartě
| ID | Karta (efekt) | Zdroj | Proč nesedí |
|---|---|---|---|
| `"123"` | Momentum (útok) | řev býložravého dinosaura, vrchol v 1,2 s | dinosaurus + pozdě; spíš zrychlující se rozběh / úder |
| `"122"` | Stavební posila (+7 hrad) | klus a frkání koně | stavba bez stavebního zvuku |
| `"077"` | Přeměna magie | Deploy Units Sword Slice | meč u magie |
| `"117"` | Průzkum dolů | vyhození nábojnice dělostřelectva + jeskyně | nábojnice v dole |
| `"096"` | Nedobytná pevnost (+18 hradba, +8 hrad) | potápění lodi do vody + hrom | voda u pevnosti |
| `"C26"` | Krvavá oběť | tasení meče + kapání na pokličku hrnce | 6 pravidelných pingů A#5, kuchyňské |
| `"055"` | Poslední vzdor | meč + štít točící se po zemi | štít na zemi zní jako porážka |
| `"019"` | Zápalné šípy | stejný `arrow_hit.wav` jako Déšť šípů `"008"` | chybí oheň, dvě karty stejný zvuk |
| `"051"`, `"078"`, `"C10"` | Drak, Upíří drak, Chaotický drak | všechny `dragon_breath.mp3` | tři karty stejný zvuk |

## 4. Hlavní zvuk přichází pozdě (wiki: do 0,3 s, AI hraje další kartu po ~1 s)
Vrchol hlasitosti / hlavní nástup v sekundách:
`"016"` Velký pramen 1,0–1,14 · `"066"` Kamenná daň 1,15 · `"139"` Pohlcení hradeb 1,34 · `"C04"` Krádež chaosu 1,34 · `"C02"` Entropie 1,31 · `"123"` Momentum 1,21 · `"C24"` Temný rituál 1,17 · `"060"` Chrám 1,13 · `"063"` Věž strážní 1,1 · `"C39"` Velký zmatek 1,1 · `"C43"` Nájezd 1,1 · `"126"` Magický proud 1,09 · `"076"` Vojenská základna 1,08 · `"102"` Kamenný příval 1,03 · `"D09"` Inspirace 1,03 · `"090"` Rozšíření těžby 1,01 · `"004"` Magie 1,0 · `"099"` Temný přenos 1,0 · `"082"` Masivní zeď 0,88 · `"D08"` Vize 0,88 · `"039"` Stavební boom 0,87 · `"067"` Sabotér 0,86 · `"128"` Archmág 0,84 · `"C17"` Telekineze 0,84.
U „náběhových“ zvuků (braam, riser) to může být záměr, ale při rychlém sledu karet se vrchol nestihne.

## 5. Nadužívané zdroje (pravidlo „každá karta jiná“)
| Zdroj | Počet karet |
|---|---|
| Fire Crackling Crunchy Flame Burn 03 | 11 (`"050"`, `"108"`, `"114"`, `"127"`, `"135"`, `"C01"`, `"C03"`, `"C15"`, `"C35"`, `"C44"`, `"C45"`) |
| Bluezone stone_impact 015 / 041 / hammer_015 / steel_bar | ~25 stavebních a důlních karet |
| Church Bells 3 Different | 6 |
| Metal Shield Block Hits | 5 |
| Thunder Rumble (interiér) | 4 (`"009"`, `"057"`, `"096"`, `"C29"`) |
| Xylofon / kalimba | 6 |

## Jak pokračovat
1. Uživatel si v `deckbuilder.html` poslechne podezřelé karty (filtr podle ID) a označí 👎 ty, které opravdu vadí.
2. Na PC se zdroji: pro každou 👎 kartu nový řádek v `map.tsv` a `render.py`. Před výběrem nechat MiMo-Audio popsat kandidátní zdroje (co je slyšet, kolik úderů, stejná/různá výška), ať se nepoužije další „svatba“.

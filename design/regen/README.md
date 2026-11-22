# Přegenerování zbylé grafiky do stylu darkmage (2026-11-22)

Úkol od uživatele: přegenerovat veškerou grafiku, která ještě nemá nový styl, víc variant,
důraz na kampaň a bosse. **Nic není nasazené do hry** – vše jsou varianty k výběru.
**K výběru: `design/regen/<dávka>.jpg`** – vlevo STARÉ (co je teď ve hře), vedle očíslované nové
varianty ze všech kol (1–4 první kolo, 5+ druhé pojetí / opravy). Soubory variant:
`~/Pictures/Generated/karty/<název>/<název>_<dávka>_vN.png`.

## 1. Analýza současného stylu (nové karty, 195 artů z 18.–21. 11.)

Co nové arty spojuje (a co se drží v promptech):

| Rys | Nové arty (cíl) | Staré portréty kampaně (stav) |
|---|---|---|
| Technika | olejomalba, viditelné tahy štětce, ručně malovaná textura | hladký 3D render, plastové povrchy |
| Světlo | silný šerosvit, jeden výrazný zdroj (oheň, magie, měsíc, pochodeň), teplé obrysové světlo | rovnoměrný šedozelený opar, nízký kontrast |
| Barva | každý obrázek má vlastní dominantní barvu (oranžový chaos, zářivě modrá magie, zlato staveb, rudý útok, jedovatě zelená…) | všude stejná tlumená modrozelená |
| Kompozice | střídá makro, portrét, polocelek, celek, podhled, pohled zezadu; často předmět místo postavy | postava vždy uprostřed, celá, zepředu |
| Čitelnost | hlavní motiv jasně světlejší než pozadí, čte se i v malém | postavy splývají s pozadím |

Pravidla, podle kterých jsou psané prompty (vychází z `wiki/systems/art-pipeline.md`):
- styl `darkmage` vždy (tahy štětce), paleta se ale **píše do promptu** – modrozelená už není výchozí;
- portréty soupeřů = **avatar**: v kampani se zobrazují i v malém (horní lišta hry, dlaždice lokace),
  proto převážně záběr od pasu / detail tváře, tvář výrazně nasvícená, obrysové světlo;
- v rámci jedné lokace se střídá záběr i barva světla, aby se soupeři nepletli;
- **bossové**: monumentální scéna (podhled, trůn / doupě, nejsilnější světlo, „awe-inspiring“);
  u Goblin krále navíc samostatný portrét (`goblin_kral_profil`) pro avatar;
- každá lokace má vlastní barevný svět:
  - Goblinský tábor – bláto, mech, oheň táboráků, zlato kořisti;
  - Magické bažiny – jedovatá zeleň, zářivě modrá a fialová magie, mlha, měsíc;
  - Trpasličí hory – zlato výhně, žula, studená modř sněhu;
  - Temná citadela – čerň, fialová, krvavě rudý měsíc;
  - Dračí impérium – láva, popel, oranžový chaos, zlato (lokace zatím **neměla žádné arty**).

## 2. Co se generuje (dávky v `tools/art_batches/`)

| Dávka | Obsah | Kusů × variant |
|---|---|---|
| `kampan_goblini` | 9 soupeřů + Goblin král (scéna + portrét) + lokace | 12 × 4 |
| `kampan_baziny` | 9 soupeřů + Pán Mlhy + lokace | 11 × 4 |
| `kampan_hory` | 9 soupeřů + Thane + lokace | 11 × 4 |
| `kampan_citadela` | 9 soupeřů + Temný Pán + lokace | 11 × 4 |
| `kampan_draci` | **nové**: 9 soupeřů + Dračí Císař + lokace | 11 × 4 |
| `ostatni_a` | pozadí hradů (6), pozadí bojiště, balíček v obchodě, ruby karet (6), avatary (9), ikona aplikace | 24 × 4 |
| `ostatni_b` | kolosové karty 140–142, záložní arty surovin, výchozí art | 8 × 4 |
| `kampan_goblini_b`, `kampan_baziny_b` | druhé pojetí (jiný záběr, světlo, barva) | 12 + 11 × 2 |
| `ostatni_c` | druhé pojetí: dramatičtější pozadí hradů, sytější ruby karet, jiné motivy avatarů | 21 × 2 |
| `kampan_opravy` | opravy zmetků (Druid – dvojobraz, Generál – cizí tvář, Zaklínač – z člověka krysa) | 3 × 2 |
| `ostatni_opravy` | Avatar temný hrad (v ostatni_c místo okna vyšel symbol jako emoji) | 1 × 2 |

Vynecháno záměrně: UI textury (tlačítka, rámy – nově řešené zvlášť), ikony surovin, `bg_campaign`
(je to rám, ne malba), avatary 3/4/6/9/11 a `enemy_icon_3` (skoro kopie jiných), `deckbuild_bg*`
(Tvorba balíčku už má obsidiánové pozadí).

## 3. Jak vybírat

Přehledy (číslo = varianta, platí napříč koly):

| List | Co obsahuje |
|---|---|
| `kampan_goblini.jpg` | 1–4 první kolo, 5–6 druhé pojetí |
| `kampan_baziny.jpg` | 1–4, 5–6 druhé pojetí, 7–8 opravy (jen Druid a Zaklínač) |
| `kampan_hory.jpg` | 1–4 (střídají se dvě pojetí) |
| `kampan_citadela.jpg` | 1–4, u Generála navíc 5–6 oprava |
| `kampan_draci.jpg` | 1–4, sloupec STARÉ je prázdný (lokace art neměla) |
| `ostatni_a.jpg` | pozadí, ruby, avatary, ikona; 1–4 první kolo, 5–6 druhé pojetí, u hradu 7–8 oprava |
| `ostatni_b.jpg` | kolosy, záložní a výchozí art |

U každého řádku napiš číslo varianty, „nechat staré“, „zahodit“ nebo „znovu – co změnit“.
Nasazení po výběru: portréty a lokace se jen zmenší na 496×496 a přepíšou drawable
(kód se nemění); Dračí impérium potřebuje doplnit `cardArt` v `CampaignData.kt`.

## 4. Postřehy k výsledkům (můj názor, rozhodnutí je na tobě)

- **Kampaň obecně:** obrovský skok proti starým portrétům, které byly malé siluety v šedozelené mlze
  a v avataru skoro nečitelné. Nové jsou tváře a polocelky s vlastní barvou světla.
  - U Hor, Citadely a Draků se varianty střídají mezi dvěma pojetími (1/3 a 2/4), u Goblinů a Bažin
    jsou 1–4 jedno pojetí a 5–6 druhé.
  - Pro avatar doporučuji varianty s **detailem tváře**, scény (celky) se hodí spíš na dlaždici lokace.
- **Goblini:** nejvýraznější Berserk 1–4, Král-portrét 1–4, Náčelník 5/6, Válečník 5/6. Troll 1–4 má
  tvář málo vidět, Troll 5/6 je čitelnější.
- **Bažiny:** Pijavice 1–4 je spíš ryba, lepší 5/6 (rudě svítící pijavice na molu). Zaklínač 5/6 se
  změnil v krysího muže (zajímavé, ale jiný motiv), oprava je v `kampan_opravy`. Pán Mlhy 5/6 má
  silnější bossí tvář, 1–4 víc atmosféru.
- **Hory:** Thane 2/4 (detail s korunou) jako avatar, 1/3 (trůn) jako bossí scéna. Mág 2/4 má prstenec
  run, který může působit jako text.
- **Citadela:** Temný Pán 2/4 (fialové oči, koruna) je silný avatar. Generál 2 má v rohu cizí tvář,
  oprava je v `kampan_opravy`.
- **Dračí impérium (nové):** všech 11 položek použitelných. Císař 2/4 (hlava s korunou) jako avatar,
  1/3 jako scéna. Lokace 1/3 (sopka a palác) působí víc než 2/4 (město shora).
- **Pozadí hradů:** první kolo (1–4) je klidnější a méně kontrastní než staré. Druhé pojetí (5–6) je
  dramatičtější a blíž starým (cesta do středu, silné světlo). Nejlíp vychází citadela 5/6, bažiny 5/6
  a zima 5/6; goblini 5/6 se vrátili k savaně jako staré. Zvaž, jestli pozadí měnit vůbec,
  staré jsou slušné.
- **Ruby karet:** 1–4 jsou tmavé a decentní (sedí k obsidiánu a bronzu), 5–6 jsou sytější
  (rudá kůže goblinů, fialový samet citadely, dračí oko). Staré mají nejvýraznější drahokam,
  ale působí lacině, jako 3D render.
- **Avatary hráče:** 1–4 jsou si skoro stejné, 5–6 mají jiný motiv (lebka s korunou, mág z profilu,
  goblin s mincí, maskovaný stín, řvoucí vojevůdce). Hrad 5/6 je zmetek, oprava je v
  `ostatni_opravy`. Pozor: avatary 3/4/6/9/11 a `enemy_icon_3` jsem nepřegeneroval. Kdyby se
  měnily jen některé, sada by byla stylově smíšená.
- **Ikona aplikace:** 1/3 (zlaté D) nebo 2/4 (mág v kápi). Zaoblené 2/4 mají světlé rohy, před
  nasazením by se ořízly.
- **Kolosy (140–142):** dřív jsi říkal „Kolose necháme“. Vygenerované jsou jen pro srovnání,
  klidně celé zahoď.

# Zvuky karet

> Každá karta má vlastní zvuk zahrání: WAV v `res/raw`, napojený přes `soundResId` v `CardPresentation.kt`. 176 zvuků je sestaveno z knihoven nahrávek (Sonniss GDC 2024/2026, RPG Sound Pack CC0). Předchozí verze vygenerovaná přes Stable Audio byla nahrazena, protože uživatel ji označil za „hroznou“.

## Jak se zvuk karty přehraje

- `playSoundForCardGlobal(card)` v `GameViewModel.kt` (sdílené offline i online) má pořadí: `card.soundResId` → `card.sound` (`CardSound`) → automatická detekce z efektů (`ATTACK`, `BUILD`, `RESOURCE`, `MINE_DESTROY`, `CARD_PLAY`).
- `soundResId` přichází z `CardPres` (`CardPresentation.kt`) přes `CardRepository`. Pokud ho karta má, přehraje se `SoundManager.playCustom(resId)`: nový `MediaPlayer` na jedno přehrání, hlasitost `sfx(0.75f)`, víc zvuků se může překrývat.
- Od 2026-11-22 má `soundResId` **všech 197 karet**, takže obecné zvuky (`card_attack*.mp3`, `build.mp3`, `card_resource.wav`, `card_play.mp3`) už pro karty slouží jen jako záloha.
- Pozor na tempo AI: soupeř hraje další kartu zhruba po 1 s (`delay(1000L)`). Zvuky delší než 1 s se proto při rychlém sledu karet krátce překryjí. Uživatel chtěl 1–1,5 s vědomě.

## Formát a úroveň

| Vlastnost | Hodnota |
|---|---|
| Formát | WAV PCM 16 bit, stereo, 44,1 kHz (zdroje 48/96 kHz převzorkované přes FFT) |
| Délka | 0,6–1,5 s. Údery jsou přirozeně kratší, vždy od nástupu po další událost ve zdrojovém souboru, max 1,5 s |
| Okraje | fade-in 2 ms (u výřezu z plochy 20 ms), fade-out 150 ms (kosinový) |
| Hlasitost | nejhlasitějších 400 ms na **−12,5 LUFS** (K-vážení podle ITU-R BS.1770). Medián zvuků, které se uživateli líbí (Berserk, Nestabilní vír) a starších nahrávek, je −12,1 LUFS |
| Jasné zvuky | pokud je víc než 40 % energie nad 6 kHz (mince, sklo), low-pass 10 kHz a cíl o 1,5 dB níž |
| Limiter | měkký (tanh), strop −1 dBFS, smí stlačit špičky nejvýš o 6 dB. Řídká ťuknutí proto zůstávají kolem −16 LUFS |

**Proč ne RMS:** první verze (Stable Audio) srovnávala RMS. Syčivé zvuky měly přes 10 % energie nad 6 kHz, ucho je tam nejcitlivější, takže zněly o 4+ dB hlasitěji (Magický pramen −8,3 LUFS) a „rvaly uši“.

## Zdroje a licence

Zvuky jsou z knihoven nahrávek. Uživatel je stáhl 2026-11-22 do `C:\Users\Scratch\Downloads`, protože weby blokují automatické stahování (Cloudflare):

| Knihovna | Licence | Použití |
|---|---|---|
| Sonniss **GDC 2024** Game Audio Bundle (9 zipů, 23,5 GB) | Sonniss GDC licence: komerční použití ve hře bez uvádění autora. **Zakázáno** šířit zvuky jako zvuky (i upravené) a trénovat na nich AI | kámen (Bluezone Stone Impact), zbraně (Justsoundeffects Melee), kovové údery (Haunted Metal), dřevo, voda, oheň, hrom, výbuchy (DavidDumais), zvonky… |
| Sonniss **GDC 2026** Game Audio Bundle (5 zipů, 7,5 GB) | dtto | magie (Fantasy Game 2, Emotion and Magic), zbraně (Melee Weapons 2, Medieval Weapons), dinosauři (mamut), vlkodlak, karty, knihy, kostelní zvony, oheň, led |
| RPG Sound Pack (artisticdude, OpenGameArt) | CC0 | mince, kouzlo, meče, zlobři, stíny, slizy |

> ⚠️ Repozitář `Scratch741/Termiti` je **veřejný**, takže WAVy odvozené ze Sonnissu jsou v něm volně ke stažení. Licence přitom zakazuje „zveřejnění zvuků jako zvuků“ (hotové APK je v pořádku). Uživatel 2026-11-23 rozhodl pushnout je i tak, na vlastní riziko. Čisté řešení: přepnout repozitář na soukromý, nebo zvuky ze Sonnissu nahradit CC0.

## Postup

Skripty jsou v `C:\Users\Scratch\Pictures\Generated\zvuky\knihovny\_postup\`:
1. `index_zips.py` a `list_relevant.py` vypíšou obsah zipů a vyberou relevantní knihovny (bez aut, motorek, ambientů měst).
2. `extract.py` rozbalí vybrané WAVy (436 souborů, 7,5 GB) do `…\zvuky\knihovny\` jako `g001…`, `r341…` a zapíše `_index.tsv` (ID → zip a původní cesta).
3. `scan.py` a `audio.py` rozdělí dlouhé soubory na jednotlivé události. GDC soubory obsahují desítky variant za sebou.
4. `map.tsv` určuje pro každou kartu 1–3 vrstvy:
   - `g041:2` je třetí událost zdroje g041;
   - `g172@4.8` je výřez od 4,8 s;
   - `+0.3` je posun vrstvy, `~-6` zesílení v dB.
5. `render.py` vrstvy smíchá, ořízne, srovná hlasitost a zapíše WAV do `res/raw/<nazev>.wav` a k poslechu do `C:\Users\Scratch\Pictures\Generated\zvuky\karty_knihovny\<ID>_<nazev>.wav`.

Kontroly při sestavení:
- zesílení přes +15 dB znamená tichý zdroj a zesílený šum, takže se mění zdroj;
- hlavní zvuk musí přijít do 0,3 s od zahrání, jinak se posune výřez;
- přehled tvaru vlny je ve `lwaves*.png`.

Výměna zvuku jedné karty: uprav řádek v `map.tsv` a spusť `render.py … <nazev>`. Kód se měnit nemusí.

Lokální AI (Ollama) zvuk hodnotit neumí. K dispozici je jen `gemma4:12b`, která bere text a obrázky, ne audio.

## Přehled zvuků

Beze změny zůstaly zvuky, které se uživateli líbí: `"024"` Berserk (`berserk.wav`) a `"C25"` Nestabilní vír (`nestabilni_vir.wav`). Beze změny jsou i starší ručně nahrané zvuky: `"008"` a `"019"` `arrow_hit`, `"003"` `fireball`, `"017"` `valecny_sekyrnik`, `"021"` `delostrelectvo`, `"022"` `primy_zasah`, `"026"` `ostrelovac`, `"027"` `drums_of_war`, `"046"` `goblin`, `"047"` `ogr`, `"049"` `jed`, `"051"`/`"078"`/`"C10"` `dragon_breath`, `"052"` `demon_laugh`, `"053"` `plamenomet`, `"C05"` `chaoticky_vybuch`, `"C19"` `spalena_knihovna`.

Z knihoven (2026-11-22, 176 karet):

| ID | Karta | Soubor | Zdroje (knihovna – soubor) | Délka |
|---|---|---|---|---|
| `"001"` | Rychlý útok | `rychly_utok.wav` | GDC2026 344 Audio - Cinematic Fight Vol. 1 – FGHTImpt_4 x Punch, Body 02_344 Audio_Cinematic Fight Vol 1<br>GDC2024 BluezoneCorp - High Voltage – Bluezone_BC0299_electricity_texture_sizzling_crackling_006 | 0,96 s |
| `"007"` | Silný úder | `silny_uder.wav` | GDC2026 David Dumais Audio - Melee Weapons Sound Effects Pack 2 – SWSH_SWING IMPACTS Quick Heavy Weapon Swing To Thud Impact V<br>GDC2024 The Soundcatcher - Seismic Activities – Cabinet_Metal_Rumble_Rattle_Shake_Vibrate_Debris_Dpa4060_Sei | 1,5 s |
| `"006"` | Převaha síly | `prevaha_sily.wav` | GDC2024 Justsoundeffects - Melee Weapons – WEAPArmr_Metal Shield Block Hits_JSE_MW<br>GDC2024 Mechanical Wave - Torturing Metal – METLCrsh_Drop Fall Metal Rattle Scrap Debris_06_MWSFX_TM | 1,02 s |
| `"002"` | Kamenná zeď | `kamenna_zed.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041 | 0,6 s |
| `"010"` | Palisáda | `palisada.wav` | GDC2026 344 Audio - Haunting Ambiences Vol. 3 – WOODImpt_Wooden Hit, Dark, Heavy Hit, Vampire's Prison_344 A<br>GDC2024 InMotionAudio - Wood – WOODBrk_Snap09_InMotionAudio_Wood | 0,86 s |
| `"005"` | Posila hradu | `posila_hradu.wav` | GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Opening and Closing 09_RogueWaves_Cre<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,5 s |
| `"009"` | Pevné základy | `pevne_zaklady.wav` | GDC2024 BluezoneCorp - Modern Cinematic Impact – Bluezone_BC0294_modern_cinematic_impact_boom_003<br>GDC2026 Jake Fielding - Interior Wind Rain and Storms – THUN_Interior Thunder Rumble_JF_INT Storm_01 | 1,5 s |
| `"018"` | Mohutná věž | `mohutna_vez.wav` | GDC2024 BluezoneCorp - Modern Cinematic Impact – Bluezone_BC0294_modern_cinematic_impact_boom_003<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Medium Rock Dropping 011 | 1,5 s |
| `"004"` | Magie | `magie.wav` | GDC2026 Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms – MAGAngl_Magic Light Spell Enchantment Potion Effect Tonal Br | 1,5 s |
| `"011"` | Zásoby kamene | `zasoby_kamene.wav` | RPG Sound Pack (CC0) – spell<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,5 s |
| `"012"` | Mobilizace | `mobilizace.wav` | GDC2024 Mechanical Wave - Cinematic Feel – DSGNBram_Cinematic Horn_MWSFX_CF 05<br>RPG Sound Pack (CC0) – chainmail1 | 1,4 s |
| `"013"` | Magický pramen | `magicky_pramen.wav` | GDC2024 BluezoneCorp - Designed Water – Bluezone_BC0298_designed_water_texture_014<br>GDC2024 InMotionAudio - Cave Design – WATRDrip_SingleDripDesign05_InMotionAudio_CaveDesign | 1,45 s |
| `"014"` | Kamenolom | `kamenolom.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2024 The Soundcatcher - Seismic Activities – Cabinet_Metal_Rumble_Rattle_Shake_Vibrate_Debris_Dpa4060_Sei | 1,5 s |
| `"015"` | Výcvikový tábor | `vycvikovy_tabor.wav` | GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi | 1,0 s |
| `"016"` | Velký pramen | `velky_pramen.wav` | GDC2024 BluezoneCorp - Designed Water – Bluezone_BC0298_designed_water_transition_impact_017 | 1,4 s |
| `"020"` | Beranidlo | `beranidlo.wav` | GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Door Slams, Impacts, 3_RogueWaves_Cre<br>GDC2024 DavidDumais - Explosion SFX Pack – WOODCrsh_Designed Wood Crash And Debris 13_DDUMAIS_NONE | 1,34 s |
| `"023"` | Dvojitý úder | `dvojity_uder.wav` | GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPSwrd_Sword Slide Cuts, Metallic, Impact CM4 2_344 Audio_<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPSwrd_Sword Slide Cuts, Metallic, Impact CM4 2_344 Audio_ | 1,48 s |
| `"025"` | Protiútok | `protiutok.wav` | GDC2024 Justsoundeffects - Melee Weapons – WEAPArmr_Metal Shield Block Hits_JSE_MW<br>GDC2026 David Dumais Audio - Melee Weapons Sound Effects Pack 2 – METLFric_SWING SCRAPE Swift Melee Weapon Swing With A Long B | 1,5 s |
| `"028"` | Záplata | `zaplata.wav` | GDC2024 Pole Position - The Metal Hit Sweeteners Library – Spade - HIT - Drumstick - Ring - Mute<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015 | 1,0 s |
| `"029"` | Opevnění | `opevneni.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2024 InMotionAudio - Wood – WOODImpt_Drops20_InMotionAudio_Wood | 0,61 s |
| `"030"` | Kamenný val | `kamenny_val.wav` | GDC2024 The Soundcatcher - Seismic Activities – Staircase_Metal_Rumble_Rattling_Vibrate_Debris_D100_Seismic_<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,4 s |
| `"031"` | Renovace | `renovace.wav` | GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different <br>GDC2024 Pole Position - The Metal Hit Sweeteners Library – Spade - HIT - Drumstick - Ring - Mute | 1,46 s |
| `"032"` | Citadela | `citadela.wav` | GDC2026 Jake Fielding - Cinematic Horn Braams – DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Ins | 1,4 s |
| `"033"` | Zemní val | `zemni_val.wav` | GDC2024 Pole Position - The Metal Hit Sweeteners Library – Spade - HIT - Drumstick - Ring - Mute<br>GDC2024 The Soundcatcher - Seismic Activities – Kitchen_Cupboard_Impact_Rattle_Vibrate_Rumble_Mkh8040Seismic | 1,46 s |
| `"034"` | Opravář | `opravar.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010 | 0,76 s |
| `"035"` | Základní kámen | `zakladni_kamen.wav` | GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_011<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,4 s |
| `"036"` | Hradní příkop | `hradni_prikop.wav` | RPG Sound Pack (CC0) – chainmail2<br>GDC2024 InMotionAudio - Submerge – WATRImpt_Impact20_InMotionAudio_Submerge | 1,32 s |
| `"037"` | Rychlá magie | `rychla_magie.wav` | GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Pure SciFi-Whoosh Fast 03_RSCPC_DW<br>RPG Sound Pack (CC0) – magic1 | 1,32 s |
| `"038"` | Vojenský rozkaz | `vojensky_rozkaz.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Large Book Squeak Page Turn_ 01_MWSFX_CDAP<br>GDC2026 Epic Stock Media - HD Lock And Mechanism Sound Design Kit – MECHLtch_Click Deep Mechanism Latch Button Nearfield Thunk 0 | 0,88 s |
| `"039"` | Stavební boom | `stavebni_boom.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – GAMEMisc_Magic Creation 23_CB Sounddesign_APPlicable Sounds | 1,5 s |
| `"040"` | Alchymie | `alchymie.wav` | GDC2024 Eiravaein Works - Flask – OBJCont_Flask,glass,figuredbottle,realcork,liquid,remove,squ<br>RPG Sound Pack (CC0) – bubble2<br>RPG Sound Pack (CC0) – metal-ringing | 1,33 s |
| `"041"` | Magické trio | `magicke_trio.wav` | GDC2024 Mechanical Wave - Sound Effects Collection – BELLHand_Metallic Bell_ 22_MWSFX_SEC<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Medium Rock Dropping 011 | 1,5 s |
| `"042"` | Velký kamenolom | `velky_kamenolom.wav` | GDC2026 Cinematic Sound Design - Colossal Impacts – Woosh Debris<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041 | 1,05 s |
| `"043"` | Výcvikové centrum | `vycvikove_centrum.wav` | GDC2024 Justsoundeffects - Melee Weapons – WEAPSwrd_Weapon 03 Slow Scrapes_JSE_MW<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPSwrd_Sword Slide Cuts, Metallic, Impact CM4 2_344 Audio_ | 1,34 s |
| `"044"` | Trifekta dolů | `trifekta_dolu.wav` | GDC2024 BluezoneCorp - Steampunk Machines – Bluezone_BC0305_steampunk_machine_mechanical_sequence_start_<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – GAMEMisc_Magic Creation 23_CB Sounddesign_APPlicable Sounds | 1,5 s |
| `"045"` | Očarované doly | `ocarovane_doly.wav` | GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Xylophone Ringtone 2_CB Sounddesign_APPlicable Sounds<br>GDC2024 InMotionAudio - Cave Design – WATRDrip_SingleDripDesign05_InMotionAudio_CaveDesign | 1,5 s |
| `"048"` | Upír | `upir.wav` | RPG Sound Pack (CC0) – shade10<br>GDC2024 Justsoundeffects - Gore Mini Pack – GORESplt_Gore Splatter 01_JSE_GMP | 1,09 s |
| `"050"` | Podkopání hradeb | `podkopani_hradeb.wav` | GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_<br>GDC2026 Cinematic Sound Design - Colossal Impacts – Woosh Debris | 1,4 s |
| `"054"` | Válečný pochod | `valecny_pochod.wav` | GDC2024 Jake Fielding - Haunted Metal Vol.3 - Cinematic Tones & Textures – DSGNTonl_Deep Metallic Hits, Epic, Sub, Bass_JF_Haunted Meta<br>RPG Sound Pack (CC0) – chainmail1 | 1,4 s |
| `"055"` | Poslední vzdor | `posledni_vzdor.wav` | GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPSwrd_Sword Slide Cuts, Metallic, Impact CM4 2_344 Audio_<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPArmr_Metal Shield Spin On Floor, Buckler MKH_344 Audio_M | 1,5 s |
| `"056"` | Nájezdník | `najezdnik.wav` | GDC2026 David Dumais Audio - Melee Weapons Sound Effects Pack 2 – SWSH_SWING IMPACTS Quick Heavy Weapon Swing To Thud Impact V<br>RPG Sound Pack (CC0) – ogre2 | 0,96 s |
| `"057"` | Bašta | `basta.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2026 Jake Fielding - Interior Wind Rain and Storms – THUN_Interior Thunder Rumble_JF_INT Storm_01 | 1,5 s |
| `"058"` | Obranný val | `obranny_val.wav` | GDC2026 344 Audio - Haunting Ambiences Vol. 3 – WOODImpt_Wooden Hit, Dark, Heavy Hit, Vampire's Prison_344 A<br>GDC2024 Mechanical Wave - Lumberjack – WOODBrk_Large Tree Heavy Fall-07_MWSFX_LJ | 0,94 s |
| `"059"` | Pevnostní hrad | `pevnostni_hrad.wav` | GDC2024 UberDuo - The Wood Stove Audio Prop Set – DOORMetl_Woodstove, Door, Iron, Close_UberDuo_WOOD<br>GDC2024 Mechanical Wave - Torturing Metal – METLImpt_Metal Impact-03_MWSFX_TM | 1,0 s |
| `"060"` | Chrám | `chram.wav` | GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different <br>GDC2024 Orbital Emitter - Cinematic Transitions for Editors Volume 2 – 112,StringsSectionRiser | 1,4 s |
| `"061"` | Tunely | `tunely.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Medium Rock Dropping 011<br>GDC2024 The Soundcatcher - Seismic Activities – Cabinet_Metal_Rumble_Rattle_Shake_Vibrate_Debris_Dpa4060_Sei | 1,5 s |
| `"062"` | Obranná aliance | `obranna_aliance.wav` | GDC2024 Justsoundeffects - Melee Weapons – WEAPArmr_Metal Shield Block Hits_JSE_MW<br>GDC2024 Justsoundeffects - Melee Weapons – WEAPArmr_Metal Shield Block Hits_JSE_MW | 1,08 s |
| `"063"` | Věž strážní | `vez_strazni.wav` | GDC2024 Mechanical Wave - Sound Effects Collection – BELLHand_Metallic Bell_ 22_MWSFX_SEC<br>GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Airy-Whoosh Wind Gust 11_RSCPC_DW | 1,5 s |
| `"064"` | Zásobník | `zasobnik.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – OBJPack_Big Cardboard Box Impact_ 02_MWSFX_CDAP<br>GDC2024 Jake Fielding - Squeaky Gates – DOORGate_Wooden Metal Hinge Creaks_Jake Fielding_Squeaky Gat<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Shaking Small Wooden Box 030 | 1,05 s |
| `"065"` | Lupič | `lupic.wav` | GDC2026 Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms – CLOTHFlp_Action Inventory Open Flip Cloth Canvas Bag Slide L<br>RPG Sound Pack (CC0) – coin2 | 0,85 s |
| `"066"` | Kamenná daň | `kamenna_dan.wav` | GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Deviant-Whoosh Flabby Slow 09_RSCPC_DW<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,5 s |
| `"067"` | Sabotér | `saboter.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2024 Mechanical Wave - Sound Effects Collection – ICEMisc_Ice Sizzle_05_MWSFX_SEC | 1,5 s |
| `"068"` | Demoralizace | `demoralizace.wav` | RPG Sound Pack (CC0) – shade9<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – WINDInt_Loop Weather Wind Whipping Constricted Flow Turbulen | 1,46 s |
| `"069"` | Dvojitý agent | `dvojity_agent.wav` | GDC2024 Mechanical Wave - Glass – GLASTonl_Dark Tone Thrill_07_MWSFX_GL | 1,5 s |
| `"070"` | Krize zásobování | `krize_zasobovani.wav` | GDC2024 InMotionAudio - Wood – WOODBrk_Snap09_InMotionAudio_Wood<br>GDC2024 InMotionAudio - Wood – WOODImpt_Drops20_InMotionAudio_Wood | 0,7 s |
| `"071"` | Špión | `spion.wav` | GDC2026 344 Audio - Antique Small Metals – METLMvmt_  Tinkering Antique Lock_344 Audio_Antique Small Me<br>GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Opening and Closing 09_RogueWaves_Cre | 1,41 s |
| `"072"` | Zbrojnice | `zbrojnice.wav` | GDC2024 Pole Position - The Metal Hit Sweeteners Library – Iron - Thick - HIT - Hammer<br>GDC2024 Pole Position - The Metal Hit Sweeteners Library – Iron - Thick - HIT - Hammer<br>GDC2024 Justsoundeffects - Steampunk Gadgets – AIRBrst_Steam Release Short 03_JSE_SG_Mono | 1,5 s |
| `"073"` | Škola magie | `skola_magie.wav` | GDC2026 344 Audio - Antique Books – PAPRMisc_Antique Books Flicking Through Pages 11_344 Audio_A<br>GDC2024 Rescopic Sound - Parallax – SCIMisc_Ping 18_RSCPC_PX | 1,5 s |
| `"074"` | Tržiště | `trziste.wav` | RPG Sound Pack (CC0) – coin<br>RPG Sound Pack (CC0) – coin3<br>GDC2024 Mechanical Wave - Sound Effects Collection – BELLHand_Metallic Bell_ 22_MWSFX_SEC | 1,0 s |
| `"075"` | Rozmach těžby | `rozmach_tezby.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Xylophone Ringtone 2_CB Sounddesign_APPlicable Sounds | 1,5 s |
| `"076"` | Vojenská základna | `vojenska_zakladna.wav` | GDC2026 Jake Fielding - Cinematic Horn Braams – DSGNBram____Cinematic Horn Braam, Epic, Cinematic, Dark, Ins<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – WINDInt_Loop Weather Wind Whipping Constricted Flow Turbulen | 1,4 s |
| `"077"` | Přeměna magie | `premena_magie.wav` | GDC2026 Epic Stock Media - Tower Defense Game – DSGNStngr_Action Deploy Units Sword Slice Special Move Layer | 1,5 s |
| `"079"` | Obléhání | `oblehani.wav` | GDC2024 BluezoneCorp - Steampunk Weapon And Textures – Bluezone_BC0296_steampunk_weapon_cannon_shot_013_02 | 1,5 s |
| `"080"` | Velkovýroba | `velkovyroba.wav` | GDC2024 Justsoundeffects - Steampunk Gadgets – AMBDsgn_Factory Hall with Large Steam Mashines 02_JSE_SG<br>GDC2024 Pole Position - The Metal Hit Sweeteners Library – Iron - Thick - HIT - Hammer | 1,46 s |
| `"131"` | Invaze | `invaze.wav` | GDC2024 Pole Position - The Warfare 2 Library – Tank Mine - EXPLOSION - Mounted on Metal Beam - DISTANT - AM<br>GDC2026 Cinematic Sound Design - Colossal Impacts – Woosh Debris | 1,4 s |
| `"130"` | Rabování | `rabovani.wav` | GDC2026 Cinematic Sound Design - Hybrid Game & UI Elements – Foley Coin Flip Single Fast<br>RPG Sound Pack (CC0) – coin3<br>GDC2026 344 Audio - Antique Luggage – OBJLug_Rumaging Through Suitcase, Antique Suitcase_344 Audio | 0,67 s |
| `"129"` | Příprava | `priprava.wav` | GDC2026 Cinematic Sound Design - Paper Foley – A4 Printing Paper Rattle Page Turn Tail<br>GDC2024 UberDuo - Game Night Audio Props – GAMEBoard_Chess, Contact, King Takes_UberDuo_Game | 1,0 s |
| `"081"` | Rychlá hradba | `rychla_hradba.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 0,66 s |
| `"082"` | Masivní zeď | `masivni_zed.wav` | GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_075<br>GDC2026 344 Audio - Elemental Palette Designed Vol. 1 – WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental  | 1,5 s |
| `"083"` | Nouzové opevnění | `nouzove_opevneni.wav` | GDC2026 344 Audio - Haunting Ambiences Vol. 3 – WOODImpt_Wooden Hit, Dark, Heavy Hit, Vampire's Prison_344 A<br>GDC2026 344 Audio - Haunting Ambiences Vol. 3 – WOODImpt_Wooden Hit, Dark, Heavy Hit, Vampire's Prison_344 A<br>GDC2024 Mechanical Wave - Cardboard and Paper – OBJPack_Big Cardboard Box Slide_ 04_MWSFX_CDAP | 1,37 s |
| `"084"` | Velká oprava | `velka_oprava.wav` | GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Opening and Closing 09_RogueWaves_Cre<br>GDC2024 Pole Position - The Metal Hit Sweeteners Library – Spade - HIT - Drumstick - Ring - Mute<br>GDC2024 UberDuo - The Workshop Audio Playset – TOOLHand_Bar Clamp, Screw, Tighten_UberDuo_TOOL | 1,12 s |
| `"085"` | Královská obnova | `kralovska_obnova.wav` | GDC2024 Orbital Emitter - Cinematic Transitions for Editors Volume 2 – 80,TheGong<br>GDC2026 Cinematic Sound Design - Hybrid Game & UI Elements – Cofetti Whoosh Pluck Spill | 1,5 s |
| `"086"` | Zednická rota | `zednicka_rota.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_steel_bar_01_010<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041 | 0,69 s |
| `"087"` | Pevnost | `pevnost.wav` | GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Door Slams, Impacts, 3_RogueWaves_Cre<br>GDC2026 344 Audio - Antique Luggage – OBJLug_Suitcase Being Locked, Antique Suitcase 1_344 Audio_A | 1,0 s |
| `"088"` | Zesílené hradby | `zesilene_hradby.wav` | GDC2024 Pole Position - The Metal Hit Sweeteners Library – Iron - Thick - HIT - Hammer<br>GDC2024 Pole Position - The Metal Hit Sweeteners Library – Oil Barrel - HIT - Hammer - Take 2 | 1,0 s |
| `"089"` | Architekt | `architekt.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Large Book Squeak Page Turn_ 01_MWSFX_CDAP<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041 | 0,7 s |
| `"090"` | Rozšíření těžby | `rozsireni_tezby.wav` | GDC2024 The Soundcatcher - Seismic Activities – Metal_Rails_Pack_Rumble_Rattle_Shake_Vibrate_Debris_Mkh8040_<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015 | 1,4 s |
| `"091"` | Barikády | `barikady.wav` | GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi<br>GDC2024 Jake Fielding - Squeaky Gates – DOORGate_Wooden Metal Hinge Creaks_Jake Fielding_Squeaky Gat | 1,0 s |
| `"092"` | Strategická výstavba | `strategicka_vystavba.wav` | GDC2026 Epic Stock Media - Board Game - Sound Set Kit for Tabletop and Digital Games – GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Sma<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Kalimba 3 Up_CB Sounddesign_APPlicable Sounds | 0,85 s |
| `"093"` | Cihla na cihlu | `cihla_na_cihlu.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015 | 1,01 s |
| `"094"` | Sklad materiálu | `sklad_materialu.wav` | GDC2024 InMotionAudio - Wood – WOODImpt_Drops20_InMotionAudio_Wood<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Dry Tree Branches Contact 024<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 0,9 s |
| `"095"` | Obchod s kamenem | `obchod_s_kamenem.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015<br>GDC2026 Cinematic Sound Design - Hybrid Game & UI Elements – Foley Coin Flip Single Fast | 0,66 s |
| `"096"` | Nedobytná pevnost | `nedobytna_pevnost.wav` | GDC2026 Epic Stock Media - Elemental Mutation Whooshes and Impacts – WATRImpt_Impact Water Deep Submerge Bubble Drown Ship Hit 05<br>GDC2026 Jake Fielding - Interior Wind Rain and Storms – THUN_Interior Thunder Rumble_JF_INT Storm_01 | 1,5 s |
| `"097"` | Obnova království | `obnova_kralovstvi.wav` | GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different  | 1,4 s |
| `"098"` | Hod cihlou | `hod_cihlou.wav` | GDC2026 Cinematic Sound Design - Ultra Transitions & Impacts – Woosh Sweep Slide Infographics Basic<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2024 Sonic Bat - Videogame Foley Essentials Vol. II – SBvfe2_Medium Rock Dropping 011 | 0,9 s |
| `"099"` | Temný přenos | `temny_prenos.wav` | GDC2026 Cinematic Sound Design - Ultra Transitions & Impacts – Transition Braam Slow Dark Creepy | 1,5 s |
| `"100"` | Krvavý úder | `krvavy_uder.wav` | RPG Sound Pack (CC0) – sword-unsheathe<br>GDC2024 Justsoundeffects - Melee Weapons – WEAPAxe_Long Two-Handed Axe Flesh Hit_JSE_MW<br>GDC2024 Justsoundeffects - Gore Mini Pack – GORESplt_Gore Splatter 01_JSE_GMP | 1,0 s |
| `"101"` | Náhlá smrt | `nahla_smrt.wav` | GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Deviant-Whoosh Flabby Slow 09_RSCPC_DW<br>GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different  | 1,5 s |
| `"102"` | Kamenný příval | `kamenny_prival.wav` | GDC2024 The Soundcatcher - Seismic Activities – Staircase_Metal_Rumble_Rattling_Vibrate_Debris_D100_Seismic_<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_015 | 1,4 s |
| `"103"` | Magické rozdělení | `magicke_rozdeleni.wav` | GDC2026 Epic Stock Media - Elemental Mutation Whooshes and Impacts – ELECMisc_Impact Electric Tonal Deep Movement Motion Hiss Gli | 1,5 s |
| `"132"` | Zoufalý žold | `zoufaly_zold.wav` | RPG Sound Pack (CC0) – coin3<br>GDC2024 UberDuo - Game Night Audio Props – GAMEBoard_Chess, Contact, King Takes_UberDuo_Game<br>RPG Sound Pack (CC0) – coin2 | 1,0 s |
| `"133"` | Zamořené krysy | `zamorene_krysy.wav` | GDC2026 Epic Stock Media - HD Game Materials – ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07<br>GDC2026 Epic Stock Media - HD Game Materials – ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07<br>GDC2024 UberDuo - The Wood Stove Audio Prop Set – FIREMisc_Fire Crackling In A Woodstove_UberDuo_WOOD | 1,4 s |
| `"134"` | Zamořená Krysa | `zamorena_krysa.wav` | GDC2026 Epic Stock Media - HD Game Materials – ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07<br>GDC2026 Epic Stock Media - HD Game Materials – ICEFric_Dry Ice Squeak Metal Animal Mouse Imitation Short 07 | 0,94 s |
| `"135"` | Osudová mince | `osudova_mince.wav` | GDC2026 Cinematic Sound Design - Hybrid Game & UI Elements – Foley Coin Flip Single Fast<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"136"` | Poslední výpad | `posledni_vypad.wav` | GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Door Slams, Impacts, 3_RogueWaves_Cre<br>GDC2026 David Dumais Audio - Melee Weapons Sound Effects Pack 2 – SWSH_SWING IMPACTS Quick Heavy Weapon Swing To Thud Impact V | 1,43 s |
| `"137"` | Zapomenutá poznámka | `zapomenuta_poznamka.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Paper Sheet Crumple_ 04_MWSFX_CDAP<br>RPG Sound Pack (CC0) – magic1 | 1,07 s |
| `"138"` | Podkopané valy | `podkopane_valy.wav` | GDC2024 Mechanical Wave - Lumberjack – WOODBrk_Tree Crack and Fall-13_MWSFX_TJ | 1,5 s |
| `"139"` | Pohlcení hradeb | `pohlceni_hradeb.wav` | GDC2024 Mechanical Wave - Torturing Metal – METLTonl_Reversed Metal-28_MWSFX_TM<br>GDC2024 Jake Fielding - Haunted Metal Vol.3 - Cinematic Tones & Textures – DSGNRise_Deep Metallic Rattle, Tense, Epic_JF_Haunted Metal  | 1,5 s |
| `"140"` | Prastarý kolos | `prastary_kolos.wav` | GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_214<br>GDC2024 The Soundcatcher - Seismic Activities – Staircase_Metal_Rumble_Rattling_Vibrate_Debris_D100_Seismic_ | 1,4 s |
| `"141"` | Probuzení kolosu | `probuzeni_kolosu.wav` | GDC2026 Alexander Kopeikin - 100 kHz Designed Ice – ice, crack, ice block snapping-001<br>GDC2024 Jake Fielding - Haunted Metal Vol.2 - Cinematic Creaks & Risers – DSGNRise_Cinematic Metallic Riser, Trailer, Designed Eerie W | 1,4 s |
| `"142"` | Úder kolosu | `uder_kolosu.wav` | GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_075<br>GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_041 | 1,4 s |
| `"143"` | Velké zemětřesení | `velke_zemetreseni.wav` | GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_011<br>GDC2024 The Soundcatcher - Seismic Activities – Kitchen_Cupboard_Impact_Rattle_Vibrate_Rumble_Mkh8040Seismic | 1,4 s |
| `"108"` | Likvidace | `likvidace.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Paper Sheet Crumple_ 04_MWSFX_CDAP<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"109"` | Rekrut | `rekrut.wav` | RPG Sound Pack (CC0) – coin<br>RPG Sound Pack (CC0) – chainmail1 | 0,88 s |
| `"112"` | Stavitel | `stavitel.wav` | GDC2024 BluezoneCorp - Stone Impact – Bluezone_BC0297_stone_impact_hammer_015<br>GDC2024 Rogue Waves - Creaking Door – DOORCreak_Wooden Door, Opening and Closing 09_RogueWaves_Cre | 1,43 s |
| `"110"` | Vzpomínka | `vzpominka.wav` | GDC2024 Mechanical Wave - Glass – GLASMisc_Reverse Glass Effect_04_MWSFX_GL<br>GDC2026 344 Audio - Antique Books – PAPRMisc_Antique Books Slow Page Turns 6_344 Audio_Antiques  | 1,28 s |
| `"111"` | Intuice | `intuice.wav` | GDC2026 Sonic Bat - Music Boxes – SBmb_Music Box A 013 | 1,5 s |
| `"104"` | Válečný trénink | `valecny_trenink.wav` | GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi<br>GDC2024 Justsoundeffects - Melee Weapons – WEAPArmr_Metal Shield Block Hits_JSE_MW | 1,27 s |
| `"107"` | Prokletí | `prokleti.wav` | GDC2026 344 Audio - Ghostly Presences Vol. 1 – AMBDsgn_Evil Spell Ambience_344 Audio_Ghostly Presences | 1,4 s |
| `"C01"` | Chaotická jiskra | `chaoticka_jiskra.wav` | GDC2026 InMotionAudio - Arc – ELECArc_ArcDesign15_InMotionAudio_Arc<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"C02"` | Entropie | `entropie.wav` | GDC2026 Epic Stock Media - Elemental Mutation Whooshes and Impacts – GLASMvmt_Whoosh Glass Crystal Fragments Sharp Shards Dry 05_ | 1,5 s |
| `"C03"` | Chaotický důl | `chaoticky_dul.wav` | GDC2024 UberDuo - The Wood Stove Audio Prop Set – FIREMisc_Fire Crackling In A Woodstove_UberDuo_WOOD<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,4 s |
| `"C04"` | Krádež chaosu | `kradez_chaosu.wav` | GDC2024 Mechanical Wave - Cinematic Feel – WHSH_Sci-Fi Heavy Whoosh_MWSFX_CF 29<br>GDC2024 Mechanical Wave - Glass – GLASMisc_Glass Bottle Open_01_MWSFX_GL | 1,5 s |
| `"C06"` | Bouře chaosu | `boure_chaosu.wav` | GDC2024 Bolt - Backyard Rain & Thunder - Suburban Rain Recordings – RAIN_Distant Thunder and Rain Long Thunderstorm C_BOLT_Backy<br>GDC2024 BluezoneCorp - High Voltage – Bluezone_BC0299_electricity_surge_discharge_electrical_arc_c | 1,4 s |
| `"C07"` | Chaotický štít | `chaoticky_stit.wav` | GDC2026 InMotionAudio - Arc – ELECArc_ArcPowerUpDesign04_InMotionAudio_Arc | 1,4 s |
| `"C08"` | Zázrak chaosu | `zazrak_chaosu.wav` | GDC2026 Epic Stock Media - Elemental Mutation Whooshes and Impacts – FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_E<br>GDC2024 Orbital Emitter - Cinematic Transitions for Editors Volume 2 – 112,StringsSectionRiser | 1,5 s |
| `"C09"` | Chaotická krize | `chaoticka_krize.wav` | GDC2024 BluezoneCorp - High Voltage – Bluezone_BC0299_electricity_surge_discharge_electrical_arc_c<br>GDC2024 Pole Position - Artillery Vehicle Bandkanon 1C – Bandkanon 1C - t10 - VAR SFX - Simulated Shell Ejection 1 -  | 1,5 s |
| `"C11"` | Chaos a řád | `chaos_a_rad.wav` | GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different <br>GDC2024 Jake Fielding - Haunted Metal Vol.1 - Cinematic Hits & Impacts – DSGNBoom_Cinematic Metallic Hit, Boom, Trailer, Sub_JF_Haunt | 1,4 s |
| `"C12"` | Anarchie | `anarchie.wav` | GDC2026 344 Audio - Haunting Ambiences Vol. 5 – FIRECrkl_Fire Crackling, Popping, Witch's Cauldron_344 Audio<br>GDC2024 Mechanical Wave - Glass – GLASBrk_Glass Break Hit_04_MWSFX_GL | 1,4 s |
| `"C13"` | Sabotáž | `sabotaz.wav` | GDC2024 Rescopic Sound - High Voltage – ELECEmf_Electrical Panel Pitchdown 12st_RSCPC_HV | 1,4 s |
| `"C14"` | Ničení kamenolomu | `niceni_kamenolomu.wav` | GDC2024 DavidDumais - Explosion SFX Pack – EXPLDsgn_Nuclear Explosion 07_DDUMAIS_NONE | 1,4 s |
| `"C15"` | Zákeřnost | `zakernost.wav` | GDC2024 Justsoundeffects - Melee Weapons – WEAPAxe_One-Handed Axe Drops_JSE_MW<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"C16"` | Velká sabotáž | `velka_sabotaz.wav` | GDC2024 BluezoneCorp - Steampunk Weapon And Textures – Bluezone_BC0296_steampunk_weapon_flare_shot_explosion_003<br>GDC2024 InMotionAudio - Wood – WOODBrk_Snap09_InMotionAudio_Wood | 1,14 s |
| `"C17"` | Telekineze | `telekineze.wav` | GDC2024 Mechanical Wave - Cinematic Feel – WHSH_Sci-Fi Heavy Whoosh_MWSFX_CF 29<br>GDC2026 344 Audio - Casino Cards Vol. 1 – GAMECas_Pick Up Multiple Cards At Once 5_344 Audio_Casino Ca | 1,0 s |
| `"C18"` | Krádež osudu | `kradez_osudu.wav` | GDC2026 344 Audio - Casino Cards Vol. 1 – GAMECas_Automatic Shuffler, Shuffling 1_344 Audio_Casino Car<br>GDC2024 Mechanical Wave - Cinematic Feel – WHSH_Sci-Fi Heavy Whoosh_MWSFX_CF 29 | 1,14 s |
| `"C20"` | Prázdná mysl | `prazdna_mysl.wav` | GDC2026 SoundBits - Vox Bestiae - Source Elements – CREAEthr_Ethereal Entity Grim Pain Long 4_SNDBTS_VB-SE | 1,5 s |
| `"C21"` | Bezedný toulec | `bezedny_toulec.wav` | RPG Sound Pack (CC0) – swing<br>RPG Sound Pack (CC0) – swing2<br>RPG Sound Pack (CC0) – swing3<br>GDC2024 Justsoundeffects - Steampunk Gadgets – MECHMisc_Ricochet Hits 01_JSE_SG | 1,5 s |
| `"C22"` | Chaos manufaktura | `chaos_manufaktura.wav` | GDC2024 BluezoneCorp - Steampunk Machines – Bluezone_BC0305_steampunk_machine_mechanical_sequence_start_<br>GDC2024 Justsoundeffects - Steampunk Gadgets – AIRBrst_Steam Release Short 03_JSE_SG_Mono | 1,5 s |
| `"C23"` | Nepodařený experiment | `nepodareny_experiment.wav` | GDC2024 Mechanical Wave - Glass – GLASBrk_Glass Break Hit_04_MWSFX_GL<br>RPG Sound Pack (CC0) – bubble3<br>GDC2026 Epic Stock Media - Humanoid Creatures Vol 4 - Monstrous and Undead Creature Vocalization Sound Sets – HMNBrth_Construction Kit Male Screeching Breath Inhale Weak  | 1,38 s |
| `"C24"` | Temný rituál | `temny_ritual.wav` | GDC2026 344 Audio - Ghostly Presences Vol. 1 – AMBDsgn_Evil Spell Ambience_344 Audio_Ghostly Presences | 1,4 s |
| `"C26"` | Krvavá oběť | `krvava_obet.wav` | RPG Sound Pack (CC0) – sword-unsheathe2<br>GDC2024 Justsoundeffects - Water in Motion – WATRDrip_Dripping Water On Metal Pot Lid_JSE_WIM_Mono | 1,5 s |
| `"C27"` | Odraz magie | `odraz_magie.wav` | GDC2024 Rescopic Sound - Parallax – SCIMisc_Zap Short 14_RSCPC_PX<br>GDC2024 Mechanical Wave - Torturing Metal – METLImpt_Metal Impact-03_MWSFX_TM | 1,5 s |
| `"C28"` | Chaotická trofej | `chaoticka_trofej.wav` | RPG Sound Pack (CC0) – chainmail2<br>GDC2026 Epic Stock Media - HD Game Materials – METLImpt_Metal Old File Impact Tap Against Tire Iron Metalli | 0,75 s |
| `"C44"` | Daň z chaosu | `dan_z_chaosu.wav` | GDC2026 344 Audio - Antique Luggage – OBJLug_Suitcase Opening and Closing, Antique Suitcase_344 Au<br>RPG Sound Pack (CC0) – coin2<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"C45"` | Chaotická šipka | `chaoticka_sipka.wav` | RPG Sound Pack (CC0) – swing<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_<br>GDC2026 344 Audio - Historical Weapons Vol. 2 – WEAPBlnt_Spear And Stick Impact, Wooden MKH 2_344 Audio_Medi | 1,4 s |
| `"C29"` | Bouřlivá mysl | `bourliva_mysl.wav` | GDC2026 Jake Fielding - Interior Wind Rain and Storms – THUN_Interior Thunder Rumble_JF_INT Storm_01<br>GDC2024 BluezoneCorp - High Voltage – Bluezone_BC0299_electricity_surge_discharge_electrical_arc_c | 1,4 s |
| `"C30"` | Chrám chaosu | `chram_chaosu.wav` | GDC2026 Ivo Vicic - Church Bells – 04 Church Bells, Near Distance, In Church Tower-3 Different <br>GDC2026 344 Audio - Haunting Ambiences Vol. 5 – FIRECrkl_Fire Crackling, Popping, Witch's Cauldron_344 Audio | 1,4 s |
| `"C31"` | Chaotický výměník | `chaoticky_vymenik.wav` | GDC2024 Justsoundeffects - Steampunk Gadgets – AIRBrst_Steam Release Short 03_JSE_SG_Mono<br>GDC2024 BluezoneCorp - Tiny Gears - Small Mechanism – Bluezone_BC0301_tiny_gears_small_mechanism_sequence_045 | 1,46 s |
| `"C32"` | Vzájemná zkáza | `vzajemna_zkaza.wav` | GDC2024 Pole Position - The Warfare 2 Library – Haubits FH77 Howitzer - FIRING - Multiple Shots 2 - FRONT -  | 1,4 s |
| `"C33"` | Krádež identity | `kradez_identity.wav` | GDC2026 SoundBits - Vox Hominis - Human Effort Voices – VOXLaff_Laughing Female 02 06_SNDBTS_VH<br>GDC2026 Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms – CLOTHFlp_Action Inventory Open Flip Cloth Canvas Bag Slide L | 0,64 s |
| `"C34"` | Shapeshifter | `shapeshifter.wav` | GDC2026 Epic Stock Media - Halloween Game - Haunted House and Horror Audio Scare Kit – CREABeast_Creature Werewolf Growl Menacing Monstrous 06_ESM_<br>GDC2024 Justsoundeffects - Gore Mini Pack – GOREBone_Bone Breaks Celery 01_JSE_GMP | 1,4 s |
| `"C35"` | Chaotická přeměna | `chaoticka_premena.wav` | GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – GAMEMisc_Magic Creation 23_CB Sounddesign_APPlicable Sounds<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"C36"` | Skrytá bomba | `skryta_bomba.wav` | GDC2024 Mechanical Wave - Sound Effects Collection – ICEMisc_Ice Sizzle_05_MWSFX_SEC | 1,4 s |
| `"C37"` | Bomba | `bomba.wav` | GDC2024 DavidDumais - Explosion SFX Pack – EXPLReal_Medium Realistic Explosion 15_DDUMAIS_NONE | 1,5 s |
| `"C38"` | Explodovaná bomba | `explodovana_bomba.wav` | GDC2024 DavidDumais - Explosion SFX Pack – DESTRCrsh_Designed Car Explosion With Metal Breaking And Gla | 1,5 s |
| `"C39"` | Velký zmatek | `velky_zmatek.wav` | GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Airy-Whoosh Wind Gust 11_RSCPC_DW<br>GDC2026 344 Audio - Casino Cards Vol. 1 – GAMECas_Dealing 3_344 Audio_Casino Cards Vol 1 | 1,4 s |
| `"D01"` | Průzkumník | `pruzkumnik.wav` | GDC2026 Epic Stock Media - HD Lock And Mechanism Sound Design Kit – MECHMisc_Tool Tape Measure Pull Retract Spring Slide Spin Lo<br>GDC2026 344 Audio - Elemental Palette Designed Vol. 1 – WINDDsgn_Wind, Rush, Whoosh, Long x5 01_344 Audio_Elemental  | 0,96 s |
| `"D02"` | Věštba | `vestba.wav` | GDC2026 344 Audio - Elemental Palette Designed Vol. 1 – WATRMisc_Water, Liquid Impact, Bubble, Sci Fi, Hit 04_344 Au<br>GDC2026 Sonic Bat - Music Boxes – SBmb_Music Box C 059 | 1,09 s |
| `"D03"` | Kronika | `kronika.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Large Book Squeak Page Turn_ 01_MWSFX_CDAP<br>GDC2026 344 Audio - Antique Books – PAPRMisc_Antique Books Slow Page Turns 6_344 Audio_Antiques  | 0,98 s |
| `"D04"` | Bojová taktika | `bojova_taktika.wav` | GDC2024 UberDuo - Game Night Audio Props – GAMEBoard_Chess, Contact, King Takes_UberDuo_Game<br>RPG Sound Pack (CC0) – armor-light | 0,8 s |
| `"D05"` | Stavební plány | `stavebni_plany.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – PAPRHndl_Paper Sheet Crumple_ 04_MWSFX_CDAP<br>GDC2026 Cinematic Sound Design - Paper Foley – A4 Printing Paper Rattle Page Turn Tail | 0,72 s |
| `"D06"` | Elitní zvěd | `elitni_zved.wav` | GDC2026 David Dumais Audio - Melee Weapons Sound Effects Pack 2 – WEAPWhip_WHIP Snap Crack 05_DDUMAIS_MWP2<br>GDC2026 344 Audio - Haunting Ambiences Vol. 3 – WOODImpt_Wooden Hit, Dark, Heavy Hit, Vampire's Prison_344 A | 1,5 s |
| `"D07"` | Tajná knihovna | `tajna_knihovna.wav` | GDC2024 Mechanical Wave - Cardboard and Paper – OBJPack_Big Cardboard Box Slide_ 04_MWSFX_CDAP<br>GDC2026 Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms – MAGAngl_Magic Light Spell Enchantment Potion Effect Tonal Br | 1,4 s |
| `"D08"` | Vize | `vize.wav` | GDC2026 Alexander Kopeikin - Emotion and Magic – magic, energy flow, astonishment-001 | 1,4 s |
| `"D09"` | Inspirace | `inspirace.wav` | GDC2024 Orbital Emitter - Cinematic Transitions for Editors Volume 2 – 112,StringsSectionRiser<br>GDC2026 Cinematic Sound Design - Paper Foley – A4 Printing Paper Rattle Page Turn Tail | 1,4 s |
| `"113"` | Goblin šaman | `goblin_saman.wav` | RPG Sound Pack (CC0) – mnstr14<br>GDC2026 InMotionAudio - Arc – ELECArc_ArcDesign15_InMotionAudio_Arc | 1,42 s |
| `"114"` | Chaotický mudrc | `chaoticky_mudrc.wav` | GDC2026 344 Audio - Antique Books – PAPRMisc_Pile Of Antique Books Falling Over 8_344 Audio_Anti<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"115"` | Studna vědomostí | `studna_vedomosti.wav` | GDC2024 BluezoneCorp - Designed Water – Bluezone_BC0298_designed_water_impact_006<br>GDC2026 344 Audio - Antique Books – PAPRMisc_Antique Books Slow Page Turns 6_344 Audio_Antiques  | 1,04 s |
| `"116"` | Chaotická replikace | `chaoticka_replikace.wav` | GDC2024 UberDuo - Game Night Audio Props – GAMEBoard_Scrabble, Shotgun, Tiles, Drop_UberDuo_Game<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Kalimba 3 Up_CB Sounddesign_APPlicable Sounds | 0,75 s |
| `"117"` | Průzkum dolů | `pruzkum_dolu.wav` | GDC2024 Pole Position - Artillery Vehicle Bandkanon 1C – Bandkanon 1C - t10 - VAR SFX - Simulated Shell Ejection 1 - <br>GDC2024 InMotionAudio - Cave Design – AMBUndr_CaveDesign01_InMotionAudio_CaveDesign | 1,48 s |
| `"118"` | Magický žolík | `magicky_zolik.wav` | GDC2026 344 Audio - Casino Cards Vol. 1 – GAMECas_Pick Up Multiple Cards At Once 5_344 Audio_Casino Ca<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Xylophone Ringtone 2_CB Sounddesign_APPlicable Sounds | 0,84 s |
| `"120"` | Hromadná sleva | `hromadna_sleva.wav` | RPG Sound Pack (CC0) – coin3<br>GDC2026 CB_Sounddesign - Applicable Sounds - Organic UI and Building Games SFX – UIMisc_Kalimba 3 Up_CB Sounddesign_APPlicable Sounds | 0,85 s |
| `"121"` | Kletba cen | `kletba_cen.wav` | RPG Sound Pack (CC0) – coin<br>GDC2026 344 Audio - Haunting Ambiences Vol. 5 – FIRECrkl_Fire Crackling, Popping, Witch's Cauldron_344 Audio | 1,5 s |
| `"123"` | Momentum | `momentum.wav` | GDC2026 344 Audio - Dinosaurs Vol. 2 – ANMLRept_Large Herbivore Roar 01_344 Audio_Dinosaurs Vol 2 | 1,5 s |
| `"124"` | Alchymistova volba | `alchymistova_volba.wav` | GDC2024 Mechanical Wave - Glass – GLASMisc_Glass Bottle Open_01_MWSFX_GL<br>GDC2024 Justsoundeffects - Water in Motion – WATRFizz_Fizzing Effervescent Tablets 03_JSE_WIM_Mono | 1,5 s |
| `"125"` | Válečný zápal | `valecny_zapal.wav` | GDC2026 Epic Stock Media - Elemental Mutation Whooshes and Impacts – FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_E | 1,5 s |
| `"126"` | Magický proud | `magicky_proud.wav` | GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Watery-Whoosh FIzzy Fast 03_RSCPC_DW | 1,5 s |
| `"127"` | Pyroblast | `pyroblast.wav` | GDC2024 BluezoneCorp - Steampunk Weapon And Textures – Bluezone_BC0296_steampunk_weapon_flare_shot_explosion_003<br>GDC2026 Epic Stock Media - Synthesized Nature Loops and Sounds – FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ | 1,5 s |
| `"128"` | Archmág | `archmag.wav` | GDC2024 Orbital Emitter - Cinematic Transitions for Editors Volume 2 – 08,DragonReveal - dramatic and recap | 1,4 s |
| `"C43"` | Nájezd | `najezd.wav` | GDC2024 UberDuo - Horsin’ Around – Character Play Set – ANMLHors_Horse, Quarter Horse, Mare, Trot_Uberduo_HORS_Stere<br>GDC2024 Rescopic Sound - Distinct Whooshes – WHSH_Airy-Whoosh Wind Gust 11_RSCPC_DW | 1,4 s |
| `"C42"` | Klon | `klon.wav` | GDC2026 Cinematic Sound Design - Cartoon Impacts – Vibrato Impact Snap Spin Transition | 1,4 s |
| `"C41"` | Zrcadlo | `zrcadlo.wav` | GDC2024 Mechanical Wave - Glass – GLASBrk_Glass Break Hit_04_MWSFX_GL<br>GDC2024 Mechanical Wave - Sound Effects Collection – BELLHand_Metallic Bell_ 22_MWSFX_SEC | 1,0 s |
| `"C40"` | Zákeřný špeh | `zakerny_speh.wav` | GDC2026 Epic Stock Media - Fantasy Game 2 - Sound Kit for Enchanted Realms – CLOTHFlp_Action Inventory Open Flip Cloth Canvas Bag Slide L<br>RPG Sound Pack (CC0) – sword-unsheathe | 0,84 s |
| `"119"` | Válečný pokřik | `valecny_pokrik.wav` | GDC2026 Epic Stock Media - Humanoid Creatures Vol 4 - Monstrous and Undead Creature Vocalization Sound Sets – CREAHmn_Designed Orc Male Attack Long Heavy Hit Charged Up 0 | 1,4 s |
| `"122"` | Stavební posila | `stavebni_posila.wav` | GDC2024 UberDuo - Horsin’ Around – Character Play Set – ANMLHors_Horse, Quarter Horse, Mare, Trot_Uberduo_HORS_Stere<br>GDC2024 UberDuo - Horsin’ Around – Character Play Set – ANMLHors_Horse, Quarter Horse, Mare, Snort_Uberduo_HORS_Ster | 1,4 s |
| `"L01"` | Temný mág | `temny_mag.wav` | GDC2026 Alexander Kopeikin - Emotion and Magic – magic, action gesture, evil presence, onslaught-004<br>GDC2026 Federico Soler - Effective Trailer Booms Vol. 2 – EffectiveTrailer_Booms_Vol2_075 | 1,5 s |
| `"T48"` | Upír (zlatý) | `upir.wav` | stejný jako Upír | |

## Poslech a hodnocení v deckbuilder.html

`app/src/main/java/com/example/termiti/deckbuilder.html` (otevřít přímo z repozitáře přes `file://`):
- každá dlaždice karty má 🔊 (přehraje zvuk; v Deck Builderu kartu nepřidá do balíčku) a 👎 (označí zvuk jako špatný, uloží se v `localStorage` pod `termiti_bad_sounds`);
- v hlavičce je „👎 Špatné zvuky: N“. Kliknutí zkopíruje seznam označených zvuků (`ID název (soubor)`) do schránky, ten se pak vloží Claudovi k přegenerování;
- filtr „👎 Zvuk“ ukáže jen označené karty. V Designer módu jsou 🔊/👎 i u vybrané karty;
- `SOUND_MAP` (ID → soubor v `res/raw`) je generovaný z `CardPresentation.kt` stejně jako `ART_MAP`. Při změně `soundResId` ho uprav taky. Zvuky se načítají relativně (`../../../../res/raw/`), proto stránka musí ležet na svém místě v repozitáři.

## Related pages
- [[systems/art-pipeline]] – grafika karet; „každá karta jiná“ platí i pro zvuky
- [[architecture]] – `CardPresentation.kt`, `CardRepository.kt`, `SoundManager.kt`
- [[cards/types]] – typy karet (Útok, Stavba, Magie, Důl, Chaos)

## Changelog
- 2026-11-22: Zvuky ze Stable Audio nahrazeny sestavami z knihoven nahrávek (Sonniss GDC 2024/2026, RPG Sound Pack). Hlasitost srovnána podle K-vážení na −12,5 LUFS. Berserk a Nestabilní vír zůstaly.
- 2026-11-22: deckbuilder.html: přehrání zvuku karty (🔊), označení špatných (👎), filtr a kopírování seznamu.
- 2026-11-22: Stránka založena; 178 karet dostalo vlastní zvuk (Stable Audio 3 v ComfyUI), všech 197 karet má `soundResId`.

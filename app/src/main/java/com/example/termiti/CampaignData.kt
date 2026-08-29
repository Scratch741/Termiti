package com.example.termiti

// ── Handicap aplikovaný na HRÁČE při daném soupeři ───────────────────────────
data class PlayerHandicap(
    val extraCastle : Int = 0,                              // záporné = méně HP hradu
    val extraWall   : Int = 0,                              // záporné = méně hradeb
    val extraMagic  : Int = 0,                              // záporné = méně startovní magie
    val extraAttack : Int = 0,                              // záporné = méně startovního útoku
    val extraStones : Int = 0,                              // záporné = méně startovních kamenů
    val extraMines  : Map<ResourceType, Int> = emptyMap()  // záporné = zničený důl
)

// ── Jeden soupeř v kampani ────────────────────────────────────────────────────
data class CampaignOpponent(
    val id         : String,
    val name       : String,
    val title      : String,
    val avatar     : String,
    /** Plná ilustrace pro roster kartu (CampaignLocationScreen) – pokud null, spadne na [avatar]. */
    val cardArt    : String? = null,
    val description: String,
    val isBoss     : Boolean = false,

    // AI startovní stav
    val aiCastle      : Int = 30,
    val aiWall        : Int = 10,
    /** Strop hradeb AI (MAX_WALL = 50 pokud nenastaveno). Nízký strop u slabých soupeřů,
     *  aby jejich hradba na bojišti nevypadala vizuálně "prázdná" vůči globálním 50. */
    val aiMaxWall     : Int = MAX_WALL,
    val aiStartMagic  : Int = 0,
    val aiStartAttack : Int = 0,
    val aiStartStones : Int = 0,
    val aiExtraMines  : Map<ResourceType, Int> = emptyMap(),
    /** Kosmetický vzhled AI hradu/hradby na bojišti – jednotné pro celou lokaci
     *  (viz [castleSkinDrawable]/[wallSkinDrawable]), ne výchozí "castle_player"/"wall_player". */
    val aiCastleSkin  : String = "castle_player",
    val aiWallSkin    : String = "wall_player",

    val deckCardCounts: Map<String, Int>,

    // Herní konfigurace souboje
    val winTarget          : Int = 60,  // výška hradu pro výhru hráče výstavbou (999 = nutno zabít)
    val aiWinTarget        : Int = 60,  // výška hradu pro výhru AI výstavbou
    val aiStartHandSize    : Int = 4,   // počet karet AI na začátku
    val playerStartHandSize: Int = 4,   // počet karet hráče na začátku

    val playerHandicap: PlayerHandicap = PlayerHandicap(),

    val rewardGold: Int = 50,
    val rewardGems: Int = 0,
    val rewardXp  : Int = 75
) {
    /** Localized display name — active language pack (by [id]) → built-in Czech [name] fallback. */
    val displayName: String get() = LanguageManager.campaignOpponentName(id, name)
    /** Localized display title — active language pack (by [id]) → built-in Czech [title] fallback. */
    val displayTitle: String get() = LanguageManager.campaignOpponentTitle(id, title)
    /** Localized display description — active language pack (by [id]) → built-in Czech [description] fallback. */
    val displayDescription: String get() = LanguageManager.campaignOpponentDesc(id, description)
}

// ── Lokace = skupina soupeřů zakončená bossem ─────────────────────────────────
data class CampaignLocation(
    val id         : String,
    val name       : String,
    val emoji      : String,
    val description: String,
    val opponents  : List<CampaignOpponent>
) {
    val boss: CampaignOpponent get() = opponents.last()

    /** Localized display name — active language pack (by [id]) → built-in Czech [name] fallback. */
    val displayName: String get() = LanguageManager.campaignLocationName(id, name)
    /** Localized display description — active language pack (by [id]) → built-in Czech [description] fallback. */
    val displayDescription: String get() = LanguageManager.campaignLocationDesc(id, description)
}

// ── Celá kampaň ───────────────────────────────────────────────────────────────
//
//  Filozofie winTarget:
//  · winTarget nízký (45–55) → výstavba hradu je reálná alternativa k zabití,
//    zvlášť u malých balíčků, co rychle dojdou.
//  · winTarget vysoký (70–80) → u obranářů, kde výstavba trvá příliš dlouho.
//  · winTarget = 999 → u citadelních válečníků pozdní hry; výstavba = prohra.
//
//  Filozofie velikosti balíčku:
//  · Malý (18–24) → AI opakuje stejné karty; dojde rychle → výstavba viabilní.
//  · Velký (38–48) → AI vydrží věčně; musíš soupeře aktivně zničit.
//
object CampaignData {

    val locations: List<CampaignLocation> = listOf(

        // ════════════════════════════════════════════════════════════════════
        // LOKACE 1 – Goblinský tábor
        // winTarget: 45 → 48 → 52 → 55 → 57 → 58 → 59 → 60 → 60 → 62
        // Balíčky:   18 → 20 → 22 → 24 → 25 → 26 → 27 → 28 → 30 → 32
        // ════════════════════════════════════════════════════════════════════
        CampaignLocation(
            id = "loc_goblins", name = "Goblinský tábor", emoji = "🌲",
            description = "Zanedbaný tábor goblinů. Slabí, ale přemnožení.",
            opponents = listOf(

                // 1 ── 16 karet, winTarget 45 ───────────────────────────────
                // Malý balíček + nízký winTarget = výstavba je nejjednodušší cesta.
                CampaignOpponent(
                    id = "gob_scout", name = "Goblin Průzkumník", title = "Drzý rváč",
                    avatar = "player_icon_10", cardArt = "goblin_pruzkumnik", description = "Nejslabší z tlupy. Postav hrad na 45 nebo ho sejmi.",
                    aiCastle = 25, aiWall = 5, aiMaxWall = 5,
                    deckCardCounts = mapOf("001" to 6, "046" to 2, "012" to 4, "D04" to 4),
                    winTarget = 45, aiWinTarget = 27,
                    rewardGold = 30
                ),

                // 2 ── 22 karet, winTarget 48 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_archer", name = "Goblin Lučištník", title = "Ostrostřelec z kopce",
                    avatar = "player_icon_10", cardArt = "goblin_lucistnik", description = "Střílí šípy na hradby. Aspoň míří správným směrem.",
                    aiCastle = 27, aiWall = 6, aiMaxWall = 6,
                    deckCardCounts = mapOf("008" to 5, "046" to 2, "012" to 3, "D04" to 3, "022" to 2,
                        "019" to 5, "C21" to 2),
                    winTarget = 48, aiWinTarget = 29,
                    rewardGold = 35
                ),

                // 3 ── 26 karet, winTarget 52 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_shaman", name = "Goblin Šaman", title = "Čaroděj bez školy",
                    avatar = "player_icon_10", cardArt = "goblin_saman", description = "Neumí kouzlit, ale dělá, že ano.",
                    aiCastle = 28, aiWall = 7, aiStartMagic = 1, aiMaxWall = 7,
                    deckCardCounts = mapOf("113" to 4, "001" to 4, "004" to 5, "046" to 3,
                        "D04" to 3, "047" to 2, "003" to 3, "013" to 2),
                    winTarget = 52, aiWinTarget = 29,
                    rewardGold = 40
                ),

                // 4 ── 24 karet, winTarget 55 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_warrior", name = "Goblin Válečník", title = "Rváč s klackem",
                    avatar = "player_icon_10", cardArt = "goblin_valecnik", description = "Přišel o zbraň. Teď bojuje klackem.",
                    aiCastle = 30, aiWall = 8, aiMaxWall = 8,
                    deckCardCounts = mapOf(
                        "104" to 2, "001" to 5, "046" to 2, "012" to 6,
                        "047" to 4, "020" to 2, "017" to 3
                    ),
                    winTarget = 55, aiWinTarget = 32,
                    rewardGold = 45
                ),

                // 5 ── 25 karet, winTarget 57 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_looter", name = "Goblin Drancovač", title = "Mistr nepořádku",
                    avatar = "player_icon_10", cardArt = "goblin_drancovac", description = "Drancuje vesnice a pak se diví, proč ho nikdo nemá rád.",
                    aiCastle = 32, aiWall = 8, aiMaxWall = 8,
                    deckCardCounts = mapOf(
                        "001" to 3, "046" to 2, "012" to 3, "056" to 3,
                        "017" to 3, "015" to 2, "050" to 3, "C12" to 1,
                        "037" to 3, "C31" to 2
                    ),
                    winTarget = 57, aiWinTarget = 34,
                    rewardGold = 50
                ),

                // 6 ── 26 karet, winTarget 58, hráč lízne jen 3 karty ───────
                // Berserk překvapí menší rukou na startu.
                CampaignOpponent(
                    id = "gob_berserker", name = "Goblin Berserk", title = "Zuřivý houf jednoho",
                    avatar = "player_icon_10", cardArt = "goblin_berserk", description = " Startovní ruka: 3 karty. Pozor na jeho hradby!",
                    aiCastle = 34, aiWall = 7, aiMaxWall = 7,
                    deckCardCounts = mapOf(
                        "104" to 1, "001" to 3, "046" to 2, "012" to 3,
                        "017" to 2, "015" to 2, "050" to 3, "037" to 4,
                        "024" to 3, "006" to 2, "023" to 2
                    ),
                    winTarget = 58, aiWinTarget = 36,
                    playerStartHandSize = 3,
                    rewardGold = 55
                ),

                // 7 ── 27 karet, winTarget 59 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_troll", name = "Goblin Troll", title = "Boří hradby, staví drby",
                    avatar = "player_icon_10", cardArt = "goblin_troll", description = "Velký pro goblina. Specialita: boření hradeb.",
                    aiCastle = 35, aiWall = 10, aiMaxWall = 10,
                    deckCardCounts = mapOf(
                        "104" to 1, "119" to 4, "001" to 3, "046" to 2,
                        "012" to 3, "047" to 3, "017" to 2, "027" to 2,
                        "015" to 2, "050" to 3, "037" to 2
                    ),
                    winTarget = 59, aiWinTarget = 37,
                    rewardGold = 60
                ),

                // 8 ── 29 karet, winTarget 60 ───────────────────────────────
                CampaignOpponent(
                    id = "gob_commander", name = "Goblin Velitel", title = "Vůdce tlupy",
                    avatar = "player_icon_10", cardArt = "goblin_velitel", description = "Velí ostatním. Alespoň trochu ví, co dělá.",
                    aiCastle = 37, aiWall = 10, aiMaxWall = 10,
                    deckCardCounts = mapOf(
                        "104" to 2, "109" to 4, "001" to 3, "046" to 2,
                        "012" to 3, "056" to 2, "020" to 2, "017" to 2,
                        "015" to 2, "050" to 3, "037" to 2, "055" to 2
                    ),
                    winTarget = 60, aiWinTarget = 39,
                    rewardGold = 65
                ),

                // 9 ── 31 karet, winTarget 60, AI má extra mine útoku ────────
                CampaignOpponent(
                    id = "gob_warchief", name = "Goblin Válečný náčelník", title = "Téměř nebezpečný",
                    avatar = "player_icon_10", cardArt = "goblin_valecny_nacelnik", description = "Jeden krok od trůnu. Sekyrníci a přímé zásahy.",
                    aiCastle = 38, aiWall = 11, aiMaxWall = 11,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "104" to 2, "001" to 3, "008" to 3, "004" to 2,
                        "046" to 2, "022" to 4, "017" to 5, "015" to 2,
                        "037" to 3, "026" to 2, "038" to 3
                    ),
                    winTarget = 60, aiWinTarget = 40,
                    rewardGold = 80
                ),

                // 10 BOSS ── 37 karet, winTarget 62, AI lízne 5 karet ────────
                CampaignOpponent(
                    id = "gob_king", name = "Goblin Král", title = "Pán tábora",
                    avatar = "goblin_kral_profil", cardArt = "goblin_kral", description = "Král goblinů. 5 karet v ruce a extra doly útoku.",
                    isBoss = true,
                    aiCastle = 40, aiWall = 12, aiMaxWall = 12,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "104" to 2, "109" to 3, "119" to 2, "001" to 3,
                        "004" to 4, "046" to 3, "056" to 2, "022" to 4,
                        "017" to 5, "015" to 1, "050" to 3, "038" to 3,
                        "054" to 2
                    ),
                    winTarget = 62, aiStartHandSize = 5, aiWinTarget = 42,
                    rewardGold = 150, rewardXp = 200
                )
            ).map { it.copy(aiCastleSkin = "castle_player_4", aiWallSkin = "wall_goblin") }
        ),

        // ════════════════════════════════════════════════════════════════════
        // LOKACE 2 – Trpasličí hory
        // winTarget: 58 → 60 → 70 → 62 → 63 → 65 → 65 → 68 → 80 → 75
        // U obranářů vyšší winTarget – přelstít zeď výstavbou je těžší.
        // Balíčky: 28 → 28 → 28 → 28 → 30 → 30 → 32 → 33 → 35 → 38
        // ════════════════════════════════════════════════════════════════════
        CampaignLocation(
            id = "loc_dwarves", name = "Trpasličí hory", emoji = "⛰️",
            description = "Opevněné horské průsmyky. Trpaslíci staví lépe než bojují.",
            opponents = listOf(

                // 1 ── 25 karet, winTarget 60 ───────────────────────────────
                CampaignOpponent(
                    id = "dwf_miner", name = "Horník", title = "Stavitel hradeb",
                    avatar = "player_icon_10", cardArt = "hory_hornik", description = "Celý den kope a staví. Útok? To nezná.",
                    aiCastle = 27, aiWall = 15, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.STONES to 1),
                    deckCardCounts = mapOf(
                        "002" to 3, "010" to 2, "005" to 5, "011" to 2,
                        "091" to 2, "014" to 2, "095" to 2, "042" to 1,
                        "059" to 3, "009" to 3
                    ),
                    winTarget = 60,
                    rewardGold = 60
                ),

                // 2 ── 27 karet, winTarget 60 ───────────────────────────────
                CampaignOpponent(
                    id = "dwf_carpenter", name = "Trpasličí Tesař", title = "Palisádový mistr",
                    avatar = "player_icon_10", cardArt = "hory_tesar", description = "Staví palisády tak rychle, že mu nestíháš číst jméno.",
                    aiCastle = 28, aiWall = 18, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.STONES to 1),
                    deckCardCounts = mapOf(
                        "112" to 2, "122" to 2, "129" to 3, "010" to 5,
                        "029" to 2, "005" to 4, "014" to 2, "042" to 1,
                        "086" to 3, "049" to 2, "032" to 1
                    ),
                    winTarget = 60,
                    rewardGold = 65
                ),

                // 3 ── 30 karet, winTarget 70 ───────────────────────────────
                // Kamenný Strážce má vysoký winTarget – jeho hradby jsou tak silné,
                // že sám může vyhrát výstavbou rychleji než ty.
                CampaignOpponent(
                    id = "dwf_guardian", name = "Kamenný Strážce", title = "Neprůstřelný",
                    avatar = "player_icon_10", cardArt = "hory_strazce", description = "Chodící zeď. Výstavba nestačí – musíš ho prorazit silou.",
                    aiCastle = 30, aiWall = 20, aiMaxWall = 40,
                    aiExtraMines = mapOf(ResourceType.STONES to 2),
                    deckCardCounts = mapOf(
                        "138" to 2, "139" to 1, "002" to 4, "018" to 4,
                        "011" to 2, "014" to 3, "D05" to 2, "091" to 3,
                        "030" to 3, "010" to 2, "082" to 2, "087" to 2
                    ),
                    winTarget = 95, aiWinTarget = 55,
                    rewardGold = 70
                ),

                // 4 ── 30 karet, winTarget 62 ───────────────────────────────
                CampaignOpponent(
                    id = "dwf_smith", name = "Trpasličí Kovář", title = "Mistr kovadliny",
                    avatar = "player_icon_10", cardArt = "hory_kovar", description = "Vyrábí zbraně i hradby. Útok a obrana najednou.",
                    aiCastle = 30, aiWall = 18, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.STONES to 1),
                    deckCardCounts = mapOf(
                        "138" to 4, "022" to 4, "002" to 4, "012" to 2,
                        "091" to 4, "050" to 4, "098" to 4, "017" to 4
                    ),
                    winTarget = 62,
                    rewardGold = 75
                ),

                // 5 ── 30 karet, winTarget 63 ───────────────────────────────
                CampaignOpponent(
                    id = "dwf_ranger", name = "Horský Ranger", title = "Průsmykový hlídač",
                    avatar = "player_icon_10", cardArt = "hory_ranger", description = "Hlídá průsmyky a bourá hradby. Rád používá beranidla.",
                    aiCastle = 30, aiWall = 15, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.STONES to 1),
                    deckCardCounts = mapOf(
                        "020" to 4, "002" to 3, "008" to 3, "010" to 2,
                        "038" to 2, "026" to 4, "022" to 3, "061" to 2,
                        "057" to 3, "059" to 4
                    ),
                    winTarget = 63,
                    rewardGold = 80
                ),

                // 6 ── 30 karet, winTarget 65 ───────────────────────────────
                CampaignOpponent(
                    id = "dwf_warrior", name = "Horský Bojovník", title = "Sekyrový veterán",
                    avatar = "player_icon_10", cardArt = "hory_bojovnik", description = "Na rozdíl od ostatních trpaslíků opravdu bojuje.",
                    aiCastle = 30, aiWall = 12, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "100" to 2, "104" to 2, "138" to 4, "022" to 2,
                        "012" to 2, "017" to 5, "053" to 2, "D06" to 2,
                        "091" to 2, "074" to 2, "015" to 2, "057" to 3
                    ),
                    winTarget = 65,
                    rewardGold = 85
                ),

                // 7 ── 32 karet, winTarget 65 ───────────────────────────────
                // Mág sabotuje jeden hráčův důl kamenů jako součást kouzla.
                CampaignOpponent(
                    id = "dwf_mage", name = "Trpasličí Mág", title = "Horský čaroděj",
                    avatar = "player_icon_10", cardArt = "hory_mag", description = "Sabotoval tvůj kamenolom. Startovní důl kamene −1.",
                    aiCastle = 31, aiWall = 14, aiMaxWall = 35,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 1),
                    deckCardCounts = mapOf(
                        "115" to 2, "127" to 3, "137" to 2, "003" to 3,
                        "C25" to 2, "073" to 2, "C41" to 1, "099" to 2,
                        "057" to 4, "059" to 4, "C19" to 2, "C02" to 3,
                        "C32" to 2
                    ),
                    winTarget = 65,
                    playerHandicap = PlayerHandicap(
                        extraMines = mapOf(ResourceType.STONES to -1)
                    ),
                    rewardGold = 90
                ),

                // 8 ── 33 karet, winTarget 68, AI lízne 5 karet ─────────────
                CampaignOpponent(
                    id = "dwf_general", name = "Trpasličí Generál", title = "Velitel horské armády",
                    avatar = "player_icon_10", cardArt = "hory_general", description = "Zkušený velitel. Startuje s 5 kartami a extra mine útoku.",
                    aiCastle = 32, aiWall = 18, aiMaxWall = 40,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "131" to 1, "007" to 4, "022" to 4, "017" to 4,
                        "002" to 4, "018" to 4, "014" to 2, "D04" to 3,
                        "021" to 3, "054" to 3
                    ),
                    winTarget = 68, aiStartHandSize = 5,
                    rewardGold = 100
                ),

                // 9 ── 35 karet, winTarget 80 ───────────────────────────────
                // Kolos – výstavba na 80 je extrémně náročná. Musíš ho prolomit.
                CampaignOpponent(
                    id = "dwf_colossus", name = "Kamenný Kolos", title = "Neprolomitelná pevnost",
                    avatar = "player_icon_10", cardArt = "hory_kolos", description = "35 karet a winTarget 80. Prolomit nebo zemřít.",
                    aiCastle = 33, aiWall = 22, aiMaxWall = 50,
                    aiExtraMines = mapOf(ResourceType.STONES to 2),
                    deckCardCounts = mapOf(
                        "139" to 1, "018" to 5, "002" to 5, "030" to 5,
                        "034" to 5, "036" to 5, "009" to 5, "063" to 4
                    ),
                    winTarget = 100, aiWinTarget = 50,
                    rewardGold = 120
                ),

                // 10 BOSS ── 38 karet, winTarget 75, AI lízne 5 karet ────────
                CampaignOpponent(
                    id = "dwf_thane", name = "Trpasličí Thane", title = "Pán hor",
                    avatar = "player_icon_10", cardArt = "hory_thane", description = "Vládce hor. 38 karet, 5 startovních karet. Útok nebo útok. Jednorázová odměna: 200 XP.",
                    isBoss = true,
                    aiCastle = 35, aiWall = 20, aiMaxWall = 50*,
                    aiExtraMines = mapOf(ResourceType.STONES to 1, ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "136" to 4, "022" to 4, "017" to 4, "002" to 3,
                        "018" to 4, "015" to 3, "079" to 3, "054" to 3,
                        "059" to 4, "029" to 2, "051" to 1, "021" to 2
                    ),
                    winTarget = 75, aiStartHandSize = 5,
                    rewardGold = 220, rewardGems = 3, rewardXp = 200
                )
            ).map { it.copy(aiCastleSkin = "castle_hory", aiWallSkin = "wall_hory") }
        ),

        // ════════════════════════════════════════════════════════════════════
        // LOKACE 3 – Temná citadela
        // winTarget: 62 → 63 → 65 → 65 → 999 → 70 → 999 → 999 → 999 → 999
        // Od Temného Válečníka výš: výstavba = okamžitá prohra.
        // Balíčky: 30 → 30 → 33 → 33 → 35 → 35 → 38 → 38 → 42 → 48
        // ════════════════════════════════════════════════════════════════════
        CampaignLocation(
            id = "loc_citadel", name = "Temná citadela", emoji = "🏰",
            description = "Sídlo temnoty. Soupeři hrají nefér — a jsou na to pyšní.",
            opponents = listOf(

                // 1 ── 30 karet, winTarget 62 ───────────────────────────────
                CampaignOpponent(
                    id = "cit_knight", name = "Stínový Rytíř", title = "Zloděj zdrojů",
                    avatar = "🗡️", description = "Krade ti zdroje a ještě se směje. Začínáš s méně magií.",
                    aiCastle = 33, aiWall = 15,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "007" to 6, "001" to 5, "017" to 5, "006" to 4,
                        "012" to 4, "013" to 3, "004" to 3
                    ),
                    winTarget = 62,
                    playerHandicap = PlayerHandicap(extraMagic = -2),
                    rewardGold = 100
                ),

                // 2 ── 30 karet, winTarget 63 ───────────────────────────────
                CampaignOpponent(
                    id = "cit_archer", name = "Temný Lučištník", title = "Sniper citadely",
                    avatar = "🏹", description = "Zápalné šípy na hradby, přímé zásahy na hrad.",
                    aiCastle = 33, aiWall = 12,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "008" to 6, "019" to 6, "022" to 5, "053" to 4,
                        "027" to 5, "012" to 4
                    ),
                    winTarget = 63,
                    rewardGold = 110
                ),

                // 3 ── 33 karet, winTarget 65 ───────────────────────────────
                CampaignOpponent(
                    id = "cit_mage", name = "Temný Čaroděj", title = "Mistr ohně",
                    avatar = "🧙", description = "Ovládá magii temnoty. Začínáš s méně útokem.",
                    aiCastle = 35, aiWall = 12,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 2),
                    deckCardCounts = mapOf(
                        "003" to 6, "026" to 5, "013" to 5, "016" to 4,
                        "037" to 5, "004" to 5, "001" to 3
                    ),
                    winTarget = 65,
                    playerHandicap = PlayerHandicap(extraAttack = -2),
                    rewardGold = 120
                ),

                // 4 ── 33 karet, winTarget 65 ───────────────────────────────
                // Nekromant sabotuje hráčův důl magie.
                CampaignOpponent(
                    id = "cit_necromancer", name = "Nekromant", title = "Pán jedů a stínů",
                    avatar = "🦇", description = "Otravuje zásoby a ničí důl magie. Hrad −3, magic mine −1.",
                    aiCastle = 35, aiWall = 12,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 1),
                    deckCardCounts = mapOf(
                        "049" to 5, "003" to 5, "013" to 5, "037" to 5,
                        "069" to 4, "040" to 5, "004" to 4
                    ),
                    winTarget = 65,
                    playerHandicap = PlayerHandicap(
                        extraCastle = -3,
                        extraMines  = mapOf(ResourceType.MAGIC to -1)
                    ),
                    rewardGold = 130
                ),

                // 5 ── 35 karet, winTarget 999 ──────────────────────────────
                // Od teď výstavba = sebevražda. Citadelní válečníci mají tolik
                // útoku, že postavit 999 HP je fyzicky nemožné.
                CampaignOpponent(
                    id = "cit_warrior", name = "Temný Válečník", title = "Sekáč bez milosti",
                    avatar = "💀", description = "35 karet čistého útoku. Výstavba nestačí — zaútočíš nebo zemřeš.",
                    aiCastle = 36, aiWall = 14,
                    deckCardCounts = mapOf(
                        "021" to 5, "007" to 5, "054" to 5, "022" to 5,
                        "047" to 4, "017" to 4, "012" to 5, "004" to 2
                    ),
                    winTarget = 999,
                    rewardGold = 140
                ),

                // 6 ── 35 karet, winTarget 70 ───────────────────────────────
                // Strážce je obranný – výstavba se vrátí jako možnost, ale target je 70.
                CampaignOpponent(
                    id = "cit_guardian", name = "Temný Strážce", title = "Pevná ruka temnoty",
                    avatar = "🛡️", description = "Útočí i brání. Výstavba na 70 je tvá jediná alternativa.",
                    aiCastle = 36, aiWall = 20,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1, ResourceType.STONES to 1),
                    deckCardCounts = mapOf(
                        "007" to 5, "022" to 5, "002" to 5, "018" to 5,
                        "036" to 4, "062" to 4, "015" to 4, "004" to 3
                    ),
                    winTarget = 70,
                    rewardGold = 150
                ),

                // 7 ── 38 karet, winTarget 999, hráč začíná s 3 kartami ──────
                // Sabotér tě překvapí – méně karet, méně útoku i kamenů.
                CampaignOpponent(
                    id = "cit_saboteur", name = "Sabotér", title = "Mistr sabotáže",
                    avatar = "🕵️", description = "Okrade tě ještě před startem. Ruka 3 karty, bez útoku a kamenů.",
                    aiCastle = 37, aiWall = 15,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 2),
                    deckCardCounts = mapOf(
                        "065" to 5, "066" to 5, "067" to 5, "068" to 5,
                        "070" to 5, "071" to 4, "037" to 5, "004" to 4
                    ),
                    winTarget = 999,
                    playerStartHandSize = 3,
                    playerHandicap = PlayerHandicap(
                        extraAttack = -3,
                        extraStones = -3,
                        extraMines  = mapOf(ResourceType.ATTACK to -1)
                    ),
                    rewardGold = 160
                ),

                // 8 ── 38 karet, winTarget 999, AI lízne 5 karet ────────────
                CampaignOpponent(
                    id = "cit_dragon", name = "Ohnivý Drak", title = "Dech zkázy",
                    avatar = "🐉", description = "38 karet ohně. Startuje s 5 v ruce. Zabudovat nelze.",
                    aiCastle = 38, aiWall = 18,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1, ResourceType.MAGIC to 1),
                    deckCardCounts = mapOf(
                        "021" to 6, "007" to 6, "022" to 5, "012" to 5,
                        "015" to 5, "013" to 5, "004" to 6
                    ),
                    winTarget = 999, aiStartHandSize = 5,
                    rewardGold = 170
                ),

                // 9 ── 42 karet, winTarget 999, AI lízne 6 karet, hrad −3, hradby −3
                CampaignOpponent(
                    id = "cit_general", name = "Temný Generál", title = "Pravá ruka Pána",
                    avatar = "👿", description = "42 karet, startuje se 6. Začínáš oslaben. Útočíš nebo prohraješ.",
                    aiCastle = 40, aiWall = 20,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 1, ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "021" to 5, "023" to 5, "007" to 4, "017" to 4,
                        "018" to 4, "032" to 4, "016" to 4, "013" to 4,
                        "026" to 4, "040" to 4
                    ),
                    winTarget = 999, aiStartHandSize = 6,
                    playerHandicap = PlayerHandicap(extraCastle = -3, extraWall = -3),
                    rewardGold = 200
                ),

                // 10 BOSS ── 48 karet, winTarget 999, AI lízne 7 karet ───────
                // Démon + Drak v balíčku. Absolutní konec výstavbové strategie.
                CampaignOpponent(
                    id = "cit_lord", name = "Temný Pán", title = "Vládce temnoty",
                    avatar = "☠️", description = "48 karet, 7 startovních. Démon i Drak. Výstavba = smrt. Jednorázová odměna: 200 XP.",
                    isBoss = true,
                    aiCastle = 45, aiWall = 25,
                    aiExtraMines = mapOf(
                        ResourceType.MAGIC  to 1,
                        ResourceType.ATTACK to 1,
                        ResourceType.STONES to 1
                    ),
                    deckCardCounts = mapOf(
                        "052" to 4, "051" to 3, "021" to 6, "023" to 5,
                        "016" to 4, "043" to 4, "044" to 3, "040" to 4,
                        "032" to 5, "004" to 6, "054" to 4
                    ),
                    winTarget = 999, aiStartHandSize = 7,
                    playerHandicap = PlayerHandicap(extraCastle = -5, extraWall = -5),
                    rewardGold = 400, rewardGems = 6, rewardXp = 200
                )
            ).map { it.copy(aiCastleSkin = "castle_citadela", aiWallSkin = "wall_citadela") }
        ),

        // ════════════════════════════════════════════════════════════════════
        // LOKACE 4 – Dračí impérium
        // Všichni soupeři mají winTarget = 999 – výstavba je okamžitá sebevražda.
        // Důraz na chaos karty, legendární útok a těžké handicapy hráče.
        // Balíčky: 38 → 40 → 40 → 42 → 42 → 44 → 44 → 48 → 50 → 55
        // ════════════════════════════════════════════════════════════════════
        CampaignLocation(
            id = "loc_dragon", name = "Dračí impérium", emoji = "🐉",
            description = "Říši vládnou draci a chaos. Výstavba = smrt. Výjimky neexistují.",
            opponents = listOf(

                // 1 ── 38 karet, winTarget 999, AI má extra mine útoku ─────────
                CampaignOpponent(
                    id = "drg_warden", name = "Dračí Strážce", title = "Ochránce bran",
                    avatar = "🐲", description = "Brána do říše. Útok a Chaos v jednom. Hrad −3.",
                    aiCastle = 42, aiWall = 18,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "021" to 6, "007" to 5, "054" to 5, "022" to 5,
                        "017" to 5, "C01" to 4, "C02" to 4, "004" to 4
                    ),
                    winTarget = 999,
                    playerHandicap = PlayerHandicap(extraCastle = -3),
                    rewardGold = 180
                ),

                // 2 ── 40 karet, winTarget 999, AI má extra mine magie ─────────
                CampaignOpponent(
                    id = "drg_shaman", name = "Dračí Šaman", title = "Mistr chaosu",
                    avatar = "🔮", description = "Kouzlí chaosem. Sabotoval tvůj útok a magie mine −1.",
                    aiCastle = 43, aiWall = 20,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 2),
                    deckCardCounts = mapOf(
                        "C01" to 5, "C02" to 5, "C05" to 4, "013" to 4,
                        "016" to 4, "037" to 5, "040" to 5, "077" to 4,
                        "004" to 4
                    ),
                    winTarget = 999,
                    playerHandicap = PlayerHandicap(
                        extraAttack = -3,
                        extraMines  = mapOf(ResourceType.MAGIC to -1)
                    ),
                    rewardGold = 200
                ),

                // 3 ── 40 karet, winTarget 999, AI lízne 5 karet ──────────────
                CampaignOpponent(
                    id = "drg_rider", name = "Dračí Jezdec", title = "Sedlo z plamenů",
                    avatar = "🏇", description = "Startuje s 5 kartami. Hrad −3, hradby −3.",
                    aiCastle = 44, aiWall = 20,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 1, ResourceType.MAGIC to 1),
                    deckCardCounts = mapOf(
                        "021" to 6, "051" to 3, "054" to 5, "079" to 4,
                        "C01" to 4, "C06" to 3, "017" to 5, "012" to 5,
                        "004" to 5
                    ),
                    winTarget = 999, aiStartHandSize = 5,
                    playerHandicap = PlayerHandicap(extraCastle = -3, extraWall = -3),
                    rewardGold = 220
                ),

                // 4 ── 42 karet, winTarget 999, sabotáž kamenů ────────────────
                CampaignOpponent(
                    id = "drg_saboteur", name = "Entropický Sabotér", title = "Rozsévač chaosu",
                    avatar = "🕵️", description = "Zničil tvůj kamenolom a vyprázdnil zásoby. Mine kamene −1.",
                    aiCastle = 45, aiWall = 22,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 1, ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "065" to 5, "068" to 5, "070" to 5, "071" to 5,
                        "C02" to 4, "C09" to 4, "069" to 4, "040" to 5,
                        "004" to 5
                    ),
                    winTarget = 999,
                    playerHandicap = PlayerHandicap(
                        extraCastle = -4,
                        extraStones = -4,
                        extraMines  = mapOf(ResourceType.STONES to -1)
                    ),
                    rewardGold = 240
                ),

                // 5 ── 42 karet, winTarget 999, playerStartHand 3 ──────────────
                CampaignOpponent(
                    id = "drg_wyrm", name = "Starý Wyrm", title = "Žijící rána",
                    avatar = "🦎", description = "Přepadne tě dřív, než procitnete. Ruka 3, hrad −5.",
                    aiCastle = 46, aiWall = 22,
                    aiExtraMines = mapOf(ResourceType.ATTACK to 2),
                    deckCardCounts = mapOf(
                        "021" to 7, "054" to 5, "022" to 5, "023" to 5,
                        "079" to 4, "C06" to 4, "017" to 5, "004" to 7
                    ),
                    winTarget = 999,
                    playerStartHandSize = 3,
                    playerHandicap = PlayerHandicap(extraCastle = -5, extraWall = -3),
                    rewardGold = 260
                ),

                // 6 ── 44 karet, winTarget 999, AI lízne 5 karet ──────────────
                CampaignOpponent(
                    id = "drg_general", name = "Dračí Generál", title = "Velitel spálené země",
                    avatar = "👿", description = "5 karet a 3 typy dolů. Útok nebo záhuba. Hrad −5, hradby −5.",
                    aiCastle = 48, aiWall = 25,
                    aiExtraMines = mapOf(
                        ResourceType.ATTACK to 2,
                        ResourceType.MAGIC  to 1
                    ),
                    deckCardCounts = mapOf(
                        "021" to 6, "051" to 4, "054" to 5, "079" to 4,
                        "C05" to 4, "C06" to 3, "C09" to 3, "017" to 5,
                        "043" to 4, "004" to 6
                    ),
                    winTarget = 999, aiStartHandSize = 5,
                    playerHandicap = PlayerHandicap(extraCastle = -5, extraWall = -5),
                    rewardGold = 280
                ),

                // 7 ── 44 karet, winTarget 999, mine útoku −1 ─────────────────
                CampaignOpponent(
                    id = "drg_venom", name = "Jedovatý Drak", title = "Zkáza z dálky",
                    avatar = "☠️", description = "Otráví tvůj výcvikový tábor. Mine útoku −1, hrad −5.",
                    aiCastle = 48, aiWall = 25,
                    aiExtraMines = mapOf(ResourceType.MAGIC to 2, ResourceType.ATTACK to 1),
                    deckCardCounts = mapOf(
                        "078" to 3, "051" to 4, "052" to 3, "021" to 5,
                        "C10" to 3, "C02" to 4, "016" to 4, "040" to 5,
                        "069" to 4, "004" to 5, "077" to 4
                    ),
                    winTarget = 999,
                    playerHandicap = PlayerHandicap(
                        extraCastle = -5,
                        extraMines  = mapOf(ResourceType.ATTACK to -1)
                    ),
                    rewardGold = 300
                ),

                // 8 ── 48 karet, winTarget 999, AI lízne 6 karet ──────────────
                CampaignOpponent(
                    id = "drg_tyrant", name = "Dračí Tyran", title = "Ohnivý trůn",
                    avatar = "🔱", description = "6 karet, 3 typy dolů. Legendy v balíčku. Hrad −5, hradby −5.",
                    aiCastle = 50, aiWall = 28,
                    aiExtraMines = mapOf(
                        ResourceType.MAGIC  to 1,
                        ResourceType.ATTACK to 2,
                        ResourceType.STONES to 1
                    ),
                    deckCardCounts = mapOf(
                        "052" to 4, "051" to 4, "078" to 3, "021" to 5,
                        "C10" to 4, "C06" to 4, "C09" to 3, "016" to 4,
                        "043" to 4, "044" to 3, "040" to 5, "004" to 5
                    ),
                    winTarget = 999, aiStartHandSize = 6,
                    playerHandicap = PlayerHandicap(extraCastle = -5, extraWall = -5),
                    rewardGold = 350
                ),

                // 9 ── 50 karet, winTarget 999, AI lízne 6 karet ──────────────
                CampaignOpponent(
                    id = "drg_archon", name = "Dračí Archon", title = "Pravá ruka Impéria",
                    avatar = "🌑", description = "6 karet, 3 extra miny. Hrad −7, hradby −5, mine magie −1.",
                    aiCastle = 52, aiWall = 30,
                    aiExtraMines = mapOf(
                        ResourceType.MAGIC  to 2,
                        ResourceType.ATTACK to 2
                    ),
                    deckCardCounts = mapOf(
                        "052" to 5, "051" to 4, "078" to 4, "C10" to 4,
                        "C06" to 5, "C09" to 4, "016" to 4, "043" to 4,
                        "044" to 4, "040" to 5, "077" to 4, "004" to 3
                    ),
                    winTarget = 999, aiStartHandSize = 6,
                    playerHandicap = PlayerHandicap(
                        extraCastle = -7,
                        extraWall   = -5,
                        extraMines  = mapOf(ResourceType.MAGIC to -1)
                    ),
                    rewardGold = 420
                ),

                // 10 BOSS ── 55 karet, winTarget 999, AI lízne 7 karet ─────────
                CampaignOpponent(
                    id = "drg_emperor", name = "Dračí Císař", title = "Vládce věčného plamene",
                    avatar = "🐉", description = "55 karet, 7 startovních. Drak, Démon i Chaos drak. Hrad −8, hradby −8. Jednorázová odměna: 200 XP.",
                    isBoss = true,
                    aiCastle = 55, aiWall = 35,
                    aiExtraMines = mapOf(
                        ResourceType.MAGIC  to 2,
                        ResourceType.ATTACK to 2,
                        ResourceType.STONES to 1
                    ),
                    deckCardCounts = mapOf(
                        "052" to 4, "051" to 4, "078" to 4, "C10" to 4,
                        "C06" to 4, "C09" to 4, "C05" to 4, "016" to 4,
                        "043" to 4, "044" to 3, "040" to 4, "077" to 4,
                        "021" to 4, "004" to 4
                    ),
                    winTarget = 999, aiStartHandSize = 7,
                    playerHandicap = PlayerHandicap(extraCastle = -8, extraWall = -8),
                    rewardGold = 600, rewardGems = 8, rewardXp = 200
                )
            ).map { it.copy(aiCastleSkin = "castle_drak", aiWallSkin = "wall_drak") }
        )
    )
}

// ============================================================
// LanguagePack.kt
// ============================================================
package com.example.termiti

import org.json.JSONObject

/**
 * Localized name + description for a single card (keyed by card id).
 * Also reused for abilities (name=title, desc=description) and campaign content:
 * locations (name+desc, [title] unused) and opponents (name+title+desc).
 */
data class CardText(val name: String, val desc: String, val title: String = "")

/**
 * A loaded language pack: metadata + all UI strings + all card texts.
 *
 * Parsed from assets/lang/<code>.json.
 * Any missing UI key falls back to the Czech [fallback] pack so the app
 * never crashes on an incomplete community translation. Missing card texts
 * fall back to the card's built-in Czech name/description (see CardRepository).
 */
data class LanguagePack(
    val language: Language,
    val strings:  AppStrings,
    /** id → localized {name, desc}. Empty for an untranslated pack. */
    val cards:    Map<String, CardText> = emptyMap(),
    /** passive-ability id → localized {name=title, desc=description}. */
    val abilities: Map<String, CardText> = emptyMap(),
    /** CampaignLocation id → localized {name, desc}. */
    val campaignLocations: Map<String, CardText> = emptyMap(),
    /** CampaignOpponent id → localized {name, title, desc}. */
    val campaignOpponents: Map<String, CardText> = emptyMap()
) {
    companion object {

        /** Hard-coded Czech fallback — used when JSON loading fails entirely. */
        fun fallback(): LanguagePack = LanguagePack(
            language = Language("cs", "Čeština", "🇨🇿", "Termiti Team"),
            strings  = buildStrings("cs", JSONObject(), null)
        )

        /**
         * Parses a language pack JSON.
         * [fallbackPack] provides values for any missing keys.
         */
        fun fromJson(root: JSONObject, fallbackPack: LanguagePack? = null): LanguagePack {
            val meta = root.optJSONObject("meta") ?: JSONObject()
            val language = Language(
                code    = meta.optString("code",    "??"),
                name    = meta.optString("name",    "Unknown"),
                flag    = meta.optString("flag",    "🏳️"),
                author  = meta.optString("author",  "Community"),
                version = meta.optInt("version",    1)
            )
            val strings = buildStrings(language.code, root.optJSONObject("strings") ?: JSONObject(), fallbackPack?.strings)
            val cards   = buildCards(root.optJSONObject("cards"))
            val abilities = buildCards(root.optJSONObject("abilities"))
            val campaignLocations = buildCards(root.optJSONObject("campaignLocations"))
            val campaignOpponents = buildCards(root.optJSONObject("campaignOpponents"))
            return LanguagePack(language, strings, cards, abilities, campaignLocations, campaignOpponents)
        }

        /**
         * Parses an optional "<id>": { "name": "...", "desc": "...", "title": "..." } object.
         * A missing field within an entry stays empty → caller falls back to the built-in
         * Czech text. Returns an empty map if there is no such block at all.
         */
        private fun buildCards(obj: JSONObject?): Map<String, CardText> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, CardText>(obj.length())
            val ids = obj.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val e  = obj.optJSONObject(id) ?: continue
                out[id] = CardText(
                    name  = e.optString("name", ""),
                    desc  = e.optString("desc", ""),
                    title = e.optString("title", "")
                )
            }
            return out
        }

        /**
         * Builds [AppStrings] from a JSON "strings" object.
         * Any missing key falls back to [fb] (fallback strings), then to a built-in Czech default.
         */
        private fun buildStrings(code: String, s: JSONObject, fb: AppStrings?): AppStrings {
            fun str(key: String, czDefault: String): String =
                if (s.has(key)) s.getString(key)
                else fb?.let { fbVal(it, key) } ?: czDefault

            return AppStrings(code, mapOf(

                "ok" to str("ok",      "OK"),
                "cancel" to str("cancel",  "Zrušit"),
                "confirm" to str("confirm", "Potvrdit"),
                "back" to str("back",    "← ZPĚT"),

                "settings" to str("settings",      "NASTAVENÍ"),
                "music" to str("music",         "🎵  Hudba"),
                "soundEffects" to str("soundEffects",  "🔊  Efekty"),
                "languageLabel" to str("languageLabel", "🌐  Jazyk"),

                "play" to str("play",         "HRÁT"),
                "buildDeck" to str("buildDeck",    "TVORBA BALÍČKU"),
                "multiplayer" to str("multiplayer",  "MULTIPLAYER"),
                "profile" to str("profile",      "PROFIL"),
                "shop" to str("shop",         "OBCHOD"),
                "exit" to str("exit",         "KONEC"),

                "ownDeck" to str("ownDeck",     "VLASTNÍ BALÍČEK"),
                "superRandom" to str("superRandom", "SUPER NÁHODNÉ"),
                "arena" to str("arena",       "Aréna"),
                "campaign" to str("campaign",    "KAMPAŇ"),

                "yourTurn" to str("yourTurn",      "VÁŠ TAH"),
                "opponentTurn" to str("opponentTurn",  "TAH SOUPEŘE"),
                "endTurn" to str("endTurn",       "Ukončit tah"),
                "endCombo" to str("endCombo",      "Konec combo"),
                "endGame" to str("endGame",       "Ukončení hry"),
                "waitingTurn" to str("waitingTurn",   "Čekám…"),
                "inspectGame" to str("inspectGame",   "Prohlédnout hru"),
                "gameLog" to str("gameLog",       "HERNÍ LOG"),
                "enemy" to str("enemy",         "Nepřítel"),
                "viewOpponentHand" to str("viewOpponentHand", "Oponent"),
                "viewMyHand" to str("viewMyHand",       "Moje karty"),
                "discard" to str("discard",       "Zahodit"),
                "castle" to str("castle",        "Hrad"),
                "wall" to str("wall",          "Hradby"),
                "round" to str("round",         "Kolo"),

                "decisionTitle" to str("decisionTitle",           "ROZHODNUTÍ"),
                "decisionPreviewGame" to str("decisionPreviewGame",     "Náhled hry"),
                "decisionBackToDecision" to str("decisionBackToDecision",  "Zpět na rozhodnutí"),
                "decisionChooseType" to str("decisionChooseType",      "Vyber si kartu typu **%s** — **získáš** ji do ruky"),
                "decisionBurnOpponent" to str("decisionBurnOpponent",    "Vyber kartu ze soupeřova balíčku — **spálí se**"),
                "decisionFromDiscard" to str("decisionFromDiscard",     "**Vrať si** kartu z odhazovacího balíčku do ruky"),
                "decisionFromDeck" to str("decisionFromDeck",        "Vyber kartu ze svého balíčku — **zkopíruje se** ti do ruky"),
                "decisionDrawFromDeck" to str("decisionDrawFromDeck",    "Vyber kartu ze svého balíčku — **lízneš si** ji do ruky"),
                "decisionMine" to str("decisionMine",            "**Postav si** jeden důl: **Magie**, **Útok**, **Kámen** nebo **Chaos**"),
                "decisionSmartJoker" to str("decisionSmartJoker",        "Vyber si kartu podle situace — **získáš** ji do ruky"),
                "decisionPeekTitle" to str("decisionPeekTitle",         "ŠPEHOVÁNÍ"),
                "decisionPeekSubtitle" to str("decisionPeekSubtitle",      "Vidíš soupeřovu ruku — **ukradni** jednu kartu"),
                "decisionAlchemyTitle" to str("decisionAlchemyTitle",      "ALCHYMIE"),
                "decisionAlchemySubtitle" to str("decisionAlchemySubtitle",   "Vyber si, kterou surovinu **získáš**"),
                "decisionResourceCardDesc" to str("decisionResourceCardDesc",  "Přidá %d %s do tvých surovin."),

                "resultVictory" to str("resultVictory",         "VÝHRA!"),
                "resultDefeat" to str("resultDefeat",          "PROHRA"),
                "resultDraw" to str("resultDraw",            "REMÍZA"),
                "resultCastleBuilt" to str("resultCastleBuilt",         "Postavil jsi hrad!"),
                "resultCastleBuiltOpponent" to str("resultCastleBuiltOpponent", "Soupeř postavil mocný hrad."),
                "resultCastleDestroyed" to str("resultCastleDestroyed",     "Zničil jsi soupeřův hrad!"),
                "resultCastleDestroyedSelf" to str("resultCastleDestroyedSelf", "Tvůj hrad byl zničen."),
                "resultHpWins" to str("resultHpWins",          "Balíčky došly – tvůj hrad je vyšší!"),
                "resultHpLose" to str("resultHpLose",          "Balíčky došly – soupeř měl vyšší hrad."),
                "resultHpDraw" to str("resultHpDraw",          "Balíčky došly – oba hrady jsou stejně vysoké."),
                "resultHpWinsTurnLimit" to str("resultHpWinsTurnLimit", "Limit 99 kol – tvůj hrad je vyšší!"),
                "resultHpLoseTurnLimit" to str("resultHpLoseTurnLimit", "Limit 99 kol – soupeř měl vyšší hrad."),
                "resultBothDead" to str("resultBothDead",        "Oba hrady zničeny."),
                "resultPlayAgain" to str("resultPlayAgain",       "Hrát znovu"),
                "resultBackToMenu" to str("resultBackToMenu",      "Zpět do menu"),

                "campaignVictory" to str("campaignVictory",              "VÍTĚZSTVÍ!"),
                "campaignDefeat" to str("campaignDefeat",               "PORÁŽKA"),
                "campaignRewardFirstKill" to str("campaignRewardFirstKill",      "ODMĚNA ZA PRVNÍ PORAŽENÍ"),
                "campaignRewardAlreadyClaimed" to str("campaignRewardAlreadyClaimed", "Odměna již byla vyplacena"),
                "campaignRetry" to str("campaignRetry",                "🔄  Zkusit znovu"),
                "campaignBackToLocation" to str("campaignBackToLocation",       "Zpět na lokaci"),
                "campaignNextOpponent" to str("campaignNextOpponent",         "Další soupeř"),

                "deckBuilder" to str("deckBuilder",   "STAVITEL BALÍČKU"),
                "deckSave" to str("deckSave",      "Uložit"),
                "deckReset" to str("deckReset",     "Resetovat"),
                "deckClear" to str("deckClear",     "Vymazat"),
                "deckCardCount" to str("deckCardCount", "%d / %d karet"),
                "deckDefaultName" to str("deckDefaultName",  "Balíček %d"),
                "dbActiveShort" to str("dbActiveShort",    "✓ aktivní"),
                "dbSearchHint" to str("dbSearchHint",     "🔍 Hledat…"),
                "dbEffectLabel" to str("dbEffectLabel",    "Efekt:"),
                "catAttack" to str("catAttack",        "Útok"),
                "catDefense" to str("catDefense",       "Obrana"),
                "catResources" to str("catResources",     "Zdroje"),
                "catMines" to str("catMines",         "Doly"),
                "catCombo" to str("catCombo",         "🔗 Kombo"),
                "catDecision" to str("catDecision",      "Rozhodnutí"),
                "catOther" to str("catOther",         "Ostatní"),
                "dbFilterUnlocked" to str("dbFilterUnlocked", "🔓 Odemčené"),
                "dbDisassemble" to str("dbDisassemble",    "Rozebrat  +%d✨"),
                "dbDustGain" to str("dbDustGain",       "+%d ✨ prachu"),
                "dbDustCost" to str("dbDustCost",       "−%d ✨ prachu"),
                "dbBadgeNew" to str("dbBadgeNew",       "NOVÉ"),
                "dbTemplates" to str("dbTemplates",      "Šablony"),
                "dbSetActive" to str("dbSetActive",      "Nastavit aktivní"),
                "dbActiveDeck" to str("dbActiveDeck",     "✓ Aktivní balíček"),
                "dbComposition" to str("dbComposition",    "Složení balíčku"),
                "dbInDeck" to str("dbInDeck",         "V BALÍČKU"),
                "dbConfirm" to str("dbConfirm",        "✓  Potvrdit"),
                "dbDone" to str("dbDone",           "✓  Hotovo"),

                "arenaDraft" to str("arenaDraft",    "DRAFT ARÉNY"),
                "arenaPickCard" to str("arenaPickCard", "Vyber kartu"),
                "arenaWins" to str("arenaWins",     "%d výher"),

                "mulliganTitle" to str("mulliganTitle",    "MULLIGAN"),
                "mulliganSubtitle" to str("mulliganSubtitle", "Vyber karty k **výměně**"),
                "mulliganConfirm" to str("mulliganConfirm",  "Potvrdit"),
                "mulliganYouFirst" to str("mulliganYouFirst",        "Ty začínáš první"),
                "mulliganOpponentFirst" to str("mulliganOpponentFirst",   "Soupeř začíná první"),
                "mulliganWaitingOpponent" to str("mulliganWaitingOpponent", "Čekám na soupeře…"),
                "mulliganWaitingOpponentTimer" to str("mulliganWaitingOpponentTimer", "Čekám na soupeře… (%d s)"),
                "mulliganInstruction" to str("mulliganInstruction",     "Klikni na karty, které chceš **vyměnit** za nové z balíčku"),
                "mulliganSelected" to str("mulliganSelected",        "**Vybráno: %d** — klikni znovu pro zrušení"),
                "mulliganPlayNoSwap" to str("mulliganPlayNoSwap",      "Hrát bez výměny"),
                "mulliganSwap" to str("mulliganSwap",            "Vyměnit"),

                "limitChangeTitle" to str("limitChangeTitle",    "ZMĚNA LIMITŮ"),
                "limitChangeSubtitle" to str("limitChangeSubtitle", "Limit kopií u těchto karet se snížil.\nPřebytečné kopie byly rozebrány na prach."),
                "limitChangeNewLimit" to str("limitChangeNewLimit", "nyní max. %d"),
                "limitChangeDust" to str("limitChangeDust",     "+%d prachu"),
                "limitChangeDecks" to str("limitChangeDecks",    "Z balíčků odebráno: %d"),
                "limitChangeConfirm" to str("limitChangeConfirm",  "Rozumím"),

                "profileTitle" to str("profileTitle",  "PROFIL"),
                "profileWins" to str("profileWins",   "Výhry"),
                "profileLosses" to str("profileLosses", "Prohry"),
                "profileGames" to str("profileGames",  "Celkem her"),
                "profileLevel" to str("profileLevel",             "Úroveň %d"),
                "profileGold" to str("profileGold",              "Zlato"),
                "profileGems" to str("profileGems",              "Drahokamy"),
                "profilePlayed" to str("profilePlayed",            "Odehráno"),
                "profileUnlockAll" to str("profileUnlockAll",         "Všechny karty odemčeny"),
                "profileUnlockCampaign" to str("profileUnlockCampaign",    "Celá kampaň odemčena"),
                "profileSectionAvatar" to str("profileSectionAvatar",     "Ikonka hráče"),
                "profileSectionCastle" to str("profileSectionCastle",     "Skin hradu"),
                "profileSectionWall" to str("profileSectionWall",       "Skin hradby"),
                "profileSectionCardBack" to str("profileSectionCardBack",   "Rub karty"),
                "profileSectionAbilities" to str("profileSectionAbilities",  "Pasivní schopnosti"),
                "profileActiveCount" to str("profileActiveCount",       "Aktivní: %d / %d"),
                "profileSectionCosmetics" to str("profileSectionCosmetics",  "Kosmetika"),
                "profileCosmeticsSoon" to str("profileCosmeticsSoon",     "Různé cardbacky, hrady a zdi za drahokamy."),
                "profileActive" to str("profileActive",            "✓ Aktivní"),
                "toggleOn" to str("toggleOn",                 "✓ ZAP"),
                "toggleOff" to str("toggleOff",                "VYP"),
                "slotFull" to str("slotFull",                 "PLNO"),
                "castleClassic" to str("castleClassic",            "Klasický"),
                "castleStone" to str("castleStone",              "Kamenný"),
                "castleDark" to str("castleDark",               "Temný"),
                "castleOutlawCamp" to str("castleOutlawCamp",         "Tábor psanců"),
                "castleVariant" to str("castleVariant",            "Hrad %d"),
                "wallClassic" to str("wallClassic",              "Klasická"),
                "wallVariant" to str("wallVariant",              "Hradba %d"),
                "cardBackBasic" to str("cardBackBasic",            "Základní"),
                "cardBackStyle2" to str("cardBackStyle2",           "Styl 2"),
                "cardBackStyle3" to str("cardBackStyle3",           "Styl 3"),
                "questsTitle" to str("questsTitle",              "Denní questy"),
                "questsReset" to str("questsReset",              "Resetují se zítra"),
                "questClaim" to str("questClaim",               "Převzít!"),
                "questWinGames" to str("questWinGames",            "Vyhraj %d her"),
                "questWinOnline" to str("questWinOnline",           "Vyhraj %d online her"),
                "questPlayCards" to str("questPlayCards",           "Zahraj %d karet"),
                "questDealDamage" to str("questDealDamage",          "Způsob %d poškození hradu"),
                "questWinCampaign" to str("questWinCampaign",         "Poraž %d soupeřů v kampani"),
                "questCompletedNotif" to str("questCompletedNotif",      "🎯 Quest splněn – převzít odměnu!"),

                "shopTitle" to str("shopTitle", "OBCHOD"),
                "shopBuy" to str("shopBuy",   "Koupit"),
                "shopDust" to str("shopDust",  "Prach"),

                "onlineConnecting" to str("onlineConnecting",   "Připojování…"),
                "onlineWaiting" to str("onlineWaiting",      "Čekám na soupeře…"),
                "onlineDisconnected" to str("onlineDisconnected", "Odpojeno"),

                "rarityCommon" to str("rarityCommon",    "Běžná"),
                "rarityRare" to str("rarityRare",      "Vzácná"),
                "rarityEpic" to str("rarityEpic",      "Epická"),
                "rarityLegendary" to str("rarityLegendary", "Legendární"),

                "typeAttack" to str("typeAttack",   "Útok"),
                "typeBuild" to str("typeBuild",    "Stavba"),
                "typeMagic" to str("typeMagic",    "Magie"),
                "typeChaos" to str("typeChaos",    "Chaos"),
                "typeMines" to str("typeMines",    "Doly"),
                "typeDecision" to str("typeDecision", "Rozhodnutí"),
                "typeDraw" to str("typeDraw",     "Líznutí"),

                "resMagic" to str("resMagic",  "Magie"),
                "resAttack" to str("resAttack", "Útok"),
                "resStone" to str("resStone",  "Kámen"),
                "resChaos" to str("resChaos",  "Chaos"),

                "logActorPlayer" to str("logActorPlayer", "Hráč"),
                "logActorAi" to str("logActorAi",     "AI"),
                "logVerbPlayed" to str("logVerbPlayed",    "zahrál"),
                "logVerbDiscarded" to str("logVerbDiscarded", "zahodil"),
                "logVerbBurned" to str("logVerbBurned",    "🔥 spálil"),
                "logVerbStolen" to str("logVerbStolen",    "🃏 ukradl"),
                "logBurnedFromOppDeck" to str("logBurnedFromOppDeck", "Hráč zahodil ze soupeřova balíku: %s"),
                "logChose" to str("logChose",             "Hráč si vybral: %s"),
                "logTookFromDiscard" to str("logTookFromDiscard",   "Hráč si vzal z odhazovacího balíčku: %s"),
                "logCopiedFromDeck" to str("logCopiedFromDeck",    "Hráč zkopíroval z balíčku: %s"),
                "logDrewFromDeck" to str("logDrewFromDeck",      "Hráč vytáhl z balíčku: %s"),
                "logChoseMine" to str("logChoseMine",         "Hráč si vybral důl: %s"),
                "logJoker" to str("logJoker",             "Magický žolík: hráč si zvolil %s"),
                "logStoleFromHand" to str("logStoleFromHand",     "Hráč ukradl ze soupeřovy ruky: %s"),
                "logChoseResource" to str("logChoseResource",     "Hráč si vybral: %d× %s"),
                "logPlayerFirst" to str("logPlayerFirst",       "Hráč začíná jako první!"),
                "logAiFirst" to str("logAiFirst",           "AI začíná jako první!"),
                "logNotEnough" to str("logNotEnough",         "Nedostatek %s pro: %s"),
                "logConditionNotMet" to str("logConditionNotMet",   "%s: podmínka nesplněna!"),
                "logReplication" to str("logReplication",       "Replikace: %d× %s zamícháno do balíčku"),
                "logDiscardEffectTriggered" to str("logDiscardEffectTriggered", "Zahozením karty %s se spustil efekt!"),
                "logPlayerEndTurn" to str("logPlayerEndTurn",     "Hráč ukončil tah"),
                "logPlayerSkip" to str("logPlayerSkip",        "Hráč přeskočil kolo"),
                "logAiDiscardFromDeck" to str("logAiDiscardFromDeck", "AI zahodila z tvého balíku: %s"),
                "logAiJoker" to str("logAiJoker",           "AI zvolila žolíka: %s"),
                "logAiStoleFromHand" to str("logAiStoleFromHand",   "AI ukradla z tvé ruky: %s"),
                "logAiChoseResource" to str("logAiChoseResource",   "AI si vybrala %d× %s"),
                "logAiWaited" to str("logAiWaited",          "AI čekala"),
                "logBothPassedEmpty" to str("logBothPassedEmpty",   "Oba hráči pasovali s prázdnými balíčky – konec hry!"),
                "logBothNoCards" to str("logBothNoCards",       "Obě strany bez karet – konec hry!"),
                "logTrapDrewYou" to str("logTrapDrewYou",   "Ty jsi lízl"),
                "logTrapDrewAi" to str("logTrapDrewAi",    "AI lízla"),
                "logTrapCastle" to str("logTrapCastle",    "HRAD"),
                "logTrapWall" to str("logTrapWall",      "ZEĎ"),
                "logTrapHp" to str("logTrapHp",        "HP"),
                "logTrapTriggered" to str("logTrapTriggered", "pasca spuštěna"),
                "backShort" to str("backShort", "← Zpět"),
                "opponentDefault" to str("opponentDefault", "Soupeř"),
                "mpQuickMatch" to str("mpQuickMatch", "RYCHLÝ ZÁPAS"),
                "mpSuperRandom" to str("mpSuperRandom", "SUPER NÁHODNÝ"),
                "mpLeaderboard" to str("mpLeaderboard", "ŽEBŘÍČEK"),
                "mpDisconnect" to str("mpDisconnect", "ODPOJIT"),
                "mpModeSuperRandom" to str("mpModeSuperRandom", "Super Náhodný"),
                "mpModeQuick" to str("mpModeQuick", "Rychlý zápas"),
                "mpDeckLabel" to str("mpDeckLabel", "BALÍČEK"),
                "mpQueue" to str("mpQueue", "Fronta"),
                "mpGamesSuffix" to str("mpGamesSuffix", "(%d her)"),
                "mpConnectHint" to str("mpConnectHint", "Připoj se k lobby serveru a najdi soupeře"),
                "mpNickname" to str("mpNickname", "Přezdívka"),
                "mpConnect" to str("mpConnect", "PŘIPOJIT SE"),
                "mpConnecting" to str("mpConnecting", "Připojuji k serveru…"),
                "mpCancel" to str("mpCancel", "← ZRUŠIT"),
                "mpSearching" to str("mpSearching", "Hledám soupeře…"),
                "mpInQueue" to str("mpInQueue", "Ve frontě: %d hráčů"),
                "mpBackPlain" to str("mpBackPlain", "ZPĚT"),
                "mpOpponentFound" to str("mpOpponentFound", "SOUPEŘ NALEZEN!"),
                "mpPreparing" to str("mpPreparing", "Připravuji hru…"),
                "mpConnectionError" to str("mpConnectionError", "CHYBA PŘIPOJENÍ"),
                "mpRetry" to str("mpRetry", "ZKUSIT ZNOVU"),
                "errUnknown" to str("errUnknown", "neznámá chyba"),
                "errNetAbort" to str("errNetAbort", "spojení přerušeno sítí"),
                "errRefused" to str("errRefused", "server nepřijímá spojení"),
                "errNoHost" to str("errNoHost", "nelze najít server"),
                "errNotFound" to str("errNotFound", "server nenalezen"),
                "errTimeout" to str("errTimeout", "vypršel čas připojení"),
                "errReset" to str("errReset", "spojení resetováno"),
                "errUnreachable" to str("errUnreachable", "síť nedostupná"),
                "errRejected" to str("errRejected", "server odmítl spojení"),
                "errUnknownConn" to str("errUnknownConn", "neznámá chyba připojení"),
                "lobbyDeckReady" to str("lobbyDeckReady", "Balíček: %d karet připraveno"),
                "lobbyDeckRandom" to str("lobbyDeckRandom", "Balíček: náhodný"),
                "lobbyEnterNick" to str("lobbyEnterNick", "Zadej přezdívku"),
                "lobbySearchingSuperRandom" to str("lobbySearchingSuperRandom", "Hledám super náhodného soupeře…"),
                "lobbyDeckNot30" to str("lobbyDeckNot30", "Vybraný balíček nemá 30 karet"),
                "lobbyConnectFailed" to str("lobbyConnectFailed", "Nepodařilo se připojit: %s"),
                "lobbyConnectionLost" to str("lobbyConnectionLost", "Spojení přerušeno (kód %s)"),
                "lobbyConnected" to str("lobbyConnected", "Připojeno ✓"),
                "lobbyInQueue" to str("lobbyInQueue", "Ve frontě…"),
                "lobbyOpponentFoundPreparing" to str("lobbyOpponentFoundPreparing", "Soupeř nalezen! Připravuji hru…"),
                "lobbyOpponentLeftWin" to str("lobbyOpponentLeftWin", "Soupeř se odpojil – vyhráváš!"),
                "lobbyOutdated" to str("lobbyOutdated", "Tvá verze hry je zastaralá. Aktualizuj aplikaci pro hraní online."),
                "shopPacks" to str("shopPacks", "BALÍČKY"),
                "shopPackInfo" to str("shopPackInfo", "5 karet • 1× vzácná nebo lepší garantována"),
                "shopBuyPack" to str("shopBuyPack", "KOUPIT BALÍČEK"),
                "shopCanBuy" to str("shopCanBuy", "Můžeš koupit %d× balíček"),
                "shopEarnGold" to str("shopEarnGold", "Zlato získáš vítězstvím v bitvě"),
                "shopPackOpened" to str("shopPackOpened", "BALÍČEK OTEVŘEN!"),
                "shopTapToReveal" to str("shopTapToReveal", "Klepni na kartu pro odkrytí"),
                "shopDuplicates" to str("shopDuplicates", "Duplikáty → +%d prachu"),
                "shopFinish" to str("shopFinish", "DOKONČIT"),
                "lbLoadFailed" to str("lbLoadFailed", "Nepodařilo se načíst žebříček\n%s"),
                "lbTitle" to str("lbTitle", "🏆 ŽEBŘÍČEK"),
                "lbTotalPlayers" to str("lbTotalPlayers", "%d hráčů celkem"),
                "lbModeSuperRandom" to str("lbModeSuperRandom", "🌪️ Super Náhodný"),
                "lbLoading" to str("lbLoading", "Načítám žebříček…"),
                "lbRetry" to str("lbRetry", "🔄 Zkusit znovu"),
                "lbEmpty" to str("lbEmpty", "Žebříček je prázdný"),
                "lbPlayer" to str("lbPlayer", "Hráč"),
                "stay" to str("stay", "Zůstat"),
                "leave" to str("leave", "Odejít"),
                "surrender" to str("surrender", "Vzdát se"),
                "surrenderQ" to str("surrenderQ", "Vzdát se?"),
                "close" to str("close", "Zavřít"),
                "closeX" to str("closeX", "✕ Zavřít"),
                "tapToClose" to str("tapToClose", "Klepnutím zavřeš"),
                "backToMenuCaps" to str("backToMenuCaps", "ZPĚT DO MENU"),
                "roundN" to str("roundN", "Kolo %d"),
                "opponentAcc" to str("opponentAcc", "soupeře"),
                "statWinShort" to str("statWinShort", "V"),
                "statLossShort" to str("statLossShort", "P"),
                "mpStats" to str("mpStats", "STATISTIKY"),
                "leaveGameQ" to str("leaveGameQ", "Opustit hru?"),
                "leaveGameMsg" to str("leaveGameMsg", "Rozehraná partie bude ztracena. Opravdu chceš odejít do menu?"),
                "onlineSurrenderMsg" to str("onlineSurrenderMsg", "Vzdát se? Prohra bude zaznamenána a soupeř bude prohlášen vítězem."),
                "onlineOppLeft" to str("onlineOppLeft", "Soupeř se odpojil"),
                "onlineWaitReconnect" to str("onlineWaitReconnect", "Čekám na reconnect…"),
                "onlineConnLost" to str("onlineConnLost", "Ztraceno připojení"),
                "onlineReconnecting" to str("onlineReconnecting", "Připojuji se zpět…"),
                "onlineYouFirst" to str("onlineYouFirst", "⚔ Ty začínáš první"),
                "onlineOppFirst" to str("onlineOppFirst", "⏳ Soupeř začíná první"),
                "onlineGameOver" to str("onlineGameOver", "Konec hry"),
                "onlineDraw" to str("onlineDraw", "Remíza!"),
                "onlineDrawEqual" to str("onlineDrawEqual", "Obě strany mají stejný hrad"),
                "onlineVictory" to str("onlineVictory", "Vítězství!"),
                "onlineDefeat" to str("onlineDefeat", "Prohra"),
                "onlineYouBeat" to str("onlineYouBeat", "Porazil jsi %s"),
                "onlineWinnerWon" to str("onlineWinnerWon", "%s zvítězil"),
                "onlineBackToLobby" to str("onlineBackToLobby", "Zpět do lobby"),
                "lostCardsTitle" to str("lostCardsTitle", "SPÁLENÉ & UKRADENÉ KARTY"),
                "lostCardsSubtitle" to str("lostCardsSubtitle", "Karty, o které tě připravil soupeř."),
                "lostCardsEmpty" to str("lostCardsEmpty", "Žádná karta zatím nebyla spálena ani ukradena."),
                "lostCardsButton" to str("lostCardsButton", "Spálené & ukradené (%d)"),
                "badgeStolen" to str("badgeStolen", "UKRADENO"),
                "badgeBurned" to str("badgeBurned", "SPÁLENO"),
                "inspectGameCaps" to str("inspectGameCaps", "PROHLÉDNOUT HRU"),
                "handCount" to str("handCount", "RUKA (%d)"),
                "actionWait" to str("actionWait", "Čekat"),
                "logGameStarts" to str("logGameStarts", "— Hra začíná —"),
                "arenaTitleDraft" to str("arenaTitleDraft", "ARÉNA — DRAFT"),
                "arenaPickOne" to str("arenaPickOne", "Vyber jednu kartu"),
                "arenaPick" to str("arenaPick", "VYBRAT"),
                "arenaDeckComposition" to str("arenaDeckComposition", "SLOŽENÍ BALÍČKU"),
                "arenaEffects" to str("arenaEffects", "Efekty"),
                "arenaRecentPicks" to str("arenaRecentPicks", "Poslední výběry"),
                "arenaEnded" to str("arenaEnded", "ARÉNA UKONČENA"),
                "arenaWinsWord" to str("arenaWinsWord", "vítězství"),
                "arenaRank0" to str("arenaRank0", "Příště to vyjde!"),
                "arenaRank1" to str("arenaRank1", "Dobrý začátek."),
                "arenaRank2" to str("arenaRank2", "Solidní výkon!"),
                "arenaRank3" to str("arenaRank3", "Výborně! Jsi silný protivník."),
                "arenaRank4" to str("arenaRank4", "Legenda arény!"),
                "arenaWinsCount" to str("arenaWinsCount", "Vítězství: %d"),
                "arenaNextBattle" to str("arenaNextBattle", "DALŠÍ BITVA"),
                "arenaEnd" to str("arenaEnd", "UKONČIT ARÉNU"),
                "arenaYouLost" to str("arenaYouLost", "Prohrál jsi"),
                "arenaVictory" to str("arenaVictory", "Vítězství!"),
                "arenaDraw" to str("arenaDraw", "Remíza"),
                "resultYourCastleDestroyed" to str("resultYourCastleDestroyed", "Tvůj hrad byl zničen."),
                "resultEnemyCastleBuilt" to str("resultEnemyCastleBuilt", "Nepřítel dokončil svůj hrad."),
                "resultDrawEqual" to str("resultDrawEqual", "Balíčky došly – hrady jsou stejně vysoké."),
                "campaignPickHint" to str("campaignPickHint", "Vyber lokaci a poraž\nvšechny soupeře"),
                "campaignCleared" to str("campaignCleared", "Vyčištěno"),
                "campaignLocked" to str("campaignLocked", "Zamčeno"),
                "campaignDefeated" to str("campaignDefeated", "Poražen"),
                "campaignOppLocked" to str("campaignOppLocked", "Zamčen"),
                "campaignFight" to str("campaignFight", "Bojuj!"),
                "profileNamePrompt" to str("profileNamePrompt", "Zadej své jméno hrdiny"),
                "profileNameHint" to str("profileNameHint", "Jméno hrdiny…"),
                "profileEnterGame" to str("profileEnterGame", "VSTOUPIT DO HRY"),
                "back2" to str("back2", "Zpět"),
                "rewardOnlineWin" to str("rewardOnlineWin", "🌐 Online výhra"),
                "rewardWin" to str("rewardWin", "⚔️ Výhra"),
                "rewardLoss" to str("rewardLoss", "💀 Prohra"),
                "dbResourceLabel" to str("dbResourceLabel", "Zdroj:"),
                "dbCraft" to str("dbCraft", "Vyrobit  ✨%d"),
                "dbManaCurve" to str("dbManaCurve", "Mana křivka"),
                "rogueBuildDeck" to str("rogueBuildDeck", "SESTAV BALÍČEK"),
                "rogueRarityLabel" to str("rogueRarityLabel", "Rarita:"),
                "rogueCostLabel" to str("rogueCostLabel", "Cena:"),
                "rogueDeckTitle" to str("rogueDeckTitle", "ROGUELIKE — BALÍČEK"),
                "rogueSavedDecks" to str("rogueSavedDecks", "Uložené balíčky:"),
                "rogueCardsCount" to str("rogueCardsCount", "Karty: %d / %d"),
                "rogueBudget" to str("rogueBudget", "Rozpočet: %d / %d bodů"),
                "rogueDeckEmpty" to str("rogueDeckEmpty", "Balíček je zatím prázdný."),
                "rogueFillDeck" to str("rogueFillDeck", "Doplň balíček na %d karet."),
                "rogueStartRun" to str("rogueStartRun", "ZAHÁJIT RUN"),
                "rogueDeckNotReady" to str("rogueDeckNotReady", "BALÍČEK NENÍ HOTOVÝ"),
                "rogueVictory" to str("rogueVictory", "VÍTĚZSTVÍ"),
                "rogueNext" to str("rogueNext", "%s  ·  následuje %s"),
                "roguePickRequired" to str("roguePickRequired", "Vyber kartu (POVINNÉ) — zbývá %d×"),
                "rogueAdd" to str("rogueAdd", "PŘIDAT"),
                "roguePickExtra" to str("roguePickExtra", "Vyber jeden navíc, nebo přeskoč"),
                "rogueNewMine" to str("rogueNewMine", "Nový důl"),
                "rogueSkip" to str("rogueSkip", "Přeskočit"),
                "rogueSurrenderMsg" to str("rogueSurrenderMsg", "Rozehraný run bude ztracen. Opravdu se chceš vzdát?"),
                "rogueYourDeck" to str("rogueYourDeck", "TVŮJ BALÍČEK (%d)"),
                "rogueRunComplete" to str("rogueRunComplete", "RUN DOKONČEN!"),
                "rogueRunOver" to str("rogueRunOver", "RUN SKONČIL"),
                "rogueAllBattles" to str("rogueAllBattles", "Probil ses všemi %d bitvami."),
                "rogueCastleFell" to str("rogueCastleFell", "Tvůj hrad padl."),
                "rogueBattlesWon" to str("rogueBattlesWon", "Vyhrané bitvy: %d / %d"),
                "rogueBattleLabel" to str("rogueBattleLabel", "Bitva %d / %d"),
                "rogueActTitles" to str("rogueActTitles", "Hranice|Válečná pole|Citadela"),
                "rogueEnemiesAct1" to str("rogueEnemiesAct1", "Goblin zvěd|Pěšák|Nájezdník|Lučištník|Zloděj"),
                "rogueEnemiesAct2" to str("rogueEnemiesAct2", "Válečník|Sabotér|Temný učeň|Obléhatel|Žoldnéř"),
                "rogueEnemiesAct3" to str("rogueEnemiesAct3", "Generál|Arcimág|Pán citadely|Válečný vládce|Katan"),
                "replayWon" to str("replayWon", "vyhrál"),
                "replayLost" to str("replayLost", "prohrál"),
                "deckStarter" to str("deckStarter", "Začátečník"),
                "presetAttacker" to str("presetAttacker", "⚔️ Útočník"),
                "presetMage" to str("presetMage", "🔮 Mágik"),
                "presetDefender" to str("presetDefender", "🏰 Obránce"),
                "presetDefender2" to str("presetDefender2", "🏰 Obránce2"),
                "presetCardsmith" to str("presetCardsmith", "📚 Kartář"),
                "presetSaboteur" to str("presetSaboteur", "🕵️ Sabotér"),
            ))
        }

        /** Reads a field value from an [AppStrings] instance by key name via reflection-free lookup. */
        private fun fbVal(fb: AppStrings, key: String): String? = fb[key]
    }
}

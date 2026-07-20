// ============================================================
// LanguagePack.kt
// ============================================================
package com.example.termiti

import org.json.JSONObject

/** Localized name + description for a single card (keyed by card id). */
data class CardText(val name: String, val desc: String)

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
    val abilities: Map<String, CardText> = emptyMap()
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
            return LanguagePack(language, strings, cards, abilities)
        }

        /**
         * Parses the optional "cards" object: { "<id>": { "name": "...", "desc": "..." }, … }.
         * A missing name/desc within an entry stays empty → caller falls back to the card's
         * built-in Czech text. Returns an empty map if there is no "cards" block at all.
         */
        private fun buildCards(obj: JSONObject?): Map<String, CardText> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, CardText>(obj.length())
            val ids = obj.keys()
            while (ids.hasNext()) {
                val id = ids.next()
                val e  = obj.optJSONObject(id) ?: continue
                out[id] = CardText(
                    name = e.optString("name", ""),
                    desc = e.optString("desc", "")
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

            return AppStrings(
                languageCode = code,

                ok      = str("ok",      "OK"),
                cancel  = str("cancel",  "Zrušit"),
                confirm = str("confirm", "Potvrdit"),
                back    = str("back",    "← ZPĚT"),

                settings      = str("settings",      "NASTAVENÍ"),
                music         = str("music",         "🎵  Hudba"),
                soundEffects  = str("soundEffects",  "🔊  Efekty"),
                languageLabel = str("languageLabel", "🌐  Jazyk"),

                play         = str("play",         "HRÁT"),
                buildDeck    = str("buildDeck",    "BALÍČEK"),
                multiplayer  = str("multiplayer",  "MULTIPLAYER"),
                profile      = str("profile",      "PROFIL"),
                shop         = str("shop",         "OBCHOD"),
                exit         = str("exit",         "KONEC"),

                ownDeck     = str("ownDeck",     "Vlastní balíček"),
                superRandom = str("superRandom", "Super náhodné"),
                arena       = str("arena",       "Aréna"),
                campaign    = str("campaign",    "Kampaň"),

                yourTurn      = str("yourTurn",      "VÁŠ TAH"),
                opponentTurn  = str("opponentTurn",  "TAH SOUPEŘE"),
                endTurn       = str("endTurn",       "Ukončit tah"),
                endCombo      = str("endCombo",      "Konec combo"),
                endGame       = str("endGame",       "Ukončení hry"),
                waitingTurn   = str("waitingTurn",   "Čekám…"),
                inspectGame   = str("inspectGame",   "Prohlédnout hru"),
                gameLog       = str("gameLog",       "HERNÍ LOG"),
                enemy         = str("enemy",         "Nepřítel"),
                viewOpponentHand = str("viewOpponentHand", "Oponent"),
                viewMyHand       = str("viewMyHand",       "Moje karty"),
                discard       = str("discard",       "Zahodit"),
                castle        = str("castle",        "Hrad"),
                wall          = str("wall",          "Hradby"),
                round         = str("round",         "Kolo"),

                decisionTitle           = str("decisionTitle",           "ROZHODNUTÍ"),
                decisionPreviewGame     = str("decisionPreviewGame",     "Náhled hry"),
                decisionBackToDecision  = str("decisionBackToDecision",  "Zpět na rozhodnutí"),
                decisionChooseType      = str("decisionChooseType",      "Vyber si kartu typu **%s** — **získáš** ji do ruky"),
                decisionBurnOpponent    = str("decisionBurnOpponent",    "Vyber kartu ze soupeřova balíčku — **spálí se**"),
                decisionFromDiscard     = str("decisionFromDiscard",     "**Vrať si** kartu z odhazovacího balíčku do ruky"),
                decisionFromDeck        = str("decisionFromDeck",        "Vyber kartu ze svého balíčku — **zkopíruje se** ti do ruky"),
                decisionDrawFromDeck    = str("decisionDrawFromDeck",    "Vyber kartu ze svého balíčku — **lízneš si** ji do ruky"),
                decisionMine            = str("decisionMine",            "**Postav si** jeden důl: **Magie**, **Útok**, **Kámen** nebo **Chaos**"),
                decisionSmartJoker        = str("decisionSmartJoker",        "Vyber si kartu podle situace — **získáš** ji do ruky"),
                decisionPeekTitle         = str("decisionPeekTitle",         "ŠPEHOVÁNÍ"),
                decisionPeekSubtitle      = str("decisionPeekSubtitle",      "Vidíš soupeřovu ruku — **ukradni** jednu kartu"),
                decisionAlchemyTitle      = str("decisionAlchemyTitle",      "ALCHYMIE"),
                decisionAlchemySubtitle   = str("decisionAlchemySubtitle",   "Vyber si, kterou surovinu **získáš**"),
                decisionResourceCardDesc  = str("decisionResourceCardDesc",  "Přidá %d %s do tvých surovin."),

                resultVictory         = str("resultVictory",         "VÝHRA!"),
                resultDefeat          = str("resultDefeat",          "PROHRA"),
                resultDraw            = str("resultDraw",            "REMÍZA"),
                resultCastleBuilt         = str("resultCastleBuilt",         "Postavil jsi hrad!"),
                resultCastleBuiltOpponent = str("resultCastleBuiltOpponent", "Soupeř postavil mocný hrad."),
                resultCastleDestroyed     = str("resultCastleDestroyed",     "Zničil jsi soupeřův hrad!"),
                resultCastleDestroyedSelf = str("resultCastleDestroyedSelf", "Tvůj hrad byl zničen."),
                resultHpWins          = str("resultHpWins",          "Balíčky došly – tvůj hrad je vyšší!"),
                resultHpLose          = str("resultHpLose",          "Balíčky došly – soupeř měl vyšší hrad."),
                resultHpDraw          = str("resultHpDraw",          "Balíčky došly – oba hrady jsou stejně vysoké."),
                resultHpWinsTurnLimit = str("resultHpWinsTurnLimit", "Limit 99 kol – tvůj hrad je vyšší!"),
                resultHpLoseTurnLimit = str("resultHpLoseTurnLimit", "Limit 99 kol – soupeř měl vyšší hrad."),
                resultBothDead        = str("resultBothDead",        "Oba hrady zničeny."),
                resultPlayAgain       = str("resultPlayAgain",       "Hrát znovu"),
                resultBackToMenu      = str("resultBackToMenu",      "Zpět do menu"),

                deckBuilder   = str("deckBuilder",   "STAVITEL BALÍČKU"),
                deckSave      = str("deckSave",      "Uložit"),
                deckReset     = str("deckReset",     "Resetovat"),
                deckClear     = str("deckClear",     "Vymazat"),
                deckCardCount = str("deckCardCount", "%d / %d karet"),
                deckDefaultName  = str("deckDefaultName",  "Balíček %d"),
                dbActiveShort    = str("dbActiveShort",    "✓ aktivní"),
                dbSearchHint     = str("dbSearchHint",     "🔍 Hledat…"),
                dbEffectLabel    = str("dbEffectLabel",    "Efekt:"),
                catAttack        = str("catAttack",        "Útok"),
                catDefense       = str("catDefense",       "Obrana"),
                catResources     = str("catResources",     "Zdroje"),
                catMines         = str("catMines",         "Doly"),
                catCombo         = str("catCombo",         "🔗 Kombo"),
                catDecision      = str("catDecision",      "Rozhodnutí"),
                catOther         = str("catOther",         "Ostatní"),
                dbFilterUnlocked = str("dbFilterUnlocked", "🔓 Odemčené"),
                dbDisassemble    = str("dbDisassemble",    "Rozebrat  +%d✨"),
                dbDustGain       = str("dbDustGain",       "+%d ✨ prachu"),
                dbDustCost       = str("dbDustCost",       "−%d ✨ prachu"),
                dbBadgeNew       = str("dbBadgeNew",       "NOVÉ"),
                dbTemplates      = str("dbTemplates",      "Šablony"),
                dbSetActive      = str("dbSetActive",      "Nastavit aktivní"),
                dbActiveDeck     = str("dbActiveDeck",     "✓ Aktivní balíček"),
                dbComposition    = str("dbComposition",    "Složení balíčku"),
                dbConfirm        = str("dbConfirm",        "✓  Potvrdit"),
                dbDone           = str("dbDone",           "✓  Hotovo"),

                arenaDraft    = str("arenaDraft",    "DRAFT ARÉNY"),
                arenaPickCard = str("arenaPickCard", "Vyber kartu"),
                arenaWins     = str("arenaWins",     "%d výher"),

                mulliganTitle    = str("mulliganTitle",    "MULLIGAN"),
                mulliganSubtitle = str("mulliganSubtitle", "Vyber karty k **výměně**"),
                mulliganConfirm  = str("mulliganConfirm",  "Potvrdit"),
                mulliganYouFirst        = str("mulliganYouFirst",        "Ty začínáš první"),
                mulliganOpponentFirst   = str("mulliganOpponentFirst",   "Soupeř začíná první"),
                mulliganWaitingOpponent = str("mulliganWaitingOpponent", "Čekám na soupeře…"),
                mulliganInstruction     = str("mulliganInstruction",     "Klikni na karty, které chceš **vyměnit** za nové z balíčku"),
                mulliganSelected        = str("mulliganSelected",        "**Vybráno: %d** — klikni znovu pro zrušení"),
                mulliganPlayNoSwap      = str("mulliganPlayNoSwap",      "Hrát bez výměny"),
                mulliganSwap            = str("mulliganSwap",            "Vyměnit"),

                profileTitle  = str("profileTitle",  "PROFIL"),
                profileWins   = str("profileWins",   "Výhry"),
                profileLosses = str("profileLosses", "Prohry"),
                profileGames  = str("profileGames",  "Celkem her"),
                profileLevel             = str("profileLevel",             "Úroveň %d"),
                profileGold              = str("profileGold",              "Zlato"),
                profileGems              = str("profileGems",              "Drahokamy"),
                profilePlayed            = str("profilePlayed",            "Odehráno"),
                profileUnlockAll         = str("profileUnlockAll",         "Všechny karty odemčeny"),
                profileSectionAvatar     = str("profileSectionAvatar",     "Ikonka hráče"),
                profileSectionCastle     = str("profileSectionCastle",     "Skin hradu"),
                profileSectionCardBack   = str("profileSectionCardBack",   "Rub karty"),
                profileSectionAbilities  = str("profileSectionAbilities",  "Pasivní schopnosti"),
                profileActiveCount       = str("profileActiveCount",       "Aktivní: %d / %d"),
                profileSectionCosmetics  = str("profileSectionCosmetics",  "Kosmetika"),
                profileCosmeticsSoon     = str("profileCosmeticsSoon",     "Různé cardbacky, hrady a zdi za drahokamy."),
                profileActive            = str("profileActive",            "✓ Aktivní"),
                toggleOn                 = str("toggleOn",                 "✓ ZAP"),
                toggleOff                = str("toggleOff",                "VYP"),
                slotFull                 = str("slotFull",                 "PLNO"),
                castleClassic            = str("castleClassic",            "Klasický"),
                castleStone              = str("castleStone",              "Kamenný"),
                castleDark               = str("castleDark",               "Temný"),
                cardBackBasic            = str("cardBackBasic",            "Základní"),
                cardBackStyle2           = str("cardBackStyle2",           "Styl 2"),
                cardBackStyle3           = str("cardBackStyle3",           "Styl 3"),
                questsTitle              = str("questsTitle",              "Denní questy"),
                questsReset              = str("questsReset",              "Resetují se zítra"),
                questClaim               = str("questClaim",               "Převzít!"),
                questWinGames            = str("questWinGames",            "Vyhraj %d her"),
                questWinOnline           = str("questWinOnline",           "Vyhraj %d online her"),
                questPlayCards           = str("questPlayCards",           "Zahraj %d karet"),
                questDealDamage          = str("questDealDamage",          "Způsob %d poškození hradu"),
                questWinCampaign         = str("questWinCampaign",         "Poraž %d soupeřů v kampani"),

                shopTitle = str("shopTitle", "OBCHOD"),
                shopBuy   = str("shopBuy",   "Koupit"),
                shopDust  = str("shopDust",  "Prach"),

                onlineConnecting   = str("onlineConnecting",   "Připojování…"),
                onlineWaiting      = str("onlineWaiting",      "Čekám na soupeře…"),
                onlineDisconnected = str("onlineDisconnected", "Odpojeno"),

                rarityCommon    = str("rarityCommon",    "Běžná"),
                rarityRare      = str("rarityRare",      "Vzácná"),
                rarityEpic      = str("rarityEpic",      "Epická"),
                rarityLegendary = str("rarityLegendary", "Legendární"),

                typeAttack   = str("typeAttack",   "Útok"),
                typeBuild    = str("typeBuild",    "Stavba"),
                typeMagic    = str("typeMagic",    "Magie"),
                typeChaos    = str("typeChaos",    "Chaos"),
                typeMines    = str("typeMines",    "Doly"),
                typeDecision = str("typeDecision", "Rozhodnutí"),
                typeDraw     = str("typeDraw",     "Líznutí"),

                resMagic  = str("resMagic",  "Magie"),
                resAttack = str("resAttack", "Útok"),
                resStone  = str("resStone",  "Kámen"),
                resChaos  = str("resChaos",  "Chaos"),

                logActorPlayer = str("logActorPlayer", "Hráč"),
                logActorAi     = str("logActorAi",     "AI"),
                logVerbPlayed    = str("logVerbPlayed",    "zahrál"),
                logVerbDiscarded = str("logVerbDiscarded", "zahodil"),
                logVerbBurned    = str("logVerbBurned",    "🔥 spálil"),
                logVerbStolen    = str("logVerbStolen",    "🃏 ukradl"),
                logBurnedFromOppDeck = str("logBurnedFromOppDeck", "Hráč zahodil ze soupeřova balíku: %s"),
                logChose             = str("logChose",             "Hráč si vybral: %s"),
                logTookFromDiscard   = str("logTookFromDiscard",   "Hráč si vzal z odhazovacího balíčku: %s"),
                logCopiedFromDeck    = str("logCopiedFromDeck",    "Hráč zkopíroval z balíčku: %s"),
                logDrewFromDeck      = str("logDrewFromDeck",      "Hráč vytáhl z balíčku: %s"),
                logChoseMine         = str("logChoseMine",         "Hráč si vybral důl: %s"),
                logJoker             = str("logJoker",             "Magický žolík: hráč si zvolil %s"),
                logStoleFromHand     = str("logStoleFromHand",     "Hráč ukradl ze soupeřovy ruky: %s"),
                logChoseResource     = str("logChoseResource",     "Hráč si vybral: %d× %s"),
                logPlayerFirst       = str("logPlayerFirst",       "Hráč začíná jako první!"),
                logAiFirst           = str("logAiFirst",           "AI začíná jako první!"),
                logNotEnough         = str("logNotEnough",         "Nedostatek %s pro: %s"),
                logConditionNotMet   = str("logConditionNotMet",   "%s: podmínka nesplněna!"),
                logReplication       = str("logReplication",       "Replikace: %d× %s zamícháno do balíčku"),
                logDiscardEffectTriggered = str("logDiscardEffectTriggered", "Zahozením karty %s se spustil efekt!"),
                logPlayerEndTurn     = str("logPlayerEndTurn",     "Hráč ukončil tah"),
                logPlayerSkip        = str("logPlayerSkip",        "Hráč přeskočil kolo"),
                logAiDiscardFromDeck = str("logAiDiscardFromDeck", "AI zahodila z tvého balíku: %s"),
                logAiJoker           = str("logAiJoker",           "AI zvolila žolíka: %s"),
                logAiStoleFromHand   = str("logAiStoleFromHand",   "AI ukradla z tvé ruky: %s"),
                logAiChoseResource   = str("logAiChoseResource",   "AI si vybrala %d× %s"),
                logAiWaited          = str("logAiWaited",          "AI čekala"),
                logBothPassedEmpty   = str("logBothPassedEmpty",   "Oba hráči pasovali s prázdnými balíčky – konec hry!"),
                logBothNoCards       = str("logBothNoCards",       "Obě strany bez karet – konec hry!"),
                logTrapDrewYou   = str("logTrapDrewYou",   "Ty jsi lízl"),
                logTrapDrewAi    = str("logTrapDrewAi",    "AI lízla"),
                logTrapCastle    = str("logTrapCastle",    "HRAD"),
                logTrapWall      = str("logTrapWall",      "ZEĎ"),
                logTrapHp        = str("logTrapHp",        "HP"),
                logTrapTriggered = str("logTrapTriggered", "pasca spuštěna"),
            )
        }

        /** Reads a field value from an [AppStrings] instance by key name via reflection-free lookup. */
        private fun fbVal(fb: AppStrings, key: String): String? = when (key) {
            "ok"      -> fb.ok;      "cancel"  -> fb.cancel
            "confirm" -> fb.confirm; "back"    -> fb.back
            "settings" -> fb.settings; "music" -> fb.music
            "soundEffects" -> fb.soundEffects; "languageLabel" -> fb.languageLabel
            "play" -> fb.play; "buildDeck" -> fb.buildDeck
            "multiplayer" -> fb.multiplayer; "profile" -> fb.profile
            "shop" -> fb.shop; "exit" -> fb.exit
            "ownDeck" -> fb.ownDeck; "superRandom" -> fb.superRandom
            "arena" -> fb.arena; "campaign" -> fb.campaign
            "yourTurn" -> fb.yourTurn; "opponentTurn" -> fb.opponentTurn
            "endTurn" -> fb.endTurn; "discard" -> fb.discard
            "endCombo" -> fb.endCombo; "endGame" -> fb.endGame
            "waitingTurn" -> fb.waitingTurn
            "inspectGame" -> fb.inspectGame
            "castle" -> fb.castle; "wall" -> fb.wall; "round" -> fb.round
            "decisionTitle" -> fb.decisionTitle
            "decisionPreviewGame" -> fb.decisionPreviewGame
            "decisionBackToDecision" -> fb.decisionBackToDecision
            "decisionChooseType" -> fb.decisionChooseType
            "decisionBurnOpponent" -> fb.decisionBurnOpponent
            "decisionFromDiscard" -> fb.decisionFromDiscard
            "decisionFromDeck" -> fb.decisionFromDeck
            "decisionMine" -> fb.decisionMine
            "resultVictory" -> fb.resultVictory; "resultDefeat" -> fb.resultDefeat
            "resultDraw" -> fb.resultDraw; "resultCastleBuilt" -> fb.resultCastleBuilt
            "resultCastleBuiltOpponent" -> fb.resultCastleBuiltOpponent
            "resultCastleDestroyed" -> fb.resultCastleDestroyed
            "resultCastleDestroyedSelf" -> fb.resultCastleDestroyedSelf
            "resultHpWins" -> fb.resultHpWins; "resultHpLose" -> fb.resultHpLose
            "resultHpWinsTurnLimit" -> fb.resultHpWinsTurnLimit
            "resultHpLoseTurnLimit" -> fb.resultHpLoseTurnLimit
            "resultBothDead" -> fb.resultBothDead
            "resultPlayAgain" -> fb.resultPlayAgain; "resultBackToMenu" -> fb.resultBackToMenu
            "deckBuilder" -> fb.deckBuilder; "deckSave" -> fb.deckSave
            "deckReset" -> fb.deckReset; "deckClear" -> fb.deckClear
            "deckCardCount" -> fb.deckCardCount
            "arenaDraft" -> fb.arenaDraft; "arenaPickCard" -> fb.arenaPickCard
            "arenaWins" -> fb.arenaWins
            "mulliganTitle" -> fb.mulliganTitle; "mulliganSubtitle" -> fb.mulliganSubtitle
            "mulliganConfirm" -> fb.mulliganConfirm
            "mulliganYouFirst" -> fb.mulliganYouFirst
            "mulliganOpponentFirst" -> fb.mulliganOpponentFirst
            "mulliganWaitingOpponent" -> fb.mulliganWaitingOpponent
            "mulliganInstruction" -> fb.mulliganInstruction
            "mulliganSelected" -> fb.mulliganSelected
            "mulliganPlayNoSwap" -> fb.mulliganPlayNoSwap
            "mulliganSwap" -> fb.mulliganSwap
            "profileTitle" -> fb.profileTitle; "profileWins" -> fb.profileWins
            "profileLosses" -> fb.profileLosses; "profileGames" -> fb.profileGames
            "shopTitle" -> fb.shopTitle; "shopBuy" -> fb.shopBuy; "shopDust" -> fb.shopDust
            "onlineConnecting" -> fb.onlineConnecting
            "onlineWaiting" -> fb.onlineWaiting; "onlineDisconnected" -> fb.onlineDisconnected
            "rarityCommon" -> fb.rarityCommon; "rarityRare" -> fb.rarityRare
            "rarityEpic" -> fb.rarityEpic; "rarityLegendary" -> fb.rarityLegendary
            "typeAttack" -> fb.typeAttack; "typeBuild" -> fb.typeBuild
            "typeMagic" -> fb.typeMagic; "typeChaos" -> fb.typeChaos
            "typeMines" -> fb.typeMines; "typeDecision" -> fb.typeDecision
            "typeDraw" -> fb.typeDraw
            "resMagic" -> fb.resMagic; "resAttack" -> fb.resAttack
            "resStone" -> fb.resStone; "resChaos" -> fb.resChaos
            else -> null
        }
    }
}

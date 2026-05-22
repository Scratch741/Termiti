// ============================================================
// LanguagePack.kt
// ============================================================
package com.example.termiti

import org.json.JSONObject

/**
 * A loaded language pack: metadata + all UI strings.
 *
 * Parsed from assets/lang/<code>.json.
 * Any missing key falls back to the Czech [fallback] pack so the app
 * never crashes on an incomplete community translation.
 */
data class LanguagePack(
    val language: Language,
    val strings:  AppStrings
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
            return LanguagePack(language, strings)
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
                discard       = str("discard",       "Zahodit"),
                castle        = str("castle",        "Hrad"),
                wall          = str("wall",          "Hradby"),
                round         = str("round",         "Kolo"),

                decisionTitle           = str("decisionTitle",           "ROZHODNUTÍ"),
                decisionPreviewGame     = str("decisionPreviewGame",     "Náhled hry"),
                decisionBackToDecision  = str("decisionBackToDecision",  "Zpět na rozhodnutí"),
                decisionChooseType      = str("decisionChooseType",      "Vyber si kartu typu %s"),
                decisionBurnOpponent    = str("decisionBurnOpponent",    "Vyber kartu ze soupeřova balíku k zahození"),
                decisionFromDiscard     = str("decisionFromDiscard",     "Vyber si kartu z odhazovacího balíčku"),
                decisionFromDeck        = str("decisionFromDeck",        "Vyber si kartu ze svého balíčku"),
                decisionMine            = str("decisionMine",            "Vyber si důl: Magie, Útok, Kámen nebo Chaos"),

                resultVictory         = str("resultVictory",         "VÝHRA!"),
                resultDefeat          = str("resultDefeat",          "PROHRA"),
                resultDraw            = str("resultDraw",            "REMÍZA"),
                resultCastleBuilt     = str("resultCastleBuilt",     "Postavil jsi hrad!"),
                resultCastleDestroyed = str("resultCastleDestroyed", "Zničil jsi soupeřův hrad!"),
                resultHpWins          = str("resultHpWins",          "Vyšší hrad po 99 kolech!"),
                resultHpLose          = str("resultHpLose",          "Soupeř měl vyšší hrad."),
                resultBothDead        = str("resultBothDead",        "Oba hrady zničeny."),
                resultPlayAgain       = str("resultPlayAgain",       "Hrát znovu"),
                resultBackToMenu      = str("resultBackToMenu",      "Zpět do menu"),

                deckBuilder   = str("deckBuilder",   "STAVITEL BALÍČKU"),
                deckSave      = str("deckSave",      "Uložit"),
                deckReset     = str("deckReset",     "Resetovat"),
                deckClear     = str("deckClear",     "Vymazat"),
                deckCardCount = str("deckCardCount", "%d / %d karet"),

                arenaDraft    = str("arenaDraft",    "DRAFT ARÉNY"),
                arenaPickCard = str("arenaPickCard", "Vyber kartu"),
                arenaWins     = str("arenaWins",     "%d výher"),

                mulliganTitle    = str("mulliganTitle",    "MULLIGAN"),
                mulliganSubtitle = str("mulliganSubtitle", "Vyber karty k výměně"),
                mulliganConfirm  = str("mulliganConfirm",  "Potvrdit"),

                profileTitle  = str("profileTitle",  "PROFIL"),
                profileWins   = str("profileWins",   "Výhry"),
                profileLosses = str("profileLosses", "Prohry"),
                profileGames  = str("profileGames",  "Celkem her"),

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
            "resultCastleDestroyed" -> fb.resultCastleDestroyed
            "resultHpWins" -> fb.resultHpWins; "resultHpLose" -> fb.resultHpLose
            "resultBothDead" -> fb.resultBothDead
            "resultPlayAgain" -> fb.resultPlayAgain; "resultBackToMenu" -> fb.resultBackToMenu
            "deckBuilder" -> fb.deckBuilder; "deckSave" -> fb.deckSave
            "deckReset" -> fb.deckReset; "deckClear" -> fb.deckClear
            "deckCardCount" -> fb.deckCardCount
            "arenaDraft" -> fb.arenaDraft; "arenaPickCard" -> fb.arenaPickCard
            "arenaWins" -> fb.arenaWins
            "mulliganTitle" -> fb.mulliganTitle; "mulliganSubtitle" -> fb.mulliganSubtitle
            "mulliganConfirm" -> fb.mulliganConfirm
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
            else -> null
        }
    }
}

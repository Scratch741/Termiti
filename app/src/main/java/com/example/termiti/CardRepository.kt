// ============================================================
// CardRepository.kt
// ============================================================
package com.example.termiti

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Jediný zdroj pravdy o kartách pro Android klienta.
 *
 * Herní data (id, name, cost, costType, effects, rarity…) se načítají z
 * assets/cards.json, který je automaticky generován ze server/game/cards.js
 * Gradle taskem "syncCards" před každým buildem.
 *
 * Prezentační data specifická pro Android (art, zvuk, popis, biasy…) jsou
 * v [CardPresentation]. Karta bez záznamu v presentations funguje, ale
 * zobrazí se bez ilustrace a s prázdným popisem.
 *
 * Použití:
 *   CardRepository.init(context)   // jednou při startu aplikace
 *   CardRepository.allCards        // List<Card> kdekoli v kódu
 */
object CardRepository {

    private var _allCards: List<Card> = emptyList()
    val allCards: List<Card> get() = _allCards

    fun init(context: Context) {
        try {
            val json = context.assets.open("cards.json").bufferedReader().readText()
            _allCards = parseCards(JSONArray(json))
            Log.i("CardRepository", "Načteno ${_allCards.size} karet z cards.json")
        } catch (e: Exception) {
            Log.e("CardRepository", "Nepodařilo se načíst cards.json", e)
        }
    }

    // ── Parsování karet ──────────────────────────────────────────────────────

    private fun parseCards(arr: JSONArray): List<Card> = buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            runCatching { parseCard(obj) }
                .onSuccess { add(it) }
                .onFailure { Log.e("CardRepository", "Chyba při parsování karty ${obj.optString("id")}", it) }
        }
    }

    private fun parseCard(obj: JSONObject): Card {
        val id   = obj.getString("id")
        val pres = CardPresentation.presentations[id] ?: CardPres()
        return Card(
            id          = id,
            name        = obj.getString("name"),
            description = pres.description,
            cost        = obj.getInt("cost"),
            costType    = ResourceType.valueOf(obj.getString("costType")),
            rarity      = Rarity.valueOf(obj.getString("rarity")),
            effects     = parseEffects(obj.getJSONArray("effects")),
            isCombo     = obj.optBoolean("isCombo", false),
            isXCost     = obj.optBoolean("isXCost", false),
            artResId    = pres.artResId,
            type        = pres.type,
            artBiasX    = pres.artBiasX,
            artBiasY    = pres.artBiasY,
            artScale    = pres.artScale,
            isBasic         = pres.isBasic,
            sound           = pres.sound,
            soundResId      = pres.soundResId,
            isPlaceholder   = obj.optBoolean("isPlaceholder", false),
            nameAccusative  = pres.nameAccusative
        )
    }

    // ── Efekty ───────────────────────────────────────────────────────────────

    private fun parseEffects(arr: JSONArray): List<CardEffect> =
        (0 until arr.length()).map { parseEffect(arr.getJSONObject(it)) }

    private fun parseEffect(obj: JSONObject): CardEffect = when (val t = obj.getString("type")) {
        "AddResource"         -> CardEffect.AddResource(resType(obj), obj.getInt("amount"))
        "AddResourceDelayed"  -> CardEffect.AddResourceDelayed(resType(obj), obj.getInt("amount"), obj.optInt("turns", 1))
        "AddMine"             -> CardEffect.AddMine(resType(obj), obj.getInt("amount"))
        "BuildWall"           -> CardEffect.BuildWall(obj.getInt("amount"))
        "BuildCastle"         -> CardEffect.BuildCastle(obj.getInt("amount"))
        "AttackPlayer"        -> CardEffect.AttackPlayer(obj.getInt("amount"))
        "AttackWall"          -> CardEffect.AttackWall(obj.getInt("amount"))
        "AttackCastle"        -> CardEffect.AttackCastle(obj.getInt("amount"))
        "StealResource"       -> CardEffect.StealResource(resType(obj), obj.getInt("amount"))
        "DrainResource"       -> CardEffect.DrainResource(resType(obj), obj.getInt("amount"))
        "StealCastle"         -> CardEffect.StealCastle(obj.getInt("amount"))
        "DestroyMine"         -> CardEffect.DestroyMine(resType(obj), obj.optInt("amount", 1))
        "BlockMine"           -> CardEffect.BlockMine(resType(obj), obj.getInt("turns"))
        "StealCard"           -> CardEffect.StealCard(obj.optInt("count", 1))
        "BurnCard"            -> CardEffect.BurnCard(obj.optInt("count", 1))
        "AddCardsToDeck"      -> CardEffect.AddCardsToDeck(obj.getString("cardId"), obj.optInt("count", 1))
        "AddToOpponentDeck"   -> CardEffect.AddToOpponentDeck(obj.getString("cardId"), obj.optInt("count", 1))
        "TrapOnDraw"          -> CardEffect.TrapOnDraw(parseEffect(obj.getJSONObject("effect")))
        "DrawCard"            -> CardEffect.DrawCard(obj.optInt("count", 1))
        "DrawBoth"            -> CardEffect.DrawBoth(obj.optInt("count", 1))
        "CloneNextPlayed"     -> CardEffect.CloneNextPlayed(obj.optInt("count", 2))
        "XScaledAttackPlayer" -> CardEffect.XScaledAttackPlayer(obj.optInt("divisor", 2))
        "XScaledAttackCastle" -> CardEffect.XScaledAttackCastle(obj.optInt("divisor", 2))
        "XScaledBuildCastle"  -> CardEffect.XScaledBuildCastle(obj.optInt("divisor", 2))
        "XScaledDualResource" -> CardEffect.XScaledDualResource(
            typeA   = ResourceType.valueOf(obj.getString("typeA")),
            typeB   = ResourceType.valueOf(obj.getString("typeB")),
            divisor = obj.optInt("divisor", 2)
        )
        "ConditionalEffect"   -> CardEffect.ConditionalEffect(
            condition = parseCondition(obj.getJSONObject("condition")),
            effect    = parseEffect(obj.getJSONObject("effect"))
        )
        "SwapHands"           -> CardEffect.SwapHands
        "RandomizeHands"      -> CardEffect.RandomizeHands
        "GiveRandomCard"      -> CardEffect.GiveRandomCard(ResourceType.valueOf(obj.getString("costType")))
        "ModifyHandCost"      -> CardEffect.ModifyHandCost(
            delta           = obj.getInt("delta"),
            targetOpponent  = obj.optBoolean("targetOpponent", false)
        )
        "DrawPerCardPlayed"        -> CardEffect.DrawPerCardPlayed(
            cardType = obj.optString("cardType").takeIf { it.isNotEmpty() }
        )
        "GainResourcePerCardPlayed" -> CardEffect.GainResourcePerCardPlayed(
            type     = resType(obj),
            amount   = obj.getInt("amount"),
            cardType = obj.optString("cardType").takeIf { it.isNotEmpty() }
        )
        "GainCastlePerCardPlayed" -> CardEffect.GainCastlePerCardPlayed(
            amount   = obj.getInt("amount"),
            cardType = obj.optString("cardType").takeIf { it.isNotEmpty() }
        )
        "ShapeShift"              -> CardEffect.ShapeShift
        "ConvertMine"             -> CardEffect.ConvertMine(
            from = ResourceType.valueOf(obj.getString("from")),
            to   = ResourceType.valueOf(obj.getString("to"))
        )
        "DecisionBurnOpponent"  -> CardEffect.DecisionBurnOpponent(obj.optInt("picks", 4))
        "DecisionChooseType"    -> CardEffect.DecisionChooseType(
            obj.getString("cardType"),
            obj.optInt("picks", 4),
            obj.optInt("costReduction", 0)
        )
        "DecisionFromDiscard"   -> CardEffect.DecisionFromDiscard(obj.optInt("picks", 4))
        "DecisionFromDeck"      -> CardEffect.DecisionFromDeck(obj.optInt("picks", 4))
        "DecisionMine"          -> CardEffect.DecisionMine
        "SmartJoker"            -> CardEffect.SmartJoker
        "MomentumAttack"        -> CardEffect.MomentumAttack(
            base           = obj.getInt("base"),
            bonusPerAttack = obj.optInt("bonusPerAttack", obj.optInt("bonusPerConsecutiveAttack", 4))
        )
        "PeekAndStealHand"      -> CardEffect.PeekAndStealHand
        "DecisionChooseResource" -> {
            val optsArr = obj.getJSONArray("options")
            val opts = (0 until optsArr.length()).map { i ->
                val o = optsArr.getJSONObject(i)
                ResourceOption(ResourceType.valueOf(o.getString("resType")), o.getInt("amount"))
            }
            CardEffect.DecisionChooseResource(opts)
        }
        else -> throw IllegalArgumentException("Neznámý typ efektu: $t")
    }

    // ── Podmínky ─────────────────────────────────────────────────────────────

    private fun parseCondition(obj: JSONObject): Condition = when (val t = obj.getString("type")) {
        "ResourceAbove"           -> Condition.ResourceAbove(resType(obj), obj.getInt("threshold"))
        "WallAbove"               -> Condition.WallAbove(obj.getInt("threshold"))
        "WallBelow"               -> Condition.WallBelow(obj.getInt("threshold"))
        "CastleAbove"             -> Condition.CastleAbove(obj.getInt("threshold"))
        "CastleBelow"             -> Condition.CastleBelow(obj.getInt("threshold"))
        "ResourceMoreThanOpponent"-> Condition.ResourceMoreThanOpponent(resType(obj))
        "LastPlayedType"          -> Condition.LastPlayedType(obj.getString("cardType"))
        else -> throw IllegalArgumentException("Neznámý typ podmínky: $t")
    }

    // ── Pomocné ──────────────────────────────────────────────────────────────

    private fun resType(obj: JSONObject): ResourceType =
        ResourceType.valueOf(obj.getString("resType"))
}

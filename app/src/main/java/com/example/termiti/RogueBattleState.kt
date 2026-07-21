package com.example.termiti

import org.json.JSONArray
import org.json.JSONObject

// ============================================================
// RogueBattleState.kt
// Serializace rozehrané roguelike BITVY (GameState + příznaky), aby se
// dala obnovit přesně tam, kde skončila → quit uprostřed bitvy nedává
// výhodu (žádný free retry).
//
// Snapshoty se berou jen v ČISTÝCH bodech: začátek hráčova tahu (idle)
// nebo začátek tahu AI (po hráčově akci). V nich je pendingDecision null,
// combo/mulligan false → není třeba serializovat overlay stav.
//
// Karty se ukládají jako baseId+id+příznaky a rekonstruují z template;
// morph karty (Zrcadlo/Klon/Shapeshifter) se po obnově znovu odvodí
// transformačními funkcemi z lastPlayedCard.
// ============================================================

/** Rozehraná bitva připravená k obnově. */
data class RogueBattleSave(
    val turnPhase        : String,       // "PLAYER_IDLE" | "AI_TURN"
    val game             : GameState,
    val quickDrawUsed    : Boolean,
    val aiDrawsAtStart   : Boolean,      // parametry finishTurn (jen pro AI_TURN)
    val playerDrawsAtEnd : Boolean,
    val playerWaited     : Boolean,
    val lastCard         : Card?,
    val lastCardAction   : CardAction,
    val lastCardByPlayer : Boolean,
    val enemyName        : String,       // jen pro top bar (deck/staty jsou v game.aiState)
    val enemyAvatar      : String
)

object RogueBattleCodec {

    // ── Karta ──────────────────────────────────────────────────────────────
    private fun cardToJson(c: Card) = JSONObject().apply {
        put("b", c.baseId)
        put("i", c.id)
        if (c.isGenerated)      put("g", true)
        if (c.costModifier != 0) put("cm", c.costModifier)
        c.localizationId?.let { put("l", it) }
    }

    private fun cardFromJson(o: JSONObject, allCards: List<Card>): Card? {
        val base = allCards.find { it.baseId == o.getString("b") } ?: return null
        return base.copy(
            id             = o.getString("i"),
            isGenerated    = o.optBoolean("g", false),
            costModifier   = o.optInt("cm", 0),
            localizationId = if (o.has("l")) o.getString("l") else null
        )
    }

    private fun cardsToJson(list: List<Card>) = JSONArray().apply { list.forEach { put(cardToJson(it)) } }
    private fun cardsFromJson(arr: JSONArray?, all: List<Card>): MutableList<Card> {
        val out = mutableListOf<Card>()
        if (arr != null) for (i in 0 until arr.length()) cardFromJson(arr.getJSONObject(i), all)?.let { out.add(it) }
        return out
    }

    // ── Mapy surovin/dolů ──────────────────────────────────────────────────
    private fun resMapToJson(m: Map<ResourceType, Int>) = JSONObject().apply { m.forEach { (t, n) -> put(t.name, n) } }
    private fun resMapFromJson(o: JSONObject?): MutableMap<ResourceType, Int> {
        val out = mutableMapOf<ResourceType, Int>()
        o?.keys()?.forEach { k -> runCatching { ResourceType.valueOf(k) }.getOrNull()?.let { out[it] = o.getInt(k) } }
        return out
    }

    // ── PlayerState ────────────────────────────────────────────────────────
    private fun playerToJson(p: PlayerState) = JSONObject().apply {
        put("castle", p.castleHP)
        put("wall",   p.wallHP)
        put("res",    resMapToJson(p.resources))
        put("mines",  resMapToJson(p.mines))
        put("blocked", resMapToJson(p.mineBlockedTurns))
        put("pending", JSONArray().apply {
            p.pendingResources.forEach { put(JSONObject().put("t", it.type.name).put("a", it.amount).put("tl", it.turnsLeft)) }
        })
        put("deck",    cardsToJson(p.deck))
        put("hand",    cardsToJson(p.hand))
        put("discard", cardsToJson(p.discardPile))
        p.lastPlayedType?.let { put("lastType", it) }
        p.drawCardOnPlay?.let { put("drawOnPlay", it) }
        p.cloneNextPlayed?.let { put("cloneNext", it) }
        if (p.attackCardsThisTurn != 0) put("attackCards", p.attackCardsThisTurn)
        if (p.nextCardIsCombo) put("nextCombo", true)
        put("grp", JSONArray().apply {
            p.gainResourcePerCardPlayed.forEach { e ->
                put(JSONObject().put("t", e.type.name).put("a", e.amount).apply { e.cardType?.let { put("ct", it) } })
            }
        })
        put("gcp", JSONArray().apply {
            p.gainCastlePerCardPlayed.forEach { e ->
                put(JSONObject().put("a", e.amount).apply { e.cardType?.let { put("ct", it) } })
            }
        })
        p.lastPlayedCard?.let { put("lastPlayed", cardToJson(it)) }
    }

    private fun playerFromJson(o: JSONObject, all: List<Card>): PlayerState {
        val ps = PlayerState(
            castleHP = o.getInt("castle"),
            wallHP   = o.getInt("wall"),
            resources        = resMapFromJson(o.optJSONObject("res")),
            mines            = resMapFromJson(o.optJSONObject("mines")),
            mineBlockedTurns = resMapFromJson(o.optJSONObject("blocked"))
        )
        o.optJSONArray("pending")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                runCatching { ResourceType.valueOf(e.getString("t")) }.getOrNull()?.let {
                    ps.pendingResources.add(PendingResource(it, e.getInt("a"), e.getInt("tl")))
                }
            }
        }
        ps.deck.addAll(cardsFromJson(o.optJSONArray("deck"), all))
        ps.hand.addAll(cardsFromJson(o.optJSONArray("hand"), all))
        ps.discardPile.addAll(cardsFromJson(o.optJSONArray("discard"), all))
        ps.lastPlayedType  = if (o.has("lastType")) o.getString("lastType") else null
        ps.drawCardOnPlay  = if (o.has("drawOnPlay")) o.getString("drawOnPlay") else null
        ps.cloneNextPlayed = if (o.has("cloneNext")) o.getInt("cloneNext") else null
        ps.attackCardsThisTurn = o.optInt("attackCards", 0)
        ps.nextCardIsCombo = o.optBoolean("nextCombo", false)
        o.optJSONArray("grp")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                runCatching { ResourceType.valueOf(e.getString("t")) }.getOrNull()?.let {
                    ps.gainResourcePerCardPlayed.add(
                        CardEffect.GainResourcePerCardPlayed(it, e.getInt("a"), if (e.has("ct")) e.getString("ct") else null)
                    )
                }
            }
        }
        o.optJSONArray("gcp")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                ps.gainCastlePerCardPlayed.add(
                    CardEffect.GainCastlePerCardPlayed(e.getInt("a"), if (e.has("ct")) e.getString("ct") else null)
                )
            }
        }
        ps.lastPlayedCard = o.optJSONObject("lastPlayed")?.let { cardFromJson(it, all) }
        return ps
    }

    // ── GameState ──────────────────────────────────────────────────────────
    private fun gameToJson(g: GameState) = JSONObject().apply {
        put("turn",   g.currentTurn)
        put("active", g.activePlayer.name)
        put("pwt",    g.playerWinTarget)
        put("awt",    g.aiWinTarget)
        put("pmh",    g.playerMaxHand)
        put("amh",    g.aiMaxHand)
        put("player", playerToJson(g.playerState))
        put("ai",     playerToJson(g.aiState))
    }

    private fun gameFromJson(o: JSONObject, all: List<Card>) = GameState(
        playerState     = playerFromJson(o.getJSONObject("player"), all),
        aiState         = playerFromJson(o.getJSONObject("ai"), all),
        currentTurn     = o.getInt("turn"),
        activePlayer    = runCatching { ActivePlayer.valueOf(o.getString("active")) }.getOrDefault(ActivePlayer.PLAYER),
        playerWinTarget = o.getInt("pwt"),
        aiWinTarget     = o.getInt("awt"),
        playerMaxHand   = o.getInt("pmh"),
        aiMaxHand       = o.getInt("amh")
    )

    // ── Celá bitva ─────────────────────────────────────────────────────────
    fun toJson(save: RogueBattleSave): String = JSONObject().apply {
        put("turnPhase",      save.turnPhase)
        put("quickDraw",      save.quickDrawUsed)
        put("aiDraws",        save.aiDrawsAtStart)
        put("playerDraws",    save.playerDrawsAtEnd)
        put("playerWaited",   save.playerWaited)
        put("game",           gameToJson(save.game))
        save.lastCard?.let { put("lastCard", cardToJson(it)) }
        put("lastCardAction", save.lastCardAction.name)
        put("lastCardByPlayer", save.lastCardByPlayer)
        put("enemyName",      save.enemyName)
        put("enemyAvatar",    save.enemyAvatar)
    }.toString()

    fun fromJson(raw: String, all: List<Card>): RogueBattleSave? = runCatching {
        val o = JSONObject(raw)
        RogueBattleSave(
            turnPhase        = o.getString("turnPhase"),
            game             = gameFromJson(o.getJSONObject("game"), all),
            quickDrawUsed    = o.optBoolean("quickDraw", false),
            aiDrawsAtStart   = o.optBoolean("aiDraws", true),
            playerDrawsAtEnd = o.optBoolean("playerDraws", true),
            playerWaited     = o.optBoolean("playerWaited", false),
            lastCard         = o.optJSONObject("lastCard")?.let { cardFromJson(it, all) },
            lastCardAction   = runCatching { CardAction.valueOf(o.getString("lastCardAction")) }.getOrDefault(CardAction.PLAYED),
            lastCardByPlayer = o.optBoolean("lastCardByPlayer", true),
            enemyName        = o.optString("enemyName", "Soupeř"),
            enemyAvatar      = o.optString("enemyAvatar", "enemy_icon_1")
        )
    }.getOrNull()
}

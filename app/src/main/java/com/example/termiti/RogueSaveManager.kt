package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Perzistence rozehraného roguelike runu na disk.
 *
 * ViewModel přežije jen změnu konfigurace (rotace), ne zabití procesu
 * (uspání telefonu → OS uvolní paměť). Tento manažer proto ukládá POSTUP
 * runu (balíček, HP, hradby, index bitvy, odměny, fáze) do SharedPreferences.
 *
 * Ukládá se jen ve fázích BATTLE/REWARD (run běží). DRAFT (nezačato) a
 * ENDED (skončeno) save mažou.
 *
 * Rozehraný stav JEDNÉ bitvy (GameState) se NEukládá – při obnově se aktuální
 * bitva restartuje od začátku proti soupeři stejného stupně. Run zůstává.
 */
object RogueSaveManager {
    private const val PREFS      = "termiti_rogue"
    private const val KEY        = "run_json"
    private const val KEY_BATTLE = "battle_json"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun hasSave(): Boolean = prefs?.contains(KEY) == true

    fun clear() { prefs?.edit()?.remove(KEY)?.remove(KEY_BATTLE)?.apply() }

    // ── Rozehraná bitva (přesný stav pro férovou obnovu) ──────────────────────
    fun saveBattle(json: String) { prefs?.edit()?.putString(KEY_BATTLE, json)?.apply() }
    fun clearBattle() { prefs?.edit()?.remove(KEY_BATTLE)?.apply() }
    fun loadBattle(all: List<Card>): RogueBattleSave? {
        val raw = prefs?.getString(KEY_BATTLE, null) ?: return null
        return RogueBattleCodec.fromJson(raw, all)
    }

    fun save(run: RogueRun, phase: RoguePhase) {
        val p = prefs ?: return
        if (phase != RoguePhase.BATTLE && phase != RoguePhase.REWARD) { clear(); return }
        val obj = JSONObject().apply {
            put("phase",       phase.name)
            put("maxCastle",   run.maxCastle)
            put("hp",          run.hp)
            put("wall",        run.wall)
            put("battleIndex", run.battleIndex)
            put("deck",        JSONArray(run.deck.map { it.baseId }))
            put("rewards",     JSONArray(run.rewardCards.map { it.baseId }))
            put("rewardCardPicksLeft",  run.rewardCardPicksLeft)
            put("rewardBonusAvailable", run.rewardBonusAvailable)
            put("rerollsLeft",          run.rerollsLeft)
            put("mines",       JSONObject().apply { run.bonusMines.forEach { (t, n) -> put(t.name, n) } })
        }
        p.edit().putString(KEY, obj.toString()).apply()
    }

    data class Saved(val run: RogueRun, val phase: RoguePhase)

    /** Načte uložený run; rebuild karet z [allCards] podle id. Null = nic uloženého. */
    fun load(allCards: List<Card>): Saved? {
        val raw = prefs?.getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            fun cards(key: String): List<Card> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { i ->
                    allCards.find { it.id == arr.getString(i) }
                }
            }
            val mines = mutableMapOf<ResourceType, Int>()
            o.optJSONObject("mines")?.let { m ->
                m.keys().forEach { k ->
                    runCatching { ResourceType.valueOf(k) }.getOrNull()?.let { mines[it] = m.getInt(k) }
                }
            }
            val run = RogueRun(
                deck        = cards("deck"),
                maxCastle   = o.getInt("maxCastle"),
                hp          = o.getInt("hp"),
                wall        = o.getInt("wall"),
                bonusMines  = mines,
                battleIndex = o.getInt("battleIndex"),
                enemy       = null,
                rewardCards = cards("rewards"),
                rewardCardPicksLeft  = o.optInt("rewardCardPicksLeft", 0),
                rewardBonusAvailable = o.optBoolean("rewardBonusAvailable", false),
                rerollsLeft = o.optInt("rerollsLeft", RogueConfig.REROLLS_PER_RUN)
            )
            Saved(run, RoguePhase.valueOf(o.getString("phase")))
        }.getOrNull()
    }
}

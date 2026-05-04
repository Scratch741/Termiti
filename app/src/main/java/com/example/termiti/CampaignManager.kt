package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Správa postupu hráče v kampani – kdo je poražen, které odměny byly vyzvednuty.
 * Singleton; inicializuj přes [init] v MainActivity.
 */
object CampaignManager {

    private const val PREFS_NAME    = "termiti_campaign"
    private const val KEY_DEFEATED  = "defeated_opponents"   // JSONArray stringů
    private const val KEY_REWARDED  = "rewarded_opponents"   // JSONArray stringů

    private var prefs: SharedPreferences? = null

    /** Množina ID soupeřů, které hráč již porazil. */
    private val _defeated  = mutableSetOf<String>()
    /** Množina ID soupeřů, jejichž odměna byla již vyplacena. */
    private val _rewarded  = mutableSetOf<String>()

    // ── Inicializace ─────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    // ── Dotazy ───────────────────────────────────────────────────────────────

    fun isDefeated(opponentId: String): Boolean = opponentId in _defeated
    fun isRewarded(opponentId: String): Boolean = opponentId in _rewarded

    /**
     * Vrátí true pokud jsou všichni soupeři v lokaci poraženi.
     */
    fun isLocationCleared(location: CampaignLocation): Boolean =
        location.opponents.all { isDefeated(it.id) }

    /**
     * Vrátí true pokud je soupeř přístupný (hráčelný).
     * Pravidlo: první soupeř v lokaci vždy dostupný;
     * ostatní pouze pokud byl poražen předchozí.
     */
    fun isUnlocked(location: CampaignLocation, opponent: CampaignOpponent): Boolean {
        val idx = location.opponents.indexOf(opponent)
        if (idx <= 0) return true
        return isDefeated(location.opponents[idx - 1].id)
    }

    /**
     * Vrátí true pokud je daná lokace odemčena (přístupná).
     * První lokace vždy dostupná; ostatní pouze pokud předchozí lokace je
     * zcela vyčištěna (boss poražen).
     */
    fun isLocationUnlocked(location: CampaignLocation): Boolean {
        val idx = CampaignData.locations.indexOf(location)
        if (idx <= 0) return true
        return isLocationCleared(CampaignData.locations[idx - 1])
    }

    // ── Aktualizace ──────────────────────────────────────────────────────────

    /** Označí soupeře jako poraženého. */
    fun markDefeated(opponentId: String) {
        _defeated.add(opponentId)
        save()
    }

    /**
     * Vyplatí odměnu za první poražení soupeře (neopakovatelné).
     * Vrátí true pokud odměna nebyla dosud vyplacena a byla nyní vyplacena.
     */
    fun claimReward(opponent: CampaignOpponent): Boolean {
        if (isRewarded(opponent.id)) return false
        _rewarded.add(opponent.id)
        save()
        PlayerProfileManager.addRewards(
            xp   = opponent.rewardXp,
            gold = opponent.rewardGold,
            gems = opponent.rewardGems
        )
        return true
    }

    // ── Persistance ──────────────────────────────────────────────────────────

    private fun save() {
        val defeatedArr = JSONArray().also { arr -> _defeated.forEach { arr.put(it) } }
        val rewardedArr = JSONArray().also { arr -> _rewarded.forEach { arr.put(it) } }
        prefs?.edit()
            ?.putString(KEY_DEFEATED, defeatedArr.toString())
            ?.putString(KEY_REWARDED, rewardedArr.toString())
            ?.apply()
    }

    private fun load() {
        _defeated.clear()
        _rewarded.clear()
        prefs?.getString(KEY_DEFEATED, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) _defeated.add(arr.getString(i))
            }
        }
        prefs?.getString(KEY_REWARDED, null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) _rewarded.add(arr.getString(i))
            }
        }
    }
}

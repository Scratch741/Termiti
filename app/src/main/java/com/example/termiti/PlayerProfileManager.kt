package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Správa hráčského profilu – čtení, ukládání, odměňování.
 * Singleton; inicializuj přes [init] v MainActivity.
 */
object PlayerProfileManager {

    private const val PREFS_NAME = "termiti_profile"
    private const val KEY_PROFILE = "profile_json"

    private var prefs: SharedPreferences? = null
    private var _profile: PlayerProfile? = null

    // ── Inicializace ─────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _profile = loadFromPrefs()
    }

    /** True pokud ještě nebyl vytvořen žádný profil (první spuštění). */
    fun isFirstLaunch(): Boolean = _profile == null

    /** Aktuální profil – null před [init] nebo při prvním spuštění. */
    val profile: PlayerProfile? get() = _profile

    // ── Vytvoření / uložení ──────────────────────────────────────────────────

    /** Vytvoří nový profil se zadaným jménem a uloží ho. */
    fun createProfile(name: String) {
        save(PlayerProfile(name = name.trim(), gold = 300))
    }

    fun save(profile: PlayerProfile) {
        _profile = profile
        prefs?.edit()?.putString(KEY_PROFILE, toJson(profile))?.apply()
    }

    // ── Odměny ───────────────────────────────────────────────────────────────

    /**
     * Přidá XP, zlato a drahokamy.
     * Automaticky provede level-up (i vícenásobný).
     * Vrátí aktualizovaný profil.
     */
    fun addRewards(xp: Int, gold: Int, gems: Int): PlayerProfile {
        var p = _profile ?: return PlayerProfile("?")
        p = p.copy(gold = p.gold + gold, gems = p.gems + gems, totalGames = p.totalGames)
        p = applyXp(p, xp)
        save(p)
        return p
    }

    /**
     * Zaregistruje výsledek hry a přidá odpovídající odměny.
     * @param campaign true pro kampaňové hry – sledují se statistiky a questy,
     *                 ale žádná obecná herní odměna (XP/zlato) se nedává;
     *                 kampaň má vlastní systém odměn přes CampaignManager.claimReward.
     */
    fun recordGameResult(win: Boolean, online: Boolean, campaign: Boolean = false): ProfileReward {
        // Kampaňové hry nemají obecnou herní odměnu – ta je řešena přes claimReward
        val (xp, gold, gems) = if (campaign) Triple(0, 0, 0)
                               else PlayerProfile.rewardForResult(win, online)
        val before = _profile ?: PlayerProfile("?")
        val levelBefore = before.level
        var p = before.copy(
            winsOffline = before.winsOffline + if (win && !online) 1 else 0,
            winsOnline  = before.winsOnline  + if (win && online)  1 else 0,
            totalGames  = before.totalGames  + 1,
            gold        = before.gold  + gold,
            gems        = before.gems  + gems
        )
        p = applyXp(p, xp)
        save(p)
        // Notifikace za odměnu z hry (level-up toast se posílá zvlášť z applyXp)
        if (!campaign && (xp > 0 || gold > 0)) {
            RewardNotifier.emit(RewardNotifier.RewardEvent(
                xp     = xp,
                gold   = gold,
                gems   = gems,
                source = if (win) (if (online) "🌐 Online výhra" else "⚔️ Výhra") else "💀 Prohra"
            ))
        }
        // Quest tracking
        if (win) QuestManager.onWin(online)
        return ProfileReward(
            xpGained    = xp,
            goldGained  = gold,
            gemsGained  = gems,
            leveledUp   = p.level > levelBefore,
            newLevel    = p.level
        )
    }

    // ── Interní ──────────────────────────────────────────────────────────────

    private fun applyXp(p: PlayerProfile, xp: Int): PlayerProfile {
        var current   = p
        var remaining = current.xp + xp
        var goldBonus = 0
        var gemsBonus = 0
        var lastLevel = current.level
        while (remaining >= current.xpNeeded()) {
            remaining -= current.xpNeeded()
            current    = current.copy(level = current.level + 1)
            lastLevel  = current.level
            goldBonus += 50 + current.level * 5          // 50+5×level zlatých za level
            if (current.level % 5 == 0) gemsBonus += 3  // +3 drahokamy každý 5. level
        }
        if (goldBonus > 0 || gemsBonus > 0) {
            current = current.copy(
                gold = current.gold + goldBonus,
                gems = current.gems + gemsBonus
            )
            RewardNotifier.emit(RewardNotifier.RewardEvent(
                gold     = goldBonus,
                gems     = gemsBonus,
                levelUp  = true,
                newLevel = lastLevel,
                source   = "🆙 Level $lastLevel!"
            ))
        }
        return current.copy(xp = remaining)
    }

    // ── Viděné karty (new badge) ─────────────────────────────────────────────

    /**
     * Označí karty jako viděné (odstraní badge "NOVÉ").
     * Volá se při prvním kliknutí na kartu v deck builderu.
     */
    fun markCardsSeen(ids: Set<String>) {
        val p = _profile ?: return
        if (ids.isEmpty()) return
        save(p.copy(seenCards = p.seenCards + ids))
    }

    // ── Pasivní schopnosti ───────────────────────────────────────────────────

    /**
     * Koupí schopnost za zlato. Vrátí true při úspěchu, false pokud
     * nemá dost zlata, nemá level nebo ji již vlastní.
     */
    fun buyAbility(abilityId: String): Boolean {
        val p = _profile ?: return false
        val ability = PassiveAbility.fromId(abilityId) ?: return false
        if (ability.id in p.unlockedAbilities) return false
        if (p.level < ability.unlockLevel) return false
        if (p.gold < ability.goldCost) return false
        save(p.copy(
            gold               = p.gold - ability.goldCost,
            unlockedAbilities  = p.unlockedAbilities + ability.id
        ))
        return true
    }

    /**
     * Nastaví seznam aktivních schopností (max [PassiveAbility.MAX_ACTIVE]).
     * Lze předat prázdný seznam pro deaktivaci všech.
     */
    fun setActiveAbilities(ids: List<String>) {
        val p = _profile ?: return
        val valid = ids.filter { it in p.unlockedAbilities }
            .take(PassiveAbility.MAX_ACTIVE)
        save(p.copy(activeAbilities = valid))
    }

    // ── Serializace ──────────────────────────────────────────────────────────

    private fun toJson(p: PlayerProfile): String {
        val unlockedArr = org.json.JSONArray().also { arr ->
            p.unlockedAbilities.forEach { arr.put(it) }
        }
        val activeArr = org.json.JSONArray().also { arr ->
            p.activeAbilities.forEach { arr.put(it) }
        }
        val seenArr = org.json.JSONArray().also { arr ->
            p.seenCards.forEach { arr.put(it) }
        }
        val collectionObj = org.json.JSONObject().also { obj ->
            p.cardCollection.forEach { (id, count) -> obj.put(id, count) }
        }
        return JSONObject().apply {
            put("name",              p.name)
            put("avatar",            p.avatar)
            put("level",             p.level)
            put("xp",                p.xp)
            put("gold",              p.gold)
            put("gems",              p.gems)
            put("winsOffline",       p.winsOffline)
            put("winsOnline",        p.winsOnline)
            put("totalGames",        p.totalGames)
            put("unlockedAbilities", unlockedArr)
            put("activeAbilities",   activeArr)
            put("cardCollection",    collectionObj)
            put("dust",              p.dust)
            put("seenCards",         seenArr)
            put("allCardsUnlocked",  p.allCardsUnlocked)
        }.toString()
    }

    private fun loadFromPrefs(): PlayerProfile? {
        val json = prefs?.getString(KEY_PROFILE, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            fun JSONObject.getStringSet(key: String): Set<String> {
                val arr = optJSONArray(key) ?: return emptySet()
                return (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
            fun JSONObject.getStringList(key: String): List<String> {
                val arr = optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.getString(it) }
            }
            val collectionMap: Map<String, Int> = run {
                val obj = o.optJSONObject("cardCollection") ?: return@run emptyMap()
                buildMap { obj.keys().forEach { key -> put(key, obj.optInt(key, 0)) } }
            }
            PlayerProfile(
                name               = o.optString("name", "Hráč"),
                avatar             = o.optString("avatar", "⚔️"),
                level              = o.optInt("level", 1),
                xp                 = o.optInt("xp", 0),
                gold               = o.optInt("gold", 0),
                gems               = o.optInt("gems", 0),
                winsOffline        = o.optInt("winsOffline", 0),
                winsOnline         = o.optInt("winsOnline", 0),
                totalGames         = o.optInt("totalGames", 0),
                unlockedAbilities  = o.getStringSet("unlockedAbilities"),
                activeAbilities    = o.getStringList("activeAbilities"),
                cardCollection     = collectionMap,
                dust               = o.optInt("dust", 0),
                seenCards          = o.getStringSet("seenCards"),
                allCardsUnlocked   = o.optBoolean("allCardsUnlocked", true)
            )
        }.getOrNull()
    }
}

/** Výsledek přidání odměn – pro zobrazení hráči po hře. */
data class ProfileReward(
    val xpGained: Int,
    val goldGained: Int,
    val gemsGained: Int,
    val leveledUp: Boolean,
    val newLevel: Int
)

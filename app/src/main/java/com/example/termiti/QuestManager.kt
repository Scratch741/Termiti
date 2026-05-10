package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Singleton spravující denní questy.
 * Inicializuj přes [init] v MainActivity (stejně jako PlayerProfileManager).
 */
object QuestManager {

    private const val PREFS_NAME     = "termiti_quests"
    private const val KEY_DATE       = "quest_date"
    private const val KEY_QUESTS     = "quests_json"
    private const val KEY_REROLLED   = "rerolled_today"

    private var prefs: SharedPreferences? = null
    private val _quests = mutableListOf<DailyQuest>()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val quests: List<DailyQuest> get() = _quests.toList()
    var rerolledToday: Boolean = false
        private set
    fun canReroll(): Boolean = !rerolledToday

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refreshIfNeeded()
    }

    // ── Denní reset ───────────────────────────────────────────────────────────

    private fun today(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun refreshIfNeeded() {
        val stored = prefs?.getString(KEY_DATE, "") ?: ""
        if (stored != today()) {
            rerolledToday = false
            generateQuests()
            save()
        } else {
            load()
        }
    }

    // ── Generování questů ─────────────────────────────────────────────────────

    /**
     * Vygeneruje 3 questy z QUEST_POOL — bez opakování stejného typu.
     * Náhodně zamíchá pool a vybere první 3 unikátní typy.
     */
    private fun generateQuests() {
        _quests.clear()
        val usedTypes = mutableSetOf<QuestType>()
        for (template in QUEST_POOL.shuffled()) {
            if (template.type in usedTypes) continue
            _quests.add(template.copy(id = UUID.randomUUID().toString()))
            usedTypes.add(template.type)
            if (_quests.size == 3) break
        }
    }

    // ── Reroll ────────────────────────────────────────────────────────────────

    /** Přeroluje jeden nedokončený quest. Max 1× za den. Vrátí true při úspěchu. */
    fun reroll(questId: String): Boolean {
        if (rerolledToday) return false
        val idx = _quests.indexOfFirst { it.id == questId && !it.completed }
        if (idx < 0) return false

        val usedTypes = _quests.mapIndexed { i, q -> if (i != idx) q.type else null }
            .filterNotNull().toSet()
        val replacement = QUEST_POOL.filter { it.type !in usedTypes }.shuffled().firstOrNull()
            ?: return false

        _quests[idx] = replacement.copy(id = UUID.randomUUID().toString())
        rerolledToday = true
        save()
        return true
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    /** Volej po každé výhře. [online] = true pro online hry. */
    fun onWin(online: Boolean) {
        updateProgress(QuestType.WIN_GAMES, 1)
        if (online) updateProgress(QuestType.WIN_ONLINE, 1)
        save()
    }

    /** Volej pokaždé, když hráč zahraje kartu. */
    fun onCardPlayed() {
        updateProgress(QuestType.PLAY_CARDS, 1)
        save()
    }

    /** Volej s množstvím poškození, které hráč způsobil nepřátelskému hradu. */
    fun onDamageDealt(amount: Int) {
        if (amount <= 0) return
        updateProgress(QuestType.DEAL_DAMAGE, amount)
        save()
    }

    /** Volej po každé výhře v kampani. */
    fun onCampaignWin() {
        updateProgress(QuestType.WIN_CAMPAIGN, 1)
        save()
    }

    // ── Claim odměny ─────────────────────────────────────────────────────────

    /** Vyplatí odměnu za dokončený quest. Vrátí quest při úspěchu, null jinak. */
    fun claimQuest(questId: String): DailyQuest? {
        val idx = _quests.indexOfFirst { it.id == questId && it.canClaim }
        if (idx < 0) return null
        val quest = _quests[idx]
        _quests[idx] = quest.copy(claimed = true)
        PlayerProfileManager.addRewards(
            xp   = quest.rewardXp,
            gold = quest.rewardGold,
            gems = quest.rewardGems
        )
        RewardNotifier.emit(RewardNotifier.RewardEvent(
            xp     = quest.rewardXp,
            gold   = quest.rewardGold,
            gems   = quest.rewardGems,
            source = "🎯 ${quest.label()}"
        ))
        save()
        return quest
    }

    // ── Interní ───────────────────────────────────────────────────────────────

    private fun updateProgress(type: QuestType, amount: Int) {
        for (i in _quests.indices) {
            val q = _quests[i]
            if (q.type == type && !q.claimed) {
                val wasCompleted = q.completed
                val updated = q.copy(progress = (q.progress + amount).coerceAtMost(q.target))
                _quests[i] = updated
                // Notifikace: quest právě dokončen → připomínka na vyzvednutí
                if (!wasCompleted && updated.completed) {
                    RewardNotifier.emit(RewardNotifier.RewardEvent(
                        source = "🎯 Quest splněn – převzít odměnu!"
                    ))
                }
            }
        }
    }

    // ── Persistence (kotlinx.serialization) ─────────────────────────────────

    private val listSerializer = ListSerializer(DailyQuest.serializer())

    private fun save() {
        prefs?.edit()
            ?.putString(KEY_DATE,      today())
            ?.putString(KEY_QUESTS,    json.encodeToString(listSerializer, _quests))
            ?.putBoolean(KEY_REROLLED, rerolledToday)
            ?.apply()
    }

    private fun load() {
        rerolledToday = prefs?.getBoolean(KEY_REROLLED, false) ?: false
        val raw = prefs?.getString(KEY_QUESTS, null) ?: run { generateQuests(); return }
        _quests.clear()
        runCatching {
            _quests.addAll(json.decodeFromString(listSerializer, raw))
        }.onFailure { generateQuests() }
    }
}

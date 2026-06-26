package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.util.Calendar
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Singleton spravující denní questy.
 * Inicializuj přes [init] v MainActivity (stejně jako PlayerProfileManager).
 *
 * Anti-cheat strategie pro reset questů:
 *  1. Priorita: GET /time ze serveru → autoritativní čas, nejde podvést lokálně.
 *  2. Fallback (offline): hybrid wall clock + SystemClock.elapsedRealtime().
 *     – elapsedRealtime() plyne od rebootu a nejde změnit nastavením data v telefonu.
 *     – Minimální interval mezi resety je MIN_RESET_INTERVAL_MS (21 hodin).
 *     – Pokud wall clock jde dozadu (manipulace), reset se zablokuje.
 */
object QuestManager {

    private const val PREFS_NAME     = "termiti_quests"
    private const val KEY_DATE       = "quest_date"
    private const val KEY_QUESTS     = "quests_json"
    private const val KEY_REROLLED   = "rerolled_today"
    // Anti-cheat klíče
    private const val KEY_RESET_WALL_MS    = "quest_reset_wall_ms"
    private const val KEY_RESET_ELAPSED_MS = "quest_reset_elapsed_ms"

    /** Minimální interval mezi resety questů: 21 hodin v ms. */
    private const val MIN_RESET_INTERVAL_MS = 21 * 60 * 60 * 1000L

    private const val SERVER_TIME_URL = "http://138.2.136.49:8765/time"
    private const val SERVER_TIMEOUT_MS = 4_000L   // max 4 s čekání na server

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

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
        // Okamžitě načti uložená data, aby UI mělo co zobrazit.
        load()
        // Anti-cheat kontrola na background threadu (může jít na server).
        // Výsledné změny questů se aplikují zpět na main thread přes Handler.
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread { checkAndResetOnBackground(mainHandler) }.start()
    }

    // ── Denní reset ───────────────────────────────────────────────────────────

    /**
     * Vrátí string dnešního data dle daného Unix timestamp v ms (nebo systémového času).
     */
    private fun dateStringOf(ms: Long): String {
        val c = Calendar.getInstance().also { it.timeInMillis = ms }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
    }

    /**
     * Pokusí se získat autoritativní čas ze serveru.
     * Vrátí Unix timestamp v ms nebo null při chybě/timeoutu.
     */
    private fun fetchServerTimeMs(): Long? = runCatching {
        val request  = Request.Builder().url(SERVER_TIME_URL).build()
        val body     = httpClient.newCall(request).execute().use { it.body?.string() } ?: return null
        val jsonObj  = org.json.JSONObject(body)
        jsonObj.getLong("serverTimeMs")
    }.getOrNull()

    /**
     * Běží na background threadu. Určí, jestli je třeba resetovat questy.
     * Pokud ano, zavolá reset na main threadu přes [mainHandler].
     */
    private fun checkAndResetOnBackground(mainHandler: android.os.Handler) {
        val nowWall    = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        val storedDate    = prefs?.getString(KEY_DATE,            "")  ?: ""
        val storedWall    = prefs?.getLong(KEY_RESET_WALL_MS,     0L)  ?: 0L
        val storedElapsed = prefs?.getLong(KEY_RESET_ELAPSED_MS,  0L)  ?: 0L

        // ── 1. Priorita: server čas ───────────────────────────────────────────
        val serverMs = fetchServerTimeMs()
        if (serverMs != null) {
            val serverDate = dateStringOf(serverMs)
            if (storedDate != serverDate) {
                // Server říká jiný den → legitimní reset
                mainHandler.post {
                    rerolledToday = false
                    generateQuests()
                    saveWithTime(serverMs, nowElapsed)
                }
            }
            // Pokud datum souhlasí, questy jsou už načteny přes load() v init – nic nedělej.
            return
        }

        // ── 2. Fallback: hybrid offline anti-cheat ────────────────────────────
        // a) Wall clock šel dozadu → nejspíš manipulace, neresettuj
        if (nowWall < storedWall) return

        // b) Zjisti čas uplynulý od posledního resetu.
        //    Pokud jsme ve stejné boot session (elapsed nešel dozadu), použij elapsed
        //    (nejde podvést změnou data). Po rebootu fallback na wall clock.
        val msSinceReset = if (nowElapsed >= storedElapsed) {
            nowElapsed - storedElapsed      // stejná boot session – spolehlivý
        } else {
            nowWall - storedWall            // reboot – wall clock (méně bezpečný)
        }

        // c) Datum se shoduje nebo neuplynulo dost času → stávající questy platí
        if (storedDate == dateStringOf(nowWall) || msSinceReset < MIN_RESET_INTERVAL_MS) return

        // d) Všechny kontroly prošly → legitimní nový den, resetuj na main threadu
        mainHandler.post {
            rerolledToday = false
            generateQuests()
            saveWithTime(nowWall, nowElapsed)
        }
    }

    // ── Generování questů ─────────────────────────────────────────────────────

    /**
     * Vygeneruje 3 questy z QUEST_POOL — bez opakování stejného typu.
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

    /** Uloží questy + anti-cheat timestamp. */
    private fun saveWithTime(wallMs: Long, elapsedMs: Long) {
        prefs?.edit()
            ?.putString(KEY_DATE,             dateStringOf(wallMs))
            ?.putLong(KEY_RESET_WALL_MS,      wallMs)
            ?.putLong(KEY_RESET_ELAPSED_MS,   elapsedMs)
            ?.putString(KEY_QUESTS,           json.encodeToString(listSerializer, _quests))
            ?.putBoolean(KEY_REROLLED,        rerolledToday)
            ?.apply()
    }

    /** Uloží při průběžném update (progress, claim, reroll) bez změny reset timestampu. */
    private fun save() {
        prefs?.edit()
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

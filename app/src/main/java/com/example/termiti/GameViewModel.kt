package com.example.termiti

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Výsledek migrace limitů karet (zobrazí se hráči po prvním spuštění nové verze). */
/**
 * Jedna karta dotčená změnou limitu kopií (rarita karty se posunula, nebo se
 * změnila tabulka [Rarity.maxCopies]).
 */
data class CardLimitChange(
    val cardId        : String,
    /** Kopií rozebráno z kolekce na prach. */
    val fromCollection: Int,
    /** Kopií odebráno z uložených balíčků. */
    val fromDecks     : Int,
    val dustGained    : Int
)

/**
 * Souhrn poslední srovnávací akce limitů kopií. Přežívá restart appky
 * (ukládá se do prefs) a čeká, až ho hráč uvidí v deck builderu — jinak by o
 * rozebrané karty přišel bez jediného slova.
 */
data class CardLimitReport(val changes: List<CardLimitChange>) {
    val dustGained            : Int get() = changes.sumOf { it.dustGained }
    val deckCardsRemoved      : Int get() = changes.sumOf { it.fromDecks }
    val collectionCardsRemoved: Int get() = changes.sumOf { it.fromCollection }
}

// ── Rozhodnutí ────────────────────────────────────────────────────────────────
/** Jednorázová volba suroviny v DecisionChooseResource (zobrazí se jako tlačítko, ne jako karta). */
data class ResourceChoice(val type: ResourceType, val amount: Int)

data class DecisionState(
    val title           : String,
    val subtitle        : String,
    val options         : List<Card>,
    val resourceChoices : List<ResourceChoice> = emptyList()
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    /** Zachytí výjimky z viewModelScope.launch, které by jinak zmizely tiše. */
    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("TERMITI_CRASH", "Nezachycená výjimka v korutině!", throwable)
        // Zobrazit v logu celý stack trace – čitelné v Logcat filtrem "TERMITI_CRASH"
        throwable.printStackTrace()
    }

    private val prefs get() = getApplication<Application>()
        .getSharedPreferences("termiti_decks", Context.MODE_PRIVATE)

    // Útočné karty  → platí ATTACK
    // Stavební karty → platí STONES
    // Ostatní (zdroje, doly) → platí MAGIC
    // Katalog všech dostupných karet
    val allCards get() = CardRepository.allCards

    // ── Lokalizace log hlášek ──────────────────────────────────────────────────
    /** Aktivní jazykový balíček – zkratka pro stavbu lokalizovaných log hlášek. */
    private val ls get() = LanguageManager.currentStrings

    /** Lokalizovaný název zdroje pro log hlášky. */
    private fun resLabel(t: ResourceType): String = when (t) {
        ResourceType.MAGIC  -> ls.resMagic
        ResourceType.ATTACK -> ls.resAttack
        ResourceType.STONES -> ls.resStone
        ResourceType.CHAOS  -> ls.resChaos
    }

    // ── Deck sloty ────────────────────────────────────────────────────────────
    val decks = androidx.compose.runtime.mutableStateListOf(
        Deck(0, "Balíček 1"),
        Deck(1, "Balíček 2"),
        Deck(2, "Balíček 3")
    )
    var activeDeckIndex = androidx.compose.runtime.mutableStateOf(0)
        private set

    // ── Uložené roguelike balíčky (šablony, obdoba decks[] z konstruovaného módu) ──
    // Musí být deklarováno PŘED init{} – ten volá loadRoguePresets(), a Kotlin
    // inicializuje vlastnosti v pořadí výskytu v souboru. Kdyby byl tento seznam
    // deklarovaný níž (jako předtím), byl by v okamžiku init{} ještě null → NPE.
    val roguePresets = androidx.compose.runtime.mutableStateListOf(
        RoguePreset("Balíček 1"), RoguePreset("Balíček 2"), RoguePreset("Balíček 3")
    )

    /**
     * Nezobrazené hlášení o srovnání limitů kopií – null = není co hlásit.
     * Čte ho deck builder ([DeckBuilderScreen]) a po odkliknutí zavolá
     * [dismissCardLimitReport].
     */
    val cardLimitReport = androidx.compose.runtime.mutableStateOf<CardLimitReport?>(null)

    init {
        loadDecks()
        loadRoguePresets()
        loadCardLimitReport()
        reconcileCardLimits()
    }

    // ── Srovnání limitů kopií ────────────────────────────────────────────────
    //
    // Limit kopií karty = Rarity.maxCopies. Když se posune (karta změní raritu,
    // nebo se přepíše tabulka v Rarity.kt), může hráč držet víc kopií, než je
    // nově povoleno — v kolekci i v uložených balíčcích.
    //
    // Dřív to řešil jednorázový příznak v prefs ("rarity_migration_v2"), který
    // se musel při KAŽDÉ další změně limitů ručně zvednout. Na to se dá snadno
    // zapomenout a hráči by zůstal nelegální balíček. Místo toho se drží otisk
    // (fingerprint) všech aktuálních limitů: jakmile se katalog v tomhle ohledu
    // liší od uloženého stavu, srovnání proběhne samo.

    private val LIMITS_FINGERPRINT_KEY = "card_limits_fingerprint"
    private val LIMITS_REPORT_KEY      = "card_limits_report"

    /**
     * Stabilní otisk limitů celého katalogu (id → maxCopies).
     * FNV-1a 64bit — na rozdíl od [String.hashCode] nezávisí na verzi JVM.
     */
    private fun currentLimitsFingerprint(): String {
        var hash = -3750763034362895579L          // FNV offset basis
        allCards.sortedBy { it.id }.forEach { card ->
            "${card.id}:${card.rarity.maxCopies};".forEach { ch ->
                hash = (hash xor ch.code.toLong()) * 1099511628211L
            }
        }
        return java.lang.Long.toHexString(hash)
    }

    /**
     * Porovná uložený otisk limitů s aktuálním a při rozdílu srovná kolekci
     * i balíčky. Výsledek se uloží pro pozdější zobrazení v deck builderu.
     *
     * Na čerstvé instalaci (a při prvním spuštění buildu s touhle kontrolou)
     * se jen zapíše výchozí otisk — hráč o nic nepřišel, není co hlásit.
     */
    private fun reconcileCardLimits() {
        if (allCards.isEmpty()) return            // katalog se nenačetl → nic neřež
        val fingerprint = currentLimitsFingerprint()
        val stored      = prefs.getString(LIMITS_FINGERPRINT_KEY, null)
        if (stored == fingerprint) return
        if (stored == null) {
            prefs.edit().putString(LIMITS_FINGERPRINT_KEY, fingerprint).apply()
            return
        }
        val changes = applyCardLimits()
        prefs.edit().putString(LIMITS_FINGERPRINT_KEY, fingerprint).apply()
        if (changes.isNotEmpty()) saveCardLimitReport(changes)
    }

    /**
     * Ořízne kolekci i balíčky na aktuální limity.
     *
     * Pořadí je podstatné: nejdřív kolekce (přebytek → prach), teprve pak
     * balíčky — ty se ořezávají na [CardCollectionManager.usableCopies], takže
     * odrážejí kolekci PO srovnání, ne před ním.
     */
    private fun applyCardLimits(): List<CardLimitChange> {
        val cardMap        = allCards.associateBy { it.id }
        val fromCollection = mutableMapOf<String, Int>()
        val dustPerCard    = mutableMapOf<String, Int>()

        // ── 1. Kolekce ───────────────────────────────────────────────────────
        PlayerProfileManager.profile?.let { profile ->
            var dustTotal = 0
            val newCollection = profile.cardCollection.mapValues { (id, count) ->
                val card   = cardMap[id] ?: return@mapValues count
                val limit  = card.rarity.maxCopies
                val excess = (count - limit).coerceAtLeast(0)
                // Základní karty se rozebrat nedají (jsou zdarma v plném počtu),
                // takže se uložený počet jen srovná — hráč nic neztrácí a do
                // hlášení nepatří.
                if (excess > 0 && !CardCollectionManager.isBasicCard(card)) {
                    fromCollection[id] = excess
                    val dust = excess * card.rarity.dustValue
                    dustPerCard[id] = dust
                    dustTotal += dust
                }
                count.coerceAtMost(limit)
            }.filter { it.value > 0 }

            if (newCollection != profile.cardCollection || dustTotal > 0) {
                PlayerProfileManager.save(
                    profile.copy(
                        cardCollection = newCollection,
                        dust           = profile.dust + dustTotal
                    )
                )
            }
        }

        // ── 2. Balíčky ───────────────────────────────────────────────────────
        // Bez profilu vrací usableCopies() 0 pro všechno → oříznout na skutečně
        // vlastněné kopie by vymazalo celý balíček. V tom případě drž jen tvrdý
        // limit rarity.
        val hasProfile = PlayerProfileManager.profile != null
        val fromDecks  = mutableMapOf<String, Int>()
        decks.forEachIndexed { i, deck ->
            val newCounts = deck.cardCounts.mapValues { (id, count) ->
                val card    = cardMap[id] ?: return@mapValues 0   // karta zmizela z katalogu
                val limit   = if (hasProfile) CardCollectionManager.usableCopies(card)
                              else card.rarity.maxCopies
                val clamped = count.coerceAtMost(limit)
                if (clamped < count) fromDecks[id] = (fromDecks[id] ?: 0) + (count - clamped)
                clamped
            }.filter { it.value > 0 }
            if (newCounts != deck.cardCounts) {
                decks[i] = deck.copy(cardCounts = newCounts)
                saveDeck(i)
            }
        }

        return (fromCollection.keys + fromDecks.keys).sorted().map { id ->
            CardLimitChange(
                cardId         = id,
                fromCollection = fromCollection[id] ?: 0,
                fromDecks      = fromDecks[id]      ?: 0,
                dustGained     = dustPerCard[id]    ?: 0
            )
        }
    }

    // ── Hlášení pro deck builder ─────────────────────────────────────────────
    // Formát v prefs: "id:kolekce:balicky:prach|id:kolekce:balicky:prach"

    /** Uloží hlášení; pokud nějaké čeká nezobrazené, sloučí je (nic se neztratí). */
    private fun saveCardLimitReport(changes: List<CardLimitChange>) {
        val merged = LinkedHashMap<String, CardLimitChange>()
        (cardLimitReport.value?.changes.orEmpty() + changes).forEach { c ->
            val prev = merged[c.cardId]
            merged[c.cardId] = if (prev == null) c else CardLimitChange(
                cardId         = c.cardId,
                fromCollection = prev.fromCollection + c.fromCollection,
                fromDecks      = prev.fromDecks      + c.fromDecks,
                dustGained     = prev.dustGained     + c.dustGained
            )
        }
        val report = CardLimitReport(merged.values.toList())
        cardLimitReport.value = report
        prefs.edit().putString(
            LIMITS_REPORT_KEY,
            report.changes.joinToString("|") {
                "${it.cardId}:${it.fromCollection}:${it.fromDecks}:${it.dustGained}"
            }
        ).apply()
    }

    private fun loadCardLimitReport() {
        val raw = prefs.getString(LIMITS_REPORT_KEY, "").orEmpty()
        if (raw.isBlank()) return
        val changes = raw.split("|").mapNotNull { entry ->
            val p = entry.split(":")
            if (p.size != 4) return@mapNotNull null
            CardLimitChange(
                cardId         = p[0],
                fromCollection = p[1].toIntOrNull() ?: 0,
                fromDecks      = p[2].toIntOrNull() ?: 0,
                dustGained     = p[3].toIntOrNull() ?: 0
            )
        }
        if (changes.isNotEmpty()) cardLimitReport.value = CardLimitReport(changes)
    }

    /** Hráč hlášení odklikl v deck builderu — zahoď ho i z prefs. */
    fun dismissCardLimitReport() {
        cardLimitReport.value = null
        prefs.edit().remove(LIMITS_REPORT_KEY).apply()
    }

    private fun saveDeck(index: Int) {
        val value = decks[index].cardCounts.entries
            .joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString("deck_$index", value).apply()
    }

    private fun loadDecks() {
        decks.forEachIndexed { i, deck ->
            val name = prefs.getString("deck_name_$i", deck.name) ?: deck.name
            val str  = prefs.getString("deck_$i", "") ?: ""
            val cardCounts = if (str.isNotEmpty()) {
                str.split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null)
                    else null
                }.toMap()
            } else emptyMap()
            decks[i] = deck.copy(name = name, cardCounts = cardCounts)
        }
        activeDeckIndex.value = prefs.getInt("active_deck", 0)
    }

    /** Nastaví balíček 0 jako předpřipravený startovní balíček pro nováčka. */
    fun grantStarterDeck() {
        val starterCounts = mapOf(
            "012" to 2,  // Mobilizace
            "065" to 1,  // Lupič
            "003" to 2,  // Ohnivá koule
            "D01" to 2,  // Průzkumník
            "013" to 2,  // Magický pramen
            "038" to 2,  // Vojenský rozkaz
            "015" to 2,  // Výcvikový tábor
            "D04" to 3,  // Bojová taktika
            "001" to 3,  // Rychlý útok
            "056" to 2,  // Nájezdník
            "047" to 3,  // Ogr
            "007" to 3,  // Silný úder
            "010" to 3,  // Palisáda
        )
        val deckName = "Začátečník"
        decks[0] = decks[0].copy(name = deckName, cardCounts = starterCounts)
        prefs.edit()
            .putString("deck_name_0", deckName)
            .putString("deck_0", starterCounts.entries.joinToString(";") { "${it.key}:${it.value}" })
            .apply()
    }

    fun renameDeck(index: Int, name: String) {
        val trimmed = name.trim().take(20).ifEmpty { "Balíček ${index + 1}" }
        decks[index] = decks[index].copy(name = trimmed)
        prefs.edit().putString("deck_name_$index", trimmed).apply()
    }

    fun setActiveDeck(index: Int) {
        activeDeckIndex.value = index
        prefs.edit().putInt("active_deck", index).apply()
    }

    fun setCardCount(deckIndex: Int, cardId: String, count: Int) {
        val deck = decks[deckIndex]
        val maxCopies = allCards.find { it.id == cardId }?.rarity?.maxCopies ?: 1
        val newCounts = deck.cardCounts.toMutableMap()
        if (count <= 0) newCounts.remove(cardId) else newCounts[cardId] = count.coerceAtMost(maxCopies)
        decks[deckIndex] = deck.copy(cardCounts = newCounts)
        saveDeck(deckIndex)
    }

    fun clearDeck(deckIndex: Int) {
        decks[deckIndex] = decks[deckIndex].copy(cardCounts = emptyMap())
        saveDeck(deckIndex)
    }

    // ── Předpřipravené šablony (každá přesně 30 karet) ───────────────────────
    val presetTemplates: List<Pair<String, Map<String, Int>>> = listOf(

        "⚔️ Útočník" to mapOf(
            "104" to 2,
            "109" to 2,
            "098" to 2,
            "046" to 2,
            "056" to 2,
            "026" to 2,
            "024" to 2,
            "022" to 2,
            "023" to 2,
            "054" to 2,
            "015" to 2,
            "038" to 2,
            "078" to 1,
            "052" to 1,
            "051" to 1,
            "053" to 2,
            "020" to 1,
        ),

        "🔮 Mágik" to mapOf(
            "076" to 2,
            "003" to 2,
            "098" to 2,
            "007" to 3,
            "091" to 2,
            "010" to 3,
            "009" to 2,
            "037" to 1,
            "074" to 2,
            "004" to 2,
            "026" to 2,
            "025" to 2,
            "087" to 2,
            "031" to 2,
        ),

        "🏰 Obránce" to mapOf(
            "107" to 1,
            "043" to 2,
            "050" to 2,
            "048" to 1,
            "D04" to 2,
            "020" to 1,
            "098" to 2,
            "017" to 2,
            "024" to 2,
            "047" to 2,
            "008" to 2,
            "010" to 2,
            "036" to 2,
            "004" to 1,
            "037" to 1,
            "074" to 2,
            "015" to 2,
            "D06" to 1,
        ),

        "🏰 Obránce2" to mapOf(
            "014" to 2,
            "016" to 2,
            "003" to 2,
            "094" to 1,
            "033" to 2,
            "035" to 1,
            "061" to 2,
            "093" to 1,
            "091" to 2,
            "057" to 1,
            "009" to 3,
            "031" to 2,
            "087" to 1,
            "086" to 1,
            "074" to 2,
            "D01" to 1,
            "071" to 2,
            "069" to 1,
            "052" to 1,
        ),

        "📚 Kartář" to mapOf(
            "107" to 1,
            "112" to 1,
            "072" to 1,
            "003" to 1,
            "022" to 2,
            "006" to 2,
            "005" to 2,
            "083" to 2,
            "094" to 1,
            "037" to 2,
            "004" to 2,
            "041" to 2,
            "D01" to 2,
            "D08" to 2,
            "069" to 1,
            "064" to 2,
            "097" to 1,
            "026" to 1,
            "044" to 1,
            "085" to 1,
        ),

        "🕵️ Sabotér" to mapOf(
            "109" to 1,
            "075" to 1,
            "015" to 2,
            "003" to 2,
            "024" to 2,
            "022" to 2,
            "D05" to 2,
            "009" to 2,
            "064" to 1,
            "074" to 2,
            "004" to 1,
            "039" to 2,
            "D08" to 1,
            "C02" to 2,
            "C11" to 1,
            "006" to 2,
            "081" to 1,
            "071" to 1,
            "086" to 2,
        ),

        "🌀 Chaos" to mapOf(
            "114" to 2,
            "116" to 1,
            "C05" to 2,
            "C10" to 1,
            "C06" to 1,
            "C22" to 2,
            "C29" to 2,
            "C03" to 1,
            "C35" to 2,
            "C02" to 2,
            "C24" to 2,
            "013" to 2,
            "046" to 2,
            "091" to 2,
            "022" to 2,
            "C25" to 2,
            "037" to 2,
        ),
    )

    fun loadPreset(deckIndex: Int, presetIndex: Int) {
        val template = presetTemplates[presetIndex].second
        // Filtruj šablonu podle skutečně vlastněných karet
        val filtered = template.mapNotNull { (cardId, wantedCount) ->
            val card    = allCards.find { it.id == cardId } ?: return@mapNotNull null
            val allowed = CardCollectionManager.usableCopies(card)
            if (allowed <= 0) null else cardId to minOf(wantedCount, allowed)
        }.toMap()
        decks[deckIndex] = decks[deckIndex].copy(cardCounts = filtered)
        saveDeck(deckIndex)
    }

    /** Vygeneruje vyvážený náhodný balíček (9/9/9/3) a uloží ho do slotu [deckIndex]. */
    fun generateBalancedDeck(deckIndex: Int) {
        val counts = buildBalancedDeck(allCards)
        decks[deckIndex] = decks[deckIndex].copy(cardCounts = counts)
        saveDeck(deckIndex)
    }

    /** Pasivní schopnosti, které AI dostala na začátku aktuální (offline) hry.
     *  Musí být inicializováno PŘED gameState, protože createInitialState() do něj zapisuje. */
    var aiPassiveAbilities = androidx.compose.runtime.mutableStateOf<List<PassiveAbility>>(emptyList())
        private set

    var gameState = androidx.compose.runtime.mutableStateOf(createInitialState())
        private set

    /** Quick draw: extra karta dolíznutá jen jednou na prvním tahu hráče. */
    private var quickDrawUsed = false
    var log = androidx.compose.runtime.mutableStateOf<List<LogEntry>>(emptyList())
        private set
    var gameOver = androidx.compose.runtime.mutableStateOf<GameResult?>(null)
        private set
    /** true po dobu 1s po rozhodující kartě – blokuje vstup, aby bylo vidět co rozhodlo */
    var gameEndPending = androidx.compose.runtime.mutableStateOf(false)
        private set
    private var gameEndJob: kotlinx.coroutines.Job? = null
    var lastCard           = androidx.compose.runtime.mutableStateOf<Card?>(null);          private set
    var lastCardAction     = androidx.compose.runtime.mutableStateOf(CardAction.PLAYED);   private set
    var lastCardIsPlayer   = androidx.compose.runtime.mutableStateOf(true);                private set
    /** Karta zahrána AI – zobrazena v řadě AI ruky, maže se při konci hráčova tahu. */
    var revealedAiCard     = androidx.compose.runtime.mutableStateOf<Card?>(null);         private set
    var revealedAiCardIdx  = androidx.compose.runtime.mutableStateOf<Int?>(null);          private set
    var cardHistory        = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Karty ztracené hráčem kvůli BurnCard / StealCard AI (celá hra). */
    var lostToOpponent     = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /**
     * Ordered log karet, které AI ztratila ze své ruky (spálené/ukradené hráčovou kartou –
     * BurnCard, StealCard, RandomizeHands, SwapHands…). Konzumuje NewBattlefield pro ghost
     * efekt v AI stripu. Bez fronty by více ztrát v JEDNÉ akci (např. Spálená knihovna,
     * BurnCard(2)) ukázalo tutéž (poslední) kartu 2× – ghost mechanika dřív četla jen
     * jedinou proměnnou `lastCard`, kterou dvě synchronní volání přepsaly dřív, než proběhla
     * rekompozice mezi nimi.
     */
    var aiHandLossLog      = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Snímky pro replay aktuální hry. */
    private val replayFrames = mutableListOf<ReplayFrame>()
    // Combo: hráč zahrál combo kartu – kolo nepokračuje automaticky
    var isPlayerComboTurn = androidx.compose.runtime.mutableStateOf(false)
        private set
    // Zahození karty NEukončuje tah, ale je povolené jen 1× za kolo
    var playerDiscardUsed = androidx.compose.runtime.mutableStateOf(false)
        private set

    // ── Kampaň ───────────────────────────────────────────────────────────────
    /** Aktuálně hraný soupeř v kampani (null = normální hra / aréna). */
    var activeCampaignOpponent = androidx.compose.runtime.mutableStateOf<CampaignOpponent?>(null)
        private set

    /**
     * Pozadí aktuálního bojiště – vybráno JEDNOU při startu hry (restartGame/
     * startCampaignBattle/startArenaBattle/startRoguelike), ne odvozeno reaktivně
     * v Composable, aby se neměnilo při každé rekompozici. Náhodné mimo kampaň,
     * u kampaně dané lokací (jinak default).
     */
    var battleBackgroundResId = androidx.compose.runtime.mutableStateOf(R.drawable.castle_background)
        private set

    /**
     * Vzhled soupeřova hradu/hradby MIMO kampaň – kampaň má vlastní skin daný lokací
     * (CampaignOpponent.aiCastleSkin/aiWallSkin, viz GameScreen.kt). Vybráno JEDNOU
     * při startu hry (stejný vzor jako [battleBackgroundResId]); default (castle_player/
     * wall_player) v normálním módu, náhodné ve vlastním balíčku / super-random módu.
     */
    var opponentCastleResId = androidx.compose.runtime.mutableStateOf(R.drawable.castle_player)
        private set
    var opponentWallResId = androidx.compose.runtime.mutableStateOf(R.drawable.wall_player)
        private set

    // ── Mulligan ──────────────────────────────────────────────────────────────
    var isMulligan = androidx.compose.runtime.mutableStateOf(true)
        private set
    var mulliganSelected = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
        private set

    // ── Rozhodnutí ────────────────────────────────────────────────────────────
    var pendingDecision    = androidx.compose.runtime.mutableStateOf<DecisionState?>(null)
        private set
    var decisionSecondsLeft = androidx.compose.runtime.mutableStateOf<Int?>(null)
        private set
    private var decisionPlayer       : PlayerState? = null
    private var decisionAi           : PlayerState? = null
    private var decisionOld          : GameState?   = null
    private var decisionIsCombo      : Boolean      = false
    private var decisionEffect       : CardEffect?  = null
    private var decisionPendingDraws : Int          = 0
    private var decisionTimerJob     : kotlinx.coroutines.Job? = null
    // Příznak: karta s Rozhodnutím je ve vzduchu (animace letu), overlay se teprve zobrazí.
    // Blokuje playCard, aby hráč nemohl zahrát další kartu v tomto 330ms okně.
    private var awaitingDecisionOverlay : Boolean   = false

    fun toggleMulliganCard(cardId: String) {
        val cur = mulliganSelected.value
        mulliganSelected.value = if (cardId in cur) cur - cardId else cur + cardId
    }

    fun confirmMulligan() {
        if (mulliganSelected.value.isEmpty()) { skipMulligan(); return }
        val old    = gameState.value
        val player = old.playerState.deepCopy()
        val ids    = mulliganSelected.value

        val returned = player.hand.filter { it.id in ids }
        player.hand.removeAll { it.id in ids }
        // Lízni náhrady DŘÍV, než vrácené karty zamícháš zpět do balíčku
        // → hráč nemůže dolíznout tytéž karty, které právě vyměnil
        // (stejné pravidlo jako online server v handleMulligan)
        player.drawCards(returned.size)
        player.deck.addAll(returned)
        player.deck.shuffle()

        gameState.value        = old.copy(playerState = player)
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        // Dolíznuté karty přilétají s odstupem 0,5 s (HandPanel). Kdyby první kolo
        // (tah AI + hráčův líz) začalo hned, druhé lízání se spustí uprostřed
        // animace a karty v ruce přeskakují. Počkej, až všechny přílety doběhnou.
        val redrawAnimMs = (returned.size - 1) * 500L + 450L + 250L
        maybeStartAiFirstTurn(startDelayMs = redrawAnimMs)
    }

    fun skipMulligan() {
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        maybeStartAiFirstTurn()
    }

    // ── Rozhodnutí – helpery ──────────────────────────────────────────────────

    /**
     * Ohodnotí vhodnost karty [card] pro aktuální situaci hráče [self] s cílovým HP [selfWinTarget]
     * soupeřícím proti [opp]. Vyšší skóre = karta se více hodí do situace.
     */
    private fun scoreCardForSituation(
        card: Card,
        self: PlayerState,
        opp: PlayerState,
        selfWinTarget: Int = 70
    ): Double {
        var score = 0.0
        val selfHpMissing = (selfWinTarget - self.castleHP).coerceAtLeast(1)
        val oppHpLeft     = opp.castleHP.coerceAtLeast(1)

        val oppWall = opp.wallHP.coerceAtLeast(0)
        val xValue  = (self.resources[card.costType] ?: 0).toDouble()

        for (fx in card.effects) {
            score += when (fx) {
                is CardEffect.AttackPlayer  -> {
                    // Zeď absorbuje útok první; pouze přebytek poškodí hrad
                    val castleDmg = (fx.amount - oppWall).coerceAtLeast(0).toDouble()
                    if (castleDmg >= oppHpLeft) 200.0 else castleDmg * 12.0 / oppHpLeft
                }
                is CardEffect.AttackCastle  -> {
                    // Přímý útok na hrad (přeskakuje zeď)
                    val dmg = fx.amount.toDouble()
                    if (dmg >= oppHpLeft) 200.0 else dmg * 12.0 / oppHpLeft
                }
                is CardEffect.XScaledAttackPlayer -> {
                    val dmg = xValue / fx.divisor
                    val castleDmg = (dmg - oppWall).coerceAtLeast(0.0)
                    if (castleDmg >= oppHpLeft) 200.0 else castleDmg * 12.0 / oppHpLeft
                }
                is CardEffect.XScaledAttackCastle -> {
                    val dmg = xValue / fx.divisor
                    if (dmg >= oppHpLeft) 200.0 else dmg * 12.0 / oppHpLeft
                }
                is CardEffect.XScaledBuildCastle  -> {
                    val amt = xValue / fx.divisor
                    if (amt >= selfHpMissing) 200.0 else amt * 10.0 / selfHpMissing
                }
                is CardEffect.AttackWall    -> fx.amount * 3.0
                is CardEffect.BuildCastle   -> {
                    if (fx.amount >= selfHpMissing) 200.0
                    else fx.amount * 10.0 / selfHpMissing
                }
                is CardEffect.ConvertWallToCastle -> {
                    val amt = self.wallHP
                    if (amt >= selfHpMissing) 200.0 else amt * 10.0 / selfHpMissing
                }
                is CardEffect.BuildWall     -> {
                    // Zeď nad cap (self.maxWall) nemá hodnotu – počítej jen to, co se vejde
                    val effective = fx.amount.coerceAtMost(self.maxWall - self.wallHP).coerceAtLeast(0)
                    // Nízký hrad → obrana je cennější (×1.0 při 30+, až ×2.0 při hradu u nuly)
                    val danger = 1.0 + (30 - self.castleHP).coerceAtLeast(0) / 30.0
                    effective * 1.5 * danger
                }
                is CardEffect.AddMine       -> {
                    val mine = (self.mines[fx.type] ?: 1).coerceAtLeast(1)
                    20.0 / mine
                }
                is CardEffect.AddResource   -> {
                    val res = (self.resources[fx.type] ?: 0)
                    fx.amount * (if (res < 3) 4.0 else 1.5)
                }
                is CardEffect.StealResource -> fx.amount * 5.0
                is CardEffect.DrainResource -> fx.amount * 3.0
                is CardEffect.DrawCard      -> fx.count * 8.0
                is CardEffect.StealCastle   -> fx.amount * 6.0
                is CardEffect.DestroyMine   -> 10.0
                is CardEffect.StealCard     -> 12.0
                is CardEffect.BurnCard      -> 8.0
                else                        -> 2.0
            }
        }
        // Ve fallback (žádná dostupná karta): preferuj tu nejblíže k dostupnosti
        val res           = self.resources[card.costType] ?: 0
        val effectiveCost = (card.cost + card.costModifier).coerceAtLeast(0)
        val deficit       = (effectiveCost - res).coerceAtLeast(0)
        if (deficit > 0) score -= deficit * 5.0   // penalizace za každý chybějící zdroj
        return score
    }

    private fun buildDecisionOptions(
        fx: CardEffect,
        self: PlayerState,
        opponent: PlayerState,
        excludeId: String? = null,   // vyloučí právě zahranou kartu z nabídky (Vzpomínka)
        selfWinTarget: Int = gameState.value.playerWinTarget
    ): List<Card> = when (fx) {
        is CardEffect.DecisionBurnOpponent -> opponent.deck.filter { !it.isPlaceholder }.shuffled().take(fx.picks)
        is CardEffect.DecisionChooseType   -> allCards.filter { it.type == fx.cardType && !it.isPlaceholder }.shuffled().take(fx.picks)
            .map { if (fx.costReduction > 0) it.copy(costModifier = -fx.costReduction) else it }
        is CardEffect.DecisionFromDiscard  -> self.discardPile.filter { it.id != excludeId && !it.isPlaceholder }.shuffled().take(fx.picks)
        is CardEffect.DecisionFromDeck     -> self.deck.filter { !it.isPlaceholder }.shuffled().take(fx.picks)
        is CardEffect.DecisionDrawFromDeck -> self.deck.filter { !it.isPlaceholder }.take(fx.picks)
        is CardEffect.DecisionMine         -> {
            // Vždy 4 možnosti (1 důl od každého typu). Dedup karet s více AddMine
            // efekty (Trifekta dolů, Velkovýroba…) musí probíhat BĚHEM výběru –
            // dodatečný distinctBy by po náhodné kolizi srazil nabídku na 3 karty.
            val seen = mutableSetOf<String>()
            listOf(
                ResourceType.MAGIC, ResourceType.ATTACK, ResourceType.STONES, ResourceType.CHAOS
            ).mapNotNull { resType ->
                allCards.filter { card ->
                    card.baseId !in seen &&
                        card.effects.any { e -> e is CardEffect.AddMine && e.type == resType }
                }.shuffled().firstOrNull()?.also { seen.add(it.baseId) }
            }
        }
        is CardEffect.SmartJoker           -> listOf("Magie", "Útok", "Stavba", "Chaos").mapNotNull { typeName ->
            // Z každého typu nejdřív filtr na karty, které si hráč může PRÁVĚ dovolit;
            // teprve z nich vybere situačně nejlepší. Jen pokud žádná dostupná není,
            // sáhne na nejdostupnější nedostupnou (jako výhledová volba).
            val pool = allCards.filter { it.type == typeName && !it.isPlaceholder }
            val affordable = pool.filter { card ->
                val res  = self.resources[card.costType] ?: 0
                val cost = (card.cost + card.costModifier).coerceAtLeast(0)
                res >= cost
            }
            val candidates = affordable.ifEmpty { pool }
            candidates.maxByOrNull { scoreCardForSituation(it, self, opponent, selfWinTarget) }
        }
        is CardEffect.PeekAndStealHand      -> opponent.hand.filter { !it.isPlaceholder }
        is CardEffect.DecisionChooseResource -> fx.options.map { opt -> resourcePlaceholderCard(opt.type, opt.amount) }
        else -> emptyList()
    }

    /** Vytvoří placeholder kartu reprezentující volbu suroviny v [CardEffect.DecisionChooseResource]. */
    private fun resourcePlaceholderCard(type: ResourceType, amount: Int): Card {
        val artRes = when (type) {
            ResourceType.MAGIC  -> R.drawable.art_placeholder_magie
            ResourceType.ATTACK -> R.drawable.art_placeholder_utok
            ResourceType.STONES -> R.drawable.art_placeholder_kamen
            ResourceType.CHAOS  -> R.drawable.art_placeholder_chaos
        }
        val label = resLabel(type)
        return Card(
            id            = "__res_${type.name}",
            name          = "+$amount $label",
            description   = ls.decisionResourceCardDesc.format(amount, label),
            cost          = 0,
            costType      = type,
            effects       = listOf(CardEffect.AddResource(type, amount)),
            isPlaceholder = true,
            type          = label,
            artResId      = artRes,
            artScale      = 0.80f,
            artBiasY      = -0.5f
        )
    }

    private fun buildDecisionState(fx: CardEffect, options: List<Card>): DecisionState = when (fx) {
        is CardEffect.DecisionBurnOpponent   -> DecisionState(ls.decisionTitle, ls.decisionBurnOpponent, options)
        is CardEffect.DecisionChooseType     -> DecisionState(ls.decisionTitle, ls.decisionChooseType.format(fx.cardType), options)
        is CardEffect.DecisionFromDiscard    -> DecisionState(ls.decisionTitle, ls.decisionFromDiscard, options)
        is CardEffect.DecisionFromDeck       -> DecisionState(ls.decisionTitle, ls.decisionFromDeck, options)
        is CardEffect.DecisionDrawFromDeck   -> DecisionState(ls.decisionTitle, ls.decisionDrawFromDeck, options)
        is CardEffect.DecisionMine           -> DecisionState(ls.decisionTitle, ls.decisionMine, options)
        is CardEffect.SmartJoker             -> DecisionState(ls.decisionTitle, ls.decisionSmartJoker, options)
        is CardEffect.PeekAndStealHand       -> DecisionState(ls.decisionPeekTitle, ls.decisionPeekSubtitle, options)
        is CardEffect.DecisionChooseResource -> DecisionState(ls.decisionAlchemyTitle, ls.decisionAlchemySubtitle, options)
        else -> DecisionState("", "", emptyList())
    }

    private fun startDecisionTimer(seconds: Int = 30) {
        decisionTimerJob?.cancel()
        decisionSecondsLeft.value = seconds
        decisionTimerJob = viewModelScope.launch(crashHandler) {
            for (s in seconds downTo 0) {
                decisionSecondsLeft.value = s
                kotlinx.coroutines.delay(1000L)
            }
            autoResolveDecision()
        }
    }

    private fun cancelDecisionTimer() {
        decisionTimerJob?.cancel()
        decisionTimerJob = null
        decisionSecondsLeft.value = null
    }

    /** Hráč si vybral [chosen] v overlay Rozhodnutí — aplikuje efekt a pokračuje v tahu. */
    fun resolveDecision(chosen: Card) {
        val player     = decisionPlayer  ?: return
        val ai         = decisionAi      ?: return
        val old        = decisionOld     ?: return
        val isComboCard = decisionIsCombo
        val effect     = decisionEffect  ?: return

        cancelDecisionTimer()

        when (effect) {
            is CardEffect.DecisionBurnOpponent -> {
                ai.deck.remove(chosen)
                ai.discardPile.add(chosen)
                log.appendLog(ls.logBurnedFromOppDeck.format(chosen.displayName))
            }
            is CardEffect.DecisionChooseType -> {
                val newCard = chosen.copy(
                    id           = "${chosen.id}_${java.util.UUID.randomUUID()}",
                    isGenerated  = true,
                    costModifier = -effect.costReduction   // záporná = sleva; 0 = beze změny
                )
                if (player.hand.size < old.playerMaxHand) player.hand.add(newCard) else player.discardPile.add(newCard)
                val discountMsg = if (effect.costReduction > 0) " (−${effect.costReduction} ${resLabel(chosen.costType)})" else ""
                log.appendLog(ls.logChose.format(chosen.displayName) + discountMsg)
            }
            is CardEffect.DecisionFromDiscard -> {
                player.discardPile.remove(chosen)
                val retrieved = chosen.copy(isGenerated = true)
                if (player.hand.size < old.playerMaxHand) player.hand.add(retrieved) else player.discardPile.add(retrieved)
                log.appendLog(ls.logTookFromDiscard.format(chosen.displayName))
            }
            is CardEffect.DecisionFromDeck -> {
                // Kopie – originál zůstane v balíčku, do ruky jde kopie s novým ID
                val copy = chosen.copy(id = "${chosen.id}_${java.util.UUID.randomUUID()}", isGenerated = true)
                if (player.hand.size < old.playerMaxHand) player.hand.add(copy) else player.discardPile.add(copy)
                log.appendLog(ls.logCopiedFromDeck.format(chosen.displayName))
            }
            is CardEffect.DecisionDrawFromDeck -> {
                // Pravý draw – karta se odstraní z balíčku a přijde do ruky
                player.deck.remove(chosen)
                if (player.hand.size < old.playerMaxHand) player.hand.add(chosen) else player.discardPile.add(chosen)
                log.appendLog(ls.logDrewFromDeck.format(chosen.displayName))
            }
            is CardEffect.DecisionMine -> {
                val newCard = chosen.copy(
                    id          = "${chosen.id}_${java.util.UUID.randomUUID()}",
                    isGenerated = true
                )
                if (player.hand.size < old.playerMaxHand) player.hand.add(newCard) else player.discardPile.add(newCard)
                log.appendLog(ls.logChoseMine.format(chosen.displayName))
            }
            is CardEffect.SmartJoker -> {
                val newCard = chosen.copy(
                    id          = "${chosen.id}_${java.util.UUID.randomUUID()}",
                    isGenerated = true
                )
                if (player.hand.size < old.playerMaxHand) player.hand.add(newCard) else player.discardPile.add(newCard)
                log.appendLog(ls.logJoker.format(chosen.displayName))
            }
            is CardEffect.PeekAndStealHand -> {
                ai.hand.remove(chosen)
                val stolen = chosen.copy(id = "${chosen.id}_stolen_${java.util.UUID.randomUUID()}", isGenerated = true)
                if (player.hand.size < old.playerMaxHand) player.hand.add(stolen) else player.discardPile.add(stolen)
                log.appendLog(ls.logStoleFromHand.format(chosen.displayName))
                cardHistory.appendHistory(chosen, CardAction.STOLEN, isMine = false)
                addCardLog("Hráč", chosen, CardAction.STOLEN, isMe = false)
            }
            is CardEffect.DecisionChooseResource -> {
                // Vybraná karta je resource placeholder — obsahuje AddResource efekt
                val addRes = chosen.effects.filterIsInstance<CardEffect.AddResource>().firstOrNull()
                if (addRes != null) {
                    player.resources[addRes.type] = ((player.resources[addRes.type] ?: 0) + addRes.amount).coerceAtMost(MAX_RESOURCE)
                    log.appendLog(ls.logChoseResource.format(addRes.amount, resLabel(addRes.type)))
                }
            }
            else -> {}
        }

        // Aktualizuj Klon/Zrcadlo karty v ruce po přidání nové karty přes Decision
        // (updateCloneCards se volá při playCard, ale Klon může přijít do ruky až teď)
        // Transformuj Shapeshifter, pokud byl přidán do ruky přes Decision efekt
        transformShapeShifters(player.hand, allCards, onlyNew = true)
        updateCloneCards(player.hand, player.lastPlayedCard, allCards)
        updateMirrorCards(player.hand, ai.lastPlayedCard, allCards)

        // Vyčisti stav
        val pendingDrawCount     = decisionPendingDraws
        pendingDecision.value    = null
        decisionPlayer           = null
        decisionAi               = null
        decisionOld              = null
        decisionEffect           = null
        decisionIsCombo          = false
        decisionPendingDraws     = 0

        val s1 = old.copy(playerState = player, aiState = ai)
        addReplayFrame(s1, chosen, isPlayer = true, action = CardAction.PLAYED)
        s1.checkWinCondition()?.let { result ->
            isPlayerComboTurn.value = false
            scheduleGameEnd(result, s1); return
        }

        if (pendingDrawCount > 0) {
            // DrawPerCardPlayed / DrawCard efekty, které vznikly při zahraní Decision karty —
            // provedeme je teď, po výběru hráče (stejná logika jako v playCard).
            viewModelScope.launch(crashHandler) {
                gameState.value = s1.copy(activePlayer = ActivePlayer.AI)
                repeat(pendingDrawCount) {
                    // Odstup mezi líznutími; zvuk hraje animace příletu karty (HandPanel)
                    delay(500L)
                    val drawResult = player.drawCards(1, old.playerMaxHand)
                    transformShapeShifters(player.hand, allCards, onlyNew = true)
                    drawResult.burned.forEach { b ->
                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                        addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                        recordCard(b, CardAction.BURNED, isPlayer = true)
                    }
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.AI
                    )
                    for (trap in drawResult.traps) {
                        reportTrap(trap, player, isPlayer = true)
                        gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai, activePlayer = ActivePlayer.AI)
                        delay(1000L)
                    }
                }
                // Pauza: Compose musí stihnout rekomponovat s deepCopy stavem (líznutá karta
                // je vidět) DŘÍVE, než finishTurn přepíše gameState mutable player referencí.
                // Bez delay Compose batchuje obě změny a líznutá karta se zobrazí až při
                // prvním deepCopy v finishTurn – tj. při lízání karty na začátku kola.
                delay(350L)
                if (isComboCard) {
                    isPlayerComboTurn.value = true
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.PLAYER
                    )
                } else {
                    isPlayerComboTurn.value = false
                    finishTurn(old, player, ai)
                }
            }
            return
        }

        if (isComboCard) {
            isPlayerComboTurn.value = true
            // PlayerState je mutable class s referenční rovností. Pokud bychom použili
            // stejnou referenci player, gameState.value = s1 by Compose vyhodnotil jako
            // beze změny (s1 == stávající gameState.value) a ruka by se nepřekreslila.
            // deepCopy() vytvoří nový objekt → Compose detekuje změnu a ruka se aktualizuje.
            gameState.value = s1.copy(playerState = player.deepCopy())
        } else {
            isPlayerComboTurn.value = false
            finishTurn(old, player, ai)
        }
    }

    /** Automaticky vybere první možnost (timeout nebo AI). */
    fun autoResolveDecision() {
        val pending = pendingDecision.value ?: return
        val first = pending.options.firstOrNull() ?: return
        resolveDecision(first)
    }

    /** Hráč si vybral zdroj v overlay DecisionChooseResource — aplikuje efekt a pokračuje. */
    fun resolveResourceDecision(type: ResourceType, amount: Int) {
        val player      = decisionPlayer  ?: return
        val ai          = decisionAi      ?: return
        val old         = decisionOld     ?: return
        val isComboCard = decisionIsCombo

        cancelDecisionTimer()

        player.resources[type] = ((player.resources[type] ?: 0) + amount).coerceAtMost(MAX_RESOURCE)
        log.appendLog(ls.logChoseResource.format(amount, resLabel(type)))

        val pendingDrawCount  = decisionPendingDraws
        pendingDecision.value = null
        decisionPlayer        = null
        decisionAi            = null
        decisionOld           = null
        decisionEffect        = null
        decisionIsCombo       = false
        decisionPendingDraws  = 0

        val s1 = old.copy(playerState = player, aiState = ai)
        s1.checkWinCondition()?.let { result ->
            isPlayerComboTurn.value = false
            scheduleGameEnd(result, s1); return
        }

        if (pendingDrawCount > 0) {
            viewModelScope.launch(crashHandler) {
                gameState.value = s1.copy(activePlayer = ActivePlayer.AI)
                repeat(pendingDrawCount) {
                    // Odstup mezi líznutími; zvuk hraje animace příletu karty (HandPanel)
                    delay(500L)
                    val drawResR = player.drawCards(1, old.playerMaxHand)
                    transformShapeShifters(player.hand, allCards, onlyNew = true)
                    drawResR.burned.forEach { b ->
                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                        addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                        recordCard(b, CardAction.BURNED, isPlayer = true)
                    }
                    gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai, activePlayer = ActivePlayer.AI)
                }
                delay(350L)
                if (isComboCard) {
                    isPlayerComboTurn.value = true
                    gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai, activePlayer = ActivePlayer.PLAYER)
                } else {
                    isPlayerComboTurn.value = false
                    finishTurn(old, player, ai)
                }
            }
            return
        }

        if (isComboCard) {
            isPlayerComboTurn.value = true
            gameState.value = s1.copy(playerState = player.deepCopy())
        } else {
            isPlayerComboTurn.value = false
            finishTurn(old, player, ai)
        }
    }

    /**
     * Pokud AI začíná jako první hráč, spustí její tah automaticky po mulliganu.
     * Počáteční hráč (AI) nelíže první kartu – pravidlo stejné jako u hráče.
     * Hráč (druhý hráč) taktéž nelíže bonusovou kartu – oba začínají se 5 z mulliganu.
     *
     * [startDelayMs] > 0 odloží tah AI (hráč je mezitím zablokovaný, activePlayer = AI)
     * – používá se po mulligan výměně, aby se lízání prvního kola nepralo s animací
     * dolízávání. Větev "hráč první" běží vždy synchronně (hráč hraje hned, potřebuje zdroje).
     */
    private fun maybeStartAiFirstTurn(startDelayMs: Long = 0L) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.AI) {
            // Hráč začíná jako první → vygeneruj mu zdroje pro první tah
            val player = old.playerState.deepCopy()
            player.generateResources()
            // Quick draw: extra karta na prvním tahu
            if (!quickDrawUsed) {
                val actives = PlayerProfileManager.profile
                    ?.activeAbilities?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList()
                if (PassiveAbility.QUICK_DRAW in actives && player.deck.isNotEmpty()) {
                    quickDrawUsed = true
                    val qdr1 = player.drawCards(1, old.playerMaxHand)
                    qdr1.burned.forEach { b ->
                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                        addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                        recordCard(b, CardAction.BURNED, isPlayer = true)
                    }
                }
            }
            gameState.value = old.copy(playerState = player)
            log.appendLog(ls.logPlayerFirst)
            snapshotRogueBattle("PLAYER_IDLE")   // první hráčův tah po mulliganu
            return
        }

        log.appendLog(ls.logAiFirst)

        // AI je počáteční hráč → nesmí lízat první kartu (aiDrawsAtStart = false)
        // Hráč jako druhý hráč si lízne 1 kartu před svým prvním tahem (playerDrawsAtEnd = true)
        if (startDelayMs > 0) {
            viewModelScope.launch(crashHandler) {
                delay(startDelayMs)
                // Hráč mohl mezitím hru restartovat / opustit → stará reference by
                // přepsala nový stav (stejná třída bugů jako stale Decision overlay)
                if (gameState.value !== old || isMulligan.value) return@launch
                finishTurn(old, old.playerState.deepCopy(), old.aiState.deepCopy(),
                           aiDrawsAtStart = false, playerDrawsAtEnd = true)
            }
        } else {
            finishTurn(old, old.playerState.deepCopy(), old.aiState.deepCopy(),
                       aiDrawsAtStart = false, playerDrawsAtEnd = true)
        }
    }

    // ── Vyvážený náhodný balíček 30 karet (9/9/9/3, respektuje rarity) ─────────
    private fun balancedDeck(): List<Card> =
        buildBalancedDeck(allCards)
            .flatMap { (id, count) ->
                val card = allCards.find { it.id == id } ?: return@flatMap emptyList()
                List(count) { card }
            }

    private fun superRandomDeck(): List<Card> =
        buildSuperRandomDeck(allCards)
            .flatMap { (id, count) ->
                val card = allCards.find { it.id == id } ?: return@flatMap emptyList()
                List(count) { card }
            }

    /** Náhodně vybraný předdefinovaný balíček (stejné presety jako v DeckBuilderu). */
    private fun presetDeck(): List<Card> =
        presetTemplates.random().second
            .flatMap { (id, count) ->
                val card = allCards.find { it.id == id } ?: return@flatMap emptyList()
                List(count) { card }
            }

    private fun createInitialState(randomDeck: Boolean = false, superRandom: Boolean = false): GameState {
        val activeDeck  = decks[activeDeckIndex.value]
        // Super náhodný mód: oba hráči sdílí stejný balíček (jen jinak zamíchaný)
        val sharedSuperDeck: List<Card>? = if (superRandom) superRandomDeck() else null

        val playerCards = when {
            superRandom  -> sharedSuperDeck!!
            randomDeck   -> balancedDeck()
            else         -> {
                val base = activeDeck.toCardList(allCards)
                val missing = 30 - base.size
                if (missing <= 0) base
                else {
                    // Doplň chybějící karty náhodnými vlastněnými kartami.
                    // canAdd = maxCopies − kopie už v balíčku, aby se nepřekročil rarity limit.
                    val baseCounts = base.groupingBy { it.id }.eachCount()
                    val ownedPool = allCards
                        .filter { !it.isPlaceholder && CardCollectionManager.usableCopies(it) > 0 }
                        .flatMap { card ->
                            val canAdd = card.rarity.maxCopies - (baseCounts[card.id] ?: 0)
                            if (canAdd > 0) List(canAdd) { card } else emptyList()
                        }
                        .shuffled()
                    base + ownedPool.take(missing)
                }
            }
        }.withUniqueIds()   // ještě NEZAMÍCHÁNO – shuffle se provede uvnitř also{} níže

        // ── Pasivní schopnosti hráče ──────────────────────────────────────────
        val actives = PlayerProfileManager.profile
            ?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()

        // Constructed (vlastní balíček): start hradu 35 – vyrovnává asymetrii
        // kill (−30) vs build (+40), kde destrukce byla numericky blíž.
        // Náhodné módy zůstávají na 30 (kvótované balíčky, ověřená balance).
        val baseCastle       = if (!randomDeck && !superRandom) 35 else 30
        val startCastle      = baseCastle + if (PassiveAbility.EXTRA_CASTLE in actives) 5 else 0
        val startWall        = 15 + if (PassiveAbility.EXTRA_WALL       in actives) 5 else 0
        val extraMagic       =       if (PassiveAbility.EXTRA_MAGIC      in actives) 1 else 0
        val extraAttack      =       if (PassiveAbility.EXTRA_ATTACK     in actives) 1 else 0
        val extraStones      =       if (PassiveAbility.EXTRA_STONES     in actives) 1 else 0
        val extraChaos       =       if (PassiveAbility.EXTRA_CHAOS      in actives) 1 else 0
        val playerWinTarget  =  70 + if (PassiveAbility.EXTRA_CASTLE     in actives) 5 else 0
        val playerMaxHand    =   7 + if (PassiveAbility.EXTRA_HAND_CARD  in actives) 1 else 0

        // ── Pasivní schopnosti AI (2 náhodné, mimo IRON_BASTION který je jen pro hráče) ──
        val aiPassives = PassiveAbility.entries
            .filter { it != PassiveAbility.IRON_BASTION }
            .shuffled()
            .take(2)
        aiPassiveAbilities.value = aiPassives

        val aiStartCastle    = baseCastle + if (PassiveAbility.EXTRA_CASTLE in aiPassives) 5 else 0
        val aiStartWall      = 15 + if (PassiveAbility.EXTRA_WALL       in aiPassives) 5 else 0
        val aiExtraMagic     =       if (PassiveAbility.EXTRA_MAGIC      in aiPassives) 1 else 0
        val aiExtraAttack    =       if (PassiveAbility.EXTRA_ATTACK     in aiPassives) 1 else 0
        val aiExtraStones    =       if (PassiveAbility.EXTRA_STONES     in aiPassives) 1 else 0
        val aiExtraChaos     =       if (PassiveAbility.EXTRA_CHAOS      in aiPassives) 1 else 0
        val aiMaxHand        =   7 + if (PassiveAbility.EXTRA_HAND_CARD  in aiPassives) 1 else 0
        // aiWinTarget: EXTRA_CASTLE AI zvýší její cíl o 5 (stejný tradeoff jako u hráče),
        //              IRON_BASTION hráče přidá dalších +5 na cíl AI.
        val aiWinTarget      = (70 + if (PassiveAbility.EXTRA_CASTLE  in aiPassives) 5 else 0) +
                                    (if (PassiveAbility.IRON_BASTION  in actives)    5 else 0)

        // Posila balíčku z hráčových pasivních schopností
        val playerBoostCards = buildList {
            fun pick(f: (Card) -> Boolean, n: Int) = allCards.filter { f(it) && !it.isPlaceholder }.shuffled().take(n)
            if (PassiveAbility.BOOST_ATTACK in actives) addAll(pick({ it.type == "Útok"   }, 2))
            if (PassiveAbility.BOOST_BUILD  in actives) addAll(pick({ it.type == "Stavba" }, 2))
            if (PassiveAbility.BOOST_MAGIC  in actives) addAll(pick({ it.type == "Magie"  }, 2))
            if (PassiveAbility.BOOST_CHAOS  in actives) addAll(pick({ it.type == "Chaos"  }, 2))
            if (PassiveAbility.BOOST_RANDOM in actives) addAll(pick({ it.type != "Důl"    }, 3))
        }.map { it.copy(isGenerated = true) }.withUniqueIds()

        val playerState = PlayerState(
            castleHP  = startCastle,
            wallHP    = startWall
        ).also {
            if (extraMagic  > 0) it.resources[ResourceType.MAGIC]  = extraMagic
            if (extraAttack > 0) it.resources[ResourceType.ATTACK] = extraAttack
            if (extraStones > 0) it.resources[ResourceType.STONES] = extraStones
            if (extraChaos  > 0) it.resources[ResourceType.CHAOS]  = extraChaos
            it.deck.addAll(playerCards + playerBoostCards)
            it.deck.shuffle()   // 1. míchání – provede se na MutableList přímo
            it.drawCards(4)
        }

        val aiState = PlayerState(
            castleHP = aiStartCastle,
            wallHP   = aiStartWall
        ).also {
            if (aiExtraMagic  > 0) it.resources[ResourceType.MAGIC]  = aiExtraMagic
            if (aiExtraAttack > 0) it.resources[ResourceType.ATTACK] = aiExtraAttack
            if (aiExtraStones > 0) it.resources[ResourceType.STONES] = aiExtraStones
            if (aiExtraChaos  > 0) it.resources[ResourceType.CHAOS]  = aiExtraChaos
            val aiBaseCards = when {
                superRandom  -> sharedSuperDeck!!.withUniqueIds()
                !randomDeck && activeDeck.isValid -> presetDeck().withUniqueIds()
                else         -> balancedDeck().withUniqueIds()
            }
            // Posila balíčku z AI pasivních schopností
            val aiBoostCards = buildList {
                fun pick(f: (Card) -> Boolean, n: Int) = allCards.filter { f(it) && !it.isPlaceholder }.shuffled().take(n)
                if (PassiveAbility.BOOST_ATTACK in aiPassives) addAll(pick({ it.type == "Útok"  }, 2))
                if (PassiveAbility.BOOST_BUILD  in aiPassives) addAll(pick({ it.type == "Stavba" }, 2))
                if (PassiveAbility.BOOST_MAGIC  in aiPassives) addAll(pick({ it.type == "Magie" }, 2))
                if (PassiveAbility.BOOST_CHAOS  in aiPassives) addAll(pick({ it.type == "Chaos" }, 2))
                if (PassiveAbility.BOOST_RANDOM in aiPassives) addAll(pick({ it.type != "Důl"   }, 3))
            }.map { it.copy(isGenerated = true) }.withUniqueIds()
            it.deck.addAll(aiBaseCards + aiBoostCards)
            it.deck.shuffle()   // 2. míchání – oddělené volání, zaručeně jiný stav Random
            // QUICK_DRAW: AI lízne 1 kartu navíc na začátku první tahu
            val initDraw = 4 + if (PassiveAbility.QUICK_DRAW in aiPassives) 1 else 0
            it.drawCards(initDraw, aiMaxHand)
        }

        val firstPlayer = if (Random.nextBoolean()) ActivePlayer.PLAYER else ActivePlayer.AI
        // Pokud AI začíná jako první, finishTurn přičte +1 před hráčovým prvním tahem.
        // Začneme na 0, aby se hráčovo první kolo správně zobrazilo jako T1.
        val startTurn = if (firstPlayer == ActivePlayer.AI) 0 else 1
        return GameState(
            playerState     = playerState,
            aiState         = aiState,
            activePlayer    = firstPlayer,
            currentTurn     = startTurn,
            playerWinTarget = playerWinTarget,
            aiWinTarget     = aiWinTarget,
            playerMaxHand   = playerMaxHand,
            aiMaxHand       = aiMaxHand
        )
    }

    fun playCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        if (awaitingDecisionOverlay) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Affordability check (X-kost karty jsou vždy zahratelné)
        if (!card.isXCost && (player.resources[card.costType] ?: 0) < card.effectiveCost) {
            log.appendLog(ls.logNotEnough.format(resLabel(card.costType), card.displayName))
            return
        }

        // 1. Zaplatit a přesunout kartu z ruky PŘED aplikací efektů
        // (aby karta "lízni kartu" nejdřív zmizela z ruky, pak se líže nová)
        // Snapshot zdrojů PŘED zaplacením – ConditionalEffect (ResourceAbove) se
        // vyhodnocuje proti tomuto stavu, aby karta mohla splnit vlastní podmínku.
        player.preCostResources = player.resources.toMap()
        val xValue: Int
        if (card.isXCost) {
            xValue = player.resources[card.costType] ?: 0
            player.resources[card.costType] = 0
        } else {
            xValue = 0
            player.resources[card.costType] = (player.resources[card.costType] ?: 0) - card.effectiveCost
        }
        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.PLAYED, isPlayer = true)
        addCardLog("Hráč", card, CardAction.PLAYED, isMe = true)
        playSoundForCard(card)

        // 2. Efekty (vč. podmínek) se vyhodnotí AŽ PO odebrání karty z ruky
        // Nastav typ právě hrané karty – podmínka LastPlayedType to přečte uvnitř applyEffects
        player.lastPlayedType  = card.type
        // lastPlayedCard se nastavuje AŽ PO applyEffects (níže), aby Klon nečetl sám sebe
        // Před aplikací: zaznamenej nesplněné podmínky pro hráče
        card.effects.filterIsInstance<CardEffect.ConditionalEffect>().forEach { ce ->
            if (!checkCondition(ce.condition, player, ai)) {
                log.appendLog(ls.logConditionNotMet.format(card.displayName))
            }
        }
        // Quest: zahraná karta
        QuestManager.onCardPlayed()

        // DrawCard efekty se zpracují samostatně (postupný líz s animací a zvukem)
        var pendingDrawCount = 0
        // NextCardIsCombo: flag nastaven předchozí kartou → tato karta se chová jako combo (neukončí tah).
        val nextComboBoost = player.nextCardIsCombo
        if (nextComboBoost) player.nextCardIsCombo = false
        // CloneNextPlayed: flag nastaven předchozí kartou → naklonuj tuto kartu do balíčku.
        // Klony se přidají hned (hráč vidí nárůst balíčku), UUID zaručuje unikátní ID každé kopie.
        val cloneCount = player.cloneNextPlayed
        if (cloneCount != null && cloneCount > 0) {
            repeat(cloneCount) {
                player.deck.add(card.copy(id = "${card.id}_clone_${java.util.UUID.randomUUID()}", isGenerated = true))
            }
            player.deck.shuffle()
            player.cloneNextPlayed = null
            log.appendLog(ls.logReplication.format(cloneCount, card.displayName))
        }
        // DrawPerCardPlayed: flag byl nastaven předchozí kartou → tato karta triggeruje líz (s volitelným filtrem typu)
        val drawFilter = player.drawCardOnPlay
        if (drawFilter != null && (drawFilter.isEmpty() || drawFilter == card.type)) pendingDrawCount += 1
        // GainResourcePerCardPlayed: přidej zdroje nastavené předchozí kartou (s filtrem typu)
        for (grp in player.gainResourcePerCardPlayed) {
            if (grp.cardType == null || grp.cardType == card.type) {
                player.resources[grp.type] = ((player.resources[grp.type] ?: 0) + grp.amount).coerceAtMost(MAX_RESOURCE)
            }
        }
        // GainCastlePerCardPlayed: přidej HP hradu nastavené předchozí kartou (s filtrem typu)
        for (gcpp in player.gainCastlePerCardPlayed) {
            if (gcpp.cardType == null || gcpp.cardType == card.type) {
                player.castleHP = (player.castleHP + gcpp.amount).coerceAtMost(100)
            }
        }
        val aiCastleHpBefore = ai.castleHP
        // Ztráty soupeře se sbírají a vyhodnocují až PO applyEffects: zobrazení
        // musí být sekvenční (víc karet naráz) a ghost ve stripu se smí pustit
        // jen u skutečné ztráty z ruky, což se pozná až podle overdrawIds.
        val oppLosses   = mutableListOf<Pair<Card, CardAction>>()
        val overdrawIds = mutableSetOf<String>()
        applyEffects(card.effects, player, ai, allCards, xValue = xValue,
            onOpponentCardLost = { lostCard, action ->
                cardHistory.appendHistory(lostCard, action, isMine = false)
                addCardLog("Hráč", lostCard, action, isMe = false)
                oppLosses.add(lostCard to action)
            },
            onDrawCard = { _, count -> pendingDrawCount += count },
            maxHandSize = old.playerMaxHand,
            onSelfCardLost = { lostCard, action ->
                // Hráčovy vlastní zahozené karty (např. Velký zmatek)
                cardHistory.appendHistory(lostCard, action, isMine = true)
                addCardLog("Hráč", lostCard, action, isMe = true)
            },
            opponentMaxHandSize = old.aiMaxHand,
            // Hráčova karta se ve slotu zobrazila UŽ PŘED applyEffects
            // (recordCard výše), takže přelíznutí ho může rovnou přebít.
            onOverdrawBurn = { burned, isSelf ->
                if (isSelf) showOverdrawBurn(burned, isPlayer = true)
                else        overdrawIds.add(burned.id)   // zobrazí revealOpponentLosses
            })
        // Ghost v AI stripu jen u SKUTEČNÉ ztráty z ruky. Přelíznutí jde z balíčku
        // rovnou do odhazovacího, ruka se nezmenší → položka ve frontě by se nikdy
        // nespotřebovala a rozjela by kurzor pro všechny další ztráty.
        oppLosses.forEach { (lostCard, action) ->
            if (lostCard.id !in overdrawIds) recordAiHandLoss(lostCard, action)
        }
        revealOpponentLosses(oppLosses)
        // Quest: poškození hradu
        val castleDmg = (aiCastleHpBefore - ai.castleHP).coerceAtLeast(0)
        if (castleDmg > 0) QuestManager.onDamageDealt(castleDmg)
        // Snapshot už není potřeba – vyčistit, aby neovlivnil další vyhodnocení
        player.preCostResources = null

        // ── Rozhodnutí: pauza tahu pro výběr hráče ──────────────────────────
        // Momentum: aktualizuj počítadlo za útočné karty (PŘED decision pausou, ale PO applyEffects)
        if (card.costType == ResourceType.ATTACK) player.attackCardsThisTurn++
        // lastPlayedCard PO applyEffects – Klon nečte sám sebe; Mirror ji čte jen pro soupeře
        val prevPlayerLastPlayed = player.lastPlayedCard   // uložit před přepisem pro Klon+Decision
        player.lastPlayedCard = card
        // Mirror/Klon: aktualizuj lastPlayedCard na efektivně kopírovanou kartu,
        // aby příští Klon v ruce zkopíroval správnou kartu (např. Intuici), ne [Mirror]/[Clone] efekt.
        if (card.effects.any { it is CardEffect.Mirror } && ai.lastPlayedCard != null) {
            player.lastPlayedCard = ai.lastPlayedCard
        } else if (card.effects.any { it is CardEffect.Clone } && prevPlayerLastPlayed != null) {
            player.lastPlayedCard = prevPlayerLastPlayed
        }
        // Reset Mirror/Clone v discardPile → originální art, jinak by karta přes deck/Vzpomínku
        // ukazovala cizí art místo art_zrcadlo / art_klon
        resetMirrorCloneInPile(player.discardPile, card, allCards)
        updateCloneCards(player.hand, player.lastPlayedCard, allCards)

        fun isDecisionFx(fx: CardEffect) =
            fx is CardEffect.DecisionBurnOpponent || fx is CardEffect.DecisionChooseType    ||
            fx is CardEffect.DecisionFromDiscard  || fx is CardEffect.DecisionFromDeck      ||
            fx is CardEffect.DecisionDrawFromDeck || fx is CardEffect.DecisionMine          ||
            fx is CardEffect.SmartJoker           || fx is CardEffect.PeekAndStealHand      ||
            fx is CardEffect.DecisionChooseResource

        var decisionFx: CardEffect? = card.effects.firstOrNull { isDecisionFx(it) }

        // Mirror/Klon: pokud kopírovaná karta obsahuje Decision efekt, propaguj ho sem.
        // Bez toho by zůstal no-op a hráč by nikdy nedostal výběrový overlay.
        if (decisionFx == null) {
            if (card.effects.any { it is CardEffect.Mirror }) {
                decisionFx = ai.lastPlayedCard?.effects?.firstOrNull { isDecisionFx(it) }
            }
            if (decisionFx == null && card.effects.any { it is CardEffect.Clone }) {
                decisionFx = prevPlayerLastPlayed?.effects?.firstOrNull { isDecisionFx(it) }
            }
        }
        if (decisionFx != null) {
            val options = buildDecisionOptions(decisionFx, player, ai, excludeId = card.id)
            if (options.isNotEmpty() || decisionFx is CardEffect.DecisionChooseResource) {
                decisionPlayer       = player
                decisionAi           = ai
                decisionOld          = old
                decisionIsCombo      = card.isCombo || nextComboBoost
                decisionEffect       = decisionFx
                // Uložíme pending lízy (DrawPerCardPlayed, DrawCard efekty) –
                // provedeme je v resolveDecision po výběru karty hráčem.
                decisionPendingDraws = pendingDrawCount
                val decisionState = buildDecisionState(decisionFx, options)
                gameState.value = old.copy(playerState = player, aiState = ai)
                // Overlay zobrazíme až po dokončení animace letu karty (300ms).
                // Synchronní nastavení by způsobilo, že Compose musí recomposit
                // celý herní screen + vyrenderovat DecisionOverlay se 4+ kartami
                // ve stejném framu → animace sekala, lag ~1 s.
                awaitingDecisionOverlay = true
                viewModelScope.launch(crashHandler) {
                    try {
                        delay(330L)
                        pendingDecision.value = decisionState
                    } finally {
                        awaitingDecisionOverlay = false
                    }
                }
                // Timer jen pro online (offline = bez limitu)
                return
            }
        }

        val s1 = old.copy(playerState = player, aiState = ai)
        addReplayFrame(s1, card, isPlayer = true, action = CardAction.PLAYED)
        s1.checkWinCondition()?.let { result ->
            isPlayerComboTurn.value = false
            scheduleGameEnd(result, s1); return
        }

        if (pendingDrawCount > 0) {
            // Postupný líz: každá karta dolízne zvlášť se zvukem + animací
            val isComboCard = card.isCombo || nextComboBoost
            viewModelScope.launch(crashHandler) {
                // Zamkni hráče během lízání
                gameState.value = s1.copy(activePlayer = ActivePlayer.AI)
                repeat(pendingDrawCount) {
                    // Odstup mezi líznutími; zvuk hraje animace příletu karty (HandPanel)
                    delay(500L)
                    val drawResult = player.drawCards(1, old.playerMaxHand)
                    transformShapeShifters(player.hand, allCards, onlyNew = true)
                    drawResult.burned.forEach { b ->
                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                        addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                        recordCard(b, CardAction.BURNED, isPlayer = true)
                    }
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.AI
                    )
                    for (trap in drawResult.traps) {
                        reportTrap(trap, player, isPlayer = true)
                        gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai, activePlayer = ActivePlayer.AI)
                        delay(1000L)
                    }
                }
                // Pauza: Compose musí stihnout rekomponovat s deepCopy stavem (líznutá karta
                // je vidět) DŘÍVE, než finishTurn přepíše gameState mutable player referencí.
                // Bez delay Compose batchuje obě změny a líznutá karta se zobrazí až při
                // prvním deepCopy v finishTurn – tj. při lízání karty na začátku kola.
                delay(350L)
                if (isComboCard) {
                    isPlayerComboTurn.value = true
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.PLAYER
                    )
                } else {
                    isPlayerComboTurn.value = false
                    finishTurn(old, player, ai)
                }
            }
            return
        }

        if (card.isCombo || nextComboBoost) {
            // Combo: neukončuj kolo, hráč může hrát dál
            isPlayerComboTurn.value = true
            gameState.value = s1
        } else {
            isPlayerComboTurn.value = false
            finishTurn(old, player, ai)
        }
    }

    /** Hráč explicitně ukončí tah (po sehrání combo karet). */
    fun endPlayerTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()
        isPlayerComboTurn.value = false
        log.appendLog(ls.logPlayerEndTurn)
        finishTurn(old, player, ai)
    }

    fun waitTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER || gameOver.value != null) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Čekat = přeskočit tah bez akce.
        // Karta se líže automaticky na začátku DALŠÍHO kola (v finishTurn).
        log.appendLog(ls.logPlayerSkip)
        isPlayerComboTurn.value = false
        finishTurn(old, player, ai, playerWaited = true)
    }

    fun discardCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        if (playerDiscardUsed.value) return   // zahození jen 1× za kolo
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.DISCARDED, isPlayer = true)
        addCardLog("Hráč", card, CardAction.DISCARDED, isMe = true)
        SoundManager.playDiscard()

        playerDiscardUsed.value = true
        addReplayFrame(old.copy(playerState = player, aiState = ai), card, isPlayer = true, action = CardAction.DISCARDED)

        // ── Mechanika "Zahození": alternativní efekty MÍSTO normálního zahrání ────
        // Karta má tyto efekty jen když je ZAHOZENA (ne zahrána) – card.effects se
        // v tomto případě vůbec neaplikují.
        if (card.discardEffects.isNotEmpty()) {
            log.appendLog(ls.logDiscardEffectTriggered.format(card.displayName))
            // Stejný postup jako u zahrání karty – sbírej, vyhodnoť po applyEffects
            val dOppLosses   = mutableListOf<Pair<Card, CardAction>>()
            val dOverdrawIds = mutableSetOf<String>()
            applyEffects(card.discardEffects, player, ai, allCards,
                onOpponentCardLost = { lostCard, action ->
                    cardHistory.appendHistory(lostCard, action, isMine = false)
                    addCardLog("Hráč", lostCard, action, isMe = false)
                    dOppLosses.add(lostCard to action)
                },
                maxHandSize = old.playerMaxHand,
                onSelfCardLost = { lostCard, action ->
                    cardHistory.appendHistory(lostCard, action, isMine = true)
                    addCardLog("Hráč", lostCard, action, isMe = true)
                },
                opponentMaxHandSize = old.aiMaxHand,
                onOverdrawBurn = { burned, isSelf ->
                    if (isSelf) showOverdrawBurn(burned, isPlayer = true)
                    else        dOverdrawIds.add(burned.id)
                })
            dOppLosses.forEach { (lostCard, action) ->
                if (lostCard.id !in dOverdrawIds) recordAiHandLoss(lostCard, action)
            }
            revealOpponentLosses(dOppLosses)
            val s1 = old.copy(playerState = player, aiState = ai)
            s1.checkWinCondition()?.let { result ->
                scheduleGameEnd(result, s1); return
            }
        }

        // Zahození NEukončuje tah (1× za kolo) – hráč pokračuje; combo stav se nemění
        gameState.value = old.copy(playerState = player, aiState = ai)
    }

    private fun finishTurn(
        old: GameState, player: PlayerState, ai: PlayerState,
        aiDrawsAtStart: Boolean = true,
        playerDrawsAtEnd: Boolean = true,
        playerWaited: Boolean = false
    ) {
        // Konec tahu hráče → smaž odhalení AI karty z ruky (ne discard slot uprostřed)
        revealedAiCard.value    = null
        revealedAiCardIdx.value = null
        // Nové kolo = zahození opět k dispozici
        playerDiscardUsed.value = false

        // Zablokuj hráče – AI je na tahu
        gameState.value = old.copy(
            playerState  = player,
            aiState      = ai,
            activePlayer = ActivePlayer.AI
        )
        // Roguelike snapshot: čistý bod „po hráčově akci, začátek tahu AI".
        // Quit teď → obnova rozehraje tah AI znovu (nedá se zopakovat hráčův tah).
        snapshotRogueBattle("AI_TURN", aiDrawsAtStart, playerDrawsAtEnd, playerWaited)

        viewModelScope.launch(crashHandler) {
            delay((500L..1000L).random())

            // ── Tah AI ────────────────────────────────────────────────────────
            // AI dostane zdroje a líže 1 kartu na ZAČÁTKU svého tahu
            ai.generateResources()
            if (aiDrawsAtStart && ai.deck.isNotEmpty()) {
                SoundManager.playCardDraw()
                val drawResult = ai.drawCards(1, old.aiMaxHand)
                drawResult.burned.forEach { b ->
                    cardHistory.appendHistory(b, CardAction.BURNED, isMine = false)
                    addCardLog("AI", b, CardAction.BURNED, isMe = false)
                    // Overdraw: ukaž spálenou kartu v discard slotu (oranžový prstenec)
                    // a dej hráči 0,75 s na zaregistrování, že AI přetáhla
                    recordCard(b, CardAction.BURNED, isPlayer = false)
                    gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai.deepCopy(), activePlayer = ActivePlayer.AI)
                    delay(750L)
                }
                for (trap in drawResult.traps) {
                    reportTrap(trap, ai, isPlayer = false)
                    gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai.deepCopy(), activePlayer = ActivePlayer.AI)
                    delay(1000L)
                }
                // Zkontroluj výhru ihned po výbuchu — AI mohla zemřít před prvním tahem
                if (drawResult.traps.isNotEmpty()) {
                    val afterTrap = old.copy(playerState = player.deepCopy(), aiState = ai.deepCopy(), activePlayer = ActivePlayer.AI)
                    afterTrap.checkWinCondition()?.let { result ->
                        scheduleGameEnd(result, afterTrap); return@launch
                    }
                }
            }
            transformShapeShifters(ai.hand, allCards)
            // Vizuální update Mirror karet v AI ruce – stejná logika jako u hráče.
            // Bez toho AI ruka vždy ukazuje plain Mirror art místo hráčovy poslední karty.
            updateMirrorCards(ai.hand, player.lastPlayedCard, allCards)
            updateCloneCards(ai.hand, ai.lastPlayedCard, allCards)

            // AI hraje v cyklu (podporuje combo karty)
            var aiContinues = true
            // Combo karta dopředu "nakouknutá" (viz níže) – použije se místo nového
            // aiChooseAction() volání, aby se predikce z peeku a skutečná akce nerozešly
            // (aiChooseAction má náhodný šum ve skóre, dvě volání by mohla dát jiný výsledek).
            var pendingAiChoice: AiAction? = null
            while (aiContinues) {
                // Transformuj Shapeshiftery líznuté uprostřed tahu (DrawCard, Decision apod.)
                // onlyNew = true → nemění formu již transformovaných instancí
                transformShapeShifters(ai.hand, allCards, onlyNew = true)
                val aiChoice = pendingAiChoice ?: aiChooseAction(ai, player, old.aiWinTarget, old.playerWinTarget)
                pendingAiChoice = null
                when (aiChoice) {
                    is AiAction.Play -> {
                        val aiCard = aiChoice.card
                        // Snapshot zdrojů PŘED zaplacením – viz hráčův playCard.
                        ai.preCostResources = ai.resources.toMap()
                        val aiXValue: Int
                        if (aiCard.isXCost) {
                            aiXValue = ai.resources[aiCard.costType] ?: 0
                            ai.resources[aiCard.costType] = 0
                        } else {
                            aiXValue = 0
                            ai.resources[aiCard.costType] = (ai.resources[aiCard.costType] ?: 0) - aiCard.effectiveCost
                        }
                        ai.lastPlayedType = aiCard.type
                        // lastPlayedCard se nastavuje AŽ PO applyEffects AI (viz níže)
                        // NextCardIsCombo: flag nastaven předchozí kartou AI
                        val aiNextComboBoost = ai.nextCardIsCombo
                        if (aiNextComboBoost) ai.nextCardIsCombo = false
                        // CloneNextPlayed: flag nastaven předchozí kartou AI → naklonuj tuto kartu do balíčku.
                        val aiCloneCount = ai.cloneNextPlayed
                        if (aiCloneCount != null && aiCloneCount > 0) {
                            repeat(aiCloneCount) {
                                ai.deck.add(aiCard.copy(id = "${aiCard.id}_clone_${java.util.UUID.randomUUID()}", isGenerated = true))
                            }
                            ai.deck.shuffle()
                            ai.cloneNextPlayed = null
                        }
                        // DrawPerCardPlayed: flag nastaven předchozí kartou AI → líz (s volitelným filtrem typu)
                        val aiDrawFilter = ai.drawCardOnPlay
                        if (aiDrawFilter != null && (aiDrawFilter.isEmpty() || aiDrawFilter == aiCard.type)) { ai.drawCards(1); SoundManager.playCardDraw() }
                        // GainResourcePerCardPlayed: přidej zdroje nastavené předchozí kartou AI (s filtrem typu)
                        for (grp in ai.gainResourcePerCardPlayed) {
                            if (grp.cardType == null || grp.cardType == aiCard.type) {
                                ai.resources[grp.type] = ((ai.resources[grp.type] ?: 0) + grp.amount).coerceAtMost(MAX_RESOURCE)
                            }
                        }
                        // GainCastlePerCardPlayed: přidej HP hradu nastavené předchozí kartou AI (s filtrem typu)
                        for (gcpp in ai.gainCastlePerCardPlayed) {
                            if (gcpp.cardType == null || gcpp.cardType == aiCard.type) {
                                ai.castleHP = (ai.castleHP + gcpp.amount).coerceAtMost(100)
                            }
                        }
                        // Odeber kartu z ruky PŘED efekty – stejně jako hráčův playCard.
                        // Bez tohoto pořadí by SwapHands viděl zahrávanou kartu v ruce AI
                        // a předal ji hráči místo do discardu.
                        val aiCardHandIdx = ai.hand.indexOf(aiCard)
                        ai.hand.remove(aiCard)
                        ai.discardPile.add(aiCard)
                        // Přelíznutí nasbíraná během efektů. Nezobrazují se hned:
                        // slot ve středu si nejdřív musí vzít AI zahraná karta
                        // (recordCard níž), jinak by ji plamen okamžitě přebil a
                        // hráč by nevěděl, CO ho přelíznutí stálo.
                        val aiOverdrawBurns = mutableListOf<Pair<Card, Boolean>>()
                        applyEffects(
                            aiCard.effects, ai, player, allCards, xValue = aiXValue,
                            onOpponentCardLost = { card, action -> recordOpponentLoss(card, action) },
                            onDrawCard = { state, count ->
                                repeat(count) {
                                    val r = state.drawCards(1, old.aiMaxHand)
                                    SoundManager.playCardDraw()
                                    r.burned.forEach { b ->
                                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = false)
                                        addCardLog("AI", b, CardAction.BURNED, isMe = false)
                                    }
                                    r.traps.forEach { trap ->
                                        reportTrap(trap, state, isPlayer = false)
                                    }
                                }
                            },
                            maxHandSize = old.aiMaxHand,
                            onSelfCardLost = { lostCard, action ->
                                // AI vlastní zahozené karty (např. Velký zmatek zahrané AI)
                                cardHistory.appendHistory(lostCard, action, isMine = false)
                                addCardLog("AI", lostCard, action, isMe = false)
                            },
                            opponentMaxHandSize = old.playerMaxHand,
                            // isSelf = true → přeteklo AI; false → přeteklo hráči
                            onOverdrawBurn = { burned, isSelf ->
                                aiOverdrawBurns.add(burned to !isSelf)
                            }
                        )
                        // AI auto-pick pro Decision efekty.
                        // Mirror/Clone: Decision efekty kopírované karty nejsou v aiCard.effects
                        // a applyEffects je záměrně přeskakuje (/* řeší ViewModel */). Musíme je
                        // přidat ručně, jinak AI nikdy nespustí ukradení karty / výběr z balíčku.
                        val aiDecisionSrcEffects: List<CardEffect> = when {
                            aiCard.effects.any { it is CardEffect.Mirror } ->
                                player.lastPlayedCard?.effects
                                    ?.filter { it is CardEffect.DecisionBurnOpponent || it is CardEffect.DecisionChooseType  ||
                                               it is CardEffect.DecisionFromDiscard  || it is CardEffect.DecisionFromDeck    ||
                                               it is CardEffect.DecisionDrawFromDeck || it is CardEffect.DecisionMine        ||
                                               it is CardEffect.SmartJoker           || it is CardEffect.PeekAndStealHand   ||
                                               it is CardEffect.DecisionChooseResource }
                                    ?: emptyList()
                            aiCard.effects.any { it is CardEffect.Clone } ->
                                ai.lastPlayedCard?.effects
                                    ?.filter { it is CardEffect.DecisionBurnOpponent || it is CardEffect.DecisionChooseType  ||
                                               it is CardEffect.DecisionFromDiscard  || it is CardEffect.DecisionFromDeck    ||
                                               it is CardEffect.DecisionDrawFromDeck || it is CardEffect.DecisionMine        ||
                                               it is CardEffect.SmartJoker           || it is CardEffect.PeekAndStealHand   ||
                                               it is CardEffect.DecisionChooseResource }
                                    ?: emptyList()
                            else -> emptyList()
                        }
                        for (fx in aiCard.effects + aiDecisionSrcEffects) {
                            when (fx) {
                                is CardEffect.DecisionBurnOpponent -> {
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.firstOrNull()?.let { chosen ->
                                        player.deck.remove(chosen)
                                        player.discardPile.add(chosen)
                                        log.appendLog(ls.logAiDiscardFromDeck.format(chosen.displayName))
                                    }
                                }
                                is CardEffect.DecisionChooseType -> {
                                    buildDecisionOptions(fx, ai, player).firstOrNull()?.let { chosen ->
                                        val newCard = chosen.copy(
                                            id           = "${chosen.id}_${java.util.UUID.randomUUID()}",
                                            isGenerated  = true,
                                            costModifier = -fx.costReduction
                                        )
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(newCard)
                                    }
                                }
                                is CardEffect.DecisionFromDiscard -> {
                                    val opts = buildDecisionOptions(fx, ai, player, excludeId = aiCard.id)
                                    opts.firstOrNull()?.let { chosen ->
                                        ai.discardPile.remove(chosen)
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(chosen)
                                    }
                                }
                                is CardEffect.DecisionFromDeck -> {
                                    // Kopie – originál zůstane v balíčku
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.firstOrNull()?.let { chosen ->
                                        val copy = chosen.copy(id = "${chosen.id}_${java.util.UUID.randomUUID()}", isGenerated = true)
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(copy)
                                    }
                                }
                                is CardEffect.DecisionDrawFromDeck -> {
                                    // Pravý draw – karta opustí balíček
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.firstOrNull()?.let { chosen ->
                                        ai.deck.remove(chosen)
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(chosen)
                                    }
                                }
                                is CardEffect.DecisionMine -> {
                                    // AI si vybere důl, který mu nejvíce chybí
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    val best = opts.minByOrNull { card ->
                                        val mineEffect = card.effects.filterIsInstance<CardEffect.AddMine>().firstOrNull()
                                        ai.mines[mineEffect?.type] ?: 0
                                    }
                                    best?.let { chosen ->
                                        val newCard = chosen.copy(
                                            id          = "${chosen.id}_${java.util.UUID.randomUUID()}",
                                            isGenerated = true
                                        )
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(newCard)
                                    }
                                }
                                is CardEffect.SmartJoker -> {
                                    // AI si vybere nejvhodnější kartu ze 4 nabídnutých
                                    val opts = buildDecisionOptions(fx, ai, player, selfWinTarget = old.aiWinTarget)
                                    opts.maxByOrNull { scoreCardForSituation(it, ai, player, old.aiWinTarget) }
                                        ?.let { chosen ->
                                            val newCard = chosen.copy(
                                                id          = "${chosen.id}_${java.util.UUID.randomUUID()}",
                                                isGenerated = true
                                            )
                                            if (ai.hand.size < old.aiMaxHand) ai.hand.add(newCard)
                                            log.appendLog(ls.logAiJoker.format(chosen.displayName))
                                        }
                                }
                                is CardEffect.PeekAndStealHand -> {
                                    // AI ukradne kartu s nejvyšší hodnotou z ruky hráče
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.maxByOrNull { scoreCardForSituation(it, ai, player, old.aiWinTarget) }
                                        ?.let { chosen ->
                                            player.hand.remove(chosen)
                                            val stolen = chosen.copy(id = "${chosen.id}_stolen_${java.util.UUID.randomUUID()}", isGenerated = true)
                                            if (ai.hand.size < old.aiMaxHand) ai.hand.add(stolen)
                                            log.appendLog(ls.logAiStoleFromHand.format(chosen.displayName))
                                            recordOpponentLoss(chosen, CardAction.STOLEN)
                                        }
                                }
                                is CardEffect.DecisionChooseResource -> {
                                    // AI si vybere surovinu, které má nejméně
                                    val best = fx.options.minByOrNull { ai.resources[it.type] ?: 0 }
                                    if (best != null) {
                                        ai.resources[best.type] = ((ai.resources[best.type] ?: 0) + best.amount).coerceAtMost(MAX_RESOURCE)
                                        log.appendLog(ls.logAiChoseResource.format(best.amount, resLabel(best.type)))
                                    }
                                }
                                else -> {}
                            }
                        }
                        ai.preCostResources = null
                        // lastPlayedCard PO applyEffects – Klon nečte sám sebe
                        if (aiCard.costType == ResourceType.ATTACK) ai.attackCardsThisTurn++
                        val prevAiLastPlayed = ai.lastPlayedCard
                        ai.lastPlayedCard = aiCard
                        // Mirror/Klon: aktualizuj na efektivně kopírovanou kartu (stejná logika jako u hráče)
                        if (aiCard.effects.any { it is CardEffect.Mirror } && player.lastPlayedCard != null) {
                            ai.lastPlayedCard = player.lastPlayedCard
                        } else if (aiCard.effects.any { it is CardEffect.Clone } && prevAiLastPlayed != null) {
                            ai.lastPlayedCard = prevAiLastPlayed
                        }
                        resetMirrorCloneInPile(ai.discardPile, aiCard, allCards)
                        updateCloneCards(ai.hand, ai.lastPlayedCard, allCards)
                        updateMirrorCards(player.hand, ai.lastPlayedCard, allCards)
                        addReplayFrame(old.copy(playerState = player, aiState = ai), aiCard, isPlayer = false, action = CardAction.PLAYED)
                        recordCard(aiCard, CardAction.PLAYED, isPlayer = false)
                        addCardLog("AI", aiCard, CardAction.PLAYED, isMe = false)
                        revealedAiCard.value    = aiCard
                        revealedAiCardIdx.value = aiCardHandIdx.takeIf { it >= 0 }
                        playSoundForCard(aiCard)

                        // Až teď ukaž přelíznutí — hráč nejdřív uvidí, CO soupeř
                        // zahrál (Studna vědomostí), pak teprve kartu, která mu
                        // kvůli plné ruce shořela.
                        for ((burned, isPlayerCard) in aiOverdrawBurns) {
                            delay(650L)
                            showOverdrawBurn(burned, isPlayer = isPlayerCard)
                            SoundManager.playCardDraw()
                        }

                        if (aiCard.isCombo || aiNextComboBoost) {
                            // Combo: pauza + mezistate + pokračuj.
                            // activePlayer musí zůstat AI, aby hráč nemohl kliknout
                            // v okně delay a nespustil druhou souběžnou finishTurn coroutinu.
                            // deepCopy zajistí, že Compose detekuje změny (AddToOpponentDeck, atd.)
                            val mid = old.copy(
                                playerState  = player.deepCopy(),
                                aiState      = ai.deepCopy(),
                                activePlayer = ActivePlayer.AI
                            )
                            mid.checkWinCondition()?.let { result ->
                                scheduleGameEnd(result, mid); return@launch
                            }
                            gameState.value = mid   // vizuální mezistav
                            // Nakoukni, jestli combo pokračuje další kartou, nebo tahle byla
                            // poslední (AI teď skončí kolo) – použije se místo nového
                            // aiChooseAction() volání na začátku příští iterace (viz pendingAiChoice výš).
                            val nextChoice = aiChooseAction(ai, player, old.aiWinTarget, old.playerWinTarget)
                            pendingAiChoice = nextChoice
                            // Další karta comba přijde → 1s, aby ji hráč stihl přečíst.
                            // Tahle byla poslední → jen 0,5s, není na co čekat.
                            delay(if (nextChoice is AiAction.Play) 1000L else 500L)
                        } else {
                            aiContinues = false     // normální karta → konec tahu AI
                        }
                    }
                    is AiAction.Wait -> {
                        // AI čeká = přeskočí tah
                        log.appendLog(ls.logAiWaited)
                        aiContinues = false
                        // Oba přeskočili kolo a oba mají prázdný balíček → rozhodne hrad.
                        // Stačí prázdné BALÍČKY – hráč mohl projít s kartami v ruce a zvolit čekat.
                        if (playerWaited && player.deck.isEmpty() && ai.deck.isEmpty())
                        {
                            val finalState = old.copy(playerState = player, aiState = ai)
                            val result = finalState.resolveByHp()
                            log.appendLog(ls.logBothPassedEmpty)
                            scheduleGameEnd(result, finalState)
                            return@launch
                        }
                    }
                    is AiAction.Discard -> {
                        val toDiscard = aiChoice.card
                        ai.hand.remove(toDiscard)
                        ai.discardPile.add(toDiscard)
                        addReplayFrame(old.copy(playerState = player, aiState = ai), toDiscard, isPlayer = false, action = CardAction.DISCARDED)
                        recordCard(toDiscard, CardAction.DISCARDED, isPlayer = false)
                        addCardLog("AI", toDiscard, CardAction.DISCARDED, isMe = false)

                        // ── Mechanika "Zahození": efekty spuštěné zahozením AI kartou ──
                        if (toDiscard.discardEffects.isNotEmpty()) {
                            log.appendLog(ls.logDiscardEffectTriggered.format(toDiscard.displayName))
                            applyEffects(toDiscard.discardEffects, ai, player, allCards,
                                onOpponentCardLost = { card, action -> recordOpponentLoss(card, action) },
                                maxHandSize = old.aiMaxHand,
                                opponentMaxHandSize = old.playerMaxHand,
                                onOverdrawBurn = { burned, isSelf ->
                                    showOverdrawBurn(burned, isPlayer = !isSelf)
                                },
                                onSelfCardLost = { lostCard, action ->
                                    cardHistory.appendHistory(lostCard, action, isMine = false)
                                    addCardLog("AI", lostCard, action, isMe = false)
                                })
                            val midDiscard = old.copy(playerState = player.deepCopy(), aiState = ai.deepCopy(), activePlayer = ActivePlayer.AI)
                            midDiscard.checkWinCondition()?.let { result ->
                                scheduleGameEnd(result, midDiscard); return@launch
                            }
                        }
                        aiContinues = false
                    }
                }
            }

            // ── Kontrola výhry po tahu AI ─────────────────────────────────────
            // activePlayer = AI i v mezistavu, aby hráč nemohl kliknout v okně delay
            // a nespustil druhou souběžnou finishTurn coroutinu (primární příčina pádu).
            // deepCopy zajistí, že Compose vždy detekuje změnu (reference equality pro PlayerState).
            // Bez deepCopy by se stav nezobrazil správně pokud AI mutovala player/ai in-place
            // (např. AddToOpponentDeck – player.deck se změní, ale reference zůstane stejná).
            val playerSnap = player.deepCopy()
            val aiSnap     = ai.deepCopy()
            val s2 = old.copy(
                playerState  = playerSnap,
                aiState      = aiSnap,
                activePlayer = ActivePlayer.AI
            )
            s2.checkWinCondition()?.let { result ->
                scheduleGameEnd(result, s2); return@launch
            }

            // ── Mezistav: hráč chvíli vidí AI s méně kartami (po sehrání, před lízem) ──
            gameState.value = s2
            delay(350L)

            // ── Konec kola: příprava hráčova tahu ────────────────────────────
            player.generateResources()
            if (playerDrawsAtEnd && player.deck.isNotEmpty()) {
                // zvuk líznutí hraje animace příletu karty (HandPanel)
                val drawResult = player.drawCards(1, old.playerMaxHand)
                drawResult.burned.forEach { b ->
                    cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                    addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                    // Overdraw: ukaž spálenou kartu v discard slotu (oranžový prstenec)
                    recordCard(b, CardAction.BURNED, isPlayer = true)
                }
                for (trap in drawResult.traps) {
                    reportTrap(trap, player, isPlayer = true)
                    gameState.value = old.copy(playerState = player.deepCopy(), aiState = ai.deepCopy())
                    delay(1000L)
                }
            }
            // Quick draw: extra karta na hráčově prvním tahu (pokud AI šla první)
            if (!quickDrawUsed) {
                val actives = PlayerProfileManager.profile
                    ?.activeAbilities?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList()
                if (PassiveAbility.QUICK_DRAW in actives && player.deck.isNotEmpty()) {
                    quickDrawUsed = true
                    val qdr2 = player.drawCards(1, old.playerMaxHand)
                    qdr2.burned.forEach { b ->
                        cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                        addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                        recordCard(b, CardAction.BURNED, isPlayer = true)
                    }
                    // zvuk líznutí hraje animace příletu karty (HandPanel)
                }
            }

            transformShapeShifters(player.hand, allCards)
            // Zrcadlo + Klon: refresh vizuálu na začátku každého hráčova tahu
            updateMirrorCards(player.hand, ai.lastPlayedCard, allCards)
            updateCloneCards(player.hand, player.lastPlayedCard, allCards)

            // Speciální případ: obě strany nemají vůbec nic (ruka + balíček prázdné).
            // Stává se, když hráč zahodí poslední kartu a AI nemá nic.
            // Bez tohoto hlídání dostane hráč tah s prázdnou rukou a hrou nejde hnout.
            if (player.hand.isEmpty() && player.deck.isEmpty()
                && ai.hand.isEmpty()  && ai.deck.isEmpty())
            {
                val finalState = old.copy(
                    playerState  = player.deepCopy(),
                    aiState      = ai.deepCopy(),
                    currentTurn  = old.currentTurn + 1,
                    activePlayer = ActivePlayer.PLAYER
                )
                val result = finalState.resolveByHp()
                log.appendLog(ls.logBothNoCards)
                scheduleGameEnd(result, finalState)
                return@launch
            }

            // Reset per-card-played efektů – platí jen pro toto kolo
            player.drawCardOnPlay = null;               ai.drawCardOnPlay = null
            player.gainResourcePerCardPlayed.clear();   ai.gainResourcePerCardPlayed.clear()
            player.gainCastlePerCardPlayed.clear();     ai.gainCastlePerCardPlayed.clear()
            player.cloneNextPlayed = null;              ai.cloneNextPlayed = null
            player.attackCardsThisTurn = 0;  ai.attackCardsThisTurn = 0
            player.nextCardIsCombo = false;  ai.nextCardIsCombo = false

            // Kontrola po lízu: balíčky mohly dojít právě teď
            val s3 = old.copy(
                playerState  = player.deepCopy(),
                aiState      = ai.deepCopy(),
                currentTurn  = old.currentTurn + 1,
                activePlayer = ActivePlayer.PLAYER
            )
            s3.checkWinCondition()?.let { result ->
                scheduleGameEnd(result, s3); return@launch
            }

            // Auto-pass: hráč nemá žádné karty (ruka + balíček prázdné) a nemůže nic dělat.
            // Po krátké pauze (aby hráč viděl stav) automaticky přeskočíme jeho tah.
            if (player.hand.isEmpty() && player.deck.isEmpty()) {
                // KRITICKÉ: nastav activePlayer = AI HNED, ne PLAYER jako v normální větvi níže.
                // endPlayerTurn()/waitTurn()/discardCard()/playCard() všechny kontrolují jen
                // "old.activePlayer == PLAYER" jako guard. Kdyby tu zůstal PLAYER (jak dřív),
                // hráč má ~1,3s okno (delay níže), kde může kliknout Wait/Ukončit tah a odpálit
                // DRUHOU souběžnou finishTurn korutinu vedle téhle čekající. Dvě souběžné AI
                // kola pak hrají "přes sebe" (nezávislé kopie stavu) – vypadá to jako AI hraje
                // opakovaně stejné karty a může to nezávisle 2× vyhodnotit konec hry různým
                // výsledkem (proto "prohra" zvuk, který se pak přepíše na finální "výhru").
                gameState.value = s3.copy(activePlayer = ActivePlayer.AI)
                delay(700L)
                log.appendLog(ls.logPlayerSkip)
                // Dokud hráč nemá ruku ani balíček, tento blok se volá znovu na konci
                // KAŽDÉHO dalšího AI kola (finishTurn → tenhle stejný auto-pass) – bez
                // pauzy tu na sebe kola nabalují prakticky bez přestávky a AI hraní
                // (často opakovaně stejné karty ze zbytku ruky) vypadá jako trhaný glitch.
                delay(600L)
                val autoPlayer = s3.playerState.deepCopy()
                val autoAi     = s3.aiState.deepCopy()
                finishTurn(s3, autoPlayer, autoAi, playerWaited = true)
            } else {
                gameState.value = s3
                // Roguelike snapshot: čistý bod „začátek hráčova tahu" (idle).
                snapshotRogueBattle("PLAYER_IDLE")
            }
        }
    }

    /**
     * Nastaví finální stav hry, přehraje zvuk a po 1s odhalí game-over overlay.
     * Během prodlevy je [gameEndPending] = true → UI blokuje veškerý vstup.
     */
    private fun scheduleGameEnd(result: GameResult, snapshot: GameState) {
        // Ulož replay před koncem hry
        val profile = PlayerProfileManager.profile
        val opp     = activeCampaignOpponent.value
        ReplayManager.lastReplay = GameReplay(
            frames          = replayFrames.toList(),
            playerName      = profile?.name   ?: ls.logActorPlayer,
            playerAvatar    = profile?.avatar ?: "player_icon_1",
            opponentName    = opp?.name       ?: ls.enemy,
            opponentAvatar  = opp?.avatar     ?: randomEnemyAvatar(),
            result          = result,
            playerWinTarget = snapshot.playerWinTarget,
            aiWinTarget     = snapshot.aiWinTarget
        )
        gameState.value = snapshot
        gameEndPending.value = true
        if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
        gameEndJob?.cancel()
        gameEndJob = viewModelScope.launch(crashHandler) {
            kotlinx.coroutines.delay(1750L)
            gameOver.value = result
            gameEndPending.value = false
        }
    }

    /** Karta ztracena hráčem kvůli efektu AI (BurnCard / StealCard). */
    private fun recordOpponentLoss(card: Card, action: CardAction) {
        cardHistory.appendHistory(card, action, isMine = true)
        val list = lostToOpponent.value.toMutableList()
        list.add(0, CardHistoryEntry(card, action, isMine = true))
        lostToOpponent.value = list
        addCardLog("AI", card, action, isMe = false)
    }

    // addLog → log.appendLog() z GameLogManager.kt
    // addToHistory → cardHistory.appendHistory() z GameLogManager.kt

    /**
     * Po explozi pasti přidá do odkladiště [state] jednu kopii "Explodovaná bomba" (C38).
     * Vrátí instanci karty pro případné zalogování do card history, nebo null pokud C38 neexistuje.
     */
    /**
     * Lízla se past (TrapOnDraw) — efekt už proběhl v [PlayerState.drawCards].
     *
     * Navenek se hlásí POUZE exploze ("Explodovaná bomba", C38). Samotná
     * "Bomba" (C37) je jen placeholder v balíčku, aby ji šlo cíleně odstranit
     * (Likvidace) — jako událost se neukazuje. Dřív se logovaly obě, takže
     * jedno líznutí vypadalo jako dvě spálené karty.
     *
     * Skutečnou příčinu i dopad vysvětlí systémová hláška [trapLogMsg], která
     * pastí pojmenuje ("Lízl jsi Bombu — hrad −5").
     */
    private fun reportTrap(trap: Card, state: PlayerState, isPlayer: Boolean) {
        val explosion = injectExplosionPlaceholder(state)
        if (explosion != null) {
            // recordCard = střed bojiště + zápis do historie
            recordCard(explosion, CardAction.BURNED, isPlayer = isPlayer)
            addCardLog(if (isPlayer) "Hráč" else "AI", explosion, CardAction.BURNED, isMe = isPlayer)
        }
        log.appendLog(trapLogMsg(trap, isPlayer = isPlayer))
    }

    private fun injectExplosionPlaceholder(state: PlayerState): Card? {
        val template = allCards.find { it.id == "C38" } ?: return null
        val instance = template.copy(id = "C38_${java.util.UUID.randomUUID()}", isGenerated = true)
        state.discardPile.add(instance)
        return instance
    }

    /** Sestaví log zprávu pro explozi pasti (TrapOnDraw). */
    private fun trapLogMsg(card: Card, isPlayer: Boolean): String {
        val who = if (isPlayer) ls.logTrapDrewYou else ls.logTrapDrewAi
        // 4. pád (akuzativ) jen pro češtinu; jinak lokalizovaný název karty
        val cardName = if (ls.languageCode == "cs") (card.nameAccusative ?: card.displayName) else card.displayName
        val trap = card.effects.filterIsInstance<CardEffect.TrapOnDraw>().firstOrNull()
        val dmgDesc = when (val e = trap?.effect) {
            is CardEffect.AttackCastle -> "${ls.logTrapCastle} −${e.amount}"
            is CardEffect.AttackWall   -> "${ls.logTrapWall} −${e.amount}"
            is CardEffect.AttackPlayer -> "−${e.amount} ${ls.logTrapHp}"
            is CardEffect.BuildCastle  -> "${ls.logTrapCastle} +${e.amount}"
            else                       -> ls.logTrapTriggered
        }
        return "$who $cardName! $dmgDesc"
    }

    private fun addCardLog(actorName: String, card: Card, action: CardAction, isMe: Boolean) {
        val turn = gameState.value.currentTurn
        log.value = (log.value + LogEntry.CardEvent(actorName, card, action, isMe, turn)).takeLast(50)
    }

    /**
     * Ukáže ztracenou kartu uprostřed bojiště (prstenec + ikona podle akce).
     *
     * Na rozdíl od [recordCard] NEzapisuje do historie — tu už naplnily
     * onSelfCardLost / onOpponentCardLost v applyEffects, jinak by tam karta
     * byla dvakrát. Tohle je čistě zobrazení.
     */
    private fun showLostCard(card: Card, action: CardAction, isPlayer: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isPlayer
    }

    /** Přelíznutá karta ve středu bojiště s plamenem. */
    private fun showOverdrawBurn(card: Card, isPlayer: Boolean) =
        showLostCard(card, CardAction.BURNED, isPlayer)

    /** Postupné odhalování karet, o které soupeř přišel mým přičiněním. */
    private var oppLossRevealJob: Job? = null

    /**
     * Ukáže VŠECHNY karty, o které soupeř právě přišel — jednu po druhé.
     * Spálená knihovna pálí 2 karty, Prázdná mysl 3; kdyby se nastavovaly
     * v cyklu hned, hráč by uviděl jen tu poslední a nevěděl by, co soupeři
     * vlastně zničil.
     */
    private fun revealOpponentLosses(losses: List<Pair<Card, CardAction>>) {
        oppLossRevealJob?.cancel()
        if (losses.isEmpty()) return
        if (losses.size == 1) {
            val (card, action) = losses[0]
            showLostCard(card, action, isPlayer = false)
            return
        }
        oppLossRevealJob = viewModelScope.launch {
            losses.forEachIndexed { i, (card, action) ->
                if (i > 0) delay(700L)
                showLostCard(card, action, isPlayer = false)
            }
        }
    }

    private fun recordCard(card: Card, action: CardAction, isPlayer: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isPlayer
        cardHistory.appendHistory(card, action, isMine = isPlayer)
    }

    /** Zaznamená kartu ztracenou z AI ruky – viz [aiHandLossLog]. */
    private fun recordAiHandLoss(card: Card, action: CardAction) {
        aiHandLossLog.value = aiHandLossLog.value + CardHistoryEntry(card, action, isMine = false)
    }

    /** Přidá snímek do replay záznamu aktuální hry. */
    private fun addReplayFrame(state: GameState, card: Card?, isPlayer: Boolean, action: CardAction) {
        replayFrames.add(ReplayFrame(
            state      = state.snapshot(),
            card       = card,
            isPlayer   = isPlayer,
            action     = action,
            turnNumber = state.currentTurn
        ))
    }

    fun restartGame(randomDeck: Boolean = false, superRandom: Boolean = false) {
        gameEndJob?.cancel()
        gameEndPending.value    = false
        activeCampaignOpponent.value = null
        battleBackgroundResId.value  = randomBattleBackground()
        // Náhodný vzhled soupeřova hradu/hradby – "Vlastní balíček" i "Super-random"
        // volají restartGame() (nikoli campaign), takže sem spadá vždy mimo kampaň.
        opponentCastleResId.value = randomOpponentCastleResId()
        opponentWallResId.value   = randomOpponentWallResId()
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        revealedAiCard.value    = null
        revealedAiCardIdx.value = null
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        aiHandLossLog.value     = emptyList()
        isPlayerComboTurn.value = false
        playerDiscardUsed.value = false
        awaitingDecisionOverlay = false
        quickDrawUsed           = false
        // Zruš případně čekající Decision overlay ze staré hry (např. telefon se uspal
        // uprostřed výběru karty, uživatel spustil novou hru a stará Decision přežila).
        cancelDecisionTimer()
        pendingDecision.value   = null
        decisionPlayer          = null
        decisionAi              = null
        decisionOld             = null
        decisionEffect          = null
        decisionIsCombo         = false
        decisionPendingDraws    = 0
        replayFrames.clear()
        gameState.value         = createInitialState(randomDeck, superRandom)
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
        // Zaloguj AI schopnosti, aby hráč věděl, co AI dostala
        val aiAbilities = aiPassiveAbilities.value
        if (aiAbilities.isNotEmpty()) {
            val names = aiAbilities.joinToString(", ") { "${it.icon} ${it.title}" }
            log.value = listOf(LogEntry.SystemEvent("🤖 AI dostala schopnosti: $names"))
        }
    }

    /** Spustí bitvu v kampani proti danému soupeři. */
    fun startCampaignBattle(opponent: CampaignOpponent) {
        gameEndJob?.cancel()
        gameEndPending.value    = false
        activeCampaignOpponent.value = opponent
        // Goblinský tábor, Trpasličí hory a Temná citadela mají vlastní vzhled
        // bojiště; Dračí impérium zatím výchozí (žádné vlastní pozadí zatím nemá).
        battleBackgroundResId.value = when {
            opponent.id.startsWith("gob_") -> R.drawable.castle_background_goblin
            opponent.id.startsWith("dwf_") -> R.drawable.castle_background_winter
            opponent.id.startsWith("cit_") -> R.drawable.castle_background_citadela
            else                            -> R.drawable.castle_background
        }
        aiPassiveAbilities.value = emptyList()   // kampaňský soupeř má vlastní stats, ne náhodné pasivky
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        revealedAiCard.value    = null
        revealedAiCardIdx.value = null
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        aiHandLossLog.value     = emptyList()
        isPlayerComboTurn.value = false
        playerDiscardUsed.value = false
        awaitingDecisionOverlay = false
        quickDrawUsed           = false
        cancelDecisionTimer()
        pendingDecision.value   = null
        decisionPlayer          = null
        decisionAi              = null
        decisionOld             = null
        decisionEffect          = null
        decisionIsCombo         = false
        decisionPendingDraws    = 0
        replayFrames.clear()
        gameState.value         = createCampaignState(opponent)
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
    }

    /** Sestaví GameState pro kampaňovou bitvu s konkrétním soupeřem. */
    private fun createCampaignState(opponent: CampaignOpponent): GameState {
        val activeDeck  = decks[activeDeckIndex.value]
        val playerCards = if (activeDeck.isValid) activeDeck.toCardList(allCards)
                          else balancedDeck()

        // ── Pasivní schopnosti hráče ──────────────────────────────────────────
        val actives = PlayerProfileManager.profile
            ?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()

        val h = opponent.playerHandicap

        // ── Hráčův startovní stav ────────────────────────────────────────────
        val startCastle = 30 + (if (PassiveAbility.EXTRA_CASTLE in actives) 5 else 0) + h.extraCastle
        val startWall   = 15 + (if (PassiveAbility.EXTRA_WALL   in actives) 5 else 0) + h.extraWall
        val extraMagic  =      (if (PassiveAbility.EXTRA_MAGIC  in actives) 1 else 0) + h.extraMagic
        val extraAttack =      (if (PassiveAbility.EXTRA_ATTACK in actives) 1 else 0) + h.extraAttack
        val extraStones =      (if (PassiveAbility.EXTRA_STONES in actives) 1 else 0) + h.extraStones
        val extraChaos  =       if (PassiveAbility.EXTRA_CHAOS  in actives) 1 else 0

        // playerWinTarget: extra_castle pasiv přidá +5, pak se aplikuje nastavení ze soupeře
        val passiveCastleBonus = if (PassiveAbility.EXTRA_CASTLE    in actives) 5 else 0
        val playerWinTarget    = (opponent.winTarget + passiveCastleBonus).coerceAtMost(999)
        // aiWinTarget: iron_bastion pasiv přidá +5 na soupeřův cíl
        val aiWinTarget        = (opponent.aiWinTarget + if (PassiveAbility.IRON_BASTION in actives) 5 else 0).coerceAtMost(999)
        // playerMaxHand: extra_hand_card pasiv zvýší max. ruku na 8
        val playerMaxHand      = 7 + if (PassiveAbility.EXTRA_HAND_CARD in actives) 1 else 0

        val playerState = PlayerState(
            castleHP = startCastle.coerceAtLeast(5),
            wallHP   = startWall.coerceAtLeast(0)
        ).also {
            val finalMagic  = extraMagic.coerceAtLeast(0)
            val finalAttack = extraAttack.coerceAtLeast(0)
            val finalStones = extraStones.coerceAtLeast(0)
            if (finalMagic  > 0) it.resources[ResourceType.MAGIC]  = finalMagic
            if (finalAttack > 0) it.resources[ResourceType.ATTACK] = finalAttack
            if (finalStones > 0) it.resources[ResourceType.STONES] = finalStones
            if (extraChaos  > 0) it.resources[ResourceType.CHAOS]  = extraChaos
            // Hráčovy extra/malus doly z handicapu
            for ((resType, delta) in h.extraMines) {
                it.mines[resType] = ((it.mines[resType] ?: 1) + delta).coerceAtLeast(0)
            }
            // Posila balíčku z pasivních schopností
            fun boostCards(filter: (Card) -> Boolean, count: Int): List<Card> =
                allCards.filter { filter(it) && !it.isPlaceholder }.shuffled().take(count)

            val deckBoost = buildList {
                if (PassiveAbility.BOOST_ATTACK in actives)
                    addAll(boostCards({ it.type == "Útok" }, 2))
                if (PassiveAbility.BOOST_BUILD  in actives)
                    addAll(boostCards({ it.type == "Stavba" }, 2))
                if (PassiveAbility.BOOST_MAGIC  in actives)
                    addAll(boostCards({ it.type == "Magie" }, 2))
                if (PassiveAbility.BOOST_CHAOS  in actives)
                    addAll(boostCards({ it.type == "Chaos" }, 2))
                if (PassiveAbility.BOOST_RANDOM in actives)
                    addAll(boostCards({ it.type != "Důl" }, 3))
            }

            it.deck.addAll((playerCards + deckBoost.map { c -> c.copy(isGenerated = true) }).withUniqueIds().shuffled())
            it.drawCards(opponent.playerStartHandSize.coerceIn(1, 7))
        }

        // ── AI stav ───────────────────────────────────────────────────────────
        val aiCards = opponent.deckCardCounts
            .flatMap { (id, count) ->
                val card = allCards.find { it.id == id } ?: return@flatMap emptyList()
                List(count) { card }
            }
            .withUniqueIds()
            .shuffled()

        val aiState = PlayerState(
            castleHP = opponent.aiCastle,
            wallHP   = opponent.aiWall,
            maxWall  = opponent.aiMaxWall
        ).also {
            for ((resType, bonus) in opponent.aiExtraMines) {
                it.mines[resType] = (it.mines[resType] ?: 1) + bonus
            }
            if (opponent.aiStartMagic  > 0) it.resources[ResourceType.MAGIC]  = opponent.aiStartMagic
            if (opponent.aiStartAttack > 0) it.resources[ResourceType.ATTACK] = opponent.aiStartAttack
            if (opponent.aiStartStones > 0) it.resources[ResourceType.STONES] = opponent.aiStartStones
            it.deck.addAll(aiCards)
            it.drawCards(opponent.aiStartHandSize.coerceIn(1, 10))
        }

        return GameState(
            playerState     = playerState,
            aiState         = aiState,
            activePlayer    = if (Random.nextBoolean()) ActivePlayer.PLAYER else ActivePlayer.AI,
            playerWinTarget = playerWinTarget,
            aiWinTarget     = aiWinTarget,
            playerMaxHand   = playerMaxHand
        )
    }

    // ── Aréna ─────────────────────────────────────────────────────────────────
    var arenaPhase  = androidx.compose.runtime.mutableStateOf<ArenaPhase?>(null)
        private set
    val arenaDraft  = androidx.compose.runtime.mutableStateListOf<Card>()
    var arenaOffers = androidx.compose.runtime.mutableStateOf<List<Card>>(emptyList())
        private set
    var arenaWins   = androidx.compose.runtime.mutableStateOf(0)
        private set

    fun startArena() {
        arenaDraft.clear()
        arenaWins.value  = 0
        arenaPhase.value = ArenaPhase.DRAFT
        generateArenaOffers()
    }

    private fun generateArenaOffers() {
        arenaOffers.value = allCards.filter { !it.isPlaceholder && !it.isGenerated }.shuffled().take(3)
    }

    fun pickArenaCard(card: Card) {
        arenaDraft.add(card)
        if (arenaDraft.size >= 30) startArenaBattle() else generateArenaOffers()
    }

    private fun startArenaBattle() {
        arenaPhase.value = ArenaPhase.BATTLE
        activeCampaignOpponent.value = null
        battleBackgroundResId.value  = randomBattleBackground()
        replayFrames.clear()
        val ps = PlayerState().also {
            it.deck.addAll(arenaDraft.toList().withUniqueIds().shuffled())
            it.drawCards(4)
        }
        val ai = PlayerState().also {
            it.deck.addAll(balancedDeck().withUniqueIds().shuffled())
            it.drawCards(4)
        }
        gameState.value         = GameState(playerState = ps, aiState = ai)
        gameOver.value          = null
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        revealedAiCard.value    = null
        revealedAiCardIdx.value = null
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        aiHandLossLog.value     = emptyList()
        isPlayerComboTurn.value = false
        playerDiscardUsed.value = false
        awaitingDecisionOverlay = false
        quickDrawUsed           = false
        cancelDecisionTimer()
        pendingDecision.value   = null
        decisionPlayer          = null
        decisionAi              = null
        decisionOld             = null
        decisionEffect          = null
        decisionIsCombo         = false
        decisionPendingDraws    = 0
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
        log.value               = emptyList()
    }

    fun onArenaWin() {
        arenaWins.value++
        startArenaBattle()
    }

    fun onArenaLose() {
        arenaPhase.value = ArenaPhase.ENDED
    }

    fun exitArena() {
        arenaPhase.value = null
        arenaDraft.clear()
        arenaWins.value  = 0
    }

    // ── Roguelike ───────────────────────────────────────────────────────────────
    var roguePhase   = androidx.compose.runtime.mutableStateOf<RoguePhase?>(null); private set
    var rogueRun     = androidx.compose.runtime.mutableStateOf<RogueRun?>(null);   private set
    var rogueVictory = androidx.compose.runtime.mutableStateOf(false);             private set
    // Draft stav
    /** Sestavovaný roguelike balíček (deckbuilder). Compose ho pozoruje. */
    val rogueDraft = androidx.compose.runtime.mutableStateListOf<Card>()

    private fun loadRoguePresets() {
        roguePresets.forEachIndexed { i, preset ->
            val str = prefs.getString("rogue_preset_$i", "") ?: ""
            val counts = if (str.isNotEmpty()) {
                str.split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null)
                    else null
                }.toMap()
            } else emptyMap()
            roguePresets[i] = preset.copy(cardCounts = counts)
        }
    }

    /** Uloží aktuální rozestavěný draft (rogueDraft) do slotu [index], přepíše starý obsah. */
    fun saveRoguePreset(index: Int) {
        val counts = rogueDraft.groupingBy { it.baseId }.eachCount()
        roguePresets[index] = roguePresets[index].copy(cardCounts = counts)
        prefs.edit()
            .putString("rogue_preset_$index", counts.entries.joinToString(";") { "${it.key}:${it.value}" })
            .apply()
    }

    /** Nahradí rozestavěný draft obsahem uloženého slotu (respektuje vlastnictví/rozpočet). */
    fun loadRoguePreset(index: Int) {
        val counts = roguePresets[index].cardCounts
        if (counts.isEmpty()) return
        rogueDraft.clear()
        counts.forEach { (baseId, count) ->
            val card = allCards.find { it.id == baseId } ?: return@forEach
            repeat(count) { rogueAddCard(card) }
        }
    }

    fun startRoguelike() {
        rogueDraft.clear()
        rogueRun.value     = null
        rogueVictory.value = false
        roguePhase.value   = RoguePhase.DRAFT
    }

    /** Aktuálně utracené body rozpočtu (součet cen rarit v balíčku). */
    fun rogueBudgetSpent(): Int = rogueDraft.sumOf { RogueConfig.rarityBudgetCost(it.rarity) }

    /** Kolik kopií dané karty je právě v roguelike balíčku. */
    fun rogueCountOf(card: Card): Int = rogueDraft.count { it.baseId == card.baseId }

    /**
     * Přidá kartu do balíčku. Vrátí false (a nepřidá), pokud:
     *  – balíček je plný (DECK_SIZE),
     *  – nemáš víc kopií (vlastněný počet),
     *  – by přesáhla rozpočet.
     */
    fun rogueAddCard(card: Card): Boolean {
        if (rogueDraft.size >= RogueConfig.DECK_SIZE) return false
        val owned = CardCollectionManager.usableCopies(card)
        if (rogueCountOf(card) >= owned) return false
        if (rogueBudgetSpent() + RogueConfig.rarityBudgetCost(card.rarity) > RogueConfig.BUDGET) return false
        rogueDraft.add(card)
        return true
    }

    fun rogueRemoveCard(card: Card) {
        val idx = rogueDraft.indexOfLast { it.baseId == card.baseId }
        if (idx >= 0) rogueDraft.removeAt(idx)
    }

    /** Spustí run z hotového balíčku (musí mít přesně DECK_SIZE karet). */
    fun startRogueRun() {
        if (rogueDraft.size == RogueConfig.DECK_SIZE) beginRogueRun()
    }

    private fun beginRogueRun() {
        rogueRun.value = RogueRun(
            deck        = rogueDraft.toList(),
            maxCastle   = RogueConfig.START_MAX_CASTLE,
            hp          = RogueConfig.START_MAX_CASTLE,
            wall        = RogueConfig.START_WALL,
            battleIndex = 0
        )
        startRogueBattle()
    }

    /** Připraví a spustí bitvu proti generovanému soupeři pro aktuální battleIndex. */
    private fun startRogueBattle() {
        val run   = rogueRun.value ?: return
        val enemy = generateRogueEnemy(run)
        rogueRun.value   = run.copy(enemy = enemy)
        roguePhase.value = RoguePhase.BATTLE
        RogueSaveManager.clearBattle()   // čerstvá bitva → zahoď starý snapshot (nový vznikne po mulliganu)

        // Reset bojového stavu (zrcadlí startCampaignBattle)
        gameEndJob?.cancel()
        gameEndPending.value    = false
        activeCampaignOpponent.value = enemy       // → top bar ukáže jméno/avatar, potlačí obecný dialog
        // Roguelike soupeři jsou procedurální, ne vázaní na konkrétní kampaňovou lokaci → náhodné pozadí.
        battleBackgroundResId.value = randomBattleBackground()
        aiPassiveAbilities.value = emptyList()
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        revealedAiCard.value    = null
        revealedAiCardIdx.value = null
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        aiHandLossLog.value     = emptyList()
        isPlayerComboTurn.value = false
        playerDiscardUsed.value = false
        awaitingDecisionOverlay = false
        quickDrawUsed           = false
        cancelDecisionTimer()
        pendingDecision.value   = null
        decisionPlayer          = null
        decisionAi              = null
        decisionOld             = null
        decisionEffect          = null
        decisionIsCombo         = false
        decisionPendingDraws    = 0
        replayFrames.clear()
        gameState.value         = createRogueState(run, enemy)
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
        persistRogue()
    }

    /** Uloží aktuální stav runu na disk (přežije zabití procesu). */
    private fun persistRogue() {
        val run   = rogueRun.value   ?: return
        val phase = roguePhase.value ?: return
        RogueSaveManager.save(run, phase)
    }

    fun hasRogueSave(): Boolean = RogueSaveManager.hasSave()

    /** Obnoví uložený run. REWARD → odměna; BATTLE → přesně obnovená bitva (fér). */
    fun resumeRoguelike() {
        val saved = RogueSaveManager.load(allCards) ?: return
        rogueDraft.clear()
        rogueRun.value     = saved.run
        rogueVictory.value = false
        if (saved.phase == RoguePhase.REWARD) {
            if (saved.run.rewardCards.isEmpty() && saved.run.rewardCardPicksLeft > 0) {
                val offers = if (saved.run.rewardBonusAvailable) RogueConfig.REWARD_CARD_OFFERS_BOSS else RogueConfig.REWARD_CARD_OFFERS
                rogueRun.value = saved.run.copy(rewardCards = generateRogueRewardCards(saved.run, offers))
            }
            roguePhase.value = RoguePhase.REWARD
            return
        }
        // BATTLE: obnov přesný stav bitvy; jen když snapshot chybí NEBO je nekonzistentní
        // (např. hrad ≤ 0 – bitva už měla skončit dřív, než byl snapshot pořízen),
        // restartuj aktuální bitvu znovu (postup runu zůstává zachován).
        val battle = RogueSaveManager.loadBattle(allCards)
        val valid  = battle != null &&
            battle.game.playerState.castleHP > 0 &&
            battle.game.aiState.castleHP > 0
        if (valid) restoreRogueBattle(battle!!) else {
            RogueSaveManager.clearBattle()
            startRogueBattle()
        }
    }

    /** Uloží přesný stav rozehrané bitvy (jen ve fázi BATTLE, v čistém bodě tahu). */
    private fun snapshotRogueBattle(turnPhase: String, aiDraws: Boolean = true, playerDraws: Boolean = true, playerWaited: Boolean = false) {
        if (roguePhase.value != RoguePhase.BATTLE) return
        val enemy = rogueRun.value?.enemy
        val save = RogueBattleSave(
            turnPhase        = turnPhase,
            game             = gameState.value,
            quickDrawUsed    = quickDrawUsed,
            aiDrawsAtStart   = aiDraws,
            playerDrawsAtEnd = playerDraws,
            playerWaited     = playerWaited,
            lastCard         = lastCard.value,
            lastCardAction   = lastCardAction.value,
            lastCardByPlayer = lastCardIsPlayer.value,
            enemyName        = enemy?.name   ?: "Soupeř",
            enemyAvatar      = enemy?.avatar ?: "enemy_icon_1"
        )
        RogueSaveManager.saveBattle(RogueBattleCodec.toJson(save))
    }

    /** Obnoví bitvu ze snapshotu – přesně tam, kde skončila. */
    private fun restoreRogueBattle(b: RogueBattleSave) {
        val run = rogueRun.value ?: return
        // Soupeř jen pro top bar + budoucí snapshoty (jméno/avatar ze snapshotu;
        // skutečný herní stav – deck/staty – je v b.game.aiState, ne tady).
        // Uloženo i zpět do rogueRun.enemy, aby další snapshotRogueBattle() v tomto
        // běhu (další hráčova akce) mělo odkud vzít jméno/avatar – jinak by po
        // obnově druhý snapshot spadl na fallback "Soupeř".
        val enemyShell = generateRogueEnemy(run).copy(name = b.enemyName, avatar = b.enemyAvatar)
        activeCampaignOpponent.value = enemyShell
        rogueRun.value   = run.copy(enemy = enemyShell)
        aiPassiveAbilities.value = emptyList()
        roguePhase.value = RoguePhase.BATTLE

        gameEndJob?.cancel(); gameEndPending.value = false
        gameOver.value          = null
        isMulligan.value        = false
        mulliganSelected.value  = emptySet()
        isPlayerComboTurn.value = false
        playerDiscardUsed.value = false
        awaitingDecisionOverlay = false
        quickDrawUsed           = b.quickDrawUsed
        cancelDecisionTimer()
        pendingDecision.value   = null
        decisionPlayer = null; decisionAi = null; decisionOld = null
        decisionEffect = null; decisionIsCombo = false; decisionPendingDraws = 0
        revealedAiCard.value = null; revealedAiCardIdx.value = null
        cardHistory.value = emptyList(); lostToOpponent.value = emptyList(); aiHandLossLog.value = emptyList()
        log.value = emptyList(); replayFrames.clear()
        lastCard.value         = b.lastCard
        lastCardAction.value   = b.lastCardAction
        lastCardIsPlayer.value = b.lastCardByPlayer

        // Obnov morph vzhled (Zrcadlo/Klon/Shapeshifter) z lastPlayedCard
        val g = b.game
        transformShapeShifters(g.playerState.hand, allCards)
        updateMirrorCards(g.playerState.hand, g.aiState.lastPlayedCard, allCards)
        updateCloneCards(g.playerState.hand, g.playerState.lastPlayedCard, allCards)
        transformShapeShifters(g.aiState.hand, allCards)
        updateMirrorCards(g.aiState.hand, g.playerState.lastPlayedCard, allCards)
        updateCloneCards(g.aiState.hand, g.aiState.lastPlayedCard, allCards)

        gameState.value = g

        if (b.turnPhase == "AI_TURN") {
            // Pokračuj tahem AI (znovu se rozehraje – reziduum "AI re-roll")
            finishTurn(g, g.playerState.deepCopy(), g.aiState.deepCopy(),
                       aiDrawsAtStart = b.aiDrawsAtStart, playerDrawsAtEnd = b.playerDrawsAtEnd, playerWaited = b.playerWaited)
        }
        // PLAYER_IDLE → čeká na vstup hráče
    }

    /** GameState pro roguelike bitvu: hráč startuje s aktuálním run HP + run balíčkem. */
    private fun createRogueState(run: RogueRun, enemy: CampaignOpponent): GameState {
        val playerCards = run.deck.withUniqueIds().shuffled()
        val playerState = PlayerState(
            castleHP = run.hp.coerceAtLeast(1),
            wallHP   = run.wall.coerceAtLeast(0)     // hradby = run stat (reset každou bitvu)
        ).also {
            for ((t, d) in run.bonusMines) it.mines[t] = ((it.mines[t] ?: 1) + d).coerceAtLeast(0)
            it.deck.addAll(playerCards)
            it.drawCards(4)
        }

        val aiCards = enemy.deckCardCounts.flatMap { (id, count) ->
            val c = allCards.find { it.id == id } ?: return@flatMap emptyList()
            List(count) { c }
        }.withUniqueIds().shuffled()

        val aiState = PlayerState(castleHP = enemy.aiCastle, wallHP = enemy.aiWall).also {
            for ((t, b) in enemy.aiExtraMines) it.mines[t] = (it.mines[t] ?: 1) + b
            if (enemy.aiStartMagic  > 0) it.resources[ResourceType.MAGIC]  = enemy.aiStartMagic
            if (enemy.aiStartAttack > 0) it.resources[ResourceType.ATTACK] = enemy.aiStartAttack
            if (enemy.aiStartStones > 0) it.resources[ResourceType.STONES] = enemy.aiStartStones
            it.deck.addAll(aiCards)
            it.drawCards(enemy.aiStartHandSize.coerceIn(1, 10))
        }

        return GameState(
            playerState     = playerState,
            aiState         = aiState,
            activePlayer    = if (Random.nextBoolean()) ActivePlayer.PLAYER else ActivePlayer.AI,
            playerWinTarget = enemy.winTarget,
            aiWinTarget     = enemy.aiWinTarget,
            playerMaxHand   = 7
        )
    }

    /** Volá se z onGameEnd v roguelike bitvě. */
    fun onRogueBattleEnd(win: Boolean) {
        val run = rogueRun.value ?: return
        RogueSaveManager.clearBattle()   // bitva skončila → snapshot už neplatí
        if (!win) {
            rogueVictory.value = false
            roguePhase.value   = RoguePhase.ENDED
            persistRogue()   // ENDED → save() smaže uložený run
            return
        }
        // Zastropování HP: přenes koncový hrad, cap na maxCastle, + malý heal
        val endCastle = gameState.value.playerState.castleHP
        val newHp     = (minOf(run.maxCastle, endCastle) + RogueConfig.AUTO_HEAL_ON_WIN)
                            .coerceIn(1, run.maxCastle)
        val nextIndex = run.battleIndex + 1

        if (nextIndex >= RogueConfig.TOTAL_BATTLES) {
            rogueRun.value     = run.copy(hp = newHp, battleIndex = nextIndex, enemy = null)
            rogueVictory.value = true
            roguePhase.value   = RoguePhase.ENDED
            persistRogue()
            return
        }
        val wasBoss = run.isBoss   // musí se přečíst PŘED battleIndex++ (run.isBoss se počítá z aktuálního indexu)
        rogueRun.value   = run.copy(
            hp = newHp, battleIndex = nextIndex, enemy = null,
            rewardCards = generateRogueRewardCards(
                run, if (wasBoss) RogueConfig.REWARD_CARD_OFFERS_BOSS else RogueConfig.REWARD_CARD_OFFERS
            ),
            rewardCardPicksLeft  = if (wasBoss) RogueConfig.REWARD_CARD_PICKS_BOSS else RogueConfig.REWARD_CARD_PICKS,
            rewardBonusAvailable = wasBoss
        )
        roguePhase.value = RoguePhase.REWARD
        persistRogue()
    }

    /**
     * Kartové nabídky do odměny – z HRÁČOVY KOLEKCE, respektuje vlastněné kopie.
     * Přednostně jen Rare+ (odměna má být citelný skok síly), Common jen jako
     * záchranná síť, kdyby hráč měl vykoupené skoro všechny Rare+ karty.
     */
    private fun generateRogueRewardCards(run: RogueRun, offerCount: Int): List<Card> {
        val counts = run.deck.groupingBy { it.baseId }.eachCount()
        fun poolFrom(minRarity: Rarity) = allCards.filter {
            val owned = CardCollectionManager.usableCopies(it)
            !it.isPlaceholder && !it.isGenerated && owned > 0 &&
            (counts[it.baseId] ?: 0) < owned && it.rarity >= minRarity
        }
        val rarePlus = poolFrom(Rarity.RARE)
        val pool = if (rarePlus.size >= offerCount) rarePlus else poolFrom(Rarity.COMMON)
        return pool.shuffled().take(offerCount)
    }

    /**
     * Povinný krok odměny: vybere 1 z nabízených karet. NELZE přeskočit – dokud
     * [RogueRun.rewardCardPicksLeft] neklesne na 0, hra nejde dál. Teprve pak
     * (jen po bossovi) přijde volitelný bonus, jinak rovnou další bitva.
     * Po každém výběru se nabídka celá OBNOVÍ (ne jen zmizí vybraná karta) –
     * další pick tak vidí čerstvý náhodný výběr, ne zbytek toho původního.
     */
    fun pickRewardCard(card: Card) {
        val run = rogueRun.value ?: return
        if (run.rewardCardPicksLeft <= 0) return
        val picksLeft = run.rewardCardPicksLeft - 1
        val updated = run.copy(deck = run.deck + card, rewardCardPicksLeft = picksLeft)
        if (picksLeft <= 0 && !updated.rewardBonusAvailable) {
            rogueRun.value = updated.copy(rewardCards = emptyList())
            startRogueBattle()
        } else {
            val offerCount = if (updated.rewardBonusAvailable) RogueConfig.REWARD_CARD_OFFERS_BOSS else RogueConfig.REWARD_CARD_OFFERS
            rogueRun.value = updated.copy(rewardCards = generateRogueRewardCards(updated, offerCount))
            persistRogue()
        }
    }

    /** Zahodí aktuální nabídku a vylosuje novou – stojí 1 z omezené zásoby rerollů na CELÝ run. */
    fun rerollRewardCards() {
        val run = rogueRun.value ?: return
        if (run.rewardCardPicksLeft <= 0 || run.rerollsLeft <= 0) return
        val offerCount = if (run.rewardBonusAvailable) RogueConfig.REWARD_CARD_OFFERS_BOSS else RogueConfig.REWARD_CARD_OFFERS
        rogueRun.value = run.copy(
            rewardCards = generateRogueRewardCards(run, offerCount),
            rerollsLeft = run.rerollsLeft - 1
        )
        persistRogue()
    }

    /** Volitelný bonus navíc – jen po zabití bosse a jen když jsou povinné karty už vybrané. */
    fun pickRewardBonus(reward: RogueReward) {
        val run = rogueRun.value ?: return
        if (!run.rewardBonusAvailable || run.rewardCardPicksLeft > 0) return
        val updated = when (reward) {
            RogueReward.MaxCastle -> run.copy(
                maxCastle = run.maxCastle + RogueConfig.REWARD_MAX_CASTLE,
                hp        = run.hp + RogueConfig.REWARD_MAX_CASTLE
            )
            RogueReward.Wall      -> run.copy(wall = run.wall + RogueConfig.REWARD_WALL)
            RogueReward.Repair    -> run.copy(
                hp = (run.hp + RogueConfig.REWARD_REPAIR).coerceAtMost(run.maxCastle)
            )
            is RogueReward.Mine   -> run.copy(
                bonusMines = run.bonusMines +
                    (reward.type to ((run.bonusMines[reward.type] ?: 0) + 1))
            )
        }
        rogueRun.value = updated.copy(rewardBonusAvailable = false, rewardCards = emptyList())
        startRogueBattle()
    }

    /** Přeskočí jen bonus (povinné karty už musí být hotové – jinak no-op). */
    fun skipRewardBonus() {
        val run = rogueRun.value ?: return
        if (run.rewardCardPicksLeft > 0) return
        rogueRun.value = run.copy(rewardBonusAvailable = false, rewardCards = emptyList())
        startRogueBattle()
    }

    fun exitRoguelike() {
        roguePhase.value = null
        rogueRun.value   = null
        rogueDraft.clear()
        activeCampaignOpponent.value = null
        RogueSaveManager.clear()
    }

    // ── Generování soupeře podle hloubky ────────────────────────────────────────
    /**
     * winTarget škáluje s [RogueRun.maxCastle], ne pevná konstanta – jinak by šlo
     * nastřádat max hrad odměnami napříč běhy natolik, že hráč nastoupí do bitvy
     * už NAD cílovou hranicí a "vyhraje" postavením hradu dřív, než padne první karta
     * (nahlášený exploit: 72 hradu vs. pevný cíl 70 = auto-výhra na začátku kola).
     * +15 nad aktuální strop zaručuje reálnou mezeru, kterou musí hráč v bitvě uzavřít.
     */
    private fun generateRogueEnemy(run: RogueRun): CampaignOpponent {
        val battleIndex = run.battleIndex
        val winTarget = (run.maxCastle + 15).coerceAtLeast(70)
        val act      = battleIndex / RogueConfig.BATTLES_PER_ACT
        val posInAct = battleIndex % RogueConfig.BATTLES_PER_ACT
        val isBoss   = posInAct == RogueConfig.BATTLES_PER_ACT - 1

        // Plynulý náběh per-bitva: první soupeř je slabý warmup, poslední boss tvrdý.
        //   hrad:   b0=16 → b11(boss)=42   (hráč startuje na 30, takže b0 je jasně slabší)
        //   hradby: b0=6  → b11(boss)=19
        val castle      = 16 + battleIndex * 2 + (if (isBoss) 4 else 0)
        val wall        = 6  + battleIndex     + (if (isBoss) 2 else 0)
        val startMagic  = act + (if (isBoss) 1 else 0)
        val startAttack = act + (if (isBoss) 1 else 0)
        val handSize    = 4 + (if (act >= 2) 1 else 0)
        val extraMines  = buildMap {
            if (act >= 1) put(ResourceType.ATTACK, 1)
            if (act >= 2) put(ResourceType.MAGIC,  1)
            if (isBoss)   put(ResourceType.STONES, 1)
        }

        val names = RogueConfig.ENEMY_NAMES.getOrElse(act) { RogueConfig.ENEMY_NAMES.last() }
        val baseName = names.random()

        return CampaignOpponent(
            id             = "rogue_$battleIndex",
            name           = if (isBoss) "$baseName ★" else baseName,   // ★
            title          = RogueConfig.ACT_TITLES.getOrElse(act) { "" },
            avatar         = randomEnemyAvatar(),
            description    = "",
            isBoss         = isBoss,
            aiCastle       = castle,
            aiWall         = wall,
            // Roguelike soupeři jsou procedurální, ne vázaní na konkrétní lokaci → náhodný skin
            // (bez toho by defaultovali na castle_player/wall_player, viz aiCastleSkin výchozí hodnota).
            aiCastleSkin   = randomOpponentCastleSkinId(),
            aiWallSkin     = randomOpponentWallSkinId(),
            aiStartMagic   = startMagic,
            aiStartAttack  = startAttack,
            aiExtraMines   = extraMines,
            deckCardCounts = generateRogueEnemyDeck(act),
            winTarget      = winTarget,
            aiWinTarget    = winTarget,
            aiStartHandSize = handSize,
            playerStartHandSize = 4
        )
    }

    /**
     * Nepřátelský balíček = ověřený balancedDeck (dobrá ekonomika + křivka)
     * s odebranými zabanovanými kartami. Obtížnost škáluje přes STATY i přes
     * KVALITU balíčku – první akt (warmup) nesmí mít epické/legendární karty,
     * druhý akt ještě bez legendárních, teprve třetí akt táhne z celého poolu.
     * (Bez toho měl i "nejslabší" soupeř plnohodnotný 30kartový balíček a hráč
     * s omezeným roguelike draftem ho nemohl dohnat jinak než rushem.)
     */
    private fun generateRogueEnemyDeck(act: Int): Map<String, Int> {
        val pool = when (act) {
            0    -> allCards.filter { it.rarity <= Rarity.RARE }
            1    -> allCards.filter { it.rarity != Rarity.LEGENDARY }
            else -> allCards
        }
        return buildBalancedDeck(pool)
            .flatMap { (id, count) ->
                val card = allCards.find { it.id == id } ?: return@flatMap emptyList()
                List(count) { card }
            }
            .filter { it.id !in RogueConfig.ENEMY_DECK_BANLIST }
            .groupingBy { it.baseId }
            .eachCount()
    }

    private fun playSoundForCard(card: Card) = playSoundForCardGlobal(card)
}

/**
 * Určí zvuk karty BEZ přehrání: explicitní card.sound, jinak auto-detekce z efektů.
 * Používá se i k „zapečení" zvuku zdrojové karty do Klonu/Zrcadla (jejich vlastní
 * efekty [Clone]/[Mirror] by se auto-detekcí vyhodnotily na generický zvuk).
 */
fun detectCardSound(card: Card): CardSound {
    card.sound?.let { return it }
    fun CardEffect.flatten(): List<CardEffect> =
        if (this is CardEffect.ConditionalEffect) listOf(this) + effect.flatten()
        else listOf(this)
    val allEffects = card.effects.flatMap { it.flatten() }
    val hasMineDestroy = allEffects.any { it is CardEffect.DestroyMine }
    val hasAttack = allEffects.any { e ->
        e is CardEffect.AttackPlayer        || e is CardEffect.AttackCastle  ||
        e is CardEffect.AttackWall          || e is CardEffect.StealResource ||
        e is CardEffect.DrainResource       || e is CardEffect.BlockMine     ||
        e is CardEffect.BurnCard            || e is CardEffect.StealCard     ||
        e is CardEffect.StealCastle         ||
        e is CardEffect.XScaledAttackPlayer || e is CardEffect.XScaledAttackCastle
    }
    val hasBuild = allEffects.any { e ->
        e is CardEffect.BuildCastle || e is CardEffect.BuildWall ||
        e is CardEffect.AddMine     || e is CardEffect.XScaledBuildCastle ||
        e is CardEffect.ConvertWallToCastle
    }
    val hasResource = allEffects.any { e ->
        e is CardEffect.AddResource        || e is CardEffect.AddResourceDelayed ||
        e is CardEffect.AddCardsToDeck     || e is CardEffect.XScaledDualResource ||
        e is CardEffect.DrawCard
    }
    return when {
        hasMineDestroy -> CardSound.MINE_DESTROY
        hasAttack      -> CardSound.ATTACK
        hasBuild       -> CardSound.BUILD
        hasResource    -> CardSound.RESOURCE
        else           -> CardSound.CARD_PLAY
    }
}

/**
 * Top-level funkce pro přehrání zvuku karty – sdílena mezi offline i online hrou.
 * Priorita: soundResId > card.sound > auto-detekce z efektů.
 */
fun playSoundForCardGlobal(card: Card) {
    if (card.soundResId != null) {
        SoundManager.playCustom(card.soundResId)
        return
    }
    when (detectCardSound(card)) {
        CardSound.ATTACK       -> SoundManager.playAttack()
        CardSound.MINE_DESTROY -> SoundManager.playMineDestroy()
        CardSound.BUILD        -> SoundManager.playBuild()
        CardSound.RESOURCE     -> SoundManager.playResource()
        CardSound.DRAW         -> SoundManager.playCardDraw()
        CardSound.CARD_PLAY    -> SoundManager.playCardPlay()
    }
}

// Extension pro čitelné názvy zdrojů v logu
val ResourceType.label get() = when (this) {
    ResourceType.MAGIC  -> "magie"
    ResourceType.ATTACK -> "útoku"
    ResourceType.STONES -> "kamene"
    ResourceType.CHAOS  -> "chaosu"
}

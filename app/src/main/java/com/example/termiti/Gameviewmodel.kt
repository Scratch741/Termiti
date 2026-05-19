package com.example.termiti

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ── Rozhodnutí ────────────────────────────────────────────────────────────────
data class DecisionState(
    val title    : String,
    val subtitle : String,
    val options  : List<Card>
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

    // ── Deck sloty ────────────────────────────────────────────────────────────
    val decks = androidx.compose.runtime.mutableStateListOf(
        Deck(0, "Balíček 1"),
        Deck(1, "Balíček 2"),
        Deck(2, "Balíček 3")
    )
    var activeDeckIndex = androidx.compose.runtime.mutableStateOf(0)
        private set

    init { loadDecks() }

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
            "007" to 3,
            "047" to 3,
            "D04" to 4,
            "056" to 3,
            "010" to 3,
            "015" to 3,
            "013" to 2,
            "038" to 3,
            "065" to 3,
            "D01" to 3,
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

        // 1. ⚔️ Útočník – přímý útok pomocí ATTACK zdrojů (30 karet)
        "⚔️ Útočník" to mapOf(
            // Přímý útok (ATTACK)
            "027" to 3,   // Válečný buben  – útok −4 + 2 útoku, cena 2
            "046" to 3,   // Goblin         – útok −2 + krádež, cena 1
            "056" to 3,   // Nájezdník
            "022" to 2,   // Přímý zásah    – hrad −8, cena 3
            "026" to 2,
            "024" to 2,
            "021" to 1,
            "052" to 1,
            "051" to 1,
            "078" to 1,
            "055" to 2,   // Mravenci       – útok −3 + podmíněně hrad −8, cena 2
            "054" to 2,   // Válečný pochod – útok −13 + 2atk, cena 5
            // Generování ATTACK zdrojů (MAGIC)
            "004" to 2,   // Magie            – +4 magie, zdarma
            "038" to 3,   // Vojenský rozkaz – +7 útoku, cena 2
            "043" to 2,   // Výcvikové centrum – +2 doly útoku, cena 4
        ),

        // 2. 🏰 Obránce – postavit hrad na 100 HP pomocí STONES zdrojů (30 karet)
        "🏰 Obránce" to mapOf(
                "095"   to 4,   // Obchod s kamenem
                "057"   to 3,   // Bašta
                "083"   to 2,   // Nouzové opevnění
                "094"   to 4,   // Sklad materiálu
                "089"   to 3,   // Architekt
                "084"   to 4,   // Velká oprava
                "062"   to 2,   // Obranná aliance
                "032"   to 2,   // Citadela
                "085"   to 2,   // Královská obnova
                "096"   to 1,   // Nedobytná pevnost
                "097"   to 1,   // Obnova království
                "011"   to 2,   // Zásoby kamene
            ),  // 30 karet


        // 3. ⚔️ Útočník 2 – agresivní útočný balíček (30 karet)
        "⚔️ Útočník 2" to mapOf(
                "098"   to 3,
                "054"   to 2,
                "052"   to 1,
                "051"   to 1,
                "046"   to 3,
                "026"   to 2,
                "024"   to 3,
                "022"   to 3,
                "038"   to 3,
                "015"   to 3,
                "078"   to 1,
                "056"   to 3,
                "021"   to 2,
            ),  // 30 karet

        // 4. 💰 Ekonom – budovat doly pro zdrcující ekonomiku (30 karet)
        "💰 Ekonom" to mapOf(
                "101"   to 1,
                "102"   to 1,
                "013"   to 2,
                "044"   to 1,
                "075"   to 1,
                "045"   to 1,
                "080"   to 1,
                "076"   to 1,
                "091"   to 3,
                "032"   to 2,
                "060"   to 2,
                "089"   to 2,
                "097"   to 1,
                "059"   to 3,
                "046"   to 3,
                "022"   to 3,
                "033"   to 2,
            ),  // 30 karet

        // 5. 🌀 Chaosmancer – Chaos ekonomika + sabotáž soupeře (30 karet)
        "🌀 Chaosmancer" to mapOf(
                "022"   to 2,
                "046"   to 3,
                "C26"   to 2,
                "037"   to 2,
                "C24"   to 2,
                "C02"   to 2,
                "C03"   to 1,
                "C31"   to 2,
                "C29"   to 2,
                "091"   to 3,
                "C22"   to 2,
                "C32"   to 2,
                "C11"   to 1,
                "C06"   to 1,
                "C05"   to 2,
                "C10"   to 1,
            ),  // 30 karet
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
    var cardHistory        = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Karty ztracené hráčem kvůli BurnCard / StealCard AI (celá hra). */
    var lostToOpponent     = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Snímky pro replay aktuální hry. */
    private val replayFrames = mutableListOf<ReplayFrame>()
    // Combo: hráč zahrál combo kartu – kolo nepokračuje automaticky
    var isPlayerComboTurn = androidx.compose.runtime.mutableStateOf(false)
        private set

    // ── Kampaň ───────────────────────────────────────────────────────────────
    /** Aktuálně hraný soupeř v kampani (null = normální hra / aréna). */
    var activeCampaignOpponent = androidx.compose.runtime.mutableStateOf<CampaignOpponent?>(null)
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
        player.deck.addAll(returned)
        player.deck.shuffle()
        player.drawCards(returned.size)

        gameState.value        = old.copy(playerState = player)
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        maybeStartAiFirstTurn()
    }

    fun skipMulligan() {
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        maybeStartAiFirstTurn()
    }

    // ── Rozhodnutí – helpery ──────────────────────────────────────────────────

    private fun buildDecisionOptions(
        fx: CardEffect,
        self: PlayerState,
        opponent: PlayerState,
        excludeId: String? = null   // vyloučí právě zahranou kartu z nabídky (Vzpomínka)
    ): List<Card> = when (fx) {
        is CardEffect.DecisionBurnOpponent -> opponent.deck.shuffled().take(fx.picks)
        is CardEffect.DecisionChooseType   -> allCards.filter { it.type == fx.cardType }.shuffled().take(fx.picks)
        is CardEffect.DecisionFromDiscard  -> self.discardPile.filter { it.id != excludeId }.shuffled().take(fx.picks)
        is CardEffect.DecisionFromDeck     -> self.deck.shuffled().take(fx.picks)
        else -> emptyList()
    }

    private fun buildDecisionState(fx: CardEffect, options: List<Card>): DecisionState = when (fx) {
        is CardEffect.DecisionBurnOpponent -> DecisionState("ROZHODNUTÍ", "Vyber kartu ze soupeřova balíku k zahození", options)
        is CardEffect.DecisionChooseType   -> DecisionState("ROZHODNUTÍ", "Vyber si kartu typu ${fx.cardType}", options)
        is CardEffect.DecisionFromDiscard  -> DecisionState("ROZHODNUTÍ", "Vyber si kartu z odhazovacího balíčku", options)
        is CardEffect.DecisionFromDeck     -> DecisionState("ROZHODNUTÍ", "Vyber si kartu ze svého balíčku", options)
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
                log.appendLog("Hráč zahodil ze soupeřova balíku: ${chosen.name}")
            }
            is CardEffect.DecisionChooseType -> {
                val newCard = chosen.copy(
                    id           = "${chosen.id}_${java.util.UUID.randomUUID()}",
                    isGenerated  = true,
                    costModifier = -effect.costReduction   // záporná = sleva; 0 = beze změny
                )
                if (player.hand.size < old.playerMaxHand) player.hand.add(newCard) else player.discardPile.add(newCard)
                val discountMsg = if (effect.costReduction > 0) " (−${effect.costReduction} ${chosen.costType.label})" else ""
                log.appendLog("Hráč si vybral: ${chosen.name}$discountMsg")
            }
            is CardEffect.DecisionFromDiscard -> {
                player.discardPile.remove(chosen)
                val retrieved = chosen.copy(isGenerated = true)
                if (player.hand.size < old.playerMaxHand) player.hand.add(retrieved) else player.discardPile.add(retrieved)
                log.appendLog("Hráč si vzal z odhazovacího balíčku: ${chosen.name}")
            }
            is CardEffect.DecisionFromDeck -> {
                // Karta zůstane v balíčku – do ruky přijde kopie s novým ID
                val copy = chosen.copy(id = "${chosen.id}_${java.util.UUID.randomUUID()}", isGenerated = true)
                if (player.hand.size < old.playerMaxHand) player.hand.add(copy) else player.discardPile.add(copy)
                log.appendLog("Hráč zkopíroval z balíčku: ${chosen.name}")
            }
            else -> {}
        }

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
                    delay(210L)
                    SoundManager.playCardDraw()
                    player.drawCards(1, old.playerMaxHand)
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.AI
                    )
                }
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
        val first = pendingDecision.value?.options?.firstOrNull() ?: return
        resolveDecision(first)
    }

    /**
     * Pokud AI začíná jako první hráč, spustí její tah automaticky po mulliganu.
     * Počáteční hráč (AI) nelíže první kartu – pravidlo stejné jako u hráče.
     * Hráč (druhý hráč) taktéž nelíže bonusovou kartu – oba začínají se 5 z mulliganu.
     */
    private fun maybeStartAiFirstTurn() {
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
                    player.drawCards(1, old.playerMaxHand)
                }
            }
            gameState.value = old.copy(playerState = player)
            log.appendLog("Hráč začíná jako první!")
            return
        }

        log.appendLog("AI začíná jako první!")

        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()
        // AI je počáteční hráč → nesmí lízat první kartu (aiDrawsAtStart = false)
        // Hráč jako druhý hráč si lízne 1 kartu před svým prvním tahem (playerDrawsAtEnd = true)
        finishTurn(old, player, ai, aiDrawsAtStart = false, playerDrawsAtEnd = true)
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
            superRandom              -> sharedSuperDeck!!
            randomDeck               -> balancedDeck()
            activeDeck.isValid       -> activeDeck.toCardList(allCards)
            else                     -> balancedDeck()
        }.withUniqueIds()   // ještě NEZAMÍCHÁNO – shuffle se provede uvnitř also{} níže

        // ── Pasivní schopnosti hráče ──────────────────────────────────────────
        val actives = PlayerProfileManager.profile
            ?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()

        val startCastle      = 30 + if (PassiveAbility.EXTRA_CASTLE     in actives) 5 else 0
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

        val aiStartCastle    = 30 + if (PassiveAbility.EXTRA_CASTLE     in aiPassives) 5 else 0
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

        val playerState = PlayerState(
            castleHP  = startCastle,
            wallHP    = startWall
        ).also {
            if (extraMagic  > 0) it.resources[ResourceType.MAGIC]  = extraMagic
            if (extraAttack > 0) it.resources[ResourceType.ATTACK] = extraAttack
            if (extraStones > 0) it.resources[ResourceType.STONES] = extraStones
            if (extraChaos  > 0) it.resources[ResourceType.CHAOS]  = extraChaos
            it.deck.addAll(playerCards)
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
                fun pick(f: (Card) -> Boolean, n: Int) = allCards.filter(f).shuffled().take(n)
                if (PassiveAbility.BOOST_ATTACK in aiPassives) addAll(pick({ it.type == "Útok"  }, 2))
                if (PassiveAbility.BOOST_BUILD  in aiPassives) addAll(pick({ it.type == "Stavba" }, 2))
                if (PassiveAbility.BOOST_MAGIC  in aiPassives) addAll(pick({ it.type == "Magie" }, 2))
                if (PassiveAbility.BOOST_CHAOS  in aiPassives) addAll(pick({ it.type == "Chaos" }, 2))
                if (PassiveAbility.BOOST_RANDOM in aiPassives) addAll(pick({ it.type != "Důl"   }, 3))
            }.withUniqueIds()
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
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Affordability check (X-kost karty jsou vždy zahratelné)
        if (!card.isXCost && (player.resources[card.costType] ?: 0) < card.effectiveCost) {
            log.appendLog("Nedostatek ${card.costType.label} pro: ${card.name}")
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
        player.lastPlayedType = card.type
        // Před aplikací: zaznamenej nesplněné podmínky pro hráče
        card.effects.filterIsInstance<CardEffect.ConditionalEffect>().forEach { ce ->
            if (!checkCondition(ce.condition, player, ai)) {
                log.appendLog("${card.name}: podmínka nesplněna!")
            }
        }
        // Quest: zahraná karta
        QuestManager.onCardPlayed()

        // DrawCard efekty se zpracují samostatně (postupný líz s animací a zvukem)
        var pendingDrawCount = 0
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
        applyEffects(card.effects, player, ai, allCards, xValue = xValue,
            onOpponentCardLost = { lostCard, action ->
                // Loguj kartu AI, kterou hráč spálil nebo ukradl
                cardHistory.appendHistory(lostCard, action, isMine = false)
                addCardLog("Hráč", lostCard, action, isMe = false)
            },
            onDrawCard = { _, count -> pendingDrawCount += count })
        // Quest: poškození hradu
        val castleDmg = (aiCastleHpBefore - ai.castleHP).coerceAtLeast(0)
        if (castleDmg > 0) QuestManager.onDamageDealt(castleDmg)
        // Snapshot už není potřeba – vyčistit, aby neovlivnil další vyhodnocení
        player.preCostResources = null

        // ── Rozhodnutí: pauza tahu pro výběr hráče ──────────────────────────
        val decisionFx = card.effects.firstOrNull {
            it is CardEffect.DecisionBurnOpponent ||
            it is CardEffect.DecisionChooseType   ||
            it is CardEffect.DecisionFromDiscard  ||
            it is CardEffect.DecisionFromDeck
        }
        if (decisionFx != null) {
            val options = buildDecisionOptions(decisionFx, player, ai, excludeId = card.id)
            if (options.isNotEmpty()) {
                decisionPlayer       = player
                decisionAi           = ai
                decisionOld          = old
                decisionIsCombo      = card.isCombo
                decisionEffect       = decisionFx
                // Uložíme pending lízy (DrawPerCardPlayed, DrawCard efekty) –
                // provedeme je v resolveDecision po výběru karty hráčem.
                decisionPendingDraws = pendingDrawCount
                gameState.value = old.copy(playerState = player, aiState = ai)
                pendingDecision.value = buildDecisionState(decisionFx, options)
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
            val isComboCard = card.isCombo
            viewModelScope.launch(crashHandler) {
                // Zamkni hráče během lízání
                gameState.value = s1.copy(activePlayer = ActivePlayer.AI)
                repeat(pendingDrawCount) {
                    delay(210L)
                    SoundManager.playCardDraw()
                    player.drawCards(1, old.playerMaxHand)
                    gameState.value = old.copy(
                        playerState  = player.deepCopy(),
                        aiState      = ai,
                        activePlayer = ActivePlayer.AI
                    )
                }
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

        if (card.isCombo) {
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
        log.appendLog("Hráč ukončil tah")
        finishTurn(old, player, ai)
    }

    fun waitTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER || gameOver.value != null) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Čekat = přeskočit tah bez akce.
        // Karta se líže automaticky na začátku DALŠÍHO kola (v finishTurn).
        log.appendLog("Hráč přeskočil kolo")
        isPlayerComboTurn.value = false
        finishTurn(old, player, ai, playerWaited = true)
    }

    fun discardCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.DISCARDED, isPlayer = true)
        addCardLog("Hráč", card, CardAction.DISCARDED, isMe = true)
        SoundManager.playDiscard()

        isPlayerComboTurn.value = false
        addReplayFrame(old.copy(playerState = player, aiState = ai), card, isPlayer = true, action = CardAction.DISCARDED)
        finishTurn(old, player, ai)
    }

    private fun finishTurn(
        old: GameState, player: PlayerState, ai: PlayerState,
        aiDrawsAtStart: Boolean = true,
        playerDrawsAtEnd: Boolean = true,
        playerWaited: Boolean = false
    ) {
        // Zablokuj hráče – AI je na tahu
        gameState.value = old.copy(
            playerState  = player,
            aiState      = ai,
            activePlayer = ActivePlayer.AI
        )

        viewModelScope.launch(crashHandler) {
            delay((500L..1000L).random())

            // ── Tah AI ────────────────────────────────────────────────────────
            // AI dostane zdroje a líže 1 kartu na ZAČÁTKU svého tahu
            ai.generateResources()
            if (aiDrawsAtStart && ai.deck.isNotEmpty()) {
                ai.drawCards(1, old.aiMaxHand)
                SoundManager.playCardDraw()
            }
            transformShapeShifters(ai.hand, allCards)

            // AI hraje v cyklu (podporuje combo karty)
            var aiContinues = true
            while (aiContinues) {
                val aiChoice = aiChooseAction(ai, player, old.aiWinTarget, old.playerWinTarget)
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
                        ai.hand.remove(aiCard)
                        ai.discardPile.add(aiCard)
                        applyEffects(
                            aiCard.effects, ai, player, allCards, xValue = aiXValue,
                            onOpponentCardLost = { card, action -> recordOpponentLoss(card, action) },
                            onDrawCard = { state, count ->
                                repeat(count) { state.drawCards(1, old.aiMaxHand); SoundManager.playCardDraw() }
                            }
                        )
                        // AI auto-pick pro Decision efekty
                        for (fx in aiCard.effects) {
                            when (fx) {
                                is CardEffect.DecisionBurnOpponent -> {
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.firstOrNull()?.let { chosen ->
                                        player.deck.remove(chosen)
                                        player.discardPile.add(chosen)
                                        log.appendLog("AI zahodila z tvého balíku: ${chosen.name}")
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
                                    val opts = buildDecisionOptions(fx, ai, player)
                                    opts.firstOrNull()?.let { chosen ->
                                        ai.deck.remove(chosen)
                                        if (ai.hand.size < old.aiMaxHand) ai.hand.add(chosen)
                                    }
                                }
                                else -> {}
                            }
                        }
                        ai.preCostResources = null
                        addReplayFrame(old.copy(playerState = player, aiState = ai), aiCard, isPlayer = false, action = CardAction.PLAYED)
                        recordCard(aiCard, CardAction.PLAYED, isPlayer = false)
                        addCardLog("AI", aiCard, CardAction.PLAYED, isMe = false)
                        playSoundForCard(aiCard)

                        if (aiCard.isCombo) {
                            // Combo: krátká pauza + mezistate + pokračuj.
                            // activePlayer musí zůstat AI, aby hráč nemohl kliknout
                            // v okně delay a nespustil druhou souběžnou finishTurn coroutinu.
                            val mid = old.copy(
                                playerState  = player,
                                aiState      = ai,
                                activePlayer = ActivePlayer.AI
                            )
                            mid.checkWinCondition()?.let { result ->
                                scheduleGameEnd(result, mid); return@launch
                            }
                            gameState.value = mid   // vizuální mezistav
                            delay(450L)
                        } else {
                            aiContinues = false     // normální karta → konec tahu AI
                        }
                    }
                    is AiAction.Wait -> {
                        // AI čeká = přeskočí tah
                        log.appendLog("AI čekala")
                        aiContinues = false
                        // Oba přeskočili kolo a oba mají prázdný balíček → rozhodne hrad.
                        // Stačí prázdné BALÍČKY – hráč mohl projít s kartami v ruce a zvolit čekat.
                        if (playerWaited && player.deck.isEmpty() && ai.deck.isEmpty())
                        {
                            val finalState = old.copy(playerState = player, aiState = ai)
                            val result = finalState.resolveByHp()
                            log.appendLog("Oba hráči pasovali s prázdnými balíčky – konec hry!")
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
                        aiContinues = false
                    }
                }
            }

            // ── Kontrola výhry po tahu AI ─────────────────────────────────────
            // activePlayer = AI i v mezistavu, aby hráč nemohl kliknout v okně delay
            // a nespustil druhou souběžnou finishTurn coroutinu (primární příčina pádu).
            val s2 = old.copy(
                playerState  = player,
                aiState      = ai,
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
                val burned = player.drawCards(1, old.playerMaxHand)
                SoundManager.playCardDraw()
                burned.forEach { b ->
                    cardHistory.appendHistory(b, CardAction.BURNED, isMine = true)
                    addCardLog("Hráč", b, CardAction.BURNED, isMe = true)
                }
            }
            // Quick draw: extra karta na hráčově prvním tahu (pokud AI šla první)
            if (!quickDrawUsed) {
                val actives = PlayerProfileManager.profile
                    ?.activeAbilities?.mapNotNull { PassiveAbility.fromId(it) } ?: emptyList()
                if (PassiveAbility.QUICK_DRAW in actives && player.deck.isNotEmpty()) {
                    quickDrawUsed = true
                    player.drawCards(1, old.playerMaxHand)
                    SoundManager.playCardDraw()
                }
            }

            transformShapeShifters(player.hand, allCards)

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
                log.appendLog("Obě strany bez karet – konec hry!")
                scheduleGameEnd(result, finalState)
                return@launch
            }

            // Reset per-card-played efektů – platí jen pro toto kolo
            player.drawCardOnPlay = null;               ai.drawCardOnPlay = null
            player.gainResourcePerCardPlayed.clear();   ai.gainResourcePerCardPlayed.clear()
            player.gainCastlePerCardPlayed.clear();     ai.gainCastlePerCardPlayed.clear()

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

            gameState.value = s3
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
            playerName      = profile?.name   ?: "Hráč",
            playerAvatar    = profile?.avatar ?: "⚔️",
            opponentName    = opp?.name       ?: "Nepřítel",
            opponentAvatar  = opp?.avatar     ?: "👺",
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

    private fun addCardLog(actorName: String, card: Card, action: CardAction, isMe: Boolean) {
        val turn = gameState.value.currentTurn
        log.value = (log.value + LogEntry.CardEvent(actorName, card, action, isMe, turn)).takeLast(50)
    }

    private fun recordCard(card: Card, action: CardAction, isPlayer: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isPlayer
        cardHistory.appendHistory(card, action, isMine = isPlayer)
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
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
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
        aiPassiveAbilities.value = emptyList()   // kampaňský soupeř má vlastní stats, ne náhodné pasivky
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
        quickDrawUsed           = false
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
                allCards.filter(filter).shuffled().take(count)

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

            it.deck.addAll((playerCards + deckBoost).withUniqueIds().shuffled())
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
            wallHP   = opponent.aiWall
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
        arenaOffers.value = allCards.shuffled().take(3)
    }

    fun pickArenaCard(card: Card) {
        arenaDraft.add(card)
        if (arenaDraft.size >= 30) startArenaBattle() else generateArenaOffers()
    }

    private fun startArenaBattle() {
        arenaPhase.value = ArenaPhase.BATTLE
        replayFrames.clear()
        val ps = PlayerState().also {
            it.deck.addAll(arenaDraft.toList().withUniqueIds().shuffled())
            it.drawCards(5)
        }
        val ai = PlayerState().also {
            it.deck.addAll(balancedDeck().withUniqueIds().shuffled())
            it.drawCards(5)
        }
        gameState.value         = GameState(playerState = ps, aiState = ai)
        gameOver.value          = null
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
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

    private fun playSoundForCard(card: Card) = playSoundForCardGlobal(card)
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
    if (card.sound != null) {
        when (card.sound) {
            CardSound.ATTACK       -> SoundManager.playAttack()
            CardSound.MINE_DESTROY -> SoundManager.playMineDestroy()
            CardSound.BUILD        -> SoundManager.playBuild()
            CardSound.RESOURCE     -> SoundManager.playResource()
            CardSound.DRAW         -> SoundManager.playCardDraw()
            CardSound.CARD_PLAY    -> SoundManager.playCardPlay()
        }
        return
    }
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
        e is CardEffect.AddMine     || e is CardEffect.XScaledBuildCastle
    }
    val hasResource = allEffects.any { e ->
        e is CardEffect.AddResource        || e is CardEffect.AddResourceDelayed ||
        e is CardEffect.AddCardsToDeck     || e is CardEffect.XScaledDualResource ||
        e is CardEffect.DrawCard
    }
    when {
        hasMineDestroy -> SoundManager.playMineDestroy()
        hasAttack      -> SoundManager.playAttack()
        hasBuild       -> SoundManager.playBuild()
        hasResource    -> SoundManager.playResource()
        else           -> SoundManager.playCardPlay()
    }
}

// Extension pro čitelné názvy zdrojů v logu
val ResourceType.label get() = when (this) {
    ResourceType.MAGIC  -> "magie"
    ResourceType.ATTACK -> "útoku"
    ResourceType.STONES -> "kamene"
    ResourceType.CHAOS  -> "chaosu"
}

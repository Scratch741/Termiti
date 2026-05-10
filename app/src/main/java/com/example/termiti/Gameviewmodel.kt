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
            addLog("Hráč začíná jako první!")
            return
        }

        addLog("AI začíná jako první!")

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

    private fun createInitialState(randomDeck: Boolean = false, superRandom: Boolean = false): GameState {
        val activeDeck  = decks[activeDeckIndex.value]
        val playerCards = when {
            superRandom              -> superRandomDeck()
            randomDeck               -> balancedDeck()
            activeDeck.isValid       -> activeDeck.toCardList(allCards)
            else                     -> balancedDeck()
        }.withUniqueIds().shuffled()

        // ── Pasivní schopnosti hráče ──────────────────────────────────────────
        val actives = PlayerProfileManager.profile
            ?.activeAbilities
            ?.mapNotNull { PassiveAbility.fromId(it) }
            ?: emptyList()

        val startCastle      = 30 + if (PassiveAbility.EXTRA_CASTLE     in actives) 5 else 0
        val startWall        = 10 + if (PassiveAbility.EXTRA_WALL       in actives) 5 else 0
        val extraMagic       =       if (PassiveAbility.EXTRA_MAGIC      in actives) 1 else 0
        val extraAttack      =       if (PassiveAbility.EXTRA_ATTACK     in actives) 1 else 0
        val extraStones      =       if (PassiveAbility.EXTRA_STONES     in actives) 1 else 0
        val extraChaos       =       if (PassiveAbility.EXTRA_CHAOS      in actives) 1 else 0
        val playerWinTarget  =  60 + if (PassiveAbility.EXTRA_CASTLE     in actives) 5 else 0
        val aiWinTarget      =  60 + if (PassiveAbility.IRON_BASTION     in actives) 5 else 0
        val playerMaxHand    =   7 + if (PassiveAbility.EXTRA_HAND_CARD  in actives) 1 else 0

        val playerState = PlayerState(
            castleHP  = startCastle,
            wallHP    = startWall
        ).also {
            if (extraMagic  > 0) it.resources[ResourceType.MAGIC]  = extraMagic
            if (extraAttack > 0) it.resources[ResourceType.ATTACK] = extraAttack
            if (extraStones > 0) it.resources[ResourceType.STONES] = extraStones
            if (extraChaos  > 0) it.resources[ResourceType.CHAOS]  = extraChaos
            it.deck.addAll(playerCards)
            it.drawCards(4)
        }
        val aiState = PlayerState().also {
            val aiDeck = if (superRandom) superRandomDeck() else balancedDeck()
            it.deck.addAll(aiDeck.withUniqueIds().shuffled())
            it.drawCards(4)
        }

        val firstPlayer = if (Random.nextBoolean()) ActivePlayer.PLAYER else ActivePlayer.AI
        return GameState(
            playerState     = playerState,
            aiState         = aiState,
            activePlayer    = firstPlayer,
            playerWinTarget = playerWinTarget,
            aiWinTarget     = aiWinTarget,
            playerMaxHand   = playerMaxHand
        )
    }

    fun playCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Affordability check (X-kost karty jsou vždy zahratelné)
        if (!card.isXCost && (player.resources[card.costType] ?: 0) < card.cost) {
            addLog("Nedostatek ${card.costType.label} pro: ${card.name}")
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
            player.resources[card.costType] = (player.resources[card.costType] ?: 0) - card.cost
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
                addLog("${card.name}: podmínka nesplněna!")
            }
        }
        // Quest: zahraná karta
        QuestManager.onCardPlayed()

        // DrawCard efekty se zpracují samostatně (postupný líz s animací a zvukem)
        var pendingDrawCount = 0
        val aiCastleHpBefore = ai.castleHP
        applyEffects(card.effects, player, ai, allCards, xValue = xValue,
            onOpponentCardLost = { lostCard, action ->
                // Loguj kartu AI, kterou hráč spálil nebo ukradl
                addToHistory(lostCard, action, isMine = false)
                addCardLog("Hráč", lostCard, action, isMe = false)
            },
            onDrawCard = { _, count -> pendingDrawCount += count })
        // Quest: poškození hradu
        val castleDmg = (aiCastleHpBefore - ai.castleHP).coerceAtLeast(0)
        if (castleDmg > 0) QuestManager.onDamageDealt(castleDmg)
        // Snapshot už není potřeba – vyčistit, aby neovlivnil další vyhodnocení
        player.preCostResources = null

        val s1 = old.copy(playerState = player, aiState = ai)
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
        addLog("Hráč ukončil tah")
        finishTurn(old, player, ai)
    }

    fun waitTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER || gameOver.value != null) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Čekat = přeskočit tah bez akce.
        // Karta se líže automaticky na začátku DALŠÍHO kola (v finishTurn).
        addLog("Hráč přeskočil kolo")
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
                ai.drawCards(1)
                SoundManager.playCardDraw()
            }

            // AI hraje v cyklu (podporuje combo karty)
            var aiContinues = true
            while (aiContinues) {
                val aiChoice = aiChooseAction(ai, player)
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
                            ai.resources[aiCard.costType] = (ai.resources[aiCard.costType] ?: 0) - aiCard.cost
                        }
                        ai.lastPlayedType = aiCard.type
                        applyEffects(
                            aiCard.effects, ai, player, allCards, xValue = aiXValue,
                            onOpponentCardLost = { card, action -> recordOpponentLoss(card, action) },
                            onDrawCard = { state, count ->
                                repeat(count) { state.drawCards(1); SoundManager.playCardDraw() }
                            }
                        )
                        ai.preCostResources = null
                        ai.hand.remove(aiCard)
                        ai.discardPile.add(aiCard)
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
                        addLog("AI čekala")
                        aiContinues = false
                        // Oba přeskočili kolo a oba mají prázdný balíček → rozhodne hrad.
                        // Stačí prázdné BALÍČKY – hráč mohl projít s kartami v ruce a zvolit čekat.
                        if (playerWaited && player.deck.isEmpty() && ai.deck.isEmpty())
                        {
                            val finalState = old.copy(playerState = player, aiState = ai)
                            val result = finalState.resolveByHp()
                            addLog("Oba hráči pasovali s prázdnými balíčky – konec hry!")
                            scheduleGameEnd(result, finalState)
                            return@launch
                        }
                    }
                    is AiAction.Discard -> {
                        val toDiscard = aiChoice.card
                        ai.hand.remove(toDiscard)
                        ai.discardPile.add(toDiscard)
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
                    addToHistory(b, CardAction.BURNED, isMine = true)
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
                addLog("Obě strany bez karet – konec hry!")
                scheduleGameEnd(result, finalState)
                return@launch
            }

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
        addToHistory(card, action, isMine = true)
        val list = lostToOpponent.value.toMutableList()
        list.add(0, CardHistoryEntry(card, action, isMine = true))
        lostToOpponent.value = list
        addCardLog("AI", card, action, isMe = false)
    }

    private fun addLog(message: String) {
        log.value = (log.value + LogEntry.SystemEvent(message)).takeLast(50)
    }

    private fun addCardLog(actorName: String, card: Card, action: CardAction, isMe: Boolean) {
        val turn = gameState.value.currentTurn
        log.value = (log.value + LogEntry.CardEvent(actorName, card, action, isMe, turn)).takeLast(50)
    }

    private fun recordCard(card: Card, action: CardAction, isPlayer: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isPlayer
        addToHistory(card, action, isMine = isPlayer)
    }

    private fun addToHistory(card: Card, action: CardAction, isMine: Boolean) {
        val h = cardHistory.value.toMutableList()
        h.add(0, CardHistoryEntry(card, action, isMine))
        if (h.size > 20) h.removeAt(h.size - 1)
        cardHistory.value = h
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
        gameState.value         = createInitialState(randomDeck, superRandom)
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
    }

    /** Spustí bitvu v kampani proti danému soupeři. */
    fun startCampaignBattle(opponent: CampaignOpponent) {
        gameEndJob?.cancel()
        gameEndPending.value    = false
        activeCampaignOpponent.value = opponent
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
        quickDrawUsed           = false
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
        val startWall   = 10 + (if (PassiveAbility.EXTRA_WALL   in actives) 5 else 0) + h.extraWall
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

    // ── AI heuristika ─────────────────────────────────────────────────────────

    private sealed class AiAction {
        data class Play(val card: Card)    : AiAction()
        data class Discard(val card: Card) : AiAction()
        object Wait                        : AiAction()
    }

    /**
     * Vybere akci AI podle situace na hracím poli.
     *
     * Priority a logika:
     *  1. ENDGAME (oba balíčky prázdné):
     *     – AI NIKDY neodhazuje karty (zahazování blokuje game-over podmínku).
     *     – Zahraj kartu pokud je výhodná, jinak ČEKEJ.
     *  2. Nemohu si dovolit žádnou kartu:
     *     – Plná ruka + balíček má karty → zahoď nejhorší kartu (jinak příští líznutí spálí kartu).
     *     – Volné místo v ruce + balíček má karty → ČEKEJ (příští tah lízneš potenciálně lepší kartu).
     *     – Balíček prázdný → zahoď nejméně hodnotnou kartu.
     *  3. Normální hra → vyber kartu s nejvyšším skóre.
     *     – Nejlepší karta má ≤0 skóre + plná ruka → zahoď nejhorší kartu.
     *     – Nejlepší karta má ≤0 skóre + ruka není plná → ČEKEJ.
     *
     * Skórování:
     *  – Efekty se hodnotí situačně (nízké HP, blízkost výhry, atd.) + škálují dle amount.
     *  – Podmínkový efekt přidá skóre vnitřního efektu JEN tehdy, když podmínka platí.
     *  – Skóre = součet efektů − cena karty + náhoda ±2.
     *
     * Chytrý výběr karty k zahození (bestDiscard):
     *  – Preferuje zahazovat karty s největším „shortfallem" (daleko od dovolení)
     *    v poměru k rychlosti přírůstku daného zdroje (důl).
     */
    private fun aiChooseAction(ai: PlayerState, opponent: PlayerState): AiAction {
        // X-kost karty jsou vždy zahratelné (spotřebují všechen dostupný zdroj, i 0)
        val affordable     = ai.hand.filter { card ->
            if (card.isXCost) true
            else (ai.resources[card.costType] ?: 0) >= card.cost
        }

        // Situační příznaky
        val aiLowHp        = ai.castleHP < 15
        val aiLowWall      = ai.wallHP   < 5
        val oppLowHp       = opponent.castleHP < 20
        val oppCloseToWin  = opponent.castleHP >= 50   // soupeř je blízko výhry hradem
        val aiCloseToWin   = ai.castleHP >= 50         // AI je blízko výhry hradem
        val chaos          = ai.resources[ResourceType.CHAOS] ?: 0
        val bothDecksEmpty = ai.deck.isEmpty() && opponent.deck.isEmpty()
        val handFull       = ai.hand.size >= 7

        // Rekurzivní ohodnocení jednoho efektu v kontextu stavu AI
        // xVal = hodnota X pro X-kost efekty (aktuální zásoby daného zdroje)
        // Situační příznaky soupeře – pro chytřejší hodnocení efektů
        val oppWall       = opponent.wallHP
        val oppHasWall    = oppWall > 0
        val oppResources  = opponent.resources

        fun scoreEffect(fx: CardEffect, xVal: Int = 0): Int = when (fx) {
            is CardEffect.AttackPlayer -> {
                // Pokud soupeř nemá hradby, útok jde přímo na hrad → vyšší hodnota
                val wallBonus = if (!oppHasWall) 4 else 0
                val urgency   = if (oppLowHp) 20 else if (oppCloseToWin) 6 else 8
                urgency + fx.amount / 4 + wallBonus
            }
            is CardEffect.AttackCastle -> {
                val urgency = if (oppLowHp) 22 else if (oppCloseToWin) 8 else 6
                urgency + fx.amount / 5
            }
            is CardEffect.AttackWall -> when {
                // Soupeř nemá hradby – karta je k ničemu, silně penalizuj
                !oppHasWall           -> -15
                // Málo hradeb – nízká hodnota, ale nenulová (alespoň trochu poškodíme)
                oppWall < fx.amount   -> 2 + oppWall / 4
                // Hodně hradeb – standardní nebo vyšší hodnota
                else                  -> 4 + oppWall / 6
            }
            // Záporný amount = poškození vlastního hradu (penalta)
            is CardEffect.BuildCastle    -> if (fx.amount >= 0) {
                val urgency = if (aiLowHp) 20 else if (aiCloseToWin) 12 else 5
                urgency + fx.amount / 5
            } else {
                fx.amount * 2  // záporné skóre za ztrátu HP hradu
            }
            // Záporný amount = obětování vlastních hradeb (penalta)
            is CardEffect.BuildWall      -> if (fx.amount >= 0) {
                if (aiLowWall) 16 else 5
            } else {
                fx.amount  // záporné skóre za ztrátu hradeb
            }
            is CardEffect.AddMine             -> 9   // long-term value
            is CardEffect.StealResource       -> {
                // Krást má smysl jen pokud soupeř daný zdroj má
                val available = oppResources[fx.type] ?: 0
                if (available == 0) -5 else 7 + available.coerceAtMost(4)
            }
            is CardEffect.DrainResource       -> {
                // Drainovat má smysl jen pokud soupeř daný zdroj má
                val available = oppResources[fx.type] ?: 0
                if (available == 0) -5 else 6 + available.coerceAtMost(3)
            }
            is CardEffect.AddResource         -> 3
            is CardEffect.AddResourceDelayed  -> fx.amount * 2  // méně než ihned (3), ale hodnotné
            is CardEffect.DestroyMine         -> {
                // Zničit důl má smysl jen pokud soupeř má více než povinné minimum
                val oppMines = opponent.mines[fx.type] ?: 0
                val minMines = if (fx.type == ResourceType.CHAOS) 0 else 1
                if (oppMines <= minMines) -8   // pod minimem – efekt se nevyvolá, zbytečné
                else 8 + (oppMines - minMines) * 2   // čím víc dolů, tím hodnotnější zničení
            }
            is CardEffect.BlockMine           -> {
                // Blokovat důl dává smysl jen pokud soupeř ten důl má
                val oppMines = opponent.mines[fx.type] ?: 0
                if (oppMines == 0) -6 else fx.turns * 7   // 2 kola = 14, 3 kola = 21
            }
            is CardEffect.StealCard           -> {
                // Krást kartu má smysl jen pokud ji skutečně dostaneme do ruky
                val slotsLeft = (7 - ai.hand.size).coerceAtLeast(0)
                if (slotsLeft == 0) -4 else 8   // plná ruka → ukradená karta jen shoří
            }
            is CardEffect.BurnCard            -> if (opponent.hand.isEmpty()) -8 else 6
            is CardEffect.AddCardsToDeck      -> 4
            is CardEffect.DrawCard            -> {
                // Líznout kartu má smysl jen pokud je v ruce místo;
                // karty navíc se spálí → penalizuj každou spálenou kartu
                val slotsLeft   = (7 - ai.hand.size).coerceAtLeast(0)
                val useful      = minOf(fx.count, slotsLeft)
                val burned      = fx.count - useful
                useful * 5 - burned * 4
            }
            // Krádež hradu: poškodí soupeře A léčí vlastní hrad
            is CardEffect.StealCastle    -> fx.amount + (if (oppLowHp) 8 else 0) + (if (aiLowHp) 8 else 0)
            // Podmínkový efekt: skóruj vnitřní efekt pouze pokud podmínka platí; jinak 0
            is CardEffect.ConditionalEffect ->
                if (checkCondition(fx.condition, ai, opponent)) scoreEffect(fx.effect, xVal) else 0

            // X-kost efekty: skóruj proporcionálně k reálné hodnotě efektu
            // amt*2 = lineární bonus (stejná efektivita bez ohledu na výši xVal)
            is CardEffect.XScaledAttackPlayer -> {
                val amt       = xVal / fx.divisor
                val wallBonus = if (!oppHasWall) 4 else 0
                val urgency   = if (oppLowHp) 20 else if (oppCloseToWin) 6 else 8
                urgency + amt * 2 + wallBonus
            }
            is CardEffect.XScaledAttackCastle -> {
                val amt = xVal / fx.divisor
                val urgency = if (oppLowHp) 22 else if (oppCloseToWin) 8 else 7
                urgency + amt * 2
            }
            is CardEffect.XScaledBuildCastle -> {
                val amt = xVal / fx.divisor
                val urgency = if (aiLowHp) 20 else if (aiCloseToWin) 12 else 5
                urgency + amt * 2
            }
            is CardEffect.XScaledDualResource -> {
                val amt = xVal / fx.divisor
                5 + amt * 2   // přidává dva zdroje najednou
            }
        }

        // ── Detekce lethal: karta zabije soupeře tento tah ──────────────────────
        // Simulujeme poškození hradeb + hradu efektů karty (v pořadí zahrání).
        // Pokud celkové poškození hradu ≥ soupeřův HP → lethal.
        fun isLethal(card: Card, xVal: Int): Boolean {
            var wallLeft      = opponent.wallHP
            var castleDmg     = 0
            fun processEffect(fx: CardEffect) {
                when (fx) {
                    is CardEffect.AttackPlayer -> {
                        val pierce   = (fx.amount - wallLeft).coerceAtLeast(0)
                        wallLeft     = (wallLeft - fx.amount).coerceAtLeast(0)
                        castleDmg   += pierce
                    }
                    is CardEffect.AttackCastle      -> castleDmg += fx.amount
                    is CardEffect.XScaledAttackPlayer -> {
                        val amt      = xVal / fx.divisor
                        val pierce   = (amt - wallLeft).coerceAtLeast(0)
                        wallLeft     = (wallLeft - amt).coerceAtLeast(0)
                        castleDmg   += pierce
                    }
                    is CardEffect.XScaledAttackCastle -> castleDmg += xVal / fx.divisor
                    is CardEffect.StealCastle         -> castleDmg += fx.amount
                    is CardEffect.ConditionalEffect   ->
                        if (checkCondition(fx.condition, ai, opponent)) processEffect(fx.effect)
                    else -> {}
                }
            }
            card.effects.forEach { processEffect(it) }
            return castleDmg >= opponent.castleHP
        }

        // Celkové skóre karty = suma efektů − cena + šum ±2
        // Pro X-kost karty: cena = aktuální zásoby daného zdroje (to se spotřebuje)
        fun score(card: Card): Int {
            val xVal = if (card.isXCost) (ai.resources[card.costType] ?: 0) else 0

            // ── Lethal override: karta okamžitě vyhrává hru → vždy zahraj ────
            if (isLethal(card, xVal)) return 1000 + (-2..2).random()

            // X-kost karty: nehrát příliš brzy – malé zásoby = mizivý efekt, plýtvání kartou.
            // Práh závisí na situaci; při prázdných balíčcích zásoby stejně vyprší → hraj vždy.
            if (card.isXCost && !bothDecksEmpty) {
                val minX = when {
                    aiLowHp && oppLowHp -> 2   // oba v nouzi – zahraj i za málo
                    aiLowHp || oppLowHp -> 4   // jeden v nebezpečí – nižší práh
                    else                -> 8   // normální hra – čekej na solidní zásoby
                }
                if (xVal < minX) return -20 + (-2..2).random()
            }

            // Ochrana před sebevraždou: pokud karta sníží vlastní hrad na ≤0,
            // zahraj ji pouze v situaci jisté prohry (šance na remízu).
            // Sebevraždu detekujeme jako záporné BuildCastle efekty.
            fun selfCastleDamage(effects: List<CardEffect>): Int = effects.sumOf { fx ->
                when {
                    fx is CardEffect.BuildCastle && fx.amount < 0 -> -fx.amount
                    fx is CardEffect.ConditionalEffect            ->
                        if (checkCondition(fx.condition, ai)) selfCastleDamage(listOf(fx.effect)) else 0
                    else                                          -> 0
                }
            }
            val selfDmg = selfCastleDamage(card.effects)
            if (selfDmg > 0 && selfDmg >= ai.castleHP) {
                // Karta by zničila vlastní hrad – povolíme pouze pro remízu:
                // AI je v jisté ztrátě = soupeřův hrad je nízký (mohl by příštím tahem vyhrát)
                // NEBO AI nemá žádnou šanci na obnovu a soupeř je téměř na výhře
                val couldTieKill = opponent.castleHP <= selfDmg  // i soupeř by zahynul (remíza)
                val certainLoss  = ai.castleHP <= 10 && (oppCloseToWin || opponent.castleHP < 15)
                if (!couldTieKill && !certainLoss) return -100 + (-2..2).random()
            }

            val effectScore = card.effects.sumOf { scoreEffect(it, xVal) }
            val costForScore = if (card.isXCost) xVal else card.cost
            val chaosBlock  = if (card.costType == ResourceType.CHAOS && chaos < card.cost) 100 else 0
            val noise       = (-2..2).random()
            return effectScore - costForScore - chaosBlock + noise
        }

        // Chytrý výběr karty k zahození:
        // Preferuje zahazovat karty s největším shortfallem / rychlostí dolu
        // (= karty, na které bychom čekali nejdéle)
        fun bestDiscard(): Card? = ai.hand.maxByOrNull { card ->
            val shortfall = (card.cost - (ai.resources[card.costType] ?: 0)).coerceAtLeast(0)
            val mineRate  = (ai.mines[card.costType] ?: 0).coerceAtLeast(1)
            shortfall * 10 / mineRate
        }

        // =====================================================================
        // ENDGAME: oba balíčky prázdné
        // =====================================================================
        // AI NESMÍ odhazovat – ztratila by tah a podmínka „oba čekají s prázdnými balíčky"
        // (= resolveByHp) by se nikdy nevyvolala. Vždy zahraj nebo ČEKEJ.
        if (bothDecksEmpty) {
            val aiIsLosing = ai.castleHP < opponent.castleHP
            if (affordable.isNotEmpty()) {
                val scored = affordable.map { it to score(it) }
                val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait
                if (bestScore > 0) return AiAction.Play(best)

                // AI prohrává a nemá výhodnou kartu → poslední pokus:
                // zahraj cokoli, co útočí nebo staví hrad (čekání = jistá prohra)
                if (aiIsLosing) {
                    val lastChance = affordable.firstOrNull { card ->
                        card.effects.any { fx ->
                            fx is CardEffect.AttackPlayer ||
                            fx is CardEffect.AttackCastle ||
                            fx is CardEffect.BuildCastle  ||
                            fx is CardEffect.StealCastle
                        }
                    }
                    if (lastChance != null) return AiAction.Play(lastChance)
                }
            }
            return AiAction.Wait
        }

        // =====================================================================
        // NEMOHU SI DOVOLIT ŽÁDNOU KARTU
        // =====================================================================
        if (affordable.isEmpty()) {
            // Plná ruka + balíček má karty → zahoď nejhorší kartu
            // (čekání by způsobilo spálení líznuté karty, protože ruka je plná)
            if (handFull && ai.deck.isNotEmpty()) {
                return bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
            }
            // Volné místo v ruce + balíček má karty → čekej, příště lízneš novou kartu
            if (ai.deck.isNotEmpty()) return AiAction.Wait
            // Balíček prázdný → zahoď nejméně hodnotnou kartu
            return bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
        }

        // =====================================================================
        // NORMÁLNÍ HRA – vyber nejlepší kartu
        // =====================================================================
        // Předpočítej skóre jednou (score() obsahuje náhodu, nevolej dvakrát)
        val scored = affordable.map { it to score(it) }
        val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait

        // Pokud je i nejlepší karta nevýhodná (podmínka nesplněna, čisté náklady):
        // – plná ruka → zahoď nejhorší kartu (uvolni místo pro lepší líz)
        // – jinak → čekej
        if (bestScore <= 0) {
            return if (handFull && ai.deck.isNotEmpty()) {
                bestDiscard()?.let { AiAction.Discard(it) } ?: AiAction.Wait
            } else {
                AiAction.Wait
            }
        }

        return AiAction.Play(best)
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

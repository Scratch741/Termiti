package com.example.termiti

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class MpPhase { LOBBY, CONNECTING, MULLIGAN, PLAYING, GAME_OVER, RECONNECTING }


class MultiplayerViewModel(
    val allCards: List<Card>,
    initialDecks: List<Deck>    = emptyList(),
    defaultDeckIndex: Int       = 0
) : ViewModel() {

    companion object {
        fun factory(
            allCards: List<Card>,
            decks: List<Deck> = emptyList(),
            activeDeckIndex: Int = 0
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MultiplayerViewModel(allCards, decks, activeDeckIndex) as T
        }
    }

    /** Snapshot balíčků z GameViewModel (pro výběr v lobby). */
    val decks: List<Deck> = initialDecks

    /** Index aktuálně vybraného balíčku (0-3), nebo -1 = náhodný. */
    val selectedDeckIndex = mutableStateOf(defaultDeckIndex.coerceIn(-1, maxOf(0, initialDecks.lastIndex)))

    fun selectDeck(index: Int) { selectedDeckIndex.value = index }

    /** Base ID karet pro svůj balíček – použije vybraný balíček, nebo random. */
    private fun myDeckBaseIds(): List<String> {
        val deck = decks.getOrNull(selectedDeckIndex.value)
        return if (deck != null && deck.isValid)
            deck.toCardList(allCards).shuffled().map { it.id }
        else
            randomDeckBaseIds()
    }

    /** ID karet soupeřova balíčku přijatá přes síť (z jeho HELLO zprávy). */
    private var oppDeckIds: List<String> = emptyList()

    private val net = NetworkManager()

    // ── UI state ──────────────────────────────────────────────────────────────
    var phase        = mutableStateOf(MpPhase.LOBBY);  private set
    var statusMsg    = mutableStateOf("Vyber režim");   private set
    var localIp      = mutableStateOf("…");             private set
    var myName       = mutableStateOf("Hráč")
    var oppName      = mutableStateOf("Soupeř");        private set
    var isHost       = mutableStateOf(false);           private set
    var lastHostIp   = mutableStateOf("");              private set

    /** true = právě probíhá skenování sítě */
    var isScanning  = mutableStateOf(false);            private set
    /** Seznam nalezených hostitelů ze skenování */
    var foundHosts  = mutableStateOf<List<String>>(emptyList()); private set

    init {
        // Pre-load vlastní IP adresu hned při startu – zobrazí se v lobby
        viewModelScope.launch(Dispatchers.IO) {
            localIp.value = NetworkManager.getLocalIp()
        }
    }

    // ── Game state ────────────────────────────────────────────────────────────
    var myState      = mutableStateOf(PlayerState());   private set
    var oppState     = mutableStateOf(PlayerState());   private set
    var isMyTurn     = mutableStateOf(false);           private set
    var isComboTurn  = mutableStateOf(false);           private set
    var currentTurn  = mutableStateOf(1);               private set
    var lastCard         = mutableStateOf<Card?>(null);            private set
    /** Akce poslední zaznamenané karty. */
    var lastCardAction   = mutableStateOf(CardAction.PLAYED);      private set
    /** True = karta zahrána mnou (hráčem), false = soupeřem. */
    var lastCardIsPlayer = mutableStateOf(true);                   private set
    /** True = soupeř (guest) žádá o rematch a host ještě nerozhodl. */
    var rematchRequested = mutableStateOf(false);                  private set
    /** Chronologická historie karet (nejnovější první). */
    var cardHistory    = mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Karty ztracené hráčem kvůli BurnCard / StealCard soupeře (celá hra). */
    var lostToOpponent = mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    var gameLog           = mutableStateOf<List<String>>(emptyList()); private set
    var gameOver          = mutableStateOf<Boolean?>(null);  private set   // true=win false=lose
    /** UIDs soupeřových karet, které se právě odhalují (lícem nahoru) */
    var oppRevealedCardIds = mutableStateOf<Set<String>>(emptySet()); private set
    /** true = já jdu jako první na tah */
    var goesFirst = mutableStateOf(false); private set

    // ── Mulligan ──────────────────────────────────────────────────────────────
    var mulliganSelected    = mutableStateOf<Set<String>>(emptySet()); private set
    var mulliganSubmitted   = mutableStateOf(false); private set
    private var myMulliganDone  = false
    private var oppMulliganDone = false

    // ── Internal ──────────────────────────────────────────────────────────────
    private val cardByUid            = mutableMapOf<String, Card>()
    private var iGoFirst             = false

    /** Zaznamená zahranou/zahozenou kartu jako poslední i do historie (viditelné v centru). */
    private fun recordCard(card: Card, action: CardAction, isMine: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isMine
        addToHistory(card, action, isMine)
    }

    /** Zaznamená spálenou kartu pouze do historie – lastCard se NEaktualizuje (soupeř nevidí). */
    private fun recordBurn(card: Card) {
        addToHistory(card, CardAction.BURNED, isMine = true)
    }

    private fun addToHistory(card: Card, action: CardAction, isMine: Boolean) {
        val h = cardHistory.value.toMutableList()
        h.add(0, CardHistoryEntry(card, action, isMine))
        if (h.size > 20) h.removeAt(h.size - 1)
        cardHistory.value = h
    }

    /** Karta ztracena mnou kvůli efektu soupeře (BurnCard / StealCard). */
    private fun recordOpponentLoss(card: Card, action: CardAction) {
        addToHistory(card, action, isMine = true)
        val list = lostToOpponent.value.toMutableList()
        list.add(0, CardHistoryEntry(card, action, isMine = true))
        lostToOpponent.value = list
    }
    private var turnIndex            = 0   // total turns elapsed (for first-turn no-draw rule)
    private var closingIntentionally = false
    private var heartbeatJob: Job?   = null

    // ═════════════════════════════════════════════════════════════════════════
    // Connection
    // ═════════════════════════════════════════════════════════════════════════

    fun hostGame() {
        isHost.value = true
        viewModelScope.launch {
            phase.value     = MpPhase.CONNECTING
            statusMsg.value = "Spouštím server…"
            runCatching {
                net.startServer()
                localIp.value   = NetworkManager.getLocalIp()
                statusMsg.value = "Čekám na hráče\n${localIp.value} : ${NetworkManager.PORT}"
                // Souběžně obsluhuj discovery pokusy (skenery se připojují na port 8766)
                val discoveryJob = viewModelScope.launch { net.handleDiscovery() }
                net.awaitClient()
                discoveryJob.cancel()
                onConnected()
            }.onFailure {
                statusMsg.value = "Chyba: ${it.message}"
                phase.value     = MpPhase.LOBBY
            }
        }
    }

    /** Paralelně skenuje /24 podsíť a uloží nalezené hostitele do [foundHosts]. */
    fun scanNetwork() {
        if (isScanning.value) return
        val ip = localIp.value
        if (ip.length < 7 || ip == "?") return   // IP ještě není načtena
        isScanning.value = true
        foundHosts.value = emptyList()
        viewModelScope.launch {
            foundHosts.value = net.scanSubnet(ip)
            isScanning.value = false
        }
    }

    fun joinGame(ip: String) {
        lastHostIp.value = ip.trim()
        isHost.value = false
        viewModelScope.launch {
            phase.value     = MpPhase.CONNECTING
            statusMsg.value = "Připojuji se k $ip…"
            runCatching {
                net.connectToHost(ip)
                onConnected()
            }.onFailure {
                statusMsg.value = "Nepodařilo se: ${it.message}"
                phase.value     = MpPhase.LOBBY
            }
        }
    }

    /** Pošle HELLO zprávu včetně ID karet vybraného balíčku. */
    private fun sendHello() =
        sendMsg("t" to "HELLO", "name" to myName.value,
                "deckIds" to JSONArray(myDeckBaseIds()))

    private fun onConnected() {
        closingIntentionally = false
        statusMsg.value = "Připojeno! Čekám na soupeře…"
        startIncomingCollection()
        startHeartbeat()
        sendHello()
    }

    /** Spustí coroutine sbírající zprávy ze socketu; při ukončení detekuje disconnect. */
    private fun startIncomingCollection() {
        viewModelScope.launch {
            try {
                net.incoming().collect { handleMessage(it) }
            } catch (_: Exception) {
                // IOException = spojení bylo náhle přerušeno
            } finally {
                if (!closingIntentionally) onPeerDisconnected()
            }
        }
    }

    private fun onPeerDisconnected() {
        if (phase.value == MpPhase.LOBBY || phase.value == MpPhase.RECONNECTING) return
        addLog("⚠️ Soupeř se odpojil.")
        closingIntentionally = true   // zabrání rekurzi přes collect.finally

        stopHeartbeat()
        if (phase.value == MpPhase.PLAYING) {
            // Zachováme stav hry a nabídneme reconnect
            phase.value     = MpPhase.RECONNECTING
            statusMsg.value = if (isHost.value) "Připravuji čekání na reconnect…"
                              else              "Připojení přerušeno."
            viewModelScope.launch(Dispatchers.IO) {
                net.close()
                if (isHost.value) startReconnectServer()
            }
        } else {
            // MULLIGAN / CONNECTING – příliš brzy, jdeme do lobby
            viewModelScope.launch(Dispatchers.IO) { net.close() }
            resetLobbyState(msg = "Soupeř se odpojil. Zkus novou hru.")
        }
    }

    // ── Host: znovu otevře server a čeká na reconnect guesta ─────────────────

    private suspend fun startReconnectServer() {
        closingIntentionally = false
        runCatching {
            net.startServer()
            val ip = NetworkManager.getLocalIp()
            localIp.value   = ip
            statusMsg.value = "Čekám na reconnect soupeře\n$ip"
            net.awaitClient()
            startIncomingCollection()
            startHeartbeat()
            sendHello()
        }.onFailure {
            if (!closingIntentionally)
                resetLobbyState(msg = "Reconnect selhal: ${it.message}")
        }
    }

    // ── Guest: zkusí se znovu připojit k hostu ────────────────────────────────

    fun tryReconnect() {
        closingIntentionally = false
        statusMsg.value = "Reconnect k ${lastHostIp.value}…"
        viewModelScope.launch {
            runCatching {
                net.connectToHost(lastHostIp.value)
                startIncomingCollection()
                startHeartbeat()
                sendHello()
            }.onFailure {
                statusMsg.value = "Reconnect selhal: ${it.message}"
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Message dispatch
    // ═════════════════════════════════════════════════════════════════════════

    private fun handleMessage(line: String) {
        val msg = runCatching { JSONObject(line) }.getOrNull() ?: return
        when (msg.getString("t")) {
            "HELLO"     -> onHello(msg)
            "START"     -> onStart(msg)
            "MULLIGAN"  -> onMulliganMsg(msg)
            "PLAY"      -> onOpponentPlay(msg.getString("uid"))
            "WAIT"      -> onOpponentWait()
            "DISCARD"   -> onOpponentDiscard(msg.getString("uid"))
            "BURN"      -> onOpponentBurn(msg.getString("uid"))
            "COMBO_END" -> onOpponentComboEnd()
            "REMATCH"      -> onRematchReceived()
            "DISCONNECT"   -> onPeerDisconnected()
            "REJOIN"       -> onRejoin(msg)
            "PING"         -> sendMsg("t" to "PONG")
            "PONG"         -> { /* keepalive potvrzení – nic nepotřebujeme */ }
        }
    }

    private fun onHello(msg: JSONObject) {
        oppName.value = msg.optString("name", "Soupeř")
        // Uložíme balíček soupeře (host ho použije při startování hry)
        if (msg.has("deckIds")) {
            oppDeckIds = jsonArr(msg.getJSONArray("deckIds"))
        }
        if (isHost.value) {
            if (phase.value == MpPhase.RECONNECTING) {
                viewModelScope.launch { sendRejoin() }
            } else {
                viewModelScope.launch { beginGame() }
            }
        } else {
            statusMsg.value = "Soupeř připojen. Čekám na start…"
        }
    }

    // ── Reconnect: host serializes state, guest restores it ──────────────────

    private suspend fun sendRejoin() {
        val json = JSONObject().apply {
            put("t",           "REJOIN")
            put("hostFirst",   iGoFirst)
            put("isHostTurn",  isMyTurn.value)
            put("isComboTurn", isComboTurn.value)
            put("turnIndex",   turnIndex)
            put("currentTurn", currentTurn.value)
            put("hostState",   serializeState(myState.value))
            put("guestState",  serializeState(oppState.value))
        }.toString()
        withContext(Dispatchers.IO) { net.send(json) }
        phase.value     = MpPhase.PLAYING
        statusMsg.value = ""
        addLog("↩️ Soupeř se vrátil – hra pokračuje!")
    }

    private fun onRejoin(msg: JSONObject) {
        val hostFirst  = msg.getBoolean("hostFirst")
        val isHostTurn = msg.getBoolean("isHostTurn")
        val isCombo    = msg.getBoolean("isComboTurn")

        iGoFirst          = !hostFirst
        turnIndex         = msg.getInt("turnIndex")
        currentTurn.value = msg.getInt("currentTurn")

        // Ze zprávy: hostState = stav hostitele, guestState = stav guesta
        val hostState  = deserializeState(msg.getJSONObject("hostState"))
        val guestState = deserializeState(msg.getJSONObject("guestState"))

        // Přebuduji cardByUid ze všech karet v obou stavech
        cardByUid.clear()
        listOf(hostState, guestState).forEach { s ->
            (s.hand + s.deck + s.discardPile).forEach { c -> cardByUid[c.id] = c }
        }

        myState.value     = guestState   // guest = já
        oppState.value    = hostState    // host = soupeř
        isMyTurn.value    = !isHostTurn
        isComboTurn.value = isCombo && !isHostTurn
        phase.value       = MpPhase.PLAYING
        addLog("↩️ Reconnect úspěšný – hra pokračuje!")
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun serializeState(s: PlayerState) = JSONObject().apply {
        put("castleHP",  s.castleHP)
        put("wallHP",    s.wallHP)
        put("resources", JSONObject(s.resources.mapKeys { it.key.name }))
        put("mines",     JSONObject(s.mines.mapKeys { it.key.name }))
        put("hand",      JSONArray(s.hand.map { it.id }))
        put("deck",      JSONArray(s.deck.map { it.id }))
        put("discard",   JSONArray(s.discardPile.map { it.id }))
    }

    private fun deserializeState(json: JSONObject): PlayerState {
        val s = PlayerState(
            castleHP = json.getInt("castleHP"),
            wallHP   = json.getInt("wallHP")
        )
        val res   = json.getJSONObject("resources")
        val mines = json.getJSONObject("mines")
        ResourceType.entries.forEach { t ->
            if (res.has(t.name))   s.resources[t] = res.getInt(t.name)
            if (mines.has(t.name)) s.mines[t]     = mines.getInt(t.name)
        }
        s.hand.addAll(jsonArr(json.getJSONArray("hand")).map    { buildCard(it) })
        s.deck.addAll(jsonArr(json.getJSONArray("deck")).map    { buildCard(it) })
        s.discardPile.addAll(jsonArr(json.getJSONArray("discard")).map { buildCard(it) })
        return s
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Game setup
    // ═════════════════════════════════════════════════════════════════════════

    private suspend fun beginGame() {
        val hostFirst   = Random.nextBoolean()
        val myBaseIds   = myDeckBaseIds()
        val oppBaseIds  = if (oppDeckIds.size == 30) oppDeckIds else randomDeckBaseIds()
        val myUids      = myBaseIds.mapIndexed  { i, id -> "${id}_h$i" }
        val oppUids     = oppBaseIds.mapIndexed { i, id -> "${id}_g$i" }

        val startJson = JSONObject().also {
            it.put("t", "START")
            it.put("hostFirst", hostFirst)
            it.put("hostUids",  JSONArray(myUids))
            it.put("guestUids", JSONArray(oppUids))
        }.toString()
        withContext(Dispatchers.IO) { net.send(startJson) }
        setupGame(myUids = myUids, oppUids = oppUids, iGoFirst = hostFirst)
    }

    private fun onStart(msg: JSONObject) {
        val hostFirst = msg.getBoolean("hostFirst")
        val hostUids  = jsonArr(msg.getJSONArray("hostUids"))
        val guestUids = jsonArr(msg.getJSONArray("guestUids"))
        setupGame(myUids = guestUids, oppUids = hostUids, iGoFirst = !hostFirst)
    }

    private fun setupGame(myUids: List<String>, oppUids: List<String>, iGoFirst: Boolean) {
        this.iGoFirst    = iGoFirst
        goesFirst.value  = iGoFirst
        turnIndex         = 0
        myMulliganDone    = false
        oppMulliganDone   = false
        gameOver.value    = null
        isComboTurn.value = false
        gameLog.value     = emptyList()
        currentTurn.value = 1
        mulliganSelected.value  = emptySet()
        mulliganSubmitted.value = false
        cardByUid.clear()
        lastCard.value         = null
        lastCardAction.value   = CardAction.PLAYED
        lastCardIsPlayer.value = true
        rematchRequested.value = false
        cardHistory.value      = emptyList()

        val myCards  = myUids.map  { buildCard(it) }
        val oppCards = oppUids.map { buildCard(it) }
        (myCards + oppCards).forEach { cardByUid[it.id] = it }

        myState.value = PlayerState().also {
            it.deck.addAll(myCards); it.drawCards(5)
        }
        oppState.value = PlayerState().also {
            it.deck.addAll(oppCards); it.drawCards(5)
        }

        phase.value = MpPhase.MULLIGAN
        val who = if (iGoFirst) "Ty začínáš!" else "${oppName.value} začíná."
        statusMsg.value = who
        addLog(who)
    }

    private fun buildCard(uid: String): Card {
        val baseId = uid.substringBefore("_")
        return (allCards.find { it.id == baseId } ?: allCards.first()).copy(id = uid)
    }

    private fun randomDeckBaseIds(): List<String> =
        (allCards + allCards).shuffled().take(30).map { it.id }

    // ═════════════════════════════════════════════════════════════════════════
    // Mulligan
    // ═════════════════════════════════════════════════════════════════════════

    fun toggleMulligan(uid: String) {
        val cur = mulliganSelected.value
        mulliganSelected.value = if (uid in cur) cur - uid else cur + uid
    }

    fun confirmMulligan() {
        if (mulliganSubmitted.value) return
        val selected = mulliganSelected.value.toList()
        applyMyMulligan(selected)
        sendMsg("t" to "MULLIGAN", "uids" to JSONArray(selected))
        mulliganSelected.value  = emptySet()
        myMulliganDone          = true
        mulliganSubmitted.value = true
        maybeStartGame()
    }

    fun skipMulligan() {
        if (mulliganSubmitted.value) return
        sendMsg("t" to "MULLIGAN", "uids" to JSONArray())
        myMulliganDone          = true
        mulliganSubmitted.value = true
        maybeStartGame()
    }

    private fun applyMyMulligan(uids: List<String>) {
        if (uids.isEmpty()) return
        val me       = myState.value.deepCopy()
        val returned = me.hand.filter { it.id in uids }
        me.hand.removeAll { it.id in uids }
        me.deck.addAll(returned)          // add to bottom, deterministic
        me.drawCards(returned.size)
        myState.value = me
    }

    private fun onMulliganMsg(msg: JSONObject) {
        val uids = jsonArr(msg.getJSONArray("uids"))
        if (uids.isNotEmpty()) {
            val opp      = oppState.value.deepCopy()
            val returned = opp.hand.filter { it.id in uids }
            opp.hand.removeAll { it.id in uids }
            opp.deck.addAll(returned)
            opp.drawCards(returned.size)
            oppState.value = opp
        }
        oppMulliganDone = true
        maybeStartGame()
    }

    private fun maybeStartGame() {
        if (!myMulliganDone || !oppMulliganDone) return
        phase.value = MpPhase.PLAYING
        if (iGoFirst) startMyTurn() else startOppTurn()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Turn management
    // ═════════════════════════════════════════════════════════════════════════

    private fun startMyTurn() {
        val me = myState.value.deepCopy()
        me.generateResources()
        // First player skips draw on their very first turn
        if (!(iGoFirst && turnIndex == 0)) {
            val burned = me.drawCards(1)
            burned.forEach { b ->
                recordCard(b, CardAction.BURNED, isMine = true)
                addLog("🔥 Spálena: ${b.name}")
                sendMsg("t" to "BURN", "uid" to b.id)
            }
        }
        myState.value  = me
        isMyTurn.value = true
    }

    private fun startOppTurn() {
        val opp = oppState.value.deepCopy()
        opp.generateResources()
        // Opponent (= second player if iGoFirst) always draws from turn 1
        if (!(!iGoFirst && turnIndex == 0)) opp.drawCards(1)
        oppState.value = opp
        isMyTurn.value = false
    }

    private fun endMyTurn() {
        isComboTurn.value = false
        isMyTurn.value    = false
        turnIndex++
        // Kolo se počítá jednou za oba tahy:
        // když jsem nešel první, můj tah uzavírá kolo
        if (!iGoFirst) currentTurn.value++
        startOppTurn()
    }

    private fun endOppTurn() {
        turnIndex++
        // Kolo se počítá jednou za oba tahy:
        // když jsem šel první, soupeřův tah uzavírá kolo
        if (iGoFirst) currentTurn.value++
        startMyTurn()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Local player actions
    // ═════════════════════════════════════════════════════════════════════════

    fun playCard(card: Card) {
        if (!isMyTurn.value && !isComboTurn.value) return
        val me  = myState.value.deepCopy()
        val opp = oppState.value.deepCopy()
        val xValue: Int
        if (card.isXCost) {
            xValue = me.resources[card.costType] ?: 0
            me.resources[card.costType] = 0
        } else {
            xValue = 0
            me.resources[card.costType] = (me.resources[card.costType] ?: 0) - card.cost
        }
        applyEffects(card.effects, me, opp, allCards, xValue = xValue)
        me.hand.remove(card)
        me.discardPile.add(card)
        recordCard(card, CardAction.PLAYED, isMine = true)
        addLog("Ty: ${card.name}")
        myState.value  = me
        oppState.value = opp
        sendMsg("t" to "PLAY", "uid" to card.id)
        checkWin(); if (gameOver.value != null) return
        if (card.isCombo) isComboTurn.value = true else endMyTurn()
    }

    fun waitTurn() {
        if (!isMyTurn.value && !isComboTurn.value) return
        addLog("Ty: přeskočil tah")
        sendMsg("t" to "WAIT")
        endMyTurn()
    }

    fun discardCard(card: Card) {
        if (!isMyTurn.value && !isComboTurn.value) return
        val me = myState.value.deepCopy()
        me.hand.remove(card); me.discardPile.add(card)
        myState.value = me
        recordCard(card, CardAction.DISCARDED, isMine = true)
        addLog("Ty: zahodil ${card.name}")
        sendMsg("t" to "DISCARD", "uid" to card.id)
        endMyTurn()
    }

    fun endComboTurn() {
        if (!isComboTurn.value) return
        addLog("Ty: ukončil combo")
        sendMsg("t" to "COMBO_END")
        endMyTurn()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Opponent actions (received over network)
    // ═════════════════════════════════════════════════════════════════════════

    private fun onOpponentPlay(uid: String) {
        val card = cardByUid[uid] ?: return
        val me  = myState.value.deepCopy()
        val opp = oppState.value.deepCopy()
        val oppXValue: Int
        if (card.isXCost) {
            oppXValue = opp.resources[card.costType] ?: 0
            opp.resources[card.costType] = 0
        } else {
            oppXValue = 0
            opp.resources[card.costType] = (opp.resources[card.costType] ?: 0) - card.cost
        }
        applyEffects(card.effects, opp, me, allCards, xValue = oppXValue) { lostCard, action ->
            recordOpponentLoss(lostCard, action)
        }
        // Karta záměrně zůstává v ruce – odstraníme ji až po vizuálním odhalení
        recordCard(card, CardAction.PLAYED, isMine = false)
        addLog("${oppName.value}: ${card.name}")
        myState.value  = me
        oppState.value = opp
        checkWin()

        // Označ kartu jako „odkrytou" – UI přepne rub na líc
        oppRevealedCardIds.value = oppRevealedCardIds.value + uid

        viewModelScope.launch {
            delay(1200)                      // karta je viditelná 1,2 s
            // Odeber kartu z ruky soupeře
            val updOpp = oppState.value.deepCopy()
            updOpp.hand.removeAll { it.id == uid }
            updOpp.discardPile.add(card)
            oppState.value = updOpp
            oppRevealedCardIds.value = oppRevealedCardIds.value - uid
            // Konec tahu – až po odhalení
            if (gameOver.value == null && !card.isCombo) endOppTurn()
        }
    }

    private fun onOpponentWait() {
        addLog("${oppName.value}: přeskočil tah")
        endOppTurn()
    }

    private fun onOpponentDiscard(uid: String) {
        val card = cardByUid[uid] ?: return
        val opp  = oppState.value.deepCopy()
        opp.hand.remove(card); opp.discardPile.add(card)
        oppState.value = opp
        recordCard(card, CardAction.DISCARDED, isMine = false)
        addLog("${oppName.value}: zahodil ${card.name}")
        endOppTurn()
    }

    private fun onOpponentBurn(uid: String) {
        val card = cardByUid[uid] ?: return
        // Stav je již synchronizován přes startOppTurn – jen zobrazíme vizuálně
        recordCard(card, CardAction.BURNED, isMine = false)
        addLog("🔥 ${oppName.value}: spálena ${card.name}")
    }

    private fun onOpponentComboEnd() {
        addLog("${oppName.value}: ukončil combo")
        endOppTurn()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Win condition
    // ═════════════════════════════════════════════════════════════════════════

    private fun checkWin() {
        val me  = myState.value
        val opp = oppState.value
        val bothExhausted = me.deck.isEmpty() && me.hand.isEmpty() &&
                            opp.deck.isEmpty() && opp.hand.isEmpty()
        when {
            // Simultánní smrt / oba dostaví → remíza (prohra pro oba)
            opp.castleHP <= 0 && me.castleHP <= 0 ->
                finishGame(false, "Vzájemná zkáza! Oba hrady padly současně.")
            me.castleHP >= 60 && opp.castleHP >= 60 ->
                finishGame(false, "Remíza! Oba dostavěli hrad současně.")
            opp.castleHP <= 0  -> finishGame(true,  "Zničil jsi soupeřův hrad!")
            me.castleHP  <= 0  -> finishGame(false, "Tvůj hrad byl zničen.")
            me.castleHP  >= 60 -> finishGame(true,  "Dostavěl jsi hrad na 60!")
            opp.castleHP >= 60 -> finishGame(false, "${oppName.value} dostavěl hrad na 60.")
            bothExhausted && me.castleHP > opp.castleHP ->
                finishGame(true,  "Balíčky došly – tvůj hrad je vyšší!")
            bothExhausted && opp.castleHP > me.castleHP ->
                finishGame(false, "Balíčky došly – soupeř má vyšší hrad.")
            bothExhausted ->
                finishGame(false, "Remíza! Balíčky došly a hrady jsou stejně vysoké.")
        }
    }

    private fun finishGame(won: Boolean, msg: String) {
        addLog(msg)
        gameOver.value = won
        phase.value    = MpPhase.GAME_OVER
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Rematch & lobby
    // ═════════════════════════════════════════════════════════════════════════

    // ─── Rematch ─────────────────────────────────────────────────────────────────

    /** Host nebo guest žádá o rematch. */
    fun requestRematch() {
        sendMsg("t" to "REMATCH")
        if (isHost.value) {
            // Host zahájí hru přímo — guest dostane START zprávu přes beginGame()
            viewModelScope.launch { beginGame() }
        } else {
            statusMsg.value = "Žádost odeslána…"
        }
    }

    /** Host přijímá rematch žádost od guesta. */
    fun acceptRematch() {
        rematchRequested.value = false
        viewModelScope.launch { beginGame() }
    }

    /** Host odmítá rematch žádost od guesta. */
    fun declineRematch() {
        rematchRequested.value = false
    }

    /** Zpracování příchozí REMATCH zprávy od soupeře. */
    private fun onRematchReceived() {
        if (isHost.value) {
            // Guest žádá o rematch → host musí rozhodnout (zobrazí dialog v UI)
            rematchRequested.value = true
        }
        // Guest: nic — host spustí beginGame() a guest obdrží START zprávu
    }

    fun returnToLobby() {
        stopHeartbeat()
        closingIntentionally = true
        viewModelScope.launch(Dispatchers.IO) {
            // PrintWriter má autoFlush=true, takže println() data okamžitě odešle do TCP bufferu
            // TCP zajistí jejich doručení před FIN – delay není potřeba
            runCatching {
                net.send(JSONObject().apply { put("t", "DISCONNECT") }.toString())
            }
            net.close()
        }
        resetLobbyState(msg = "Vyber režim")
    }

    private fun resetLobbyState(msg: String) {
        phase.value            = MpPhase.LOBBY
        gameOver.value         = null
        isMyTurn.value         = false
        isComboTurn.value      = false
        gameLog.value          = emptyList()
        mulliganSelected.value = emptySet()
        cardByUid.clear()
        statusMsg.value        = msg
    }

    override fun onCleared() {
        super.onCleared()
        stopHeartbeat()
        closingIntentionally = true
        net.close()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(10_000)          // každých 10 sekund
                sendMsg("t" to "PING")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendMsg(vararg pairs: Pair<String, Any>) {
        val json = JSONObject().also { obj ->
            pairs.forEach { (k, v) -> obj.put(k, v) }
        }.toString()
        viewModelScope.launch(Dispatchers.IO) { net.send(json) }
    }

    private fun jsonArr(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getString(it) }

    private fun addLog(msg: String) {
        gameLog.value = (gameLog.value + msg).takeLast(20)
    }

}

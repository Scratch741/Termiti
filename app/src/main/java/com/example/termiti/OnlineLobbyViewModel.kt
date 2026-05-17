package com.example.termiti

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Adresa lobby serveru ─────────────────────────────────────────────────────
private const val LOBBY_WS_URL = "ws://138.2.136.49:8765/lobby"

// ─── Fáze aplikace ────────────────────────────────────────────────────────────
enum class OnlinePhase {
    NAME_INPUT,       // zadání přezdívky
    CONNECTING,       // připojování k serveru
    LOBBY,            // připojeno – čekám na akci
    QUEUING,          // v matchmakingové frontě
    MATCH_FOUND,      // nalezen soupeř (přechod do hry) – mezikrok
    GAME_MULLIGAN,    // mulligan fáze
    GAME_PLAYING,     // hra probíhá
    GAME_OVER,        // hra skončila
    ERROR             // chyba připojení
}

// ─── Info o nalezeném zápase ──────────────────────────────────────────────────
data class OnlineMatchInfo(
    val gameId                : String,
    val opponentName          : String,
    val opponentAvatar        : String = "👺",
    val opponentCardBackSkin  : String = "card_back_frame",
    val opponentCastleSkin    : String = "castle_player",
    val opponentLevel         : Int    = -1,
    val side                  : String   // "A" nebo "B"
)

// ─── Odložená surovina přijatá ze serveru ────────────────────────────────────
data class OnlinePendingResource(
    val type     : String = "",
    val amount   : Int    = 0,
    val turnsLeft: Int    = 0
)

// ─── Stav hráče přijatý ze serveru ───────────────────────────────────────────
data class OnlinePlayerState(
    val castleHP         : Int                       = 30,
    val wallHP           : Int                       = 10,
    val resources        : Map<String, Int>          = emptyMap(),
    val mines            : Map<String, Int>          = emptyMap(),
    val mineBlockedTurns : Map<String, Int>          = emptyMap(),
    val pendingResources : List<OnlinePendingResource> = emptyList(), // jen myState
    val hand             : List<Card>                = emptyList(),   // jen myState
    val handSize         : Int                       = 0,             // jen oppState
    val deckSize         : Int                       = 0,
    val discardSize      : Int                       = 0,
    val maxHandSize      : Int                       = 7,             // jen myState: max velikost ruky (7 nebo 8 s extra_hand_card)
    val lastPlayedIdx    : Int?                      = null           // jen oppState: index zahrané karty
)

// ─── Herní stav (pro GAME_STATE zprávy) ──────────────────────────────────────
data class OnlineGameState(
    val activeSide       : String            = "A",
    val isMyTurn         : Boolean           = false,
    val turnNumber       : Int               = 1,
    val myState          : OnlinePlayerState = OnlinePlayerState(),
    val oppState         : OnlinePlayerState = OnlinePlayerState(),
    val myWinTarget      : Int               = 60,  // 60 nebo 65 s extra_castle
    val oppWinTarget     : Int               = 60,  // win target soupeře
    val log              : List<String>      = emptyList(),
    // ── Timer (relativní, bez závislosti na sync hodin) ──────────────────────
    val turnRemainingMs  : Long              = 15_000L, // zbývající ms ve fázi tahu
    val timebankMeMs     : Long              = 120_000L, // zbývající ms v mém timebanku
    val timebankOppMs    : Long              = 120_000L, // zbývající ms v soupeřově timebanku
    val receivedAt       : Long              = 0L        // System.currentTimeMillis() při přijetí
)

// ─── Statistiky pro jeden herní mód ──────────────────────────────────────────
data class OnlineModeStats(
    val rating : Int = 1000,
    val wins   : Int = 0,
    val losses : Int = 0,
    val draws  : Int = 0,
    val games  : Int = 0
) {
    val winRate: Int get() = if (games > 0) wins * 100 / games else 0
}

// ─── Výsledek hry ─────────────────────────────────────────────────────────────
data class OnlineGameResult(
    val winner    : String,   // "A" | "B" | "DRAW"
    val winnerName: String?,
    val youWin    : Boolean
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class OnlineLobbyViewModel(
    private val allCards: List<Card>,
    private val deviceId: String
) : ViewModel() {

    // ── Lobby stav ────────────────────────────────────────────────────────────
    var phase        = mutableStateOf(OnlinePhase.NAME_INPUT); private set
    var playerName   = mutableStateOf(PlayerProfileManager.profile?.name ?: ""); private set
    var onlineCount  = mutableStateOf(0);  private set
    var queueSize    = mutableStateOf(0);  private set
    var statusMsg    = mutableStateOf(""); private set
    var errorMsg     = mutableStateOf(""); private set
    var matchInfo    = mutableStateOf<OnlineMatchInfo?>(null); private set

    // ── Rating / MMR ──────────────────────────────────────────────────────────
    /** Aktuální rating hráče v módu normal (z WELCOME nebo GAME_OVER) */
    var myRating        = mutableStateOf<Int?>(null); private set
    /** Statistiky hráče pro všechny herní módy (z WELCOME) */
    var allModeStats    = mutableStateOf<Map<String, OnlineModeStats>>(emptyMap()); private set
    /** Rating soupeře v aktuálním zápase (z MATCH_FOUND) */
    var opponentRating  = mutableStateOf<Int?>(null); private set
    /** Změna ratingu po posledním zápase (+25 / -15 / 0) */
    var ratingChange    = mutableStateOf<Int?>(null); private set
    /** Nový rating po posledním zápase */
    var newRating       = mutableStateOf<Int?>(null); private set

    /** -1 = náhodný balíček, 0..2 = index uloženého balíčku */
    var selectedDeckIndex = mutableStateOf(-1); private set

    /** True = hráč čeká v super-náhodné frontě */
    var isSuperRandom = mutableStateOf(false); private set

    /** Předem sestavené IDs z vybraného balíčku; null = náhodný */
    private var _pendingDeckIds: List<String>? = null

    /**
     * Nastav vybraný balíček. Deck IDs se předají hned, aby joinQueue() nemusel
     * řešit lambda capture ani snapshot timing.
     * @param index   -1 = náhodný, 0..2 = uložený balíček
     * @param deckIds 30 base ID karet, nebo null pro náhodný
     */
    fun setDeckChoice(index: Int, deckIds: List<String>? = null) {
        selectedDeckIndex.value = index
        _pendingDeckIds = deckIds
        android.util.Log.d("DECK", "setDeckChoice: index=$index, deckIds=${deckIds?.size ?: "null"}")
        // Ukáže v lobby UI kolik karet bude posláno
        statusMsg.value = if (deckIds != null) "Balíček: ${deckIds.size} karet připraveno" else "Balíček: náhodný"
    }

    // ── Herní stav ────────────────────────────────────────────────────────────
    var mulliganHand         = mutableStateOf<List<Card>>(emptyList()); private set
    var mulliganSelected     = mutableStateOf<Set<String>>(emptySet()); private set
    var mulliganSubmitted    = mutableStateOf(false); private set
    var opponentMulliganDone = mutableStateOf(false); private set
    /** Zbývající sekundy mulligan odpočtu; null = timer neběží */
    var mulliganSecondsLeft  = mutableStateOf<Int?>(null); private set
    private var mulliganTimerJob: Job? = null

    var gameState        = mutableStateOf(OnlineGameState()); private set
    var gameResult       = mutableStateOf<OnlineGameResult?>(null); private set
    /** true po dobu 1s po rozhodující kartě – blokuje vstup, aby bylo vidět co rozhodlo */
    var gameEndPending   = mutableStateOf(false); private set
    var gameLog          = mutableStateOf<List<LogEntry>>(emptyList()); private set
    var lastPlayedCard   = mutableStateOf<Card?>(null); private set
    var lastPlayedByMe   = mutableStateOf(false); private set
    var lastPlayedAction = mutableStateOf<CardAction?>(null); private set

    // ── Online Rozhodnutí ─────────────────────────────────────────────────────
    /** Čekající výběr karty (Rozhodnutí); null = žádné */
    var onlinePendingDecision     = mutableStateOf<DecisionState?>(null); private set
    var onlineDecisionSecondsLeft = mutableStateOf<Int?>(null); private set
    /** Mapování baseId → instanceId pro aktuální Decision nabídku */
    private var onlineDecisionOptionsById: Map<String, String> = emptyMap()
    private var onlineDecisionTimerJob: Job? = null

    // ── Stav odpojení soupeře ─────────────────────────────────────────────────
    /** Soupeř se odpojil a čeká se na jeho reconnect */
    var opponentDisconnected      = mutableStateOf(false);  private set
    /** Zbývající sekundy do ukončení hry při odpojení soupeře */
    var opponentDisconnectSec     = mutableStateOf(0);      private set
    private var oppDisconnectJob: Job? = null

    // ── Vlastní auto-reconnect ────────────────────────────────────────────────
    /** True = probíhá pokus o znovupřipojení po výpadku */
    var isReconnecting            = mutableStateOf(false);  private set
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    // ── WebSocket ─────────────────────────────────────────────────────────────
    private var ws: WebSocket? = null
    /**
     * Čítač generací spojení – inkrementuje se při každém novém connect().
     * GameListener drží gen z doby svého vzniku; pokud se liší, je považován za zastaralý.
     */
    private var connectionGeneration = 0
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Lobby akce ────────────────────────────────────────────────────────────

    fun setName(name: String) {
        // Odstraň řídicí znaky, ponech jen tisknutelné; max 20 znaků
        playerName.value = name.filter { it >= ' ' && it != '\u007F' }.take(20)
    }

    fun connect() {
        val name = playerName.value.trim()
        if (name.isBlank()) { errorMsg.value = "Zadej přezdívku"; return }

        // Zruš předchozí spojení a invaliduj staré listenery novou generací
        ws?.cancel()
        ws = null
        connectionGeneration++
        isReconnecting.value = false
        reconnectAttempts    = 0

        phase.value     = OnlinePhase.CONNECTING
        statusMsg.value = "Připojuji k serveru…"
        errorMsg.value  = ""

        val request = Request.Builder().url(LOBBY_WS_URL).build()
        ws = httpClient.newWebSocket(request, GameListener(connectionGeneration))
    }

    /**
     * Znovu se připoj po chybě – zruší staré WS, resetuje stav a zavolá connect().
     * Používá se z ErrorPanel tlačítka "Zkusit znovu".
     */
    fun retryConnect() {
        errorMsg.value = ""
        connect()
    }

    fun joinQueue(superRandom: Boolean = false) {
        errorMsg.value = ""
        if (superRandom) {
            // Super náhodný mód: žádný vlastní balíček, speciální fronta
            val json = JSONObject().apply {
                put("type", "QUEUE_JOIN")
                put("mode", "super_random")
            }
            ws?.send(json.toString())
            isSuperRandom.value = true
            phase.value     = OnlinePhase.QUEUING
            statusMsg.value = "Hledám super náhodného soupeře…"
            return
        }

        val deckIds = _pendingDeckIds
        android.util.Log.d("DECK", "joinQueue: selectedDeckIndex=${selectedDeckIndex.value}, deckIds=${deckIds?.size ?: "null"}")
        // Pokud je vybrán konkrétní balíček ale nemá platná IDs, zablokuj
        if (selectedDeckIndex.value >= 0 && deckIds == null) {
            errorMsg.value = "Vybraný balíček nemá 30 karet"
            return
        }
        val json = JSONObject().apply {
            put("type", "QUEUE_JOIN")
            if (deckIds != null) {
                val arr = JSONArray()
                deckIds.forEach { arr.put(it) }
                put("deckIds", arr)
            }
        }
        val jsonStr = json.toString()
        android.util.Log.d("DECK", "QUEUE_JOIN payload length=${jsonStr.length}, hasDeckIds=${jsonStr.contains("deckIds")}")
        ws?.send(jsonStr)
        isSuperRandom.value = false
        phase.value     = OnlinePhase.QUEUING
        statusMsg.value = "Hledám soupeře…"
    }

    fun leaveQueue() {
        send("type" to "QUEUE_LEAVE")
        isSuperRandom.value = false
        phase.value     = OnlinePhase.LOBBY
        statusMsg.value = ""
    }

    /** Konec hry → zůstat připojený, vrátit se do lobby (připraven na nový zápas). */
    fun returnToLobby() {
        resetGameState()
        _pendingDeckIds         = null
        selectedDeckIndex.value = -1
        isSuperRandom.value     = false
        opponentRating.value    = null
        ratingChange.value      = null
        newRating.value         = null
        phase.value     = OnlinePhase.LOBBY
        statusMsg.value = ""
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
        resetGameState()
        phase.value     = OnlinePhase.NAME_INPUT
        statusMsg.value = ""
        errorMsg.value  = ""
        onlineCount.value = 0
        queueSize.value   = 0
    }

    fun clearError() {
        ws?.cancel()
        ws = null
        errorMsg.value = ""
        phase.value    = OnlinePhase.NAME_INPUT
    }

    // ── Mulligan akce ─────────────────────────────────────────────────────────

    fun toggleMulligan(cardId: String) {
        if (mulliganSubmitted.value) return
        val cur = mulliganSelected.value
        mulliganSelected.value =
            if (cardId in cur) cur - cardId else cur + cardId
    }

    fun confirmMulligan() {
        if (mulliganSubmitted.value) return
        cancelMulliganTimer()
        mulliganSubmitted.value = true
        val returnIds = mulliganSelected.value.toList()
        sendMulliganDone(returnIds)
    }

    fun skipMulligan() {
        if (mulliganSubmitted.value) return
        cancelMulliganTimer()
        mulliganSubmitted.value = true
        sendMulliganDone(emptyList())
    }

    /** Spustí odpočet [seconds] sekund; po vypršení auto-skip bez výměn. */
    private fun startMulliganTimer(seconds: Int) {
        cancelMulliganTimer()
        mulliganSecondsLeft.value = seconds
        mulliganTimerJob = viewModelScope.launch {
            for (remaining in seconds - 1 downTo 0) {
                delay(1_000)
                mulliganSecondsLeft.value = remaining
            }
            // Timeout – auto-skip (pokud už neodeslal hráč sám)
            skipMulligan()
        }
    }

    private fun cancelMulliganTimer() {
        mulliganTimerJob?.cancel()
        mulliganTimerJob = null
        mulliganSecondsLeft.value = null
    }

    // ── Online Rozhodnutí ─────────────────────────────────────────────────────

    /** Hráč vybral kartu z Decision nabídky – pošleme id serveru. */
    fun resolveOnlineDecision(card: Card) {
        cancelOnlineDecisionTimer()
        onlinePendingDecision.value = null
        sendAction("DECISION_RESPONSE", JSONObject().apply { put("chosenId", card.id) })
    }

    private fun startOnlineDecisionTimer(seconds: Int) {
        cancelOnlineDecisionTimer()
        onlineDecisionSecondsLeft.value = seconds
        onlineDecisionTimerJob = viewModelScope.launch {
            for (remaining in seconds - 1 downTo 0) {
                delay(1_000)
                onlineDecisionSecondsLeft.value = remaining
            }
            // Timeout – zavři overlay; server auto-vybere sám
            onlinePendingDecision.value = null
            onlineDecisionSecondsLeft.value = null
        }
    }

    private fun cancelOnlineDecisionTimer() {
        onlineDecisionTimerJob?.cancel()
        onlineDecisionTimerJob = null
        onlineDecisionSecondsLeft.value = null
    }

    private fun sendMulliganDone(returnIds: List<String>) {
        val gameId = matchInfo.value?.gameId
        if (gameId == null) {
            android.util.Log.w("MULLIGAN", "sendMulliganDone: matchInfo.gameId je null – posílám bez gameId (server použije player.gameId)")
        }
        val json = JSONObject().apply {
            put("type", "MULLIGAN_DONE")
            if (gameId != null) put("gameId", gameId)
            put("returnIds", JSONArray(returnIds))
        }
        val sent = ws?.send(json.toString())
        android.util.Log.d("MULLIGAN", "sendMulliganDone: gameId=$gameId returnIds=$returnIds ws=${ws != null} sent=$sent")
    }

    // ── Herní akce ────────────────────────────────────────────────────────────

    fun playCard(cardId: String) {
        sendAction("PLAY_CARD", JSONObject().apply { put("cardId", cardId) })
    }

    fun discardCard(cardId: String) {
        sendAction("DISCARD_CARD", JSONObject().apply { put("cardId", cardId) })
    }

    fun endTurn() {
        sendAction("END_TURN", JSONObject())
    }

    fun skipTurn() {
        sendAction("SKIP_TURN", JSONObject())
    }

    /** Vzdání hry – okamžitě registruje prohru a přejde do GAME_OVER bez čekání. */
    fun forfeit() {
        // Pošli serveru – ten ukončí hru a pošle GAME_OVER oběma hráčům
        sendAction("FORFEIT", JSONObject())
        // Nastav výsledek lokálně ihned pro okamžitou odezvu UI;
        // server přepíše gameResult svou GAME_OVER zprávou (stejná hodnota)
        gameResult.value = OnlineGameResult(
            winner     = "OPP",
            winnerName = matchInfo.value?.opponentName ?: "Soupeř",
            youWin     = false
        )
        phase.value = OnlinePhase.GAME_OVER
        // WebSocket zůstane otevřený → hráč se normálně vrátí do lobby přes overlay
    }

    private fun sendAction(action: String, data: JSONObject) {
        val gameId = matchInfo.value?.gameId ?: return
        val json = JSONObject().apply {
            put("type", "GAME_ACTION")
            put("gameId", gameId)
            put("action", action)
            put("data", data)
        }
        ws?.send(json.toString())
    }

    // ── WebSocket listener ────────────────────────────────────────────────────

    /**
     * @param gen Generace spojení v době vzniku listeneru.
     * Pokud se [connectionGeneration] změní (nové connect()), tento listener je zastaralý
     * a tiše ignoruje všechny callbacky – zabraňuje přepsání stavu novějšího spojení.
     */
    private inner class GameListener(private val gen: Int) : WebSocketListener() {

        private fun isStale() = gen != connectionGeneration

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isStale()) { webSocket.cancel(); return }
            val abilitiesArr = JSONArray().apply {
                PlayerProfileManager.profile?.activeAbilities?.forEach { put(it) }
            }
            webSocket.send(JSONObject().apply {
                put("type",             "JOIN")
                put("name",             playerName.value.trim())
                put("avatar",           PlayerProfileManager.profile?.avatar        ?: "⚔️")
                put("cardBackSkin",     PlayerProfileManager.profile?.cardBackSkin  ?: "card_back_frame")
                put("castleSkin",       PlayerProfileManager.profile?.castleSkin    ?: "castle_player")
                put("level",            PlayerProfileManager.profile?.level  ?: 1)
                put("activeAbilities",  abilitiesArr)
                put("deviceId",         deviceId)
            }.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale()) return
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isStale()) return
            viewModelScope.launch {
                if (isStale()) return@launch
                val inGame = phase.value == OnlinePhase.GAME_PLAYING ||
                             phase.value == OnlinePhase.GAME_MULLIGAN
                if (inGame && reconnectAttempts < maxReconnectAttempts) {
                    scheduleReconnect()
                } else {
                    phase.value    = OnlinePhase.ERROR
                    errorMsg.value = "Nepodařilo se připojit: ${t.message ?: "neznámá chyba"}"
                    isReconnecting.value = false
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isStale()) return
            if (code != 1000) {
                viewModelScope.launch {
                    if (isStale()) return@launch
                    val inGame = phase.value == OnlinePhase.GAME_PLAYING ||
                                 phase.value == OnlinePhase.GAME_MULLIGAN
                    if (inGame && reconnectAttempts < maxReconnectAttempts) {
                        scheduleReconnect()
                    } else {
                        phase.value    = OnlinePhase.ERROR
                        errorMsg.value = "Spojení přerušeno (kód $code)"
                        isReconnecting.value = false
                    }
                }
            }
        }
    }

    /** Naplánuje pokus o znovupřipojení s exponenciálním zpožděním. */
    private fun scheduleReconnect() {
        isReconnecting.value = true
        reconnectAttempts++
        val delayMs  = (reconnectAttempts * 2000L).coerceAtMost(10_000L)
        val snapGen  = connectionGeneration          // zapamatuj si generaci v době naplánování
        viewModelScope.launch {
            delay(delayMs)
            // Přeruš pokud mezitím přišlo nové manuální connect()
            if (!isReconnecting.value || snapGen != connectionGeneration) return@launch
            connectionGeneration++
            val request = Request.Builder().url(LOBBY_WS_URL).build()
            ws = httpClient.newWebSocket(request, GameListener(connectionGeneration))
        }
    }

    // ── Zpracování zpráv ──────────────────────────────────────────────────────

    private fun handleMessage(raw: String) {
        val json = try { JSONObject(raw) } catch (e: Exception) { return }
        viewModelScope.launch {
            when (json.optString("type")) {

                "WELCOME" -> {
                    onlineCount.value    = json.optInt("online", 0)
                    queueSize.value      = json.optInt("queue",  0)
                    // Načti statistiky ze všech módů (ratings = { normal: {...}, super_random: {...} })
                    val ratingsObj = json.optJSONObject("ratings")
                    if (ratingsObj != null) {
                        val parsed = mutableMapOf<String, OnlineModeStats>()
                        for (mode in ratingsObj.keys()) {
                            val m = ratingsObj.optJSONObject(mode) ?: continue
                            parsed[mode] = OnlineModeStats(
                                rating = m.optInt("rating", 1000),
                                wins   = m.optInt("wins",   0),
                                losses = m.optInt("losses", 0),
                                draws  = m.optInt("draws",  0),
                                games  = m.optInt("games",  0)
                            )
                        }
                        allModeStats.value = parsed
                        myRating.value = parsed["normal"]?.rating
                    }
                    if (isReconnecting.value) {
                        // Auto-reconnect proběhl – přejdi do lobby a čekej, zda server
                        // pošle GAME_MULLIGAN / GAME_STATE pro obnovení hry.
                        // Pokud server hru nezná (restart bez persistence), klient
                        // správně zůstane v lobby místo aby uvízl v herní fázi.
                        // preserveMatchInfo = true: matchInfo (gameId, side …) zůstane,
                        // aby sendAction() mohlo odeslat akci po příchodu GAME_STATE.
                        resetGameState(preserveMatchInfo = true)
                        phase.value     = OnlinePhase.LOBBY
                        statusMsg.value = "Reconnect ✓"
                        errorMsg.value  = ""
                    } else {
                        val inGame = phase.value == OnlinePhase.GAME_MULLIGAN ||
                                     phase.value == OnlinePhase.GAME_PLAYING
                        if (!inGame) {
                            phase.value      = OnlinePhase.LOBBY
                            statusMsg.value  = "Připojeno ✓"
                        }
                    }
                    android.util.Log.d("WELCOME", "WELCOME: isReconnecting=${isReconnecting.value} phase→${phase.value}")
                    isReconnecting.value = false
                    reconnectAttempts    = 0
                }

                "COUNT" -> {
                    onlineCount.value = json.optInt("online", 0)
                    queueSize.value   = json.optInt("queue",  0)
                }

                "QUEUE_OK" -> {
                    phase.value     = OnlinePhase.QUEUING
                    statusMsg.value = "Ve frontě…"
                }

                "MATCH_FOUND" -> {
                    matchInfo.value = OnlineMatchInfo(
                        gameId               = json.optString("gameId", ""),
                        opponentName         = json.optString("opponentName", "Soupeř"),
                        opponentAvatar       = json.optString("opponentAvatar", "👺"),
                        opponentCardBackSkin = json.optString("opponentCardBackSkin", "card_back_frame"),
                        opponentCastleSkin   = json.optString("opponentCastleSkin",   "castle_player"),
                        opponentLevel        = json.optInt("opponentLevel", -1),
                        side                 = json.optString("side", "A")
                    )
                    // Načti rating hráče i soupeře z MATCH_FOUND
                    if (!json.isNull("myRating"))       myRating.value       = json.optInt("myRating", 1000)
                    if (!json.isNull("opponentRating")) opponentRating.value = json.optInt("opponentRating", 1000)
                    ratingChange.value = null  // vyresetuj změnu z předchozího zápasu
                    // Nepřecházíme do GAME_MULLIGAN hned – čekáme na GAME_MULLIGAN ze serveru
                    statusMsg.value = "Soupeř nalezen! Připravuji hru…"
                }

                "GAME_MULLIGAN" -> {
                    val handJson   = json.optJSONArray("hand")
                    val timeoutMs  = json.optInt("timeoutMs", 30_000)
                    mulliganHand.value         = parseCardArray(handJson)
                    mulliganSelected.value     = emptySet()
                    mulliganSubmitted.value    = false
                    opponentMulliganDone.value = false
                    lastPlayedCard.value       = null   // čistý stav pro novou hru
                    lastPlayedByMe.value       = false
                    phase.value = OnlinePhase.GAME_MULLIGAN
                    startMulliganTimer((timeoutMs / 1_000).coerceAtLeast(5))
                    android.util.Log.d("MULLIGAN", "GAME_MULLIGAN přijato: hand=${mulliganHand.value.size} karet, timeout=${timeoutMs}ms")
                }

                "MULLIGAN_OK" -> {
                    // Server potvrdil mulligan, updatuj ruku
                    val handJson = json.optJSONArray("hand")
                    mulliganHand.value = parseCardArray(handJson)
                }

                "OPPONENT_MULLIGAN_DONE" -> {
                    opponentMulliganDone.value = true
                }

                "GAME_STATE" -> {
                    gameState.value = parseGameState(json)
                    phase.value = OnlinePhase.GAME_PLAYING
                    // Zahraná karta pro animaci + log záznam
                    val lpc = json.optJSONObject("lastPlayedCard")
                    if (lpc != null) {
                        val baseId = lpc.optString("baseId", "")
                        val template = allCards.find { it.id == baseId }
                        if (template != null) {
                            val card = template.copy(id = lpc.optString("id", baseId))
                            lastPlayedCard.value   = card
                            val isMe = json.optBoolean("lastPlayedByMe", false)
                            lastPlayedByMe.value   = isMe
                            val action = when (json.optString("lastPlayedAction", "PLAYED")) {
                                "DISCARDED" -> CardAction.DISCARDED
                                "BURNED"    -> CardAction.BURNED
                                "STOLEN"    -> CardAction.STOLEN
                                else        -> CardAction.PLAYED
                            }
                            lastPlayedAction.value = action
                            val actorName = if (isMe) playerName.value
                                            else (matchInfo.value?.opponentName ?: "Soupeř")
                            val turn = gameState.value.turnNumber
                            gameLog.value = (gameLog.value + LogEntry.CardEvent(actorName, card, action, isMe, turn)).takeLast(50)
                        }
                    }
                    // lpc == null → necháme předchozí kartu (mizí až po nahrazení novou)
                }

                "CARD_LOST" -> {
                    // Karta nám byla odebrána – zobrazíme ji v centru bojiště s ikonou akce
                    val action = when (json.optString("action", "")) {
                        "BURNED" -> CardAction.BURNED
                        "STOLEN" -> CardAction.STOLEN
                        else     -> null
                    }
                    if (action != null) {
                        val cardId   = json.optString("cardId", "")
                        val baseId   = cardId.substringBefore('_')
                        val template = allCards.find { it.id == baseId }
                        if (template != null) {
                            lastPlayedCard.value   = template.copy(id = cardId)
                            lastPlayedAction.value = action
                            lastPlayedByMe.value   = true  // naše karta = zobrazit jako "naše"
                            val oppName = matchInfo.value?.opponentName ?: "Soupeř"
                            val turn = gameState.value.turnNumber
                            gameLog.value = (gameLog.value + LogEntry.CardEvent(oppName, template, action, isMe = false, turn)).takeLast(50)
                        }
                    }
                }

                "GAME_OVER" -> {
                    val result = OnlineGameResult(
                        winner     = json.optString("winner", "DRAW"),
                        winnerName = json.optString("winnerName").takeIf { it.isNotEmpty() },
                        youWin     = json.optBoolean("youWin", false)
                    )
                    // Rating změna: server posílá ratingChange (+25/-15/0) a newRating
                    if (!json.isNull("ratingChange")) ratingChange.value = json.optInt("ratingChange", 0)
                    if (!json.isNull("newRating")) {
                        val nr = json.optInt("newRating", 1000)
                        newRating.value = nr
                        myRating.value  = nr
                        // Aktualizuj allModeStats pro správný mód
                        val mode = json.optString("mode", "normal")
                        val old  = allModeStats.value[mode] ?: OnlineModeStats()
                        val delta = ratingChange.value ?: 0
                        val updatedStats = old.copy(
                            rating = nr,
                            wins   = if (delta > 0) old.wins + 1   else old.wins,
                            losses = if (delta < 0) old.losses + 1 else old.losses,
                            draws  = if (delta == 0) old.draws + 1 else old.draws,
                            games  = old.games + 1
                        )
                        allModeStats.value = allModeStats.value + (mode to updatedStats)
                    }
                    // Pokud forfeit() již výsledek nastavil (winner="OPP"), nepřepisuj ho –
                    // jinak by se LaunchedEffect(gameResult) spustil podruhé a hráč
                    // by dostal dvojitou odměnu. Fázi ani gameEndPending nenastavujeme –
                    // forfeit() už přepnul do GAME_OVER okamžitě.
                    if (gameResult.value != null) return@launch

                    gameEndPending.value = true
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(1750L)
                        gameResult.value = result
                        phase.value = OnlinePhase.GAME_OVER
                        gameEndPending.value = false
                    }
                }

                "OPPONENT_LEFT" -> {
                    // Soupeř se nepřipojil včas — zruš countdown overlay a ukonči hru
                    oppDisconnectJob?.cancel()
                    opponentDisconnected.value  = false
                    opponentDisconnectSec.value = 0
                    gameResult.value = OnlineGameResult(
                        winner     = matchInfo.value?.side ?: "A",
                        winnerName = playerName.value,
                        youWin     = true
                    )
                    errorMsg.value = "Soupeř se odpojil – vyhráváš!"
                    phase.value    = OnlinePhase.GAME_OVER
                }

                "OPPONENT_DISCONNECTED" -> {
                    val sec = json.optInt("timeoutSec", 60)
                    opponentDisconnected.value  = true
                    opponentDisconnectSec.value = sec
                    oppDisconnectJob?.cancel()
                    oppDisconnectJob = viewModelScope.launch {
                        var remaining = sec
                        while (remaining > 0) {
                            delay(1_000)
                            remaining--
                            opponentDisconnectSec.value = remaining
                        }
                    }
                }

                "OPPONENT_RECONNECTED" -> {
                    oppDisconnectJob?.cancel()
                    opponentDisconnected.value  = false
                    opponentDisconnectSec.value = 0
                }

                "DECISION_REQUEST" -> {
                    val effectType = json.optString("effectType", "")
                    val timeoutSec = json.optInt("timeoutMs", 30_000) / 1_000
                    val optArr     = json.optJSONArray("options")
                    val options    = parseCardArray(optArr)

                    val (title, subtitle) = when (effectType) {
                        "DecisionBurnOpponent" -> "Likvidace"  to "Vyber kartu k zahazení ze soupeřova balíčku"
                        "DecisionChooseType"   -> {
                            val ct = json.optString("cardType", "")
                            "Rekrut" to "Vyber kartu typu $ct"
                        }
                        "DecisionFromDiscard"  -> "Vzpomínka" to "Vyber kartu z odhazovacího balíčku"
                        "DecisionFromDeck"     -> "Intuice"   to "Vyber kartu z vlastního balíčku"
                        else                   -> "Rozhodnutí" to "Vyber kartu"
                    }

                    onlinePendingDecision.value = DecisionState(title, subtitle, options)
                    startOnlineDecisionTimer(timeoutSec)
                }

                "GAME_ERROR" -> {
                    // Dočasná chyba hry (špatná akce) – zobraz jako zprávu, nepřeruš hru
                    val msg = json.optString("msg", "Chyba")
                    gameLog.appendLog("⚠️ $msg")
                }

                "ERROR" -> {
                    val msg = json.optString("msg", "Chyba serveru")
                    errorMsg.value = msg
                    when (phase.value) {
                        OnlinePhase.CONNECTING,
                        OnlinePhase.LOBBY -> {
                            phase.value = OnlinePhase.ERROR
                        }
                        OnlinePhase.GAME_MULLIGAN,
                        OnlinePhase.GAME_PLAYING -> {
                            // Server nás vyhodil z herní fáze (např. po restartu hra neexistuje).
                            // Vrátíme do lobby – jsme stále připojeni, hráč může hrát znovu.
                            resetGameState()   // zahrnuje cancelMulliganTimer()
                            phase.value     = OnlinePhase.LOBBY
                            statusMsg.value = "⚠️ $msg"
                            errorMsg.value  = ""   // zprávu zobrazí statusMsg, ne errorMsg
                        }
                        else -> { /* ostatní fáze neřešíme */ }
                    }
                }
            }
        }
    }

    // ── Parsování JSON → datové třídy ─────────────────────────────────────────

    private fun parseCardArray(arr: JSONArray?): List<Card> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Card>()
        for (i in 0 until arr.length()) {
            val obj        = arr.getJSONObject(i)
            val instanceId = obj.optString("id", "")
            val baseId     = obj.optString("baseId", instanceId.substringBefore('_'))
            val isGenerated = obj.optBoolean("isGenerated", false)
            // Najdi plný Card objekt v allCards podle baseId
            val template = allCards.find { it.id == baseId }
            if (template != null) {
                // Přetypuj s instančním ID + příznakem generování
                result.add(template.copy(id = instanceId, isGenerated = isGenerated))
            }
        }
        return result
    }

    private fun parsePlayerState(obj: JSONObject?, isMe: Boolean): OnlinePlayerState {
        if (obj == null) return OnlinePlayerState()

        val resources        = mutableMapOf<String, Int>()
        val mines            = mutableMapOf<String, Int>()
        val mineBlockedTurns = mutableMapOf<String, Int>()
        obj.optJSONObject("resources")?.let { r ->
            for (key in r.keys()) resources[key] = r.optInt(key, 0)
        }
        obj.optJSONObject("mines")?.let { m ->
            for (key in m.keys()) mines[key] = m.optInt(key, 0)
        }
        obj.optJSONObject("mineBlockedTurns")?.let { b ->
            for (key in b.keys()) mineBlockedTurns[key] = b.optInt(key, 0)
        }

        val pendingResources: List<OnlinePendingResource> = if (isMe) {
            val arr = obj.optJSONArray("pendingResources")
            buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val p = arr.optJSONObject(i) ?: continue
                        add(OnlinePendingResource(
                            type      = p.optString("type", ""),
                            amount    = p.optInt("amount", 0),
                            turnsLeft = p.optInt("turnsLeft", 0)
                        ))
                    }
                }
            }
        } else emptyList()

        val hand: List<Card> = if (isMe) {
            parseCardArray(obj.optJSONArray("hand"))
        } else {
            // Normálně skrytá; server odhalí skutečné karty v posledním GAME_STATE (game over)
            val revealedArr = obj.optJSONArray("hand")
            if (revealedArr != null) parseCardArray(revealedArr) else emptyList()
        }

        val lastPlayedIdx = if (!isMe && !obj.isNull("lastPlayedIdx"))
            obj.optInt("lastPlayedIdx", -1).takeIf { it >= 0 }
        else null

        return OnlinePlayerState(
            castleHP         = obj.optInt("castleHP",   30),
            wallHP           = obj.optInt("wallHP",      10),
            resources        = resources,
            mines            = mines,
            mineBlockedTurns = mineBlockedTurns,
            pendingResources = pendingResources,
            hand             = hand,
            handSize         = if (isMe) hand.size else obj.optInt("handSize", 0),
            deckSize         = obj.optInt("deckSize",    0),
            discardSize      = obj.optInt("discardSize", 0),
            maxHandSize      = if (isMe) obj.optInt("maxHandSize", 7) else 7,
            lastPlayedIdx    = lastPlayedIdx
        )
    }

    private fun parseGameState(json: JSONObject): OnlineGameState {
        val logArr   = json.optJSONArray("log")
        val logList  = mutableListOf<String>()
        if (logArr != null) {
            for (i in 0 until logArr.length()) logList.add(logArr.optString(i, ""))
        }
        return OnlineGameState(
            activeSide      = json.optString("activeSide", "A"),
            isMyTurn        = json.optBoolean("isMyTurn", false),
            turnNumber      = json.optInt("turnNumber", 1),
            myState         = parsePlayerState(json.optJSONObject("myState"),  true),
            oppState        = parsePlayerState(json.optJSONObject("oppState"), false),
            myWinTarget     = json.optInt("myWinTarget",  60),
            oppWinTarget    = json.optInt("oppWinTarget", 60),
            log             = logList,
            turnRemainingMs = json.optLong("turnRemainingMs", 15_000L),
            timebankMeMs    = json.optLong("timebankMeMs",    120_000L),
            timebankOppMs   = json.optLong("timebankOppMs",   120_000L),
            receivedAt      = System.currentTimeMillis()
        )
    }

    // ── Pomocné ───────────────────────────────────────────────────────────────

    /**
     * Vyresetuje herní stav.
     * @param preserveMatchInfo  true při reconnectu – zachová matchInfo (gameId, side, jméno soupeře),
     *                           protože akce (PLAY_CARD, END_TURN …) bez gameId tiše selžou.
     *                           false při normálním odchodu ze hry – kompletní reset.
     */
    private fun resetGameState(preserveMatchInfo: Boolean = false) {
        cancelMulliganTimer()
        cancelOnlineDecisionTimer()
        onlinePendingDecision.value = null
        gameEndPending.value       = false
        mulliganHand.value         = emptyList()
        mulliganSelected.value     = emptySet()
        mulliganSubmitted.value    = false
        opponentMulliganDone.value = false
        gameState.value            = OnlineGameState()
        gameResult.value           = null
        gameLog.value              = emptyList()
        lastPlayedCard.value       = null
        lastPlayedByMe.value       = false
        lastPlayedAction.value     = null
        if (!preserveMatchInfo) matchInfo.value = null
    }

    private fun send(vararg pairs: Pair<String, Any>) {
        val json = JSONObject()
        pairs.forEach { (k, v) -> json.put(k, v) }
        ws?.send(json.toString())
    }

    override fun onCleared() {
        super.onCleared()
        ws?.close(1000, "bye")
        httpClient.dispatcher.executorService.shutdown()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val allCards: List<Card>,
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val prefs    = context.getSharedPreferences("termiti_prefs", android.content.Context.MODE_PRIVATE)
            val deviceId = prefs.getString("device_id", null) ?: run {
                val newId = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("device_id", newId).apply()
                newId
            }
            return OnlineLobbyViewModel(allCards, deviceId) as T
        }
    }
}

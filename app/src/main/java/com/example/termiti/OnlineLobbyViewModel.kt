package com.example.termiti

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Adresa lobby serveru ─────────────────────────────────────────────────────
private const val LOBBY_WS_URL = "ws://192.168.125.139:8765/lobby"

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
    val gameId       : String,
    val opponentName : String,
    val side         : String   // "A" nebo "B"
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
    val lastPlayedIdx    : Int?                      = null           // jen oppState: index zahrané karty
)

// ─── Herní stav (pro GAME_STATE zprávy) ──────────────────────────────────────
data class OnlineGameState(
    val activeSide       : String            = "A",
    val isMyTurn         : Boolean           = false,
    val turnNumber       : Int               = 1,
    val myState          : OnlinePlayerState = OnlinePlayerState(),
    val oppState         : OnlinePlayerState = OnlinePlayerState(),
    val log              : List<String>      = emptyList(),
    // ── Timer (relativní, bez závislosti na sync hodin) ──────────────────────
    val turnRemainingMs  : Long              = 15_000L, // zbývající ms ve fázi tahu
    val timebankMeMs     : Long              = 120_000L, // zbývající ms v mém timebanku
    val timebankOppMs    : Long              = 120_000L, // zbývající ms v soupeřově timebanku
    val receivedAt       : Long              = 0L        // System.currentTimeMillis() při přijetí
)

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
    var playerName   = mutableStateOf(""); private set
    var onlineCount  = mutableStateOf(0);  private set
    var queueSize    = mutableStateOf(0);  private set
    var statusMsg    = mutableStateOf(""); private set
    var errorMsg     = mutableStateOf(""); private set
    var matchInfo    = mutableStateOf<OnlineMatchInfo?>(null); private set

    // ── Herní stav ────────────────────────────────────────────────────────────
    var mulliganHand      = mutableStateOf<List<Card>>(emptyList()); private set
    var mulliganSelected  = mutableStateOf<Set<String>>(emptySet()); private set
    var mulliganSubmitted = mutableStateOf(false); private set
    var opponentMulliganDone = mutableStateOf(false); private set

    var gameState        = mutableStateOf(OnlineGameState()); private set
    var gameResult       = mutableStateOf<OnlineGameResult?>(null); private set
    var gameLog          = mutableStateOf<List<LogEntry>>(emptyList()); private set
    var lastPlayedCard   = mutableStateOf<Card?>(null); private set
    var lastPlayedByMe   = mutableStateOf(false); private set
    var lastPlayedAction = mutableStateOf<CardAction?>(null); private set

    // ── WebSocket ─────────────────────────────────────────────────────────────
    private var ws: WebSocket? = null
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

        phase.value     = OnlinePhase.CONNECTING
        statusMsg.value = "Připojuji k serveru…"
        errorMsg.value  = ""

        val request = Request.Builder().url(LOBBY_WS_URL).build()
        ws = httpClient.newWebSocket(request, GameListener())
    }

    fun joinQueue() {
        send("type" to "QUEUE_JOIN")
        phase.value     = OnlinePhase.QUEUING
        statusMsg.value = "Hledám soupeře…"
    }

    fun leaveQueue() {
        send("type" to "QUEUE_LEAVE")
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
        mulliganSubmitted.value = true
        val returnIds = mulliganSelected.value.toList()
        sendMulliganDone(returnIds)
    }

    fun skipMulligan() {
        if (mulliganSubmitted.value) return
        mulliganSubmitted.value = true
        sendMulliganDone(emptyList())
    }

    private fun sendMulliganDone(returnIds: List<String>) {
        val gameId = matchInfo.value?.gameId ?: return
        val json = JSONObject().apply {
            put("type", "MULLIGAN_DONE")
            put("gameId", gameId)
            put("returnIds", JSONArray(returnIds))
        }
        ws?.send(json.toString())
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

    private inner class GameListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(JSONObject().apply {
                put("type", "JOIN")
                put("name", playerName.value.trim())
                put("deviceId", deviceId)
            }.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            viewModelScope.launch {
                phase.value    = OnlinePhase.ERROR
                errorMsg.value = "Nepodařilo se připojit: ${t.message ?: "neznámá chyba"}"
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code != 1000) {
                viewModelScope.launch {
                    phase.value    = OnlinePhase.ERROR
                    errorMsg.value = "Spojení přerušeno (kód $code)"
                }
            }
        }
    }

    // ── Zpracování zpráv ──────────────────────────────────────────────────────

    private fun handleMessage(raw: String) {
        val json = try { JSONObject(raw) } catch (e: Exception) { return }
        viewModelScope.launch {
            when (json.optString("type")) {

                "WELCOME" -> {
                    onlineCount.value  = json.optInt("online", 0)
                    queueSize.value    = json.optInt("queue",  0)
                    phase.value        = OnlinePhase.LOBBY
                    statusMsg.value    = "Připojeno ✓"
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
                        gameId       = json.optString("gameId", ""),
                        opponentName = json.optString("opponentName", "Soupeř"),
                        side         = json.optString("side", "A")
                    )
                    // Nepřecházíme do GAME_MULLIGAN hned – čekáme na GAME_MULLIGAN ze serveru
                    statusMsg.value = "Soupeř nalezen! Připravuji hru…"
                }

                "GAME_MULLIGAN" -> {
                    val handJson = json.optJSONArray("hand")
                    mulliganHand.value      = parseCardArray(handJson)
                    mulliganSelected.value  = emptySet()
                    mulliganSubmitted.value = false
                    opponentMulliganDone.value = false
                    lastPlayedCard.value    = null   // čistý stav pro novou hru
                    lastPlayedByMe.value    = false
                    phase.value = OnlinePhase.GAME_MULLIGAN
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
                            gameLog.value = (gameLog.value +
                                LogEntry.CardEvent(actorName, card, action, isMe, turn)).takeLast(50)
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
                            gameLog.value = (gameLog.value +
                                LogEntry.CardEvent(oppName, template, action, isMe = false, turn)).takeLast(50)
                        }
                    }
                }

                "GAME_OVER" -> {
                    gameResult.value = OnlineGameResult(
                        winner     = json.optString("winner", "DRAW"),
                        winnerName = json.optString("winnerName").takeIf { it.isNotEmpty() },
                        youWin     = json.optBoolean("youWin", false)
                    )
                    phase.value = OnlinePhase.GAME_OVER
                }

                "OPPONENT_LEFT" -> {
                    gameResult.value = OnlineGameResult(
                        winner     = matchInfo.value?.side ?: "A",
                        winnerName = playerName.value,
                        youWin     = true
                    )
                    errorMsg.value = "Soupeř se odpojil – vyhráváš!"
                    phase.value    = OnlinePhase.GAME_OVER
                }

                "GAME_ERROR" -> {
                    // Dočasná chyba hry (špatná akce) – zobraz jako zprávu, nepřeruš hru
                    val msg = json.optString("msg", "Chyba")
                    gameLog.value = (gameLog.value + LogEntry.SystemEvent("⚠️ $msg")).takeLast(50)
                }

                "ERROR" -> {
                    errorMsg.value = json.optString("msg", "Chyba serveru")
                    if (phase.value == OnlinePhase.CONNECTING ||
                        phase.value == OnlinePhase.LOBBY) {
                        phase.value = OnlinePhase.ERROR
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
            val obj    = arr.getJSONObject(i)
            val instanceId = obj.optString("id", "")
            val baseId     = obj.optString("baseId", instanceId.substringBefore('_'))
            // Najdi plný Card objekt v allCards podle baseId
            val template = allCards.find { it.id == baseId }
            if (template != null) {
                // Přetypuj s instančním ID (zachovej originál + přepiš id)
                result.add(template.copy(id = instanceId))
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
        } else emptyList()

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
            log             = logList,
            turnRemainingMs = json.optLong("turnRemainingMs", 15_000L),
            timebankMeMs    = json.optLong("timebankMeMs",    120_000L),
            timebankOppMs   = json.optLong("timebankOppMs",   120_000L),
            receivedAt      = System.currentTimeMillis()
        )
    }

    // ── Pomocné ───────────────────────────────────────────────────────────────

    private fun resetGameState() {
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
        matchInfo.value            = null
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

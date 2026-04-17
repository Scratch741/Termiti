package com.example.termiti

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Adresa lobby serveru ─────────────────────────────────────────────────────
// TODO: Změň na skutečnou IP/doménu Ubuntu serveru
private const val LOBBY_WS_URL = "ws://192.168.1.100:8765/lobby"

// ─── Fáze lobby ───────────────────────────────────────────────────────────────
enum class OnlinePhase {
    NAME_INPUT,   // zadání přezdívky
    CONNECTING,   // připojování k serveru
    LOBBY,        // připojeno – čekám na akci
    QUEUING,      // v matchmakingové frontě
    MATCH_FOUND,  // nalezen soupeř (přechod do hry)
    ERROR         // chyba připojení
}

// ─── Info o nalezeném zápase ──────────────────────────────────────────────────
data class OnlineMatchInfo(
    val gameId       : String,
    val opponentName : String,
    val side         : String   // "A" nebo "B" – kdo je first player
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class OnlineLobbyViewModel : ViewModel() {

    // Stav
    var phase        = mutableStateOf(OnlinePhase.NAME_INPUT); private set
    var playerName   = mutableStateOf(""); private set
    var onlineCount  = mutableStateOf(0);  private set
    var queueSize    = mutableStateOf(0);  private set  // kolik lidí hledá zápas
    var statusMsg    = mutableStateOf(""); private set
    var errorMsg     = mutableStateOf(""); private set
    var matchInfo    = mutableStateOf<OnlineMatchInfo?>(null); private set

    // WebSocket
    private var ws: WebSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Veřejné akce ─────────────────────────────────────────────────────────

    fun setName(name: String) { playerName.value = name.take(20) }

    fun connect() {
        val name = playerName.value.trim()
        if (name.isBlank()) { errorMsg.value = "Zadej přezdívku"; return }

        phase.value    = OnlinePhase.CONNECTING
        statusMsg.value = "Připojuji k serveru…"
        errorMsg.value  = ""

        val request = Request.Builder().url(LOBBY_WS_URL).build()
        ws = httpClient.newWebSocket(request, LobbyListener())
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

    // ── WebSocket listener ────────────────────────────────────────────────────

    private inner class LobbyListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Ihned po připojení se představíme serveru
            webSocket.send(JSONObject().apply {
                put("type", "JOIN")
                put("name", playerName.value.trim())
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

    // ── Zpracování zpráv ze serveru ───────────────────────────────────────────

    private fun handleMessage(raw: String) {
        val json = try { JSONObject(raw) } catch (e: Exception) { return }
        viewModelScope.launch {
            when (json.optString("type")) {

                // Server potvrdil přihlášení
                "WELCOME" -> {
                    onlineCount.value  = json.optInt("online", 0)
                    queueSize.value    = json.optInt("queue",  0)
                    phase.value        = OnlinePhase.LOBBY
                    statusMsg.value    = "Připojeno ✓"
                }

                // Aktualizace počtu hráčů
                "COUNT" -> {
                    onlineCount.value = json.optInt("online", 0)
                    queueSize.value   = json.optInt("queue",  0)
                }

                // Server potvrdil zařazení do fronty
                "QUEUE_OK" -> {
                    phase.value     = OnlinePhase.QUEUING
                    statusMsg.value = "Ve frontě…"
                }

                // Nalezen soupeř → přechod do hry
                "MATCH_FOUND" -> {
                    matchInfo.value = OnlineMatchInfo(
                        gameId       = json.optString("gameId", ""),
                        opponentName = json.optString("opponentName", "Soupeř"),
                        side         = json.optString("side", "A")
                    )
                    phase.value     = OnlinePhase.MATCH_FOUND
                    statusMsg.value = "Soupeř nalezen!"
                }

                // Chyba serveru (duplicitní jméno, atd.)
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

    // ── Helper ────────────────────────────────────────────────────────────────

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
}

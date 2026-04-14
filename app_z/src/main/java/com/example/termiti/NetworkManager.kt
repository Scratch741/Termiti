package com.example.termiti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class NetworkManager {

    companion object {
        const val PORT           = 8765
        const val DISCOVERY_PORT = 8766

        fun getLocalIp(): String {
            // Primární metoda: UDP routing trick (nejrychlejší)
            val udp = try {
                DatagramSocket().use { s ->
                    s.connect(InetAddress.getByName("8.8.8.8"), 10002)
                    s.localAddress.hostAddress ?: ""
                }
            } catch (_: Exception) { "" }
            if (udp.isNotEmpty() && udp != "0.0.0.0") return udp

            // Fallback: prohledej síťová rozhraní a najdi první IPv4 (ne loopback)
            return try {
                NetworkInterface.getNetworkInterfaces()
                    ?.asSequence()
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress ?: "?"
            } catch (_: Exception) { "?" }
        }
    }

    // ── Discovery (TCP subnet scan) ───────────────────────────────────────────

    /**
     * Paralelně zkouší TCP připojení na všechny adresy /24 podsítě.
     * Vrátí seznam IP adres, kde naslouchá server na [PORT].
     * Typická doba: ~[timeoutMs] ms bez ohledu na velikost podsítě.
     */
    suspend fun scanSubnet(localIp: String, timeoutMs: Int = 500): List<String> {
        if (localIp.length < 7 || localIp == "?") return emptyList()
        val prefix = localIp.substringBeforeLast(".")
        return withContext(Dispatchers.IO) {
            coroutineScope {
                (1..254)
                    .filter { i -> "$prefix.$i" != localIp }
                    .map { i ->
                        val ip = "$prefix.$i"
                        async {
                            try {
                                // Skenujeme DISCOVERY_PORT – game port zůstane nedotčen
                                Socket().use { s ->
                                    s.connect(InetSocketAddress(ip, DISCOVERY_PORT), timeoutMs)
                                    ip
                                }
                            } catch (_: Exception) { null }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }
        }
    }

    private var serverSocket: ServerSocket? = null
    private var discoverySocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    suspend fun startServer() = withContext(Dispatchers.IO) {
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        serverSocket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(PORT))
        }
        // Discovery socket – skenery se připojují sem, game port zůstane nedotčen
        runCatching {
            discoverySocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
        }
    }

    /**
     * Přijímá discovery probe spojení a okamžitě je zavírá.
     * Volej v samostatné coroutině po [startServer]; zruš po [awaitClient].
     */
    suspend fun handleDiscovery() = withContext(Dispatchers.IO) {
        val ds = discoverySocket ?: return@withContext
        while (!ds.isClosed) {
            runCatching { ds.accept().close() }
        }
    }

    suspend fun awaitClient() = withContext(Dispatchers.IO) {
        socket = serverSocket!!.accept()
        writer = PrintWriter(socket!!.getOutputStream(), true)
    }

    suspend fun connectToHost(ip: String) = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(ip.trim(), PORT), 8000)
        socket = s
        writer = PrintWriter(s.getOutputStream(), true)
    }

    /** Flow of newline-delimited JSON strings arriving from the peer. */
    fun incoming(): Flow<String> = flow {
        val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            emit(line!!)
        }
    }.flowOn(Dispatchers.IO)

    fun send(message: String) {
        try { writer?.println(message) } catch (_: Exception) {}
    }

    fun close() {
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        socket = null; serverSocket = null; discoverySocket = null; writer = null
    }
}

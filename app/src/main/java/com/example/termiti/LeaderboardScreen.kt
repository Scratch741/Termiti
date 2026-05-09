package com.example.termiti

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Palette ──────────────────────────────────────────────────────────────────
private val LbDeep   = Color(0xFF0D0A0E)
private val LbPanel  = Color(0xFF13101A)
private val LbCard   = Color(0xFF1A1320)
private val LbGold   = Color(0xFFD4A843)
private val LbTeal   = Color(0xFF3DBFAD)
private val LbRed    = Color(0xFFCF4A4A)
private val LbGreen  = Color(0xFF4CAF50)
private val LbMuted  = Color(0xFF7A6E5F)
private val LbText   = Color(0xFFEDE0C4)

private const val SERVER_BASE = "http://138.2.136.49:8765"

// ─── Data ─────────────────────────────────────────────────────────────────────
data class LeaderboardPlayer(
    val rank   : Int,
    val name   : String,
    val rating : Int,
    val wins   : Int,
    val losses : Int,
    val draws  : Int,
    val games  : Int
) {
    val winRate: Int get() = if (games > 0) (wins * 100 / games) else 0
}

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    var selectedMode  by remember { mutableStateOf("normal") }
    var refreshTick   by remember { mutableIntStateOf(0) }
    var isLoading     by remember { mutableStateOf(true) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    var players       by remember { mutableStateOf<List<LeaderboardPlayer>>(emptyList()) }
    var totalPlayers  by remember { mutableStateOf(0) }

    LaunchedEffect(selectedMode, refreshTick) {
        isLoading = true
        errorMsg  = null
        try {
            val result = withContext(Dispatchers.IO) { fetchLeaderboard(selectedMode) }
            players      = result.first
            totalPlayers = result.second
        } catch (e: Exception) {
            errorMsg = "Nepodařilo se načíst žebříček\n${e.message}"
        } finally {
            isLoading = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LbDeep, Color(0xFF0A081A))))
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Hlavička ──────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(LbPanel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("← Zpět", color = LbMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "🏆 ŽEBŘÍČEK",
                    color         = LbGold,
                    fontSize      = 16.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.weight(1f))
                if (totalPlayers > 0) {
                    Text(
                        "$totalPlayers hráčů celkem",
                        color    = LbMuted,
                        fontSize = 9.sp
                    )
                }
                // Refresh
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LbTeal.copy(alpha = 0.1f))
                        .border(1.dp, LbTeal.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable { refreshTick++ }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("🔄", fontSize = 12.sp)
                }
            }

            // ── Přepínač módů ─────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(LbPanel.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip("⚔️ Constructed",    "normal",       selectedMode) { selectedMode = it }
                ModeChip("🌪️ Super Náhodný", "super_random", selectedMode) { selectedMode = it }
            }

            HorizontalDivider(color = LbGold.copy(alpha = 0.1f))

            // ── Obsah ─────────────────────────────────────────────────────────
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = LbTeal, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Načítám žebříček…", color = LbMuted, fontSize = 11.sp)
                        }
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚡", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                errorMsg!!,
                                color     = LbRed,
                                fontSize  = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LbTeal.copy(alpha = 0.12f))
                                    .border(1.dp, LbTeal.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { refreshTick++ }
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text("🔄 Zkusit znovu", color = LbTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                players.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Žebříček je prázdný", color = LbMuted, fontSize = 13.sp)
                    }
                }
                else -> {
                    // ── Záhlaví tabulky ───────────────────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(LbCard)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#",       color = LbMuted, fontSize = 9.sp, modifier = Modifier.width(28.dp))
                        Text("Hráč",    color = LbMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                        Text("Rating",  color = LbMuted, fontSize = 9.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                        Text("W",       color = LbMuted, fontSize = 9.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                        Text("L",       color = LbMuted, fontSize = 9.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                        Text("W%",      color = LbMuted, fontSize = 9.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = LbGold.copy(alpha = 0.08f))

                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(players) { idx, player ->
                            PlayerRow(player = player)
                            if (idx < players.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.04f))
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

// ─── Řádek hráče ──────────────────────────────────────────────────────────────
@Composable
private fun PlayerRow(player: LeaderboardPlayer) {
    val rankColor = when (player.rank) {
        1 -> Color(0xFFFFD700)   // zlato
        2 -> Color(0xFFC0C0C0)   // stříbro
        3 -> Color(0xFFCD7F32)   // bronz
        else -> LbMuted
    }
    val rankEmoji = when (player.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
    val bg = if (player.rank <= 3) rankColor.copy(alpha = 0.05f) else Color.Transparent

    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pořadí
        if (rankEmoji != null) {
            Text(rankEmoji, fontSize = 14.sp, modifier = Modifier.width(28.dp))
        } else {
            Text(
                "${player.rank}",
                color      = rankColor,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.width(28.dp)
            )
        }

        // Jméno
        Text(
            player.name,
            color      = if (player.rank <= 3) LbText else LbText.copy(alpha = 0.85f),
            fontSize   = 12.sp,
            fontWeight = if (player.rank <= 3) FontWeight.Bold else FontWeight.Normal,
            modifier   = Modifier.weight(1f)
        )

        // Rating
        Text(
            "⭐ ${player.rating}",
            color      = LbGold,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(60.dp),
            textAlign  = TextAlign.End
        )

        // Výhry
        Text(
            "${player.wins}",
            color    = LbGreen,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )

        // Prohry
        Text(
            "${player.losses}",
            color    = LbRed,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )

        // Win rate
        val wrColor = when {
            player.winRate >= 60 -> LbGreen
            player.winRate >= 45 -> LbGold
            else                 -> LbRed
        }
        Text(
            "${player.winRate}%",
            color    = wrColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

// ─── Mode chip ────────────────────────────────────────────────────────────────
@Composable
private fun ModeChip(label: String, mode: String, selected: String, onClick: (String) -> Unit) {
    val active = mode == selected
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) LbTeal.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (active) LbTeal.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .clickable { onClick(mode) }
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color      = if (active) LbTeal else LbMuted,
            fontSize   = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── HTTP fetch ───────────────────────────────────────────────────────────────
private val _httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

private fun fetchLeaderboard(mode: String): Pair<List<LeaderboardPlayer>, Int> {
    val url     = "$SERVER_BASE/leaderboard?mode=$mode&limit=50"
    val request = Request.Builder().url(url).build()
    val body    = _httpClient.newCall(request).execute().use { it.body?.string() ?: "{}" }
    val json    = JSONObject(body)
    val arr     = json.optJSONArray("players") ?: return Pair(emptyList(), 0)
    val total   = json.optInt("total", 0)
    val list    = (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        LeaderboardPlayer(
            rank   = p.optInt("rank", i + 1),
            name   = p.optString("name", "?"),
            rating = p.optInt("rating", 1000),
            wins   = p.optInt("wins",   0),
            losses = p.optInt("losses", 0),
            draws  = p.optInt("draws",  0),
            games  = p.optInt("games",  0)
        )
    }
    return Pair(list, total)
}

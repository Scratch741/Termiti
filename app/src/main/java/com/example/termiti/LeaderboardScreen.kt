package com.example.termiti

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─── Palette ──────────────────────────────────────────────────────────────────
private val LbRed    = Color(0xFFCF4A4A)
private val LbGreen  = Color(0xFF4CAF50)
private val LbBronze = Color(0xFF8A6A3A)
private val LbSilver = Color(0xFFC9CED6)
private val LbCopper = Color(0xFFCD7F32)

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
/**
 * Žebříček ve stejném rámu jako hlavní menu a lobby ([MenuFrame]): vlevo hráč a jeho umístění,
 * uprostřed tabulka přes celé tmavé pole, vpravo tlačítko zpět na obvyklém místě.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    var selectedMode  by remember { mutableStateOf("normal") }
    var refreshTick   by remember { mutableIntStateOf(0) }
    var isLoading     by remember { mutableStateOf(true) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }
    var data          by remember { mutableStateOf(LeaderboardCache.data) }
    var fetchedAt     by remember { mutableLongStateOf(LeaderboardCache.fetchedAt) }
    var now           by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Data OBOU módů se stáhnou jednou při otevření (nebo se vezmou z paměti, když jsou čerstvá)
    // a přepínání módů už server neptá. Znovu jen tlačítkem Obnovit, nejdřív po odpočtu.
    LaunchedEffect(refreshTick) {
        val fresh = System.currentTimeMillis() - LeaderboardCache.fetchedAt < LB_CACHE_MS
        if (refreshTick == 0 && fresh && LeaderboardCache.data.isNotEmpty()) {
            data = LeaderboardCache.data; fetchedAt = LeaderboardCache.fetchedAt
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMsg  = null
        try {
            val result = withContext(Dispatchers.IO) {
                LB_MODES.associateWith { fetchLeaderboard(it) }
            }
            LeaderboardCache.data      = result
            LeaderboardCache.fetchedAt = System.currentTimeMillis()
            data = result; fetchedAt = LeaderboardCache.fetchedAt
        } catch (e: Exception) {
            errorMsg = LanguageManager.currentStrings.lbLoadFailed.format(e.message ?: "")
        } finally {
            isLoading = false
        }
    }
    // Odpočet do dalšího povoleného obnovení
    LaunchedEffect(fetchedAt) {
        while (System.currentTimeMillis() - fetchedAt < LB_REFRESH_COOLDOWN_MS) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val cooldownLeft = ((LB_REFRESH_COOLDOWN_MS - (now - fetchedAt)) / 1000).toInt().coerceAtLeast(0)
    val canRefresh   = !isLoading && (cooldownLeft == 0 || errorMsg != null)

    val players      = data[selectedMode]?.first.orEmpty()
    val totalPlayers = data[selectedMode]?.second ?: 0

    val profile   = PlayerProfileManager.profile
    val myName    = profile?.name.orEmpty()
    val me        = players.firstOrNull { it.name == myName }
    val modeLabel = if (selectedMode == "normal") "Constructed" else s.lbModeSuperRandom

    MenuFrame(
        centerFill = true,
        left = { H ->
            PlateColumn(H) {
                PlateIdentity(profile?.avatar ?: "player_icon_1", myName, H)
                PlateSeparator(H)
                // Umístění hráče ve zvoleném módu (jen pokud je mezi načtenými)
                if (me != null && !isLoading && errorMsg == null) {
                    CampaignTitle("#${me.rank}", fontSize = (H.value * 0.075f).sp)
                    RatingBlock(modeLabel, OnlineModeStats(me.rating, me.wins, me.losses, me.draws, me.games), H, showWinRate = true)
                }
            }
        },
        right = { _, iconSize -> BackOnly(iconSize, s.mpBackPlain, onBack) }
    ) { centerW, H ->
        CampaignTitle(s.lbTitle, fontSize = (centerW.value * 0.085f).sp)
        Spacer(Modifier.height(H * 0.008f))
        Image(
            painter            = painterResource(R.drawable.bg_separator),
            contentDescription = null,
            modifier           = Modifier.width(centerW * 0.82f),
            contentScale       = ContentScale.FillWidth
        )
        Spacer(Modifier.height(H * 0.022f))

        // ── Přepínač módů + obnovení ─────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeChip("Constructed",       "normal",       selectedMode) { selectedMode = it }
            ModeChip(s.lbModeSuperRandom, "super_random", selectedMode) { selectedMode = it }
            Spacer(Modifier.weight(1f))
            if (totalPlayers > 0) {
                Text(s.lbTotalPlayers.format(totalPlayers), color = TextMuted, fontSize = 9.sp)
            }
            PlainButton(
                text      = if (canRefresh || isLoading) s.lbRefresh else "${s.lbRefresh} ($cooldownLeft)",
                textColor = TextMuted,
                fontSize  = 9.sp,
                modifier  = Modifier.height(27.dp).widthIn(min = 76.dp),
                paddingH  = 10.dp,
                paddingV  = 0.dp,
                enabled   = canRefresh,
                onClick   = { refreshTick++ }
            )
        }
        Spacer(Modifier.height(H * 0.018f))

        // ── Obsah ────────────────────────────────────────────────────────────
        when {
            isLoading -> CenterNote(s.lbLoading, TextMuted)
            errorMsg != null -> {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                ) {
                    Text(errorMsg!!, color = LbRed, fontSize = 11.sp, textAlign = TextAlign.Center)
                    PlainButton(
                        text      = s.lbRetry,
                        textColor = TextPrimary,
                        fontSize  = 11.sp,
                        paddingH  = 20.dp,
                        paddingV  = 8.dp,
                        onClick   = { refreshTick++ }
                    )
                }
            }
            players.isEmpty() -> CenterNote(s.lbEmpty, TextMuted)
            else -> {
                TableRow(underline = LbBronze.copy(alpha = 0.55f)) {
                    HeadCell("#",        Modifier.width(COL_RANK))
                    HeadCell(s.lbPlayer, Modifier.weight(1f))
                    HeadCell("Rating",   Modifier.width(COL_RATING), TextAlign.End)
                    HeadCell("W",        Modifier.width(COL_NUM),    TextAlign.End)
                    HeadCell("L",        Modifier.width(COL_NUM),    TextAlign.End)
                    HeadCell("W%",       Modifier.width(COL_PCT),    TextAlign.End)
                }
                val listState = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxWidth().scrollRail(listState), state = listState) {
                    itemsIndexed(players) { _, player ->
                        PlayerRow(player, isMe = player.name == myName)
                    }
                }
            }
        }
    }
}

private val COL_RANK   = 34.dp
private val COL_RATING = 66.dp
private val COL_NUM    = 38.dp
private val COL_PCT    = 46.dp

@Composable
private fun ColumnScope.CenterNote(text: String, color: Color) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Řádek tabulky s tenkou bronzovou linkou pod sebou. */
@Composable
private fun TableRow(
    underline : Color,
    background: Color = Color.Transparent,
    padV      : Dp = 5.dp,
    content   : @Composable RowScope.() -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .drawBehind {
                drawLine(underline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 10.dp, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        content           = content
    )
}

@Composable
private fun HeadCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(),
        color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        textAlign = align, modifier = modifier
    )
}

// ─── Řádek hráče ──────────────────────────────────────────────────────────────
@Composable
private fun PlayerRow(player: LeaderboardPlayer, isMe: Boolean) {
    val rankColor = when (player.rank) {
        1 -> Color(0xFFFFD700)
        2 -> LbSilver
        3 -> LbCopper
        else -> TextMuted
    }
    val top = player.rank <= 3
    TableRow(
        underline  = LbBronze.copy(alpha = 0.22f),
        background = if (isMe) Gold.copy(alpha = 0.12f) else Color.Transparent,
        padV       = 4.dp
    ) {
        Text(
            "${player.rank}",
            color = rankColor, fontSize = if (top) 13.sp else 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(COL_RANK)
        )
        Text(
            player.name,
            color      = if (isMe) Gold else TextPrimary.copy(alpha = if (top) 1f else 0.85f),
            fontSize   = 12.sp,
            fontWeight = if (top || isMe) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1, overflow = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f)
        )
        Row(
            Modifier.width(COL_RATING),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.star_icon), contentDescription = null, modifier = Modifier.size(10.dp))
            Text("${player.rating}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("${player.wins}",   color = LbGreen, fontSize = 11.sp, modifier = Modifier.width(COL_NUM), textAlign = TextAlign.End)
        Text("${player.losses}", color = LbRed,   fontSize = 11.sp, modifier = Modifier.width(COL_NUM), textAlign = TextAlign.End)
        val wrColor = when {
            player.winRate >= 60 -> LbGreen
            player.winRate >= 45 -> Gold
            else                 -> LbRed
        }
        Text(
            "${player.winRate} %",
            color = wrColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(COL_PCT), textAlign = TextAlign.End
        )
    }
}

// ─── Mode chip ────────────────────────────────────────────────────────────────
@Composable
private fun ModeChip(label: String, mode: String, selected: String, onClick: (String) -> Unit) {
    val active = mode == selected
    PlainButton(
        text       = label,
        textColor  = if (active) Gold else TextMuted,
        fontSize   = 10.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        selected   = active,
        modifier   = Modifier.height(27.dp).widthIn(min = 92.dp),
        paddingH   = 12.dp,
        paddingV   = 0.dp,
        onClick    = { onClick(mode) }
    )
}

// ─── HTTP fetch ───────────────────────────────────────────────────────────────
/** Módy žebříčku – stahují se vždy oba naráz, přepínání pak nic neposílá. */
private val LB_MODES = listOf("normal", "super_random")
/** Jak dlouho platí stažená data při opětovném otevření obrazovky. */
private const val LB_CACHE_MS = 60_000L
/** Nejkratší rozestup mezi ručními obnoveními (tlačítko ukazuje odpočet). */
private const val LB_REFRESH_COOLDOWN_MS = 15_000L

/** Poslední stažený žebříček – přežije zavření obrazovky, ať návrat nevyvolá nový dotaz. */
private object LeaderboardCache {
    var data: Map<String, Pair<List<LeaderboardPlayer>, Int>> = emptyMap()
    var fetchedAt: Long = 0L
}

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

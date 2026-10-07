package com.example.termiti

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Šířka rolovací lišty a místo, které si pro ni seznam vpravo nechává. */
private val RAIL_WIDTH = 6.dp
internal val SCROLL_RAIL_RESERVE = 11.dp

private val RailBronze = Color(0xFF8A6A3A)
private val RailGold   = Color(0xFFD4A843)

/**
 * Rolovací lišta u pravého okraje rolovaného seznamu: tmavá drážka s bronzovým okrajem
 * a zlatý jezdec, jehož délka odpovídá viditelné části obsahu. Když se obsah vejde celý,
 * nekreslí se nic. Obsahu vpravo ubere [SCROLL_RAIL_RESERVE], aby ho lišta nepřekrývala.
 *
 * Musí stát PŘED `verticalScroll(state)` – kreslí se do výřezu, ne do rolovaného obsahu.
 */
fun Modifier.scrollRail(state: ScrollState): Modifier = this
    .drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max > 0 && max != Int.MAX_VALUE) {
            drawRail(
                visibleShare = size.height / (size.height + max),
                position     = state.value.toFloat() / max
            )
        }
    }
    .padding(end = SCROLL_RAIL_RESERVE)

/** Totéž pro LazyColumn – poloha a délka jezdce se odhadují z viditelných položek. */
fun Modifier.scrollRail(state: LazyListState): Modifier = this
    .drawWithContent {
        drawContent()
        val info    = state.layoutInfo
        val visible = info.visibleItemsInfo
        val total   = info.totalItemsCount
        if (total > 0 && visible.isNotEmpty() && (state.canScrollForward || state.canScrollBackward)) {
            val first    = visible.first()
            val avgSize  = visible.sumOf { it.size }.toFloat() / visible.size
            val content  = (avgSize * total).coerceAtLeast(1f)
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val scrolled = first.index * avgSize - first.offset
            val maxScroll = (content - viewport).coerceAtLeast(1f)
            drawRail(
                visibleShare = (viewport / content).coerceIn(0f, 1f),
                // Na konci seznamu jezdec vždy dosedne dolů, i když je odhad výšek nepřesný
                position     = if (!state.canScrollForward) 1f else (scrolled / maxScroll).coerceIn(0f, 1f)
            )
        }
    }
    .padding(end = SCROLL_RAIL_RESERVE)

private fun DrawScope.drawRail(visibleShare: Float, position: Float) {
    val w      = RAIL_WIDTH.toPx()
    val x      = size.width - w
    val r      = CornerRadius(w / 2, w / 2)
    drawRoundRect(Color(0xFF14100C), topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
    drawRoundRect(RailBronze.copy(alpha = 0.7f), topLeft = Offset(x, 0f), size = Size(w, size.height),
        cornerRadius = r, style = Stroke(1.dp.toPx()))
    val thumbH = (size.height * visibleShare).coerceIn(minOf(24.dp.toPx(), size.height), size.height)
    val y      = (size.height - thumbH) * position
    val inset  = 1.5.dp.toPx()
    drawRoundRect(
        brush        = Brush.verticalGradient(listOf(RailGold, RailBronze), startY = y, endY = y + thumbH),
        topLeft      = Offset(x + inset, y + inset),
        size         = Size(w - 2 * inset, (thumbH - 2 * inset).coerceAtLeast(0f)),
        cornerRadius = r
    )
}

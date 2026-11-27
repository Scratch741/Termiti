package com.example.termiti

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Styl „Obsidián a bronz" (Tvorba balíčku, návrh design/ui_redesign/deck/obsidian_bronz.png):
 * černé sklo s jemným lesklým přechodem, tenké bronzové hrany (světlé nahoře, tmavé dole)
 * a malé bronzové kosočtverce v rozích panelů. Vše kreslené – žádné textury kromě pozadí.
 */

internal val ObsTop      = Color(0xFF1A161A)
internal val ObsBottom   = Color(0xFF0C0A0D)
internal val BronzeLight = Color(0xFFDEB270)
internal val Bronze      = Color(0xFFA87A40)
internal val BronzeDark  = Color(0xFF604222)
/** Zlatavý text na obsidiánu (nadpisy, popisky tlačítek). */
internal val BronzeText  = Color(0xFFE2BC76)
/** Tmavý text na plném bronzu (hlavní tlačítko). */
internal val BronzeInk   = Color(0xFF1E1206)
internal val ObsText      = Color(0xFFEADEC8)
internal val ObsTextMuted = Color(0xFF968874)

/** Cinzel SemiBold – římské verzálky pro nadpisy a tlačítka v tomto stylu. */
@OptIn(ExperimentalTextApi::class)
internal val ObsCinzel = FontFamily(
    Font(R.font.cinzel, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
)

private fun Color.lit(k: Float) = Color(
    (red * k).coerceIn(0f, 1f), (green * k).coerceIn(0f, 1f), (blue * k).coerceIn(0f, 1f), alpha
)

/** Obsidiánová plocha: svislý přechod a slabý diagonální lesk v levém horním rohu. */
internal fun Modifier.obsidianSurface(
    radius: Dp = 6.dp,
    top: Color = ObsTop,
    bottom: Color = ObsBottom,
    alpha: Float = 0.94f,
    sheen: Boolean = true
): Modifier = drawBehind {
    val r = CornerRadius(radius.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(top.copy(alpha = alpha), bottom.copy(alpha = alpha))), cornerRadius = r)
    if (sheen) drawRoundRect(
        Brush.linearGradient(
            listOf(Color(0x12FFF5E6), Color.Transparent),
            start = Offset.Zero, end = Offset(size.width * 0.45f, size.height * 0.9f)
        ),
        cornerRadius = r
    )
}

/**
 * Bronzová hrana: černá vnější linka, bronz s přechodem shora dolů a vlasový odlesk uvnitř.
 * [level] < 1 hranu ztlumí (neaktivní filtr), > 1 rozjasní (aktivní).
 */
internal fun Modifier.bronzeRim(radius: Dp = 6.dp, width: Dp = 1.dp, level: Float = 1f): Modifier = drawBehind {
    drawBronzeRim(radius.toPx(), width.toPx(), level)
}

private fun DrawScope.drawBronzeRim(r: Float, w: Float, level: Float) {
    fun inset(i: Float) = Pair(Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i))
    val (o0, s0) = inset(0.5f)
    drawRoundRect(Color.Black.copy(alpha = 0.85f), o0, s0, CornerRadius(r), style = Stroke(1f))
    val i1 = 1f + w / 2
    val (o1, s1) = inset(i1)
    drawRoundRect(
        Brush.verticalGradient(listOf(BronzeLight.lit(level), BronzeDark.lit(level))),
        o1, s1, CornerRadius((r - i1).coerceAtLeast(0f)), style = Stroke(w)
    )
    val i2 = 1.5f + w
    val (o2, s2) = inset(i2)
    drawRoundRect(Color(0x1CFFECC8), o2, s2, CornerRadius((r - i2).coerceAtLeast(0f)), style = Stroke(1f))
}

private fun DrawScope.drawDiamond(c: Offset, s: Float) {
    fun poly(k: Float) = Path().apply {
        moveTo(c.x, c.y - s * k); lineTo(c.x + s * k, c.y); lineTo(c.x, c.y + s * k); lineTo(c.x - s * k, c.y); close()
    }
    drawPath(poly(1f), BronzeDark)
    drawPath(poly(0.55f), BronzeLight)
}

/** Malé bronzové kosočtverce v rozích (kreslí se přes obsah – sedí na hraně panelu). */
internal fun Modifier.bronzeCorners(size: Dp = 4.dp): Modifier = drawWithContent {
    drawContent()
    val s = size.toPx()
    listOf(Offset(1f, 1f), Offset(this.size.width - 1f, 1f), Offset(1f, this.size.height - 1f), Offset(this.size.width - 1f, this.size.height - 1f))
        .forEach { drawDiamond(it, s) }
}

/** Panel: obsidián + bronzová hrana + kosočtverce v rozích. */
internal fun Modifier.obsidianPanel(radius: Dp = 8.dp): Modifier =
    this.obsidianSurface(radius).bronzeRim(radius).bronzeCorners()

/** Vodorovný oddělovač: bronzová linka mizející do stran, uprostřed kosočtverec. */
@Composable
internal fun BronzeDivider(modifier: Modifier = Modifier, diamond: Boolean = true, alpha: Float = 0.8f) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val y = size.height / 2
        val fade = Brush.horizontalGradient(listOf(Color.Transparent, Bronze.copy(alpha = alpha), Bronze.copy(alpha = alpha), Color.Transparent))
        drawLine(fade, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Transparent)),
            Offset(0f, y + 1.dp.toPx()), Offset(size.width, y + 1.dp.toPx()), 1f)
        if (diamond) drawDiamond(Offset(size.width / 2, y), 3.dp.toPx())
    }
}

/** Svislý oddělovač (mezi katalogem a balíčkem). */
@Composable
internal fun BronzeDividerVertical(modifier: Modifier = Modifier, alpha: Float = 0.8f) {
    Canvas(modifier.fillMaxHeight().width(8.dp)) {
        val x = size.width / 2
        val fade = Brush.verticalGradient(listOf(Color.Transparent, Bronze.copy(alpha = alpha), Bronze.copy(alpha = alpha), Color.Transparent))
        drawLine(fade, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        drawDiamond(Offset(x, size.height / 2), 3.dp.toPx())
    }
}

/**
 * Tlačítko ve stylu obsidián/bronz. [primary] = plný matný bronz s tmavým textem (hlavní akce),
 * jinak obsidián s bronzovou hranou a zlatavým textem.
 */
@Composable
internal fun ObsButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
    fontSize: TextUnit = 10.sp,
    textColor: Color? = null,
    paddingH: Dp = 8.dp,
    /** Zaoblení rohů; polovina výšky = oblé konce (pilulka, kulaté tlačítko). */
    radius: Dp = 5.dp,
    /** Volitelný drobný druhý řádek pod textem (např. číslo kola na tlačítku Konec tahu). */
    subText: String? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && enabled) 0.95f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 900f), label = "obsPress"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            // vypnuté tlačítko musí být na první pohled poznat
            .alpha(if (enabled) 1f else 0.32f)
            .then(
                if (primary) Modifier.drawBehind {
                    // Kovaný bronz: tři tóny (světlá hrana, sytý střed, tmavý spodek), lesk
                    // v horní polovině, světlá linka pod horní hranou a tmavá nad spodní – plastický reliéf.
                    val r = CornerRadius(radius.toPx())
                    drawRoundRect(
                        Brush.verticalGradient(0f to Color(0xFFC89A5C), 0.45f to Color(0xFF8E6434), 1f to Color(0xFF4A3014)),
                        cornerRadius = r
                    )
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0x40FFEAC0), Color.Transparent), endY = size.height * 0.5f), cornerRadius = r)
                    val i = 3.dp.toPx()
                    // vnitřní linka reliéfu kopíruje tvar tlačítka (i u oblých konců)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0x66FFF0D0), Color(0x80000000))),
                        topLeft = Offset(i, i), size = Size(size.width - 2 * i, size.height - 2 * i),
                        cornerRadius = CornerRadius((radius.toPx() - i).coerceAtLeast(0f)), style = Stroke(1f)
                    )
                }.bronzeRim(radius, width = 1.5.dp, level = 1.15f)
                else Modifier.obsidianSurface(radius, Color(0xFF1E1A1E), Color(0xFF0E0C0F), 0.97f).bronzeRim(radius, level = 0.95f)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { SoundManager.playMenuTap(); onClick() }
            .padding(horizontal = paddingH),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (iconRes != null) Image(painterResource(iconRes), null, Modifier.size(fontSize.value.dp * 1.4f), contentScale = ContentScale.Fit)
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Na bronzu ražené písmo (světlý odlesk pod spodní hranou), na obsidiánu stín
                style = obsLabelStyle(fontSize, textColor ?: if (primary) BronzeInk else BronzeText).copy(
                    shadow = if (primary) Shadow(Color(0x73FFE7B0), Offset(0f, 1.5f), 0.5f)
                             else Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 3f)
                )
            )
        }
        if (subText != null) Text(
            subText, maxLines = 1,
            style = obsLabelStyle(fontSize * 0.68f, (textColor ?: if (primary) BronzeInk else BronzeText).copy(alpha = 0.75f), 1.sp)
                .copy(fontFamily = null, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 2.dp)
        )
        }
    }
}

/** Text Cinzelem bez horního/dolního odsazení písma (aby seděl přesně na střed). */
internal fun obsLabelStyle(fontSize: TextUnit, color: Color, letterSpacing: TextUnit = 0.5.sp) = TextStyle(
    color           = color,
    fontFamily      = ObsCinzel,
    fontWeight      = FontWeight.SemiBold,
    fontSize        = fontSize,
    letterSpacing   = letterSpacing,
    textAlign       = TextAlign.Center,
    platformStyle   = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

/**
 * Čtvercová dlaždice filtru: obsidián s bronzovou hranou. Aktivní má teplejší sklo,
 * jasnější hranu a jemnou zlatavou záři okolo.
 */
internal fun Modifier.obsidianTile(active: Boolean): Modifier = this
    .drawBehind {
        if (active) {
            val g = 3.dp.toPx()
            drawRoundRect(
                Brush.radialGradient(listOf(Color(0x55E2AA5A), Color.Transparent), radius = size.maxDimension * 0.8f),
                topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g),
                cornerRadius = CornerRadius(7.dp.toPx())
            )
        }
    }
    .obsidianSurface(4.dp, if (active) Color(0xFF2C221A) else Color(0xFF1C181C), ObsBottom, 0.97f)
    .bronzeRim(4.dp, level = if (active) 1.3f else 0.62f)

/** Cena karty v bronzovém medailonu (seznam balíčku). */
@Composable
internal fun BronzeMedallion(label: String, size: Dp = 20.dp) {
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.8f), r)
            drawCircle(Brush.verticalGradient(listOf(BronzeLight, BronzeDark)), r - 0.5f)
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF221C1E), Color(0xFF0C0A0C))), r - 1.5.dp.toPx())
        },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(
                color = ObsText, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
            )
        )
    }
}

/**
 * Medailon výměny karty (mulligan): kovaný bronzový prstenec, obsidiánový střed a dvě
 * zahnuté šipky do kruhu – kreslené, takže jsou přesně na středu (znak „⇄“ z písma nebyl).
 */
@Composable
internal fun SwapMedallion(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2 - 1.dp.toPx()
        // stín pod medailonem
        drawCircle(Color.Black.copy(alpha = 0.55f), r + 1.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
        // bronzový prstenec: černá linka, kov světlý nahoře / tmavý dole, jemný odlesk
        drawCircle(Color.Black.copy(alpha = 0.9f), r)
        drawCircle(Brush.verticalGradient(listOf(BronzeLight, Bronze, BronzeDark), startY = c.y - r, endY = c.y + r), r - 1f)
        drawCircle(Color(0x33FFF0D0), r - 2.dp.toPx(), style = Stroke(1f))
        // obsidiánový střed s vlasovou bronzovou linkou
        val ri = r * 0.76f
        drawCircle(Color.Black.copy(alpha = 0.8f), ri + 1f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2E2529), Color(0xFF0B090B)), center = c - Offset(0f, ri * 0.3f), radius = ri * 1.3f), ri)
        drawCircle(Bronze.copy(alpha = 0.45f), ri - 1.5.dp.toPx(), style = Stroke(1f))

        // dvě šipky po kružnici, otočené o 180° – symbol výměny
        val ra  = ri * 0.52f
        val sw  = ri * 0.15f
        val head = ri * 0.30f
        fun arrow(start: Float, sweep: Float, color: Brush, shift: Offset) {
            val end = Math.toRadians((start + sweep).toDouble())
            drawArc(
                color, start, sweep - 14f, useCenter = false,
                topLeft = c - Offset(ra, ra) + shift, size = Size(ra * 2, ra * 2),
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            val p   = c + shift + Offset((ra * kotlin.math.cos(end)).toFloat(), (ra * kotlin.math.sin(end)).toFloat())
            val t   = Offset((-kotlin.math.sin(end)).toFloat(), kotlin.math.cos(end).toFloat())   // tečna po směru
            val n   = Offset(kotlin.math.cos(end).toFloat(), kotlin.math.sin(end).toFloat())      // radiála
            val tip = p + t * (head * 0.55f)
            val b1  = p - t * (head * 0.45f) + n * (head * 0.55f)
            val b2  = p - t * (head * 0.45f) - n * (head * 0.55f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, color)
        }
        val shadow = Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.7f)))
        val metal  = Brush.verticalGradient(listOf(Color(0xFFF2D49A), BronzeLight, Bronze), startY = c.y - ra, endY = c.y + ra)
        val drop = Offset(0f, 1.2.dp.toPx())
        for ((brush, shift) in listOf(shadow to drop, metal to Offset.Zero)) {
            arrow(200f, 125f, brush, shift)
            arrow(20f, 125f, brush, shift)
        }
    }
}

/**
 * Křížek přes kartu vybranou k výměně (mulligan): malovaný ohnivý řez rudé magie
 * (mulligan_x.png, ComfyUI + darkmage) s měkkou rudou září pod ním. Při objevení se
 * „vrazí“ na kartu (zvětšený → skutečná velikost s lehkým zákmitem).
 * [centerY] = střed křížku jako podíl výšky karty (sedí v obrázku nad názvem).
 */
@Composable
internal fun MulliganCross(modifier: Modifier = Modifier, centerY: Float = 0.40f) {
    val scale = remember { androidx.compose.animation.core.Animatable(1.7f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, androidx.compose.animation.core.tween(110)) }
        scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 800f))
    }
    BoxWithConstraints(modifier) {
        val side = maxWidth * 1.02f
        val cy   = maxHeight * centerY
        // záře
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2, size.height * centerY)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xCCD2321E).copy(alpha = 0.55f * alpha.value), Color.Transparent), center = c, radius = size.width * 0.55f),
                radius = size.width * 0.55f, center = c
            )
        }
        Image(
            painterResource(R.drawable.mulligan_x),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(side)
                .offset(x = (maxWidth - side) / 2, y = cy - side / 2)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
        )
    }
}

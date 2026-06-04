package com.example.termiti

import androidx.compose.ui.graphics.Color

// ── Pomocné funkce závislé na herních datových typech ────────────────────────
// Definovány zde, aby byly dostupné z GameCardView, GameLog i GameHUD

internal fun rarityColor(rarity: Rarity) = when (rarity) {
    Rarity.COMMON    -> Color(0xFF9E9E9E)
    Rarity.RARE      -> Color(0xFF4A90D9)
    Rarity.EPIC      -> Color(0xFF9B59B6)
    Rarity.LEGENDARY -> Color(0xFFD4A843)
}

internal fun resourceColor(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> MagicBlue
    ResourceType.ATTACK -> AttackRed
    ResourceType.STONES -> StoneColor
    ResourceType.CHAOS  -> ChaosOrange
}

internal fun resourceIcon(type: ResourceType) = when (type) {
    ResourceType.MAGIC  -> "✨"
    ResourceType.ATTACK -> "⚔️"
    ResourceType.STONES -> "🪨"
    ResourceType.CHAOS  -> "🌀"
}

/**
 * Sdílená paleta barev pro celou aplikaci.
 * Používá se v GameScreen, DeckBuilderScreen, ArenaDraftScreen, MenuScreen a dalších.
 * Přidání nové barvy: stačí ji definovat zde — dostupná ve všech souborech stejného balíčku.
 */

// ── Pozadí ───────────────────────────────────────────────────────────────────
internal val BgDeep      = Color(0xFF0D0A0E)   // nejhlubší pozadí
internal val BgCard      = Color(0xFF1A1320)   // pozadí karet / panelů
internal val BgPanel     = Color(0xFF13101A)   // tmavší panel

// ── Zlatá / akcentová ─────────────────────────────────────────────────────────
internal val Gold        = Color(0xFFD4A843)   // zlatý akcent (tlačítka, bordery)

// ── Červené / útočné ──────────────────────────────────────────────────────────
internal val Crimson     = Color(0xFFBF2D2D)   // útok, krev
internal val CrimsonDark = Color(0xFF8B1A1A)   // tmavší červená
internal val HpRed       = Color(0xFFE53935)   // nízké HP, zahazování
internal val AttackRed   = Color(0xFFBF2D2D)   // útočný resource (= Crimson)
internal val DiscardRed  = HpRed               // alias – zvýraznění zahazování karty

// ── Zelené / stavební ─────────────────────────────────────────────────────────
internal val HpGreen     = Color(0xFF4CAF50)   // plné HP, pozitivní

// ── Tyrkysové / magie ─────────────────────────────────────────────────────────
internal val Teal        = Color(0xFF2A7A6F)   // stavba, magie (tmavá)
internal val TealLight   = Color(0xFF3DBFAD)   // stavba, magie (světlá)

// ── Text ─────────────────────────────────────────────────────────────────────
internal val TextPrimary = Color(0xFFEDE0C4)   // hlavní text (béžová)
internal val TextMuted   = Color(0xFF7A6E5F)   // sekundární / ztlumený text

// ── Resource barvy ────────────────────────────────────────────────────────────
internal val MagicPurple  = Color(0xFF9B59B6)  // EPIC rarity (ponecháno pro zpětnou kompatibilitu)
internal val MagicBlue    = Color(0xFF0D68BA)  // resource MAGIC – barva rámu karet magie
internal val StoneColor   = Color(0xFFB8A898)  // resource STONES
internal val ChaosOrange  = Color(0xFFE67E22)  // resource CHAOS
internal val WallBlue     = Color(0xFF5C9BD6)  // hradby
internal val ComboYellow  = Color(0xFFFFD600)  // zvýraznění combo karet

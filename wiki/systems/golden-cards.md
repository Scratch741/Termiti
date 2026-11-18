# Golden cards (animated variant)

> How to make a "golden" animated card: code effects (level 1) plus a layered art with motion (level 2). Prototype: test card `"T48"` Upír (zlatý). On hold since 2026-11-18 – the current focus is regenerating card arts.

## Content

### Status
- Only the prototype `"T48"` exists (copy of `"048"` Upír, `isBasic`, `T` prefix = never in random decks). It is visible in the deck builder album (search "zlat").
- Not done yet: how players get golden cards (drop / crafting / reward), storing "golden" per copy in the collection, sending it online (new field, no `PROTOCOL_VERSION` bump), limiting effects on low-end devices (`isLowEndDevice`) and to visible cards only.

### What the user decided (keep these)
- **The frame keeps its resource colour** (red Útok, blue Magie…). A fully gold-tinted frame was rejected: it hides what the card costs.
- The **shine** must travel across the whole card and leave past the bottom-right corner (it used to cut off half way). Keep it subtle (gold alpha 0.32 on the frame only) and keep the outer glow subtle too (0.13).
- The character **hovers gently up and down**, not sideways and without "breathing" (±1.4 % of the height, 3 s period). The background moves only slightly in the opposite direction.

### Level 1 – code effects (any card)
All in `GoldenCard.kt`, enabled by `golden = true` in `CardPres` (copied to `Card.golden` in `CardRepository`):
- `rememberGoldenFx()` – one infinite 6 s timer per card, read only in the draw phase (no recomposition).
- `Modifier.goldenGlow(fx, scale)` – pulsing gold glow outside the card (before `clip`).
- `Modifier.goldenShine(fx)` on the frame image – diagonal band, `CompositingStrategy.Offscreen` + `BlendMode.SrcAtop`, so it lights only the frame pixels; smoothstep easing, starts and ends fully off the card.
- `GoldenSparks(fx, …)` – a few gold sparks rising in the art area (seeded, seamless loop).
- Wired into the three renderers: `CardViewTextured` (game, `GameCardView.kt`), `CardPreview` (album) and `FullCardPreview` (detail, `scale = 2.52`) in `DeckBuilderScreen.kt`.

### Level 2 – layered art (per card, manual)
1. Characters: `comfy_remove_bg` on the finished 512×512 art → `res/drawable/art_<name>_fg.png` (transparent).
2. Background without characters: `comfy_edit` (Qwen), `enhance` false, `output_size` 512x512, `count` 2:
   > Remove the <characters> completely, including <their magic/effects>, and fill the space with what is behind them: <ground/wall they stand on> and <sky>. Keep the <moon, bats, ruins…>, the exact composition, the dark teal and charcoal color palette and the painting style unchanged. No people.
   → `res/drawable/art_<name>_bg.png`. **Do not use `comfy_inpaint`** for this: it repaints the ground (characters then float above a different floor) and leaves a dark halo of the mask.
3. Effects cut away with the background (glowing magic in hands) are drawn in code: `ArtLayers(bgResId, fgResId, glowX, glowY, glowRadius, glowColor)` – glow centre in fractions of the image (Upír: 0.54, 0.41, purple `0xFFB066FF`).
4. `CardPresentation.kt`: `golden = true, artLayers = ArtLayers(R.drawable.art_<name>_bg, R.drawable.art_<name>_fg, …)`; `artResId` stays the normal art (thumbnails, server).
5. `GoldenLayeredArt` draws bg (scale 1.07, slight vertical counter-move) → glow (`BlendMode.Plus`, pulsing) → fg (hover), using the same `artModifier` / `artAlignment` as the normal art.
- All characters in one cut-out move together (the kneeling knight hovers with the vampire). For a single hovering figure, cut the others into a separate static layer.

### New golden card = data
Like any new card (4 files: `cards.js`, `CardPresentation.kt`, `cs.json`, `en.json`, plus `cards.json`; then `node tools/regen_deckbuilder_cards.js` and `node tools/regen_card_data.js`). For a real system the golden flag should live on the owned copy, not on a separate card id.

## Related pages
- [[systems/art-pipeline]] — how the base art is made (style, sizes, ComfyUI tools)
- [[systems/deck-builder]] — album where the prototype is shown
- [[systems/online]] — golden flag would have to be sent to the opponent

## Changelog
- 2026-11-18: Page created – prototype T48 (levels 1 and 2), user decisions, procedure; on hold.

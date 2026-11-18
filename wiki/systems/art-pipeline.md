# Art pipeline (card illustrations)

> How card art is made: local AI in ComfyUI driven through the Claude extension „Ollama + ComfyUI (LAN)", the unified painterly style, readability fix for small display, and where the files go.

## Content

### Tools (use these – no other image tools)
- **ComfyUI** on the LAN machine `http://192.168.125.140:8188` (Linux, RTX 3080 Ti 12 GB). If VRAM is short, free it with `comfy_free` (ComfyUI usually holds it).
- **Claude extension „Ollama + ComfyUI (LAN)" ≥ 2.6.0** (MCP tools `mcp__Ollama___ComfyUI__LAN___*`). Package in the repo: `tools/mcp/ollama-comfy-lan-2.6.0.mcpb` – install by dragging into Claude → Settings → Extensions, then start a new session (the tool list is read at session start).
  - `comfy_variants` (workflow `krea`) – 4 variants to choose from; `comfy_generate` for a single image.
  - `comfy_edit` – Qwen Image Edit 2.1: fix details (bow, weapon, impact point), relight.
  - `comfy_inpaint` – repaint a part of an image.
  - `comfy_art_light` – „reflektor C": makes the characters readable at card size (see below).
  - `comfy_finalize` (exact size, upscale, name), `comfy_remove_bg` (BiRefNet mask / transparent PNG), `comfy_styles` (saved prompt styles).
- ComfyUI workflow **`Reflektor_C`** (saved in ComfyUI's workflow list; API copy in `tools/comfy/art_reflektor_api.json`) – the same reflector for manual tuning in the ComfyUI UI.

### Style
Styles are saved with `comfy_styles`; they live per PC in `Documents/ComfyWorkflows/styles.json` – set them with the same text on another PC (texts as of 2026-11-18):
- `darkmage` – the painting look, for **every** card:
  > dark fantasy oil painting with visible painterly brushwork, muted dark teal and charcoal palette with warm torchlight accents, moody atmosphere
- `card` – composition for cards **with a character**: centered composition, single clear subject, full figure clearly visible, clean readable silhouette, uncluttered background, no text/border/frame (negative: text, letters, watermark, border, frame, cropped).
- `herolight` – light on the character so it reads at card size (cards with a character):
  > warm rim light on the main character, the character clearly brighter than the dark background, readable silhouette
- Cards whose subject is a **building / object / place** (Opevnění, walls, mines…): only `darkmage`; write the composition into the prompt („the wall is the single clear subject, centered composition, no text…", people only tiny in the background) – `card` / `herolight` push a big figure into the middle, which the user does not want there.
- Do **not** use `darkfantasy` for cards (chunky 3D look, the old style being replaced).
- Describe distinct characters concretely (age, hair, scars, clothing) – generic descriptions give clones.

### Steps for a new / regenerated card art
1. `comfy_variants`: workflow `krea`, styles `["darkmage", "card", "herolight"]` (character cards) or `["darkmage"]` (buildings / objects), `output_size` `512x512` (generated 1024×1024 and downscaled), `count` 4, `refine_prompt` false, folder `karty/<name>`. The user picks the variant.
2. Fix defects with `comfy_edit` if needed (deformed weapons etc.).
   - **Effects the generator cannot draw** (fire jets, beams, flowing liquids): generate the scene with the device and its pose first, then add the effect with `comfy_edit` – e.g. Plamenomet: krea gave a correct bronze dragon-head siphon with hose and back barrel, but only smoke instead of fire in all 4 variants; Qwen Edit *"Replace the thin smoke coming out of the … nozzle with a long, powerful jet of bright roaring liquid fire that shoots straight from the dragon's mouth and hits the wooden palisade …; keep the soldier, … the composition, the dark teal sky and the painting style unchanged"* (`count` 2) put the jet exactly there.
   - **Many objects in motion** (volleys, swarms): say the direction explicitly – „swarm … in a high arc" gave a fan of arrows bursting upward; „side view, parallel arrows streak diagonally from the upper left to the lower right, all pointing the same direction" worked (Zápalné šípy). Then `comfy_edit` to set all of them on fire.
3. Readability at card size – the dark figures turn into silhouettes when small:
   - most cards: `comfy_art_light` on the chosen image (defaults);
   - always show the user the version with and without the reflector – where the art already has strong own light (Berserk's glowing eyes) the reflector can look too contrasty and the user keeps the plain version;
   - where it looks better (e.g. Silný úder): `comfy_edit` relight first – *"Relight the scene: add a warm rim light along the edges of the <character> and a soft warm key light on <face, hands, …>, so the figure clearly stands out from the darker background. Keep the background dark and moody and keep the original color palette (dark teal and charcoal tones…). Keep the exact composition, characters, poses, painting style and brushwork unchanged."* (`enhance` false, `output_size` 512x512), then `comfy_art_light` with `blend_image` = that result (`blend` 0.8).
   - Do not use `blend_image` to tone down a *local* edit (e.g. weaker glowing eyes): the Qwen output is not pixel-aligned with the original and the mix looks blurry/doubled (Náhlá smrt). Ask Qwen for the milder effect instead („soft, subtle glow, not too bright"). Quantities: „add a few more, about ten" – „many more" overdoes it.
   - Without the palette sentence Qwen tends to repaint skies orange. img2img re-generation (krea, denoise 0.35) does not help (tested). A mask-only brightening („D2") looks cut-out – rejected.
4. Save as `app/src/main/res/drawable/art_<name>.png` (512×512) and a server thumbnail `server/art/art_<name>.webp` 80×112 (centre crop of the square, same processing).
5. `CardPresentation.kt`: square arts use `artScale = 0.80f` and no `artBiasY`.
6. Log the card in `wiki/log.md` (what it shows, chosen variant, what was fixed).

### Layered art for golden cards (test, `"T48"`) – full procedure in [[systems/golden-cards]]
- Characters: `comfy_remove_bg` on the finished art → `art_<name>_fg.png` (512×512, transparent).
- Background without characters: `comfy_edit` (Qwen) *"Remove the <characters> completely … and fill the space with what is behind them …; keep the moon, …, the exact composition, the color palette and the painting style unchanged. No people."* (`enhance` false, `output_size` 512x512) → `art_<name>_bg.png`. Do not use `comfy_inpaint` for this: it repaints the ground (characters then float) and leaves a dark halo of the mask.
- Glow lost in the cut-out (magic in hands) is drawn in code: `ArtLayers(glowX, glowY, glowColor)` in `CardPresentation.kt`; animation in `GoldenCard.kt`.

### Reflektor C (what `comfy_art_light` / `Reflektor_C` do)
gamma 0.80 → S-curve around 0.45 (×1.18) → saturation ×1.15 → elliptical light (half-axes 0.42 × 0.50 of the image) centred on the character: inside brightness ×(1 + 0.28·(1−d²)) and a warm tint, outside darkening up to 22 %. The centre is the centroid of the BiRefNet character mask shifted 0.06 up (legs and cloaks pull the centroid down); a manual centre can be given. Pure GLSL in ComfyUI core (`GLSLShader`), ~1 s per image, deterministic – style and details stay.

## Related pages
- [[architecture]] — where card data and art references live
- [[overview]] — the game

## Changelog
- 2026-11-18: Page created – tools, style, steps, reflector C, MCP 2.6.0.
- 2026-11-18: Effects the generator cannot draw → add with `comfy_edit` (Plamenomet).
- 2026-11-18: Layered art for golden cards (Upír T48).
- 2026-11-18: Styles split: `darkmage` = look only, `herolight` = light on the character; buildings without `card`.

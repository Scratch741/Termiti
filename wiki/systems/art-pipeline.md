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

### Variety – every card must be recognisable at a glance (user, 2026-11-19)
Feedback from players: the new arts look great, but in the game they are hard to tell apart – same dark teal background, a dark figure in the middle doing something. Rules for every new or regenerated art:
- **Fewer figures, more objects.** Prefer a macro / still life of the key object when it tells the card (Jed = poison flask, Hod cihlou = hand with a brick). A figure only when the person *is* the card (Ogr, Berserk, Odstřelovač).
- **Vary the framing:** extreme close-up / macro, close-up portrait, medium shot, wide shot, top-down, low angle, silhouette against light. Do not repeat the framing of the cards most often seen next to it.
- **Vary the dominant colour / light** while keeping the painterly `darkmage` brushwork: e.g. green poison glow, red fire, cold blue moonlight, golden dawn, purple magic, misty grey, orange chaos. The dark teal sky is no longer the default – write the palette into the prompt (`darkmage` gives the brushwork; override its teal with the card's own light).
- **Vary pose and moment:** not always „figure in the centre facing the viewer with a raised weapon“ – side view, from behind, mid-motion, aftermath, hands only, the object in flight.
- **Before generating, check the neighbours:** list the arts already done in the same resource (Útok / Stavba / Magie / Chaos) and pick a framing + colour that is different from them.
- Resource hint is OK but not mandatory: Útok tends to red/orange, Stavba to stone/earth/gold, Magie to blue/purple, Chaos to orange; mix within these.
- **Chaos cards (user, 2026-11-21):** the chaos magic must be unmistakable – bright **orange chaos magic** (crackling orange lightning, orange sparks / smoke / glowing cracks) in every chaos art. Background mostly yellow-orange-red, sometimes alternate with dark teal (then write "strong contrast of glowing orange chaos against cold dark teal"). **No humans** (no faces, no hands) unless the card text explicitly names a person – use objects, creatures, places. Order of work: cheapest to most expensive, in sets of 5 (list in `design/art_review/nepredelane_arty.md`).

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

### Batch script (no Claude, saves tokens) – `tools/art_batch.py`
Runs the same pipeline straight against the ComfyUI API (`tools/comfy/krea_api.json` = Krea 2 Turbo without prompt refining and without LoRA, `tools/comfy/art_reflektor_api.json` = reflector); style texts come from `~/Pictures/ComfyWorkflow/styles.json` (`darkmage` by default).
1. Write a batch JSON (example `tools/art_batches/vzor.json`): `art` = drawable name, `title`, `prompt` (follow the Variety rules – framing, palette, "no teal"), optional `count` (4), `styles`.
2. `python tools/art_batch.py gen tools/art_batches/<batch>.json [art_x …]` → variants in `Pictures/Generated/karty/<art>/`, overview `design/art_review/<batch>.png` (one row per card, numbered), seeds in `<batch>.seeds.json`. ~25 s per image.
3. Pick numbers, then `python tools/art_batch.py apply tools/art_batches/<batch>.json art_x=2 art_y=4` (`--no-light` = without reflector) → reflector, 512×512 drawable, 80×112 server thumbnail, `CardPresentation.kt` `artScale = 0.80f` without old crop offsets, `card_data.json` regenerated, entry appended to `wiki/log.md`.
- Prompts by a local LLM: `python tools/art_plan.py <batch> <card> [<card> …] [--gen] [--model mimo-v2.6]` (card = id or Czech name). Ollama on this PC (`localhost:11434`, model `mimo-v2.6`, 9B) writes one prompt per card from the English name + effect (`en.json`) following the Variety rules, saves `tools/art_batches/<batch>.json` (edit by hand if needed); `--gen` runs `art_batch.py gen` right away. ComfyUI is on the LAN machine, so the GPUs do not collide. ~50 s per prompt. The model tends to loop and repeat itself → the script limits the answer and keeps only the first prompt; quality is weaker than Claude's (odd details) – check the prompts before generating.
- Edits (Qwen Edit: fix a detail, change background, add effect) are still done through the MCP extension or the ComfyUI UI.
- GPU is shared with Ollama (12 GB): do not run a local LLM during a batch; ComfyUI keeps the model loaded between images.

### Reflektor C (what `comfy_art_light` / `Reflektor_C` do)
gamma 0.80 → S-curve around 0.45 (×1.18) → saturation ×1.15 → elliptical light (half-axes 0.42 × 0.50 of the image) centred on the character: inside brightness ×(1 + 0.28·(1−d²)) and a warm tint, outside darkening up to 22 %. The centre is the centroid of the BiRefNet character mask shifted 0.06 up (legs and cloaks pull the centroid down); a manual centre can be given. Pure GLSL in ComfyUI core (`GLSLShader`), ~1 s per image, deterministic – style and details stay.

## Related pages
- [[architecture]] — where card data and art references live
- [[overview]] — the game

## Changelog
- 2026-11-21: Chaos card rules (orange chaos magic, warm or alternating teal background, no humans unless the card names one); style `herolight` missing on the LAN PC – write its text into the prompt.
- 2026-11-19: Batch script `tools/art_batch.py`.
- 2026-11-19: Variety rules (framing, colour, fewer figures) after player feedback.
- 2026-11-18: Page created – tools, style, steps, reflector C, MCP 2.6.0.
- 2026-11-18: Effects the generator cannot draw → add with `comfy_edit` (Plamenomet).
- 2026-11-18: Layered art for golden cards (Upír T48).
- 2026-11-18: Styles split: `darkmage` = look only, `herolight` = light on the character; buildings without `card`.

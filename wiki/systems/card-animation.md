# Card animation (image-to-video)

> Wan 2.2 I2V 14B in ComfyUI + `tools/animate_card.py` turn card art into short loops. **On hold** (user, 2026-11-20): capability kept, resume later.

Short animated loops (1–3 s) made from existing card art with local AI in ComfyUI. Status 2026-11-20: **on hold by the user** – keep the capability, come back to it later. The pipeline works on the server; **nothing is wired into the game**.

Where it stands:
- **Ping-pong (default mode) is the only truly seamless loop.** User: not optimal for Drak (the fire stream visibly runs backwards), but OK for other cards with reversible motion (swaying, breathing, glow, wing beats without a fire stream).
- No seamless *forward* loop yet: the two-run `return` mode has local differences, a long clip has no loop point. Ideas for later: non-reversible effects (fire, sparks) drawn in code over a ping-pong of the subject, like the torch flames on the battlefield and the golden cards; or a model / LoRA that supports real looping.

## Model on the server

ComfyUI `http://192.168.125.140:8188` (RTX 3080 Ti 12 GB, 32 GB RAM). Chosen by the user: **Wan 2.2 I2V 14B fp8** (better motion than 5B; supports first+last frame, so seamless loops are possible).

| File | ComfyUI folder |
|---|---|
| `wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors` | `diffusion_models/Wan2.2/` |
| `wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors` | `diffusion_models/Wan2.2/` |
| `umt5_xxl_fp8_e4m3fn_scaled.safetensors` | `text_encoders/` |
| `wan_2.1_vae.safetensors` (14B uses the Wan 2.1 VAE, not `wan2.2_vae`) | `vae/` |
| *optional* `wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors` + `..._low_noise` | `loras/` |

Installed through ComfyUI-Manager's API (`POST /api/manager/queue/install_model` with the catalog entry, then `POST /api/manager/queue/start`). Manager refuses URLs that are not in its catalog ("Invalid model install request"), so the **lightx2v LoRAs have to be copied to the server by hand** from `https://huggingface.co/Comfy-Org/Wan_2.2_ComfyUI_Repackaged/tree/main/split_files/loras`. With them, 4 steps instead of 20 (about 5× faster); the script detects them automatically.

## Script

`tools/animate_card.py` (Python + Pillow + numpy, run from repo root):

```
python tools/animate_card.py art_drak "The dragon powerfully flaps its huge wings up and down, its long neck and tail sway, it breathes a roaring stream of fire that billows over the battlements, sparks fly, storm clouds churn"
```

- Input: drawable name (`res/drawable/<art>.png`) or a path. Scaled/cropped to 512×512.
- Output: animated WebP to `~/Pictures/Generated/animace/<art>/<art>_s<seed>.webp`, 16 fps.
- `--frames` 4n+1 (default 33 ≈ 2 s; 17 ≈ 1 s, 49 ≈ 3 s), `--seed`, `--count`, `--size`, `--mode`, `--slow`.
- `--mode pingpong` (default): one `WanImageToVideo` run, played forward and back with a cosine ease at both turns (`pingpong()`, 33 → 2×25 frames). Seamless (wrap 0.4), consistent colours; motion reverses.
- `--mode free`: one run, motion one way, then a jump when repeated.
- `--mode return` (experiment, not approved): (1) `WanImageToVideo` forward (17 frames for 2 s); (2) `WanFirstLastFrameToVideo` return (25 frames) whose **start is the last 5 frames of (1) and end is the first 5 frames of (1)** (multi-frame batch via `ImageBatch`). `stitch_loop()` histogram-matches every return frame (per channel) to a reference interpolated between the last and first frame of (1), and cross-fades the 5-frame overlaps.
- Colour drift in the second run is large and not linear (Drak: brightness 85 → 100, contrast std 46–73, black point 0–33). Correction measured only at the ends → visible pulse; per-frame mean/std → contrast still "breathed" (user: "hrozně se ti mění kontrast"); full histogram matching → flat tones, but local differences remain.
- **Do not** use `WanFirstLastFrameToVideo` with the art as both first and last frame: Wan then barely moves (frame diff ~2/255) and only the brightness pulses (user: "drak se vůbec nehýbe, jen pulzuje divně světlo").
- Raw ComfyUI outputs stay in `~/Pictures/Generated/animace/<art>/_tmp/`.
- Workflow: two experts (high noise → low noise) via two `KSamplerAdvanced` passes, `ModelSamplingSD3` shift 8 (5 with LoRA), euler/simple, cfg 3.5 (1 with LoRA). Built in code; no JSON file.

## Measured (2026-11-20)

- Judge motion by numbers, not by a few thumbnails: mean abs diff of each frame vs. frame 0, mean brightness per frame, and the wrap step last→first (should be ≈ the normal step).
- `art_drak`, 17 frames, same image first+last (old loop): 131 s, **no motion** (diff ≤ 3), brightness 86→93 = pulse.
- `art_drak`, 33 frames, one run (now `--mode free`): 206 s, real motion (diff up to 13.6: wings, head, fire), brightness stable.
- `art_drak`, two-stage loop 2×17 frames, return from 1 frame to the art + 4-frame fade into frame 0: 381 s, wrap step 1.2 but user rated it ~90 % (speed jump at seams).
- `art_drak`, 5-frame context loop (17 + 25 frames): 421 s; after `stitch_loop` both seams 2.7–4.5 = like normal steps (3–5), brightness 85–86 throughout, 1.1 MB WebP. Return half moves faster (steps 4–8).
- Server needs RAM headroom: the 14B workflow peaks at ~28 GB RSS. Without swap the kernel OOM-killed ComfyUI (32 GB RAM); fixed with a 16 GB `/swapfile`. Disk must have free space too (`/upload/image` fails with `Errno 28`).

- Two-run return loop: even with histogram matching the user still saw it ("pořád to nesedí"). Frame sheets show why – the second Wan run differs *locally* (more saturated wings, greener/darker sky, hazier detail, floating orange blobs). Two separate runs never fully match.
- Loop-point search in one long clip (`art_drak`, 81 frames, 614 s, prompt "flaps rhythmically, again and again"): **no usable point** – best pair differs 12.7 vs. median step 3.2; the clip drifts away from the art and brightens 86 → 103.
- Ping-pong of one clip (forward + backward, cosine ease at both turns, 2×24 frames from that 33-frame one-run clip): wrap 0.4, brightness 86–88, 1.6 MB. Fully consistent (single run); motion reverses. User: not optimal for Drak (fire goes back), OK for other cards → default mode.

## Prompting

- Describe **motion**, in English, strong and concrete ("powerfully flaps its huge wings up and down", "roaring stream of fire billows"). Words like *subtle* / *gentle* made Wan keep the picture still. The script appends: camera stays still, no zoom/pan, clear continuous motion, keep art style.
- Big actions (walking, turning around) deform characters; keep the subject mostly in place.
- For ping-pong pick motion that looks right in reverse too: swaying, breathing, wing beats, glow, drifting smoke. Avoid fire streams, projectiles, explosions, falling objects.
- Longer clips drift away from the art and brighten (81 frames: brightness 86 → 103) — keep 2 s (33 frames).

## Open (decide with the user before wiring into the game)

- Playback on the card: animated WebP via `AnimatedImageDrawable` is API 28+ (`minSdk = 26` needs a static fallback; see `anim_savanna/HANDOFF.md`, same problem). Size: an animated 512² WebP is about 1–3 MB per card → only for selected cards (e.g. golden cards, [[systems/golden-cards]]), or smaller/shorter.
- Which cards get animations.

## Related pages
- [[systems/art-pipeline]] — where the card art itself comes from
- [[systems/golden-cards]] — animated golden cards, a likely first user of these loops

## Changelog
- 2026-11-20: ping-pong is the default mode (`--mode`); work put on hold by the user
- 2026-11-20: Wan 2.2 I2V 14B fp8 installed on the ComfyUI server, `tools/animate_card.py` added
- 2026-11-20: return frames histogram-matched (contrast was pumping)
- 2026-11-20: loop with 5-frame context at both seams, per-frame colour normalisation and overlap cross-fades
- 2026-11-20: loop rebuilt as two-stage (go + return) with colour match and seam cross-fade; the same-frame first+last loop produced no motion

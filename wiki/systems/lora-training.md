# LoRA training (AI Toolkit on the ComfyUI server)

> How to train a LoRA for the card art on the LAN server: style LoRAs `darkmage_zimage_v1` (Z-Image Turbo, via the extension) and `darkmage_krea2_v1` (Krea 2 Turbo, hand-written AI Toolkit config), dataset, config, time, tests against no LoRA, licence and the server fixes they needed.

## Content

### Tools
- **AI Toolkit** (ostris) on the ComfyUI server in `~/ai-toolkit` (own venv, web UI started with `npm run build_and_start`). Claude drives it through the „Ollama + ComfyUI (LAN)" extension:
  - `lora_dataset` – uploads images from this PC with captions (+ trigger word in front) to `~/ai-toolkit/datasets/<name>`; transparency is filled grey, `max_side` downsizes.
  - `lora_train` – creates/starts a job. **Supports only Z-Image Turbo** (with training adapter, 8-bit). Other models (Krea 2, Wan 2.2, Qwen Image, Flux 2…; list in `~/ai-toolkit/extensions_built_in/diffusion_models/`) need a hand-written config run on the server by the user – see the Krea section below. Such jobs are not in the AI Toolkit UI database, so `lora_status` does not see them; follow the log and the checkpoints in ComfyUI (`/models/loras`).
  - `lora_status` (step, speed, log, saved files, sample grid first vs latest), `lora_control` (start / stop / save_now / sample_now / delete).
- Saved LoRAs appear in ComfyUI as `loras/aitk/<job>/<job>[_<step>].safetensors` (symlink `~/ComfyUI/models/loras/aitk` → AI Toolkit output). The final file has no step suffix; checkpoints every 250 steps.
- Training takes the whole GPU – no ComfyUI / Ollama generation meanwhile. Before starting free memory: `comfy_free` (ComfyUI kept ~27 GB RAM and 6.5 GB VRAM cached from Wan / Krea). Check disk (`df -h /`) – the first run downloads Z-Image Turbo + text encoder (~21 GB).

### `darkmage_zimage_v1` – style LoRA of the card art (2026-11-20)
- **Dataset `darkmage_cards`:** the 44 arts regenerated in the Krea + `darkmage` style (2026-11-17 → 20) minus `art_krvavy_uder` (abstract slash) and `art_primy_zasah` (flat target) = 42 images, 512×512. Captions and the exclusion list: `tools/lora/darkmage_cards.json`.
  - Captions = content + framing + colour of the light, **not** the painting style (the LoRA should learn the brushwork, while palette stays promptable). Trigger `dkmg style`.
  - Bias: almost all images are Útok cards – fire, orange and dark teal dominate; Magie / Stavba / Chaos are missing.
- **Config:** Z-Image Turbo, focus `style`, rank 16, lr 1e-4, 2000 steps, resolution 512 only (dataset has no more), `layer_offloading: true`, samples + save every 250 steps. Result 81 MB.
- **Time:** ~2 s/step on the RTX 3080 Ti → 2000 steps ≈ 65 min (+ ~2 min latent caching, + model download on the very first run).

### Using it in ComfyUI
`comfy_generate` with `workflow: "zimage"`, `loras: [{"name": "aitk/darkmage_zimage_v1/darkmage_zimage_v1.safetensors", "strength": 1.0}]`, prompt starting with `dkmg style, …`, **and keep `styles: ["darkmage"]`**. ~7–8 s per 1024² image (Krea ~25 s).

### Test (same prompt + seed 4242, 1024², grid `design/art_review/lora_darkmage_zimage_v1.png`)
| Prompt | Without LoRA (style `darkmage` text) | LoRA 1.0 (trigger only) | LoRA 1.0 + `darkmage` text |
|---|---|---|---|
| Wizard tower hit by blue lightning | murky, smeared | clean painted scene, readable silhouette, blue kept | – |
| Macro: trowel + mortar, golden dawn | painterly | **photo-real** (failure) | painterly, but golden light turned into teal |
| Goblin cauldron explosion, orange | oil look, busy | clear, card-like composition, extra small figure | painterly; extra figure (prompt adherence of Z-Image) |
| Old wizard, purple light | oil look, dark face | strong rim + purple light, figure clearly brighter than background | – |
- 0.7 is close to 1.0 – a bit softer, no clear win.
- Conclusions: the LoRA gives the card look (readable silhouettes, rim light, painted flat shapes) that bare Z-Image does not have. Trigger alone fails for objects / macros without people (drift to photo) → **always add the `darkmage` style text**. It pulls skies toward dark teal (dataset bias) – write the colour of the light strongly into the prompt; a v2 should add non-Útok arts.
- Krea 2 Turbo stays the main card generator (better prompt adherence); Z-Image + LoRA is a fast alternative for drafts / variants. Not yet compared with Krea on the same prompts.

**Z-Image was deleted from the server on 2026-11-20** (`z_image_turbo_bf16.safetensors` in ComfyUI and the HF cache, to make room for Krea) → workflow `zimage` and this LoRA do not work until Z-Image is downloaded again. The LoRA file itself stays in `~/ai-toolkit/output/darkmage_zimage_v1/`.

### `darkmage_krea2_v1` – the same style LoRA for Krea 2 Turbo (2026-11-20)
- AI Toolkit has `krea2` (arch `krea2:turbo` = Turbo + training adapter `ostris/krea2_turbo_training_adapter/krea2_turbo_training_adapter_v1.safetensors`). The ComfyUI files (`krea2_turbo_fp8_scaled`, `qwen3vl_4b_fp8_scaled`) cannot be used – AI Toolkit downloads the original weights: `krea/Krea-2-Turbo` `turbo.safetensors` 26.3 GB + `Qwen/Qwen3-VL-4B-Instruct` 8.9 GB + VAE from `Qwen/Qwen-Image` ≈ **36 GB** in `~/.cache/huggingface/hub`.
- **Gated repo:** accept the Krea 2 Community License on huggingface.co/krea/Krea-2-Turbo, then log in on the server once: `hf auth login` (token stored in `~/.cache/huggingface/token`). A fine-grained token needs „read access to public gated repos". Do not use `read -s … && cmd &` – `&` sends the `read` to the background, the token stays empty and the download fails with `Illegal header value b'Bearer '`.
- **Config** `tools/lora/darkmage_krea2_v1.yaml` (copy to `~/ai-toolkit/config/`): same dataset `darkmage_cards`, rank 16, lr 1e-4, 1500 steps, 512 px, `cache_text_embeddings`, `quantize` qfloat8 (→ float8), `low_vram`, `layer_offloading` with `layer_offloading_transformer_percent: 0.6` (60 % of the ~13B transformer layers in RAM), samples 768 px / 9 steps / guidance 1.
- **Run** (on the server, after `hf auth login`):
  ```bash
  cd ~/ai-toolkit && source venv/bin/activate && unset HF_TOKEN && (nohup python run.py config/darkmage_krea2_v1.yaml > output/darkmage_krea2_v1.log 2>&1 &)
  tail -c 2000 ~/ai-toolkit/output/darkmage_krea2_v1.log
  ```
- **Time / memory:** download ~8 min (~78 MB/s), load + quantize a few min, then **2.4 s/step** → 1500 steps ≈ 60 min. GPU 10.7 GB / 99 %, RAM ~15 GB used + 4 GB swap. A `CUDACachingAllocator … OOM` warning right after sampling is harmless.
- **Use:** workflow `krea`, `lora: false` (switch off darkbrush), `loras: [{"name": "aitk/darkmage_krea2_v1/darkmage_krea2_v1.safetensors", "strength": 1.0}]`, prompt `dkmg style, …` **plus** style `darkmage`. ~12 s per 1024² image.
- **Test** (seed 4242, same 4 prompts as Z-Image; grid `design/art_review/lora_darkmage_krea2_v1.png`):
  - With the `darkmage` text the effect is **subtle** – the dataset was made by Krea + `darkmage`, so the LoRA mostly reinforces it: a bit more contrast, warmer accents, more detail in rocks / cauldron, the goblin really flies back. Composition and prompt adherence stay (Krea's strength).
  - Without the style text the LoRA carries the look for **characters** (wizard: teal background, painterly brushwork vs. plain digital illustration from bare Krea), but **not for object macros** (trowel stays a photo) – the dataset has few objects without people.
  - `krea2_darkbrush` is a different style (ink / wash sketch, white mist, high contrast) – not a replacement.
  - Golden „dawn light" is turned into teal by the `darkmage` text in all variants; without it Krea keeps it. Write the light colour strongly / override the teal.
  - Chaos magic test (sorcerer, orange/violet, „no teal", seeds 64203889600–3, `Pictures/Generated/chaos_magie/`): the background stayed dark teal **with and without** the `darkmage` text → the teal is baked into the LoRA itself (dataset bias). Without `darkmage` the energy is more saturated and the figure brighter, composition the same. For a non-teal art: lower the LoRA (~0.5) or leave it out.
- **Verdict:** usable as a mild booster of the card look; the bigger win would be a v2 with a more varied dataset (Magie / Stavba / Chaos arts, object still lifes, other light colours).

### Licence (Krea 2 Community License v1, 2026-06-22)
- Applies already to every Krea 2 use (the ComfyUI fp8 file too), the HF acceptance only unlocks the download.
- Outputs (card art, also from a LoRA = „Derivative") belong to the user; **commercial use allowed while total annual revenue < 1 000 000 USD**, above that an Enterprise licence is needed.
- Needs reasonable content filtering (human review of every art counts), AI disclosure where law / platform requires it, Acceptable Use Policy. Krea may terminate the licence with 30 days notice (then stop using the model and LoRAs). Publishing a LoRA would require „Krea" in its name + a Notice file.
- Not legal advice – check before a paid release.

### Server fixes needed (2026-11-20)
1. **CUDA OOM** when moving Z-Image to the GPU (11.6 GB not enough) → `layer_offloading: true`.
2. **`CUDNN_STATUS_SUBLIBRARY_VERSION_MISMATCH`** in the first VAE conv. Cause: system cuDNN **9.21.1** from apt (`/usr/lib/x86_64-linux-gnu/libcudnn*`, in `ldconfig`, incl. `libcudnn_engines_tensor_ir.so.9` that the pip 9.20 does not have) mixed with pip cuDNN **9.20** in the ai-toolkit venv. `LD_LIBRARY_PATH` was not the cause. Fix (user ran it):
   ```bash
   cd ~/ai-toolkit && source venv/bin/activate && pip install "nvidia-cudnn-cu13==9.21.1.*" ; deactivate
   ```
   pip warns that torch 2.13 wants exactly 9.20.0.48 – works anyway. Quick check: `python -c "import torch; x=torch.randn(1,3,64,64,device='cuda'); print(torch.nn.Conv2d(3,8,3).cuda()(x).shape)"` inside the venv. Revert: `pip install "nvidia-cudnn-cu13==9.20.0.48"`. If ai-toolkit updates torch, the mismatch may come back.
- A short OOM warning while AI Toolkit draws its samples is harmless (it recovers).

## Related pages
- [[systems/art-pipeline]] — how card art is made; the LoRA is an optional add-on
- [[systems/card-animation]] — Wan 2.2 on the same server (RAM/swap limits)

## Changelog
- 2026-11-20: Krea 2 LoRA `darkmage_krea2_v1` (config, run, test, licence); Z-Image + Wan deleted from the server.
- 2026-11-20: Page created – first LoRA `darkmage_zimage_v1`, dataset, config, test, cuDNN fix.

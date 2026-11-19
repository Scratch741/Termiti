# Termiti (Darkmage) – pokyny pro Clauda

Android karetní hra (Kotlin + Jetpack Compose) a Node.js server pro online hru. Znalosti o projektu jsou ve wiki: začni `wiki/index.md`, pravidla pro wiki jsou ve `wiki/CLAUDE.md` (každou změnu zapiš do `wiki/log.md`).

## Grafika karet a obrázky
Obrázky pro hru děláme **lokální AI v ComfyUI** přes rozšíření Claude **„Ollama + ComfyUI (LAN)" ≥ 2.6.0** (nástroje `mcp__Ollama___ComfyUI__LAN___*`: `comfy_variants`, `comfy_generate`, `comfy_edit`, `comfy_inpaint`, `comfy_art_light`, `comfy_finalize`, `comfy_remove_bg`, `comfy_styles`, `comfy_free`). Jiné generátory nepoužívej.

- Postup, styly (`darkmage` vždy; u postav navíc `card` + `herolight`), rozměry a kam soubory patří: `wiki/systems/art-pipeline.md`.
- Rozšíření je v repozitáři: `tools/mcp/ollama-comfy-lan-2.6.0.mcpb` (instalace: přetáhnout do Claude → Nastavení → Rozšíření, pak nová relace).
- ComfyUI: `http://192.168.125.140:8188`; když chybí VRAM, uvolni ji přes `comfy_free`.
- **Každý art musí být na první pohled jiný** (jiné přiblížení, barva světla, póza; méně postav, víc předmětů v makru) – pravidla v `wiki/systems/art-pipeline.md`, sekce Variety.

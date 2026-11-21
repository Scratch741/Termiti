# Návrh UI „Obsidián a bronz“ (2026-11-21) – neimplementováno

Náhledy jsou AI přemalované screenshoty z telefonu (Qwen Image Edit) – nálada, ne finální grafika; drobné texty a čísla v nich jsou zkomolené. Stránka s porovnáním: `navrh.html` (artifact https://claude.ai/artifact/WzSn7ZnkXYPrXALqgVoD5s).

## Verdikt uživatele
- **Líbí:** temný mág za tlačítky v hlavním menu (`po_menu.jpg`), nový rám dialogu – tmavý kámen + pergamen, začernalý bronz s gotickými rohy, bronzové hlavní tlačítko (`po_dialog.jpg`) – i pro mulligan.
- **Ujde:** Tvorba balíčku – filtry jako drahokamy v paticích, cena jako mince (`po_balicek.jpg`).
- **„Hnus“:** bojiště s drahokamovými paticemi, věžemi a kulatou pečetí (`po_bojiste.jpg`), náčrt sady prvků (`ui_kit.jpg`).
- **Bojiště, další kolo** (`bojiste_smery.jpg`, A pergamen / B celá obrazovka / C styl menu / D grimoár): „možná ten Hearthstone styl (B) bude pak dobrý“ – malba přes celou obrazovku, panely jako tenké kouřové sklo, karty v ruce přes spodní okraj.

## Hrady a hradby – nápady k uložení (uživatel: „nápad nemáš špatný, třeba s tou ruinou“)
Náhledy: `hrady_navrh.jpg`. Stav dnes: `NewCastleStructure` v `GameBattlefield.kt` – hrad 110×165 dp se „vynořuje“ ze země podle HP, hradba je samostatný kvádr 42×58 dp vedle něj, čísla jen ve štítku nahoře, strany odlišuje jen barva čísel (tyrkys / červená).
1. Čitelnost: teplé obrysové světlo + měkký stín na zemi, aby hrad nesplýval s pozadím (zimní bojiště).
2. Barva strany: záře u paty hradu – modrá hráč, červená soupeř (tyrkysová pryč).
3. Hradba před hradem: širší val směrem ke středu, kryje patu hradu, roste/klesá s HP.
4. **Stavy poškození podle HP** (líbilo se): > ⅔ s prapory, normální, < ⅓ poškozený (praskliny, oheň, kouř), ruina. Kvůli 13 skinům řešit společnou překryvnou vrstvou (praskliny, kouř, plamínky), ne 4 obrázky na skin.
5. Zásah / stavba: otřes, odletující kamínky a prach; při stavbě krátká zlatá záře.
6. Číslo HP ve štítku přímo nad věží; horní štítek jen pro počet karet v ruce.

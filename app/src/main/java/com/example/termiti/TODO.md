Systém odemykání karet - hráč si bude muset odemykat karty buď přes hraní nebo přes herní měnu, nebo přes "boostery", jako je to v MtG
Grafický design karet - přidání grafického designu karet
Zvuky - přidání zvuků při zahrání karty
Animace - přidání speciálních animací při zahrání karty
Hrad/hradby - vizuální reprezentace. Čím víc hradu / hradeb máš, tím větší bude
Přidání nového zdroje - Chaos - nebude na něj od začátku důl, speciální karty na generaci chaosu (například magie bude generovat chaos, nebo musíš přetvořit důl magie na důl chaosu), zelená barva, unikátní karty (například destrukce dolů, ničení hradu pro soupeře a zároveň pro hráče, atd.)


Více karet, nové mechaniky karet (lze se inspirovat z Hearthstone). Karty typu znič doly (chaos), přidej karty do balíku, znič karty (chaos), ukradni karty (chaos)
Předpřipravené balíky s určitou tématikou na win condition (výhra na hrad, na útok, na krádeže, na počet karet...)
Balancing karet
Vytvořit nové karty na stavbu - chybí velké stavební karty pro výhru přes hrad
Přetvořit většinu útočných karet - chybná myšlenka. Karty by měly z většiny útočit na hráče, ne na hradby/hrad. Pouze specializované karty útočí jen na hradbu/hrad
Nové mechaniky karet - Karty reagují na to, co jsi zahrál předtím (if last card was ATTACK → +10 damage
else → +4 damage) /  Silná karta s nevýhodou (chaos karty) / Znič nejvyšší surovinu/důl (cílený) / Deck manipulace - lízni 2, discardni 1. Podívej se na top 3 karty a vyber 1, co dáš navrch. / Swap resources s enemy (chaos) / Doomsday (chaos, -30 poškození, možnost remízy) / Ongoing/status efekty / Swap hand mezi soupeřem a hráčem
Hezčí UI
Character progress - level, kupování karet, hrdina, speciální pasivní schopnosti
Předělat/lepší multiplayer - stabilní server na linuxu, kde se ověřují všechny činnosti - prevence cheatingu
Simple animace - když zaútočíš, poběží panáček na hrad, atd.
Online zápasy - pokud dlouho hráč nenajde soupeře, automaticky dostane proti sobě AI.
Zvuky karet
Screen shake?

Připravit preset karet pro nováčka. Současné presety poté přidat AI, se kterou bojuješ v módu "vlastní balíček" - vybere si náhodný preset balík.
Extra XP za kampaň. Přidat více do kampaně. Dodat informace, že dostáváš XP a goldy za jiné módy. Lepší gold progress.
Sestavit balík - filtr na karty, které mám. Celkově rework filtry a sestavení balíku, je to dogshit.

── Nové návrhy ──────────────────────────────────────────────────────────────

Replay systém
- Uložení průběhu hry (každý tah, zahraná karta, stav po tahu)
- Možnost přehrát replay po skončení hry
- Případně sdílení / uložení replayů

Nové karty / mechaniky:

1) Bomba v balíčku — přidej oponentovi 3 bomby do jeho balíčku; po líznutí bomby hrad -5
2) Naklonování karty [Combo] — magic karta; toto kolo naklonuje do balíčku další zahranou kartu
3) Nová mechanika: Rozhodnutí — otevře se mulligan-like tabulka s výběrem karet (např. "vyber 1 ze 3 karet do ruky")
4) Nová mechanika: Nahlédnutí do balíčku — podívej se na top N karet svého nebo soupeřova balíčku, případně vyber pořadí
5) Legendární chaos karta: Velký zmatek — vyměň všem hráčům celé ruce za náhodné karty (chaos efekt)

── Nové karty a mechaniky (2. vlna) ─────────────────────────────────────────

1) Zrcadlo (magic, rare)
   - Karta se vizuálně mění podle toho, co bylo naposledy zahráno soupeřem
   - Aktivuje se AŽ po tom, co soupeř zahraje kartu (= zkopíruje její efekt)
   - Pokud soupeř ještě nic nezahrál, efekt je prázdný / slabý fallback

2) Chaotická přeměna (chaos, rare)
   - Znič vlastní důl magie a přetvoř ho na důl chaosu
   - Nový efekt: ConvertMine(MAGIC → CHAOS)

3) Dvojník (magic, rare, combo)
   - Přidej kopii náhodné karty z vlastního odhazovacího balíčku do ruky
   - Nový efekt: DrawFromDiscard(1)

4) Mulligan timer v online módu
   - Hráč má omezený čas na mulligan (např. 30s), po vypršení se automaticky potvrdí bez výměn
   - Server musí po timeoutu odeslat MULLIGAN_DONE s prázdnými returnIds

5) Shapeshifter (chaos, epic)
   - Na začátku každého kola se karta v ruce změní v náhodnou kartu ze hry
   - Speciální karta s persistentním chováním — potřeba příznak isShapeshifter + logika v generateResources/startOfTurn

6) Vrácení v čase (magic, legendary)
   - Vrať hru o 1 tah zpět (HP hradu, hradby, zdroje, ruka obou hráčů) — tato karta se ztratí
   - Potřeba: snapshot stavu před každým tahem, rollback mechanismus v enginu

7) Krádež identity (chaos, legendary)
   - Prohoď s oponentem celé ruce karet
   - Nový efekt: SwapHands
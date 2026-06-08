Systém odemykání karet - hráč si bude muset odemykat karty buď přes hraní nebo přes herní měnu, nebo přes "boostery", jako je to v MtG
Grafický design karet - přidání grafického designu karet
Zvuky - přidání zvuků při zahrání karty
Animace - přidání speciálních animací při zahrání karty
Hrad/hradby - vizuální reprezentace. Čím víc hradu / hradeb máš, tím větší bude
Přidání nového zdroje - Chaos - nebude na něj od začátku důl, speciální karty na generaci chaosu (například magie bude generovat chaos, nebo musíš přetvořit důl magie na důl chaosu), zelená barva, unikátní karty (například destrukce dolů, ničení hradu pro soupeře a zároveň pro hráče, atd.)


Více karet, nové mechaniky karet (lze se inspirovat z Hearthstone). Karty typu znič doly (chaos), přidej karty do balíku, znič karty (chaos), ukradni karty (chaos)
Přetvořit většinu útočných karet - chybná myšlenka. Karty by měly z většiny útočit na hráče, ne na hradby/hrad. Pouze specializované karty útočí jen na hradbu/hrad
Nové mechaniky karet - Karty reagují na to, co jsi zahrál předtím (if last card was ATTACK → +10 damage
else → +4 damage) /  Silná karta s nevýhodou (chaos karty) / Znič nejvyšší surovinu/důl (cílený) / Deck manipulace - lízni 2, discardni 1. Podívej se na top 3 karty a vyber 1, co dáš navrch. / Swap resources s enemy (chaos) / Doomsday (chaos, -30 poškození, možnost remízy) / Ongoing/status efekty / Swap hand mezi soupeřem a hráčem
Hezčí UI
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

5) Legendární chaos karta: Velký zmatek — vyměň všem hráčům celé ruce za náhodné karty (chaos efekt)
── Nové karty a mechaniky (2. vlna) ─────────────────────────────────────────

1) Zrcadlo (magic, rare)
   - Karta se vizuálně mění podle toho, co bylo naposledy zahráno soupeřem
   - Aktivuje se AŽ po tom, co soupeř zahraje kartu (= zkopíruje její efekt)
   - Pokud soupeř ještě nic nezahrál, efekt je prázdný / slabý fallback
- BUG: pokud zahraji v online i offline hře kartu Klon nebo zrcadlo, který naklonoval kartu Chaotický mudrc (či jinou rozhodovací kartu), tak jen zaplatím zdroje a nic se nestane.
- BUG: Pokud mám v ruce Shapeshifter a zahraji "Krádež identity", tak se soupeři nedá do ruky karta Shapeshifter, ale karta, která byla "shapeshiftnuta" a dále se nemění jako shapeshifter.
- BUG: Pokud mám v ruce kartu zrcadlo, tak má správně modrý rámeček. Pokud se ale transformovala třeba na útočnou kartu s červeným rámečkem, kartu zrcadlo zahraji, tak má špatně 2 věci. Má červený rámeček a text zrcadla, místo zrcadlené karty. (online)
- BUG: Pokud zahraji kartu, která byla zlevněna, klon toto nerespektuje a je stále dražší o 1 než originál (online).
- BUG: Na zrcadlu není "combo" efekt vidět (ten blesk) - ale combu funguje (Online)
- BUG: Zákeřný špeh - po zahrání ukradne kartu, poté spustí výběr pro ukradení karty. Takže vlastně ukradne 2 karty (zaregistrováno v online)
- BUG: Protihráč zahraje jakoukoliv kartu. Já líznu kartu zrcadlo. V tuto chvíli bych měl mít kartu Zrcadlo v základním provedení, ale už zrcadlí kartu, co zahrál protihráč v minulém kole.
- 
8) Anulace tahu (chaos, legendary)
   - Zruší všechny karty, které soupeř zahrál v předchozím kole
   - Soupeři se NEVRACÍ suroviny zaplacené za karty
   - Efekty se anulují takto:
     · Útok na hrad/hradby/hráče → vrátí se ztracené životy
     · Stavba hradu/hradeb → odeberou se přidané HP
     · Generování zdrojů → odeberou se vygenerované suroviny
     · Stavba dolu → důl se odebere
   - Potřeba: ukládat snapshot stavu před každým tahem soupeře (efekty karet, ne suroviny)

9) Combo mechanika - za každou další kartu typu X se stane něco (lízní kartu, dostaň suroviny, hrad, hradby, atd.)
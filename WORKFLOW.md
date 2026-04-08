Vytvoř datový model pro Android karetní hru v Kotlinu.

Kontext hry:
- Dva hráči (člověk vs AI), každý má hrad s HP (0–100) a hradby s HP (0–100)
- Počáteční hodnoty: hrad=25, hradby=10 (oboje)
- 3 zdroje za kolo: útok, kameny, magie (každý začíná na 0, generují se kartami)
- Hráč táhne 5 karet z balíku na začátku kola

Karta (Card) má:
- id: String
- name: String
- description: String
- cost: Int (cena v magii – magie je univerzální měna pro zahrání)
- effects: List<CardEffect>

CardEffect je sealed class s těmito podtypy:
- AddResource(type: ResourceType, amount: Int)
- BuildWall(amount: Int)
- BuildCastle(amount: Int)  
- AttackWall(amount: Int)
- AttackCastle(amount: Int)
- ConditionalEffect(condition: Condition, effect: CardEffect)

Condition je sealed class:
- ResourceAbove(type: ResourceType, threshold: Int)
- WallAbove(threshold: Int)
- WallBelow(threshold: Int)
- CastleAbove(threshold: Int)

ResourceType: MAGIC, ATTACK, STONES

GameState obsahuje:
- playerState: PlayerState
- aiState: PlayerState
- currentTurn: Int
- activePlayer: ActivePlayer (PLAYER / AI)

PlayerState obsahuje:
- castleHp: Int (0–100)
- wallHp: Int (0–100)  
- resources: Map<ResourceType, Int>
- deck: List<Card> (zamíchaný balík)
- hand: List<Card> (max 5 karet)
- discardPile: List<Card>

Podmínky konce hry (enum GameResult):
- PLAYER_CASTLE_DESTROYED
- AI_CASTLE_DESTROYED  
- PLAYER_CASTLE_BUILT (hráčův hrad dosáhl 100)
- AI_CASTLE_BUILT
- DECK_EXHAUSTED (alternativní konec – rozhodne stav hradů)

Přidej funkci checkWinCondition(state: GameState): GameResult? která vrátí výsledek nebo null pokud hra pokračuje.

Vše jako čisté Kotlin data classes a sealed classes, bez Android závislostí.
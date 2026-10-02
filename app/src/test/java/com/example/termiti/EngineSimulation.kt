package com.example.termiti

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Simulace AI vs AI nad SKUTEČNÝM herním enginem – protějšek simulátoru
 * v deckbuilder.html, který má pravidla i AI přepsané v JavaScriptu.
 *
 * Z herního kódu se bere: karty (cards.json přes CardRepository), efekty
 * (applyEffects), rozhodování AI (aiChooseAction), stav hráče a lízání
 * (PlayerState), vyhodnocení výhry (GameState), rozhodovací karty
 * (decisionOptions / scoreCardForSituation) a presety (presetDecks).
 *
 * Vlastní je jen orchestrace tahu: port AI větve GameViewModel.finishTurn bez UI,
 * zvuků, logu a pauz. Obě strany hrají jako AI. Při změně pravidel tahu
 * v GameViewModel (pořadí platby, combo, zahození, rozhodovací karty) je
 * potřeba [playTurn] srovnat.
 *
 * Start odpovídá hře s vlastním balíčkem (constructed): hrad 50, hradby 15 se stropem 40,
 * cíl 100 (CONSTRUCTED_* v PlayerState.kt), ruka 4, max. ruka 7, náhodně kdo začíná.
 * Náhodné módy (randomModeCards / randomModeRanking) hrají jako ve hře 30 / 70. BEZ pasivních schopností –
 * ve hře dostane AI 2 náhodné, tady by jen přidaly šum, který HTML simulátor nemá.
 *
 * Normálně se přeskakuje (trvá desítky sekund). Spuštění:
 *   ENGINE_SIM=1 ./gradlew testDebugUnitTest --tests "*EngineSimulation*"
 * Volitelně ENGINE_SIM_GAMES=<her na dvojici> (výchozí 400).
 * Výsledek: stdout testu + app/build/engine-sim.txt
 */
class EngineSimulation {

    // Pravidla hry – výchozí = constructed ve hře; přepsatelné pro what-if simulace
    /** Cíl výhry stavbou v constructed (ENGINE_SIM_WIN, výchozí CONSTRUCTED_WIN_TARGET). Hrad má strop MAX_CASTLE. */
    private val winTarget = System.getenv("ENGINE_SIM_WIN")?.toIntOrNull() ?: CONSTRUCTED_WIN_TARGET
    /** Strop hradeb v constructed (ENGINE_SIM_MAXWALL, výchozí CONSTRUCTED_MAX_WALL). Náhodné módy mají MAX_WALL. */
    private val maxWall   = System.getenv("ENGINE_SIM_MAXWALL")?.toIntOrNull() ?: CONSTRUCTED_MAX_WALL
    /** Start hradu v constructed (ENGINE_SIM_START, výchozí CONSTRUCTED_START_CASTLE). Náhodné módy mají vlastních 30 / 70. */
    private val startCastleConstructed = System.getenv("ENGINE_SIM_START")?.toIntOrNull() ?: CONSTRUCTED_START_CASTLE
    /** Start hradeb (ENGINE_SIM_STARTWALL, výchozí 15). */
    private val startWall = System.getenv("ENGINE_SIM_STARTWALL")?.toIntOrNull() ?: 15
    private val maxHand   = 7

    private val allCards: List<Card> by lazy {
        val f = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json")
            .map(::File).first { it.exists() }
        CardRepository.parseCardsJson(f.readText())
    }
    private val byId by lazy { allCards.associateBy { it.id } }

    /** Jak hra skončila, z pohledu balíčku A (GameState.playerState). */
    private class Outcome(val result: GameResult, val rounds: Int, val finisher: String?,
                          val track: Track = Track())

    /** Sledovaná karta v jedné hře: kolo prvního zahrání (0 = nezahrána) a počet zahození, [0] = A, [1] = B. */
    private class Track {
        val playedRound = IntArray(2)
        val discarded = IntArray(2)
        /** Hrad A, hrad B, hradby A, hradby B na konci každého dokončeného kola. */
        val castles = mutableListOf<IntArray>()
    }

    // ── Háčky pro varianty karet (viz cardVariants) – nastavují se před během, během hry jen čtou ──
    /** Náhrada karty podle id (jiná cena / efekty). Platí i pro karty vytvořené za hry (AddCardsToDeck…). */
    @Volatile private var overrides: Map<String, Card> = emptyMap()
        set(value) { field = value; effectivePool = if (value.isEmpty()) null else allCards.map { value[it.id] ?: it } }
    /** allCards s [overrides] – šablony pro efekty, které vytváří karty (jinak by vznikla původní verze). */
    @Volatile private var effectivePool: List<Card>? = null
    private val pool: List<Card> get() = effectivePool ?: allCards
    /**
     * Náhrada ConvertWallToCastle (Pohlcení hradeb). AI kartu boduje jako originál,
     * jen efekt při zahrání je jiný – převod s limitem se ze stávajících efektů
     * složit nedá (BuildWall(-N) by AI odradil a kartu by skoro nehrála).
     */
    @Volatile private var wallConvert: ((PlayerState) -> Unit)? = null
    /** Karta, jejíž zahrání/zahození se počítá (Outcome.track) – null = nesleduje se. */
    @Volatile private var trackId: String? = null
    /** Úprava balíčků před hrou (např. výměna karty za jinou ve všech presetech). */
    @Volatile private var deckEdit: (Map<String, Int>) -> Map<String, Int> = { it }

    /**
     * Presety ze hry + volitelně balíček navíc pro testování (ENGINE_SIM_EXTRA_DECK="Název=id:n,id:n,…").
     * Balíček navíc hraje round robin spolu s presety, ve hře se nemění nic.
     */
    private val presetDecks: List<Pair<String, Map<String, Int>>> by lazy {
        val extra = System.getenv("ENGINE_SIM_EXTRA_DECK")?.let { spec ->
            val (name, list) = spec.split('=', limit = 2)
            listOf(name.trim() to list.split(',').associate { it.trim().split(':').let { (id, k) -> id to k.toInt() } })
        } ?: emptyList()
        PRESET_DECKS + extra
    }

    /** Prázdná karta pro ablaci – AI ji nezahraje (nic nedělá), jen zabírá místo v balíčku. */
    private val blank = Card(id = "BLANK", name = "(prázdná)", description = "", cost = 0,
                             costType = ResourceType.MAGIC, effects = emptyList())

    private fun newSide(counts: Map<String, Int>, startCastle: Int = 35, wallCap: Int = maxWall) =
        PlayerState(castleHP = startCastle, wallHP = minOf(startWall, wallCap), maxWall = wallCap).also { s ->
        s.deck.addAll(deckEdit(counts).flatMap { (id, n) ->
            List(n) { if (id == "BLANK") blank else overrides[id] ?: byId.getValue(id) }
        }.withUniqueIds())
        s.deck.shuffle()
        s.drawCards(4)
    }

    private fun playGame(deckA: Map<String, Int>, deckB: Map<String, Int>,
                         startCastle: Int = startCastleConstructed, target: Int = winTarget,
                         wallCap: Int = maxWall): Outcome {
        val a = newSide(deckA, startCastle, wallCap)
        val b = newSide(deckB, startCastle, wallCap)
        val gs = GameState(playerState = a, aiState = b, currentTurn = 1,
                           playerWinTarget = target, aiWinTarget = target)
        val aStarts = Random.nextBoolean()
        var aTurn = aStarts
        var first = true
        var waitedA = false
        var waitedB = false
        val track = Track()
        while (true) {
            val me  = if (aTurn) a else b
            val opp = if (aTurn) b else a

            // Začátek tahu: suroviny, líz (začínající hráč v 1. tahu nelíže)
            me.generateResources()
            if (!first && me.deck.isNotEmpty()) {
                val dr = me.drawCards(1, maxHand)
                if (dr.traps.isNotEmpty()) gs.checkWinCondition()?.let { return Outcome(it, gs.currentTurn, "past", track) }
            }
            first = false

            val oppWaited = if (aTurn) waitedB else waitedA
            // Bez ruky i balíčku nejde nic dělat → automatický pas (jako auto-pass ve hře)
            val turn = if (me.hand.isEmpty() && me.deck.isEmpty()) TurnEnd(waited = true)
                       else playTurn(me, opp, gs, oppWaited) { card, played ->
                           val side = if (aTurn) 0 else 1
                           if (card.baseId == trackId) {
                               if (played) { if (track.playedRound[side] == 0) track.playedRound[side] = gs.currentTurn }
                               else track.discarded[side]++
                           }
                       }
            turn.result?.let { return Outcome(it, gs.currentTurn, turn.finisher, track) }
            if (aTurn) waitedA = turn.waited else waitedB = turn.waited

            // Konec tahu: jednotahové efekty (ve hře se mažou na konci kola, platí jen
            // pro karty strany, která je nastavila – vychází to nastejno)
            me.drawCardOnPlay = null
            me.gainResourcePerCardPlayed.clear()
            me.gainCastlePerCardPlayed.clear()
            me.cloneNextPlayed = null
            me.attackCardsThisTurn = 0
            me.nextCardIsCombo = false

            // Kolo končí tahem druhého hráče
            if (aTurn != aStarts) { gs.currentTurn++; track.castles.add(intArrayOf(a.castleHP, b.castleHP, a.wallHP, b.wallHP)) }
            if (a.hand.isEmpty() && a.deck.isEmpty() && b.hand.isEmpty() && b.deck.isEmpty())
                return Outcome(gs.resolveByHp(), gs.currentTurn, null, track)
            gs.checkWinCondition()?.let { return Outcome(it, gs.currentTurn, null, track) }
            aTurn = !aTurn
        }
    }

    private class TurnEnd(val waited: Boolean = false, val result: GameResult? = null, val finisher: String? = null)

    /** Port AI tahu z GameViewModel.finishTurn (od transformShapeShifters po konec smyčky). */
    private fun playTurn(me: PlayerState, opp: PlayerState, gs: GameState, oppWaited: Boolean,
                         onAction: (card: Card, played: Boolean) -> Unit = { _, _ -> }): TurnEnd {
        transformShapeShifters(me.hand, allCards)
        updateMirrorCards(me.hand, opp.lastPlayedCard, allCards)
        updateCloneCards(me.hand, me.lastPlayedCard, allCards)

        var pending: AiAction? = null
        var discardUsed = false
        while (true) {
            transformShapeShifters(me.hand, allCards, onlyNew = true)
            val choice = pending ?: aiChooseAction(me, opp, gs.playerWinTarget, gs.aiWinTarget,
                                                   canDiscard = !discardUsed, playerWaited = oppWaited)
            pending = null
            when (choice) {
                is AiAction.Play -> {
                    val card = choice.card
                    onAction(card, true)
                    me.preCostResources = me.resources.toMap()
                    val xValue: Int
                    if (card.isXCost) {
                        xValue = me.resources[card.costType] ?: 0
                        me.resources[card.costType] = 0
                    } else {
                        xValue = 0
                        me.resources[card.costType] = (me.resources[card.costType] ?: 0) - card.effectiveCost
                    }
                    me.lastPlayedType = card.type
                    val comboBoost = me.nextCardIsCombo
                    if (comboBoost) me.nextCardIsCombo = false
                    val cloneCount = me.cloneNextPlayed
                    if (cloneCount != null && cloneCount > 0) {
                        repeat(cloneCount) {
                            me.deck.add(card.copy(id = "${card.id}_clone_${java.util.UUID.randomUUID()}", isGenerated = true))
                        }
                        me.deck.shuffle()
                        me.cloneNextPlayed = null
                    }
                    val drawFilter = me.drawCardOnPlay
                    if (drawFilter != null && (drawFilter.isEmpty() || drawFilter == card.type)) me.drawCards(1)
                    for (grp in me.gainResourcePerCardPlayed) {
                        if (grp.cardType == null || grp.cardType == card.type)
                            me.resources[grp.type] = ((me.resources[grp.type] ?: 0) + grp.amount).coerceAtMost(MAX_RESOURCE)
                    }
                    for (gcpp in me.gainCastlePerCardPlayed) {
                        if (gcpp.cardType == null || gcpp.cardType == card.type)
                            me.castleHP = (me.castleHP + gcpp.amount).coerceAtMost(100)
                    }
                    me.hand.remove(card)
                    me.discardPile.add(card)
                    val convert = wallConvert
                    if (convert != null && card.effects.any { it is CardEffect.ConvertWallToCastle }) convert(me)
                    else applyEffects(
                        card.effects, me, opp, pool, xValue = xValue,
                        onDrawCard = { state, count -> repeat(count) { state.drawCards(1, maxHand) } },
                        maxHandSize = maxHand,
                        opponentMaxHandSize = maxHand
                    )
                    resolveDecisions(card, me, opp, gs.playerWinTarget)
                    me.preCostResources = null
                    if (card.costType == ResourceType.ATTACK) me.attackCardsThisTurn++
                    val prevLast = me.lastPlayedCard
                    me.lastPlayedCard = card
                    if (card.effects.any { it is CardEffect.Mirror } && opp.lastPlayedCard != null) {
                        me.lastPlayedCard = opp.lastPlayedCard
                    } else if (card.effects.any { it is CardEffect.Clone } && prevLast != null) {
                        me.lastPlayedCard = prevLast
                    }
                    resetMirrorCloneInPile(me.discardPile, card, allCards)
                    updateCloneCards(me.hand, me.lastPlayedCard, allCards)
                    updateMirrorCards(opp.hand, me.lastPlayedCard, allCards)

                    gs.checkWinCondition()?.let { return TurnEnd(result = it, finisher = card.name) }
                    if (card.isCombo || comboBoost) {
                        pending = aiChooseAction(me, opp, gs.playerWinTarget, gs.aiWinTarget,
                                                 canDiscard = !discardUsed, playerWaited = oppWaited)
                    } else {
                        return TurnEnd()
                    }
                }
                is AiAction.Wait -> {
                    if (oppWaited && me.deck.isEmpty() && opp.deck.isEmpty())
                        return TurnEnd(waited = true, result = gs.resolveByHp())
                    return TurnEnd(waited = true)
                }
                is AiAction.Discard -> {
                    val card = choice.card
                    onAction(card, false)
                    me.hand.remove(card)
                    me.discardPile.add(card)
                    if (card.discardEffects.isNotEmpty()) {
                        applyEffects(card.discardEffects, me, opp, pool,
                                     maxHandSize = maxHand, opponentMaxHandSize = maxHand)
                        gs.checkWinCondition()?.let { return TurnEnd(result = it, finisher = card.name + " (zahození)") }
                    }
                    discardUsed = true
                }
            }
        }
    }

    /** AI volba u rozhodovacích karet – port stejného bloku z GameViewModel.finishTurn. */
    private fun resolveDecisions(card: Card, me: PlayerState, opp: PlayerState, target: Int) {
        fun isDecision(fx: CardEffect) =
            fx is CardEffect.DecisionBurnOpponent || fx is CardEffect.DecisionChooseType  ||
            fx is CardEffect.DecisionFromDiscard  || fx is CardEffect.DecisionFromDeck    ||
            fx is CardEffect.DecisionDrawFromDeck || fx is CardEffect.DecisionMine        ||
            fx is CardEffect.SmartJoker           || fx is CardEffect.PeekAndStealHand   ||
            fx is CardEffect.DecisionChooseResource
        val copied: List<CardEffect> = when {
            card.effects.any { it is CardEffect.Mirror } -> opp.lastPlayedCard?.effects?.filter(::isDecision) ?: emptyList()
            card.effects.any { it is CardEffect.Clone }  -> me.lastPlayedCard?.effects?.filter(::isDecision) ?: emptyList()
            else -> emptyList()
        }
        fun options(fx: CardEffect, excludeId: String? = null) =
            decisionOptions(fx, me, opp, allCards, excludeId, target) { _, _ -> error("DecisionChooseResource AI nevolá") }
        for (fx in card.effects + copied) when (fx) {
            is CardEffect.DecisionBurnOpponent -> options(fx).firstOrNull()?.let { opp.deck.remove(it); opp.discardPile.add(it) }
            is CardEffect.DecisionChooseType   -> options(fx).firstOrNull()?.let { chosen ->
                val c = chosen.copy(id = "${chosen.id}_${java.util.UUID.randomUUID()}", isGenerated = true,
                                    costModifier = -fx.costReduction)
                if (me.hand.size < maxHand) me.hand.add(c)
            }
            is CardEffect.DecisionFromDiscard  -> options(fx, excludeId = card.id).firstOrNull()?.let {
                me.discardPile.remove(it); if (me.hand.size < maxHand) me.hand.add(it)
            }
            is CardEffect.DecisionFromDeck     -> options(fx).firstOrNull()?.let {
                if (me.hand.size < maxHand) me.hand.add(it.copy(id = "${it.id}_${java.util.UUID.randomUUID()}", isGenerated = true))
            }
            is CardEffect.DecisionDrawFromDeck -> options(fx).firstOrNull()?.let {
                me.deck.remove(it); if (me.hand.size < maxHand) me.hand.add(it)
            }
            is CardEffect.DecisionMine -> options(fx).minByOrNull { c ->
                me.mines[c.effects.filterIsInstance<CardEffect.AddMine>().firstOrNull()?.type] ?: 0
            }?.let { if (me.hand.size < maxHand) me.hand.add(it.copy(id = "${it.id}_${java.util.UUID.randomUUID()}", isGenerated = true)) }
            is CardEffect.SmartJoker -> options(fx).maxByOrNull { scoreCardForSituation(it, me, opp, target) }?.let {
                if (me.hand.size < maxHand) me.hand.add(it.copy(id = "${it.id}_${java.util.UUID.randomUUID()}", isGenerated = true))
            }
            is CardEffect.PeekAndStealHand -> options(fx).maxByOrNull { scoreCardForSituation(it, me, opp, target) }?.let {
                opp.hand.remove(it)
                if (me.hand.size < maxHand) me.hand.add(it.copy(id = "${it.id}_stolen_${java.util.UUID.randomUUID()}", isGenerated = true))
            }
            is CardEffect.DecisionChooseResource -> fx.options.minByOrNull { me.resources[it.type] ?: 0 }?.let {
                me.resources[it.type] = ((me.resources[it.type] ?: 0) + it.amount).coerceAtMost(MAX_RESOURCE)
            }
            else -> {}
        }
    }

    // ── Statistiky ───────────────────────────────────────────────────────────

    private class DeckStats {
        var games = 0; var points = 0.0; var wins = 0
        var winDestroy = 0; var winBuild = 0; var winHp = 0
        var roundsOfWins = 0
        val finishers = mutableMapOf<String, Int>()
    }

    private fun isWinA(r: GameResult) = r == GameResult.AI_CASTLE_DESTROYED || r == GameResult.PLAYER_CASTLE_BUILT ||
        r == GameResult.PLAYER_HP_WINS || r == GameResult.PLAYER_HP_WINS_TURN_LIMIT
    private fun isWinB(r: GameResult) = r == GameResult.PLAYER_CASTLE_DESTROYED || r == GameResult.AI_CASTLE_BUILT ||
        r == GameResult.AI_HP_WINS || r == GameResult.AI_HP_WINS_TURN_LIMIT

    private fun record(st: DeckStats, o: Outcome, asA: Boolean) {
        st.games++
        val won  = if (asA) isWinA(o.result) else isWinB(o.result)
        val lost = if (asA) isWinB(o.result) else isWinA(o.result)
        st.points += if (won) 1.0 else if (lost) 0.0 else 0.5
        if (!won) return
        st.wins++; st.roundsOfWins += o.rounds
        when (o.result) {
            GameResult.AI_CASTLE_DESTROYED, GameResult.PLAYER_CASTLE_DESTROYED -> st.winDestroy++
            GameResult.PLAYER_CASTLE_BUILT, GameResult.AI_CASTLE_BUILT         -> st.winBuild++
            else                                                               -> st.winHp++
        }
        o.finisher?.let { st.finishers[it] = (st.finishers[it] ?: 0) + 1 }
    }

    /**
     * Ablace: skupina karet z balíčku [ENGINE_SIM_DECK] (výchozí AI Chaos) se nahradí
     * prázdnou kartou a měří se pokles průměrné úspěšnosti proti ostatním presetům.
     * Skupiny: ENGINE_SIM_ABLATION="název=id,id,id;název=id,…" (id lze opakovat).
     */
    @Test
    fun ablation() {
        val spec = System.getenv("ENGINE_SIM_ABLATION")
        assumeTrue("Ablace se spouští jen s ENGINE_SIM_ABLATION", spec != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 1000
        val deckName = System.getenv("ENGINE_SIM_DECK") ?: "🌀 AI Chaos"
        val base = presetDecks.first { it.first == deckName }.second
        val others = presetDecks.filter { it.first != deckName }
        fun avg(deck: Map<String, Int>): Double = others.sumOf { (_, opp) ->
            var pts = 0.0
            repeat(n) {
                val o = playGame(deck, opp)
                pts += if (isWinA(o.result)) 1.0 else if (isWinB(o.result)) 0.0 else 0.5
            }
            100 * pts / n
        } / others.size

        val out = StringBuilder()
        val baseAvg = avg(base)
        out.appendLine("Ablace $deckName, $n her proti každému z ${others.size} presetů")
        out.appendLine(String.format("  %-34s %5.1f %%", "beze změny", baseAvg))
        for (group in spec!!.split(';').filter { it.isNotBlank() }) {
            val (label, ids) = group.split('=', limit = 2)
            val deck = base.toMutableMap()
            var removed = 0
            for (id in ids.split(',').map { it.trim() }) {
                val left = deck[id] ?: error("$id v balíčku $deckName není (nebo už je celý pryč)")
                if (left == 1) deck.remove(id) else deck[id] = left - 1
                deck["BLANK"] = (deck["BLANK"] ?: 0) + 1
                removed++
            }
            val a = avg(deck)
            out.appendLine(String.format("  %-34s %5.1f %%  (%+.1f, %d karet, %+.1f na kartu)",
                label, a, a - baseAvg, removed, (a - baseAvg) / removed))
        }
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-ablation.txt").writeText(out.toString()) }
    }

    /** Sledovaná karta za celý round robin, po balíčcích. */
    private class TrackStats { var games = 0; var played = 0; var roundSum = 0; var discarded = 0 }
    private var lastTrack: Map<String, TrackStats> = emptyMap()

    /** Průměrná úspěšnost každého presetu proti ostatním; dvojice běží paralelně. */
    private fun roundRobinAvg(n: Int): Map<String, Double> {
        val presets = presetDecks
        val pairs = presets.indices.flatMap { i -> (i + 1 until presets.size).map { j -> i to j } }
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        try {
            val tracks = presets.associate { it.first to TrackStats() }
            val scores = pairs.map { (i, j) ->
                pool.submit<Double> {
                    var pts = 0.0
                    val ti = TrackStats(); val tj = TrackStats()
                    repeat(n) {
                        val o = playGame(presets[i].second, presets[j].second)
                        pts += if (isWinA(o.result)) 1.0 else if (isWinB(o.result)) 0.0 else 0.5
                        for ((t, side) in listOf(ti to 0, tj to 1)) {
                            t.games++
                            val r = o.track.playedRound[side]
                            if (r > 0) { t.played++; t.roundSum += r }
                            t.discarded += o.track.discarded[side]
                        }
                    }
                    synchronized(tracks) {
                        for ((t, idx) in listOf(ti to i, tj to j)) tracks.getValue(presets[idx].first).apply {
                            games += t.games; played += t.played; roundSum += t.roundSum; discarded += t.discarded
                        }
                    }
                    pts / n
                }
            }.map { it.get() }
            lastTrack = tracks
            val tot = DoubleArray(presets.size)
            pairs.forEachIndexed { k, (i, j) -> tot[i] += scores[k]; tot[j] += 1 - scores[k] }
            return presets.indices.associate { presets[it].first to 100 * tot[it] / (presets.size - 1) }
        } finally { pool.shutdown() }
    }

    private class Variant(
        val name: String,
        val cards: List<Card> = emptyList(),
        val wall: ((PlayerState) -> Unit)? = null,
        val deck: ((Map<String, Int>) -> Map<String, Int>)? = null
    )

    private fun capConvert(cap: Int): (PlayerState) -> Unit = { s ->
        val m = minOf(s.wallHP, cap)
        s.castleHP = (s.castleHP + m).coerceAtMost(100)
        s.wallHP -= m
    }

    /**
     * Varianty Krvavého úderu (100), Daně z chaosu (C44) a Pohlcení hradeb (139) –
     * stejná sada jako v HTML simulátoru, round robin všech presetů pro každou.
     * Spuštění: ENGINE_SIM_VARIANTS=1 (volitelně ENGINE_SIM_GAMES, výchozí 1500).
     */
    @Test
    fun cardVariants() {
        assumeTrue("Varianty se spouští jen s ENGINE_SIM_VARIANTS=1", System.getenv("ENGINE_SIM_VARIANTS") != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 1500
        val k = byId.getValue("100"); val d = byId.getValue("C44"); val p = byId.getValue("139")
        val chaosLead = Condition.ResourceMoreThanOpponent(ResourceType.CHAOS)
        fun dan(dmg: Int, steal: Int) = d.copy(effects = listOf(
            CardEffect.AttackCastle(dmg), CardEffect.ConditionalEffect(chaosLead, CardEffect.StealCastle(steal))))
        fun krv(ap: Int, sc: Int) = k.copy(effects = listOf(CardEffect.AttackPlayer(ap), CardEffect.StealCastle(sc)))
        val variants = listOf(
            Variant("výchozí"),
            Variant("K: krádež 6→4",        listOf(krv(5, 4))),
            Variant("K: cena 6→7",          listOf(k.copy(cost = 7))),
            Variant("K: útok 8 + krádež 3", listOf(krv(8, 3))),
            Variant("K: útok 4 + krádež 4", listOf(krv(4, 4))),
            Variant("K → Dělostřelectvo", deck = { c ->
                val n100 = c["100"] ?: 0
                if (n100 == 0) c else (c - "100") + ("021" to (c["021"] ?: 0) + n100)
            }),
            Variant("D: poškození 7→6",     listOf(dan(6, 4))),
            Variant("D: krádež 4→2",        listOf(dan(7, 2))),
            Variant("D: cena 4→5",          listOf(d.copy(cost = 5))),
            Variant("P: max 20",            wall = capConvert(20)),
            Variant("P: max 25",            wall = capConvert(25)),
            Variant("P: polovina zdi",      wall = { s -> s.castleHP = (s.castleHP + (s.wallHP + 1) / 2).coerceAtMost(100); s.wallHP = 0 }),
            Variant("P: cena 8→11",         listOf(p.copy(cost = 11))),
            Variant("K krádež 4 + P max 25", listOf(krv(5, 4)), wall = capConvert(25)),
        )
        val results = LinkedHashMap<String, Map<String, Double>>()
        val t0 = System.currentTimeMillis()
        for (v in variants) {
            overrides = v.cards.associateBy { it.id }
            wallConvert = v.wall
            deckEdit = v.deck ?: { it }
            results[v.name] = roundRobinAvg(n)
            println("hotovo: ${v.name} (${(System.currentTimeMillis() - t0) / 1000} s)")
        }
        overrides = emptyMap(); wallConvert = null; deckEdit = { it }

        val decks = presetDecks.map { it.first }
        val base = results.getValue("výchozí")
        val out = StringBuilder()
        out.appendLine("Herní engine – varianty karet, round robin ${decks.size} presetů, $n her na dvojici " +
                       "(${(System.currentTimeMillis() - t0) / 1000} s). Hodnoty = průměrná úspěšnost v %, v závorce rozdíl proti výchozí.")
        out.appendLine()
        out.appendLine(String.format("%-24s", "varianta") + decks.joinToString("") { String.format("%16s", it.take(14)) })
        for ((name, r) in results) {
            out.appendLine(String.format("%-24s", name) + decks.joinToString("") { dk ->
                val v = r.getValue(dk)
                if (name == "výchozí") String.format("%16.1f", v) else String.format("%9.1f (%+5.1f)", v, v - base.getValue(dk))
            })
        }
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-variants.txt").writeText(out.toString()) }
    }

    /**
     * Pohlcení hradeb (139) za vyšší cenu: jak se změní úspěšnost balíčků a jak často ho
     * Obránce (hodně kamenných dolů) a AI Chaos (hybrid) stihnou zahrát.
     * Spuštění: ENGINE_SIM_POHLCENI=1 (volitelně ENGINE_SIM_GAMES, výchozí 2000).
     */
    @Test
    fun pohlceniCost() {
        assumeTrue("Spouští se jen s ENGINE_SIM_POHLCENI=1", System.getenv("ENGINE_SIM_POHLCENI") != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 2000
        val p = byId.getValue("139")
        val k4 = byId.getValue("100").copy(effects = listOf(CardEffect.AttackPlayer(5), CardEffect.StealCastle(4)))
        val runs = listOf(8, 11, 13, 15, 16).map { "cena $it" to listOf(p.copy(cost = it)) } +
                   listOf(15, 16).map { "cena $it + Krvavý úder krádež 4" to listOf(p.copy(cost = it), k4) }
        val decks = presetDecks.map { it.first }
        val users = presetDecks.filter { (it.second["139"] ?: 0) > 0 }.map { it.first }
        val t0 = System.currentTimeMillis()
        var base: Map<String, Double>? = null
        val rows = mutableListOf<String>()
        val trackRows = mutableListOf<String>()
        trackId = "139"
        for ((name, cards) in runs) {
            overrides = cards.associateBy { it.id }
            val r = roundRobinAvg(n)
            val b = base ?: r.also { base = it }
            rows += String.format("%-34s", name) + decks.joinToString("") { dk ->
                val v = r.getValue(dk)
                if (r === b) String.format("%16.1f", v) else String.format("%9.1f (%+5.1f)", v, v - b.getValue(dk))
            }
            trackRows += String.format("%-34s", name) + users.joinToString("") { dk ->
                val t = lastTrack.getValue(dk)
                String.format("   %-14s zahráno %5.1f %% her, v kole %4.1f, zahozeno %4.1f %% her",
                    dk.take(14), 100.0 * t.played / t.games, t.roundSum.toDouble() / t.played.coerceAtLeast(1),
                    100.0 * t.discarded / t.games)
            }
            println("hotovo: $name (${(System.currentTimeMillis() - t0) / 1000} s)")
        }
        overrides = emptyMap(); trackId = null
        val out = StringBuilder()
        out.appendLine("Herní engine – Pohlcení hradeb podle ceny, $n her na dvojici (${(System.currentTimeMillis() - t0) / 1000} s)")
        out.appendLine("Průměrná úspěšnost v %, v závorce rozdíl proti ceně 8:")
        out.appendLine(String.format("%-34s", "") + decks.joinToString("") { String.format("%16s", it.take(14)) })
        rows.forEach(out::appendLine)
        out.appendLine()
        out.appendLine("Jak balíčky s Pohlcením kartu využijí:")
        trackRows.forEach(out::appendLine)
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-pohlceni.txt").writeText(out.toString()) }
    }

    /**
     * Obecné varianty cen a jednoduchých efektů bez úprav kódu:
     *   ENGINE_SIM_COSTS="C35=3;C35=4"         – každá varianta zvlášť (proti výchozímu stavu)
     *   ENGINE_SIM_COSTS="C35=3,100=7"         – víc změn v jedné variantě
     *   ENGINE_SIM_COSTS="100=fx:AP7+SC4"      – nové efekty (cena zůstává)
     *   ENGINE_SIM_COSTS="100=6:AP7+SC4"       – cena i efekty
     *   ENGINE_SIM_COSTS="C25=nocombo"          – bez combo (combo = naopak přidat)
     *   ENGINE_SIM_COSTS="C25>C26"              – výměna karty ve všech presetech (i s dalšími změnami)
     *   ENGINE_SIM_COSTS="C23=fx:DECK.C34.3"    – karty do balíčku (OPP.<id>.<n> = do soupeřova)
     * Kódy efektů viz [fxFromCode]. U karet z první varianty se sleduje, jak často je
     * balíčky zahrají. Volitelně ENGINE_SIM_GAMES (výchozí 2000).
     */
    /** Karta ze zápisu "id" nebo "id=cena:combo:efekty" (viz costVariants); "BLANK" = prázdná. */
    private fun cardFromSpec(spec: String): Card {
        if (spec == "BLANK") return blank
        val (id, value) = spec.split('=', limit = 2).let { it[0].trim() to it.getOrNull(1) }
        var card = byId.getValue(id)
        for (part in (value ?: "").split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "fx" }) {
            card = when {
                part.toIntOrNull() != null -> card.copy(cost = part.toInt())
                part == "combo"            -> card.copy(isCombo = true)
                part == "nocombo"          -> card.copy(isCombo = false)
                else                       -> card.copy(effects = part.split('+').map(::fxFromCode))
            }
        }
        return card
    }

    /**
     * Hodnota karty v NÁHODNÝCH módech (párové měření). Pro každou hru se vygeneruje
     * balíček daného módu a [copies] jeho karet se nahradí: hráč A dostane kartu X,
     * hráč B kartu Y, jinak mají oba stejný balíček (supernáhodný ho ve hře taky sdílí).
     * Výhry A − 50 % ≈ o kolik je X lepší než Y.
     *
     *   ENGINE_SIM_RANDOM="popis=X|Y;popis2=X|Y"   X, Y = "id", "id=cena:combo:efekty" nebo "BLANK"
     *   ENGINE_SIM_MODE=super (výchozí, 50 karet sdílených, hrad 30) | balanced (30 karet, každý vlastní, hrad 30)
     *   ENGINE_SIM_GAMES (výchozí 20000 her na řádek), ENGINE_SIM_COPIES (výchozí 2)
     */
    @Test
    fun randomModeCards() {
        val spec = System.getenv("ENGINE_SIM_RANDOM")
        assumeTrue("Spouští se jen s ENGINE_SIM_RANDOM", spec != null)
        val n      = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 20000
        val copies = System.getenv("ENGINE_SIM_COPIES")?.toIntOrNull() ?: 2
        val mode   = System.getenv("ENGINE_SIM_MODE") ?: "super"
        val out = StringBuilder()
        out.appendLine("Herní engine – hodnota karty v módu '$mode', $n her na řádek, $copies kopie nahrazeny " +
                       "(hrad 30). Výhry hráče s kartou X proti hráči s kartou Y, jinak stejný balíček.")
        val t0 = System.currentTimeMillis()
        for (row in spec!!.split(';').filter { it.isNotBlank() }) {
            val (label, pair) = row.split('=', limit = 2)
            val (xs, ys) = pair.split('|').map { it.trim() }
            // Upravená karta dostane syntetické id, ať X a Y mohou být tatáž karta s jinými
            // efekty. Neupravená drží skutečné id – Shapeshifter se pozná podle "C34_".
            val xId = if ('=' in xs) "XSIM" else xs
            val yId = if ('=' in ys) "YSIM" else ys
            overrides = buildMap {
                if (xId == "XSIM") put(xId, cardFromSpec(xs).copy(id = xId))
                if (yId == "YSIM") put(yId, cardFromSpec(ys).copy(id = yId))
            }
            val pct = pairedWinPct(xId, yId, n, mode, copies)
            val se = 100 * Math.sqrt(0.25 / n)
            out.appendLine(String.format("  %-46s X %5.1f %%  (%+.1f ± %.1f)", label, pct, pct - 50, 2 * se))
            println("hotovo: $label (${(System.currentTimeMillis() - t0) / 1000} s)")
        }
        overrides = emptyMap()
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-random.txt").writeText(out.toString()) }
    }

    /**
     * Párové měření v náhodném módu: v každé hře se vygeneruje balíček módu a [copies]
     * náhodných karet se nahradí – hráč A dostane [xId], hráč B [yId], jinak stejný balíček
     * (super = sdílený, balanced = každý vlastní). Vrátí výhry A v %. Paralelně.
     */
    private fun pairedWinPct(xId: String, yId: String, n: Int, mode: String, copies: Int): Double {
        fun replace(deck: Map<String, Int>, slots: List<String>, with: String): Map<String, Int> {
            val d = deck.toMutableMap()
            for (id in slots) { val k = d.getValue(id); if (k == 1) d.remove(id) else d[id] = k - 1 }
            d[with] = (d[with] ?: 0) + copies
            return d
        }
        val threads = Runtime.getRuntime().availableProcessors()
        val pool = Executors.newFixedThreadPool(threads)
        val pts = try {
            (0 until threads).map { t ->
                pool.submit<Double> {
                    var p = 0.0
                    repeat(n / threads + if (t < n % threads) 1 else 0) {
                        val baseA = if (mode == "balanced") buildBalancedDeck(allCards) else buildSuperRandomDeck(allCards)
                        val baseB = if (mode == "balanced") buildBalancedDeck(allCards) else baseA
                        // Nahrazuje se náhodná instance karty (vážená počtem kopií)
                        fun slots(d: Map<String, Int>) = d.flatMap { (id, k) -> List(k) { id } }.shuffled().take(copies)
                        val o = playGame(replace(baseA, slots(baseA), xId), replace(baseB, slots(baseB), yId), startCastle = 30, target = 70, wallCap = MAX_WALL)
                        p += if (isWinA(o.result)) 1.0 else if (isWinB(o.result)) 0.0 else 0.5
                    }
                    p
                }
            }.sumOf { it.get() }
        } finally { pool.shutdown() }
        return 100 * pts / n
    }

    /**
     * Žebříček všech karet v náhodném módu: každá karta (2 kopie) proti prázdné kartě,
     * párově jako [randomModeCards]. Průměrná karta (Shapeshifter) vychází kolem +8.
     *   ENGINE_SIM_RANK=1, ENGINE_SIM_MODE=super|balanced, ENGINE_SIM_GAMES (výchozí 4000 na kartu)
     * Výsledek: app/build/engine-rank-<mode>.txt (seřazeno) a .csv
     */
    @Test
    fun randomModeRanking() {
        assumeTrue("Spouští se jen s ENGINE_SIM_RANK=1", System.getenv("ENGINE_SIM_RANK") != null)
        val n    = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 4000
        val mode = System.getenv("ENGINE_SIM_MODE") ?: "super"
        val cards = allCards.filter { !it.isPlaceholder && !it.id.startsWith("T") }
        val ci = 2 * 100 * Math.sqrt(0.25 / n)
        val t0 = System.currentTimeMillis()
        val rows = cards.mapIndexed { i, c ->
            val v = pairedWinPct(c.id, "BLANK", n, mode, 2) - 50
            if (i % 10 == 9) println("hotovo ${i + 1}/${cards.size} (${(System.currentTimeMillis() - t0) / 1000} s)")
            c to v
        }.sortedByDescending { it.second }
        val out = StringBuilder()
        out.appendLine("Herní engine – žebříček karet v módu '$mode': 2 kopie karty proti 2 prázdným, $n her na kartu, " +
                       "±${"%.1f".format(ci)} (95 %). ${(System.currentTimeMillis() - t0) / 1000} s.")
        rows.forEachIndexed { i, (c, v) ->
            out.appendLine(String.format("%3d. %-4s %-24s %-6s %2d %-9s %+6.1f", i + 1, c.id, c.name,
                c.costType.name.take(6), c.cost, c.rarity.name, v))
        }
        println(out)
        File("build").takeIf { it.isDirectory }?.let { dir ->
            File(dir, "engine-rank-$mode.txt").writeText(out.toString())
            File(dir, "engine-rank-$mode.csv").writeText("id;name;costType;cost;rarity;value\n" +
                rows.joinToString("\n") { (c, v) -> "${c.id};${c.name};${c.costType};${c.cost};${c.rarity};${"%.2f".format(v)}" })
        }
    }

    /** Krátké kódy efektů pro ENGINE_SIM_COSTS (stejné zkratky jako v deckbuilder.html). */
    private fun fxFromCode(code: String): CardEffect {
        // Karty do balíčku: DECK.<id>.<počet> (vlastní), OPP.<id>.<počet> (soupeřův)
        if (code.startsWith("DECK.") || code.startsWith("OPP.")) {
            val (kind, id, n) = code.split('.')
            require(id in byId) { "Karta $id neexistuje" }
            return if (kind == "DECK") CardEffect.AddCardsToDeck(id, n.toInt())
                   else CardEffect.AddToOpponentDeck(id, n.toInt())
        }
        val t = code.takeWhile { it.isLetter() }
        val a = code.drop(t.length).toInt()
        return when (t) {
            "AP" -> CardEffect.AttackPlayer(a)
            "AC" -> CardEffect.AttackCastle(a)
            "AW" -> CardEffect.AttackWall(a)
            "SC" -> CardEffect.StealCastle(a)
            "BC" -> CardEffect.BuildCastle(a)
            "BW" -> CardEffect.BuildWall(a)
            // +N suroviny: ARM magie, ARA útok, ARS kámen, ARC chaos
            "ARM" -> CardEffect.AddResource(ResourceType.MAGIC, a)
            "ARA" -> CardEffect.AddResource(ResourceType.ATTACK, a)
            "ARS" -> CardEffect.AddResource(ResourceType.STONES, a)
            "ARC" -> CardEffect.AddResource(ResourceType.CHAOS, a)
            // +N suroviny až na začátku příštího tahu: ADM magie, ADA útok, ADS kámen, ADC chaos
            "ADM" -> CardEffect.AddResourceDelayed(ResourceType.MAGIC, a)
            "ADA" -> CardEffect.AddResourceDelayed(ResourceType.ATTACK, a)
            "ADS" -> CardEffect.AddResourceDelayed(ResourceType.STONES, a)
            "ADC" -> CardEffect.AddResourceDelayed(ResourceType.CHAOS, a)
            else -> error("Neznámý kód efektu '$code' (AP, AC, AW, SC, BC, BW, ARM/ARA/ARS/ARC, ADM/ADA/ADS/ADC)")
        }
    }

    @Test
    fun costVariants() {
        val spec = System.getenv("ENGINE_SIM_COSTS")
        assumeTrue("Spouští se jen s ENGINE_SIM_COSTS", spec != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 2000
        // Výměny "A>B" (všechny kopie A v presetech nahradí B) – odděleně od změn karet
        fun swapsOf(v: String) = v.split(',').map { it.trim() }.filter { '>' in it }
            .map { it.split('>').let { (from, to) -> from.trim() to to.trim() } }
        val variants = spec!!.split(';').filter { it.isNotBlank() }.map { v ->
            v.trim() to v.split(',').map { it.trim() }.filter { '>' !in it }.map { it.split('=', limit = 2) }.map { (id, value) ->
                // Části oddělené ':' v libovolném pořadí: číslo = cena, combo/nocombo,
                // "fx" jen jako značka, ostatní = efekty spojené '+'.
                var card = byId.getValue(id)
                for (part in value.split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "fx" }) {
                    card = when {
                        part.toIntOrNull() != null -> card.copy(cost = part.toInt())
                        part == "combo"            -> card.copy(isCombo = true)
                        part == "nocombo"          -> card.copy(isCombo = false)
                        else                       -> card.copy(effects = part.split('+').map(::fxFromCode))
                    }
                }
                card
            }
        }
        val tracked = (variants.first().second.map { it.id } + swapsOf(variants.first().first).map { it.second }).distinct()
        val decks = presetDecks.map { it.first }
        // ENGINE_SIM_TRACK (např. krok rituálu, který v žádném balíčku není) → sleduj jen balíček navíc
        val users = if (System.getenv("ENGINE_SIM_TRACK") != null) presetDecks.drop(PRESET_DECKS.size).map { it.first }
        else presetDecks.filter { d ->
            tracked.any { (d.second[it] ?: 0) > 0 } ||
                swapsOf(variants.first().first).any { (from, _) -> (d.second[from] ?: 0) > 0 }
        }.map { it.first }
        val t0 = System.currentTimeMillis()
        val rows = mutableListOf<String>()
        val trackRows = mutableListOf<String>()
        var base: Map<String, Double>? = null
        for ((name, cards) in listOf("výchozí" to emptyList<Card>()) + variants) {
            overrides = cards.associateBy { it.id }
            val swaps = swapsOf(name)
            deckEdit = { counts ->
                swaps.fold(counts) { c, (from, to) ->
                    val n = c[from] ?: 0
                    if (n == 0) c else (c - from) + (to to (c[to] ?: 0) + n)
                }
            }
            trackId = System.getenv("ENGINE_SIM_TRACK") ?: tracked.singleOrNull()
            val r = roundRobinAvg(n)
            val b = base ?: r.also { base = it }
            rows += String.format("%-22s", name) + decks.joinToString("") { dk ->
                val v = r.getValue(dk)
                if (r === b) String.format("%16.1f", v) else String.format("%9.1f (%+5.1f)", v, v - b.getValue(dk))
            }
            if (trackId != null) trackRows += String.format("%-22s", name) + users.joinToString("") { dk ->
                val t = lastTrack.getValue(dk)
                String.format("   %-14s zahráno %5.1f %% her, v kole %4.1f", dk.take(14),
                    100.0 * t.played / t.games, t.roundSum.toDouble() / t.played.coerceAtLeast(1))
            }
            println("hotovo: $name (${(System.currentTimeMillis() - t0) / 1000} s)")
        }
        overrides = emptyMap(); trackId = null; deckEdit = { it }
        val out = StringBuilder()
        out.appendLine("Herní engine – varianty cen, $n her na dvojici (${(System.currentTimeMillis() - t0) / 1000} s)")
        out.appendLine("Průměrná úspěšnost v %, v závorce rozdíl proti výchozímu stavu:")
        out.appendLine(String.format("%-22s", "") + decks.joinToString("") { String.format("%16s", it.take(14)) })
        rows.forEach(out::appendLine)
        if (trackRows.isNotEmpty()) {
            out.appendLine()
            out.appendLine("Jak často balíčky kartu ${System.getenv("ENGINE_SIM_TRACK") ?: tracked.single()} zahrají:")
            trackRows.forEach(out::appendLine)
        }
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-costs.txt").writeText(out.toString()) }
    }

    /**
     * Tempo hry: jak rychle a jak „najednou" se hry rozhodují. Round robin presetů s aktuálními
     * pravidly (ENGINE_SIM_WIN / MAXWALL / START / STARTWALL). Spuštění: ENGINE_SIM_PACING=1.
     *  - délka: kola, podíl her do 12. kola
     *  - přejetí: kolik vítězi chybělo k výhře 2 kola před koncem (min(cíl − jeho hrad, hrad soupeře));
     *    velká hodnota = rozhodl jeden nápor
     *  - obraty: hry, kde po 8. kole jeden vede o ≥ 15 (rozdíl vzdáleností k výhře) – jak často vyhraje druhý
     */
    @Test
    fun pacing() {
        assumeTrue("Spouští se jen s ENGINE_SIM_PACING=1", System.getenv("ENGINE_SIM_PACING") != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 1000
        val presets = presetDecks
        val pairs = presets.indices.flatMap { i -> (i + 1 until presets.size).map { j -> i to j } }
        class Acc {
            val rounds = mutableListOf<Int>()
            var decided = 0; var burstSum = 0.0; var burst25 = 0; var burstN = 0
            var lead = 0; var comeback = 0
            var destroy = 0; var build = 0; var hp = 0
            // Stavy (hráč na konci kola od 15. kola): kolik z nich přežije útok za 100 (hrad + hradby > 100)
            var late = 0; var survive100 = 0; var survive60 = 0
            val deckPts = DoubleArray(presets.size)
        }
        fun dist(own: Int, other: Int) = minOf(winTarget - own, other).coerceAtLeast(0)
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val t0 = System.currentTimeMillis()
        val parts = try {
            pairs.map { (i, j) ->
                pool.submit<Acc> {
                    val acc = Acc()
                    repeat(n) {
                        val o = playGame(presets[i].second, presets[j].second)
                        val aWon = isWinA(o.result); val bWon = isWinB(o.result)
                        acc.deckPts[i] += if (aWon) 1.0 else if (bWon) 0.0 else 0.5
                        acc.deckPts[j] += if (bWon) 1.0 else if (aWon) 0.0 else 0.5
                        val h = o.track.castles
                        for (k in 14 until h.size) for (side in 0..1) {
                            val hp = h[k][side] + h[k][2 + side]
                            acc.late++
                            if (hp > 100) acc.survive100++
                            if (hp > 60) acc.survive60++
                        }
                        acc.rounds += h.size + 1
                        when (o.result) {
                            GameResult.AI_CASTLE_DESTROYED, GameResult.PLAYER_CASTLE_DESTROYED -> acc.destroy++
                            GameResult.PLAYER_CASTLE_BUILT, GameResult.AI_CASTLE_BUILT         -> acc.build++
                            else -> if (aWon || bWon) acc.hp++
                        }
                        val decisive = o.result in setOf(GameResult.AI_CASTLE_DESTROYED, GameResult.PLAYER_CASTLE_DESTROYED,
                                                         GameResult.PLAYER_CASTLE_BUILT, GameResult.AI_CASTLE_BUILT)
                        if (decisive) {
                            acc.decided++
                            // 2 kola před koncem: poslední záznam je konec předposledního dokončeného kola
                            val k = h.size - 2
                            val (cw, cl) = when {
                                k < 0 -> 35 to 35  // hra skončila do 2. kola – vzdálenost ze startu
                                aWon  -> h[k][0] to h[k][1]
                                else  -> h[k][1] to h[k][0]
                            }
                            val d = if (k < 0) dist(startCastleConstructed, startCastleConstructed) else dist(cw, cl)
                            acc.burstSum += d; acc.burstN++
                            if (d >= 25) acc.burst25++
                        }
                        if (h.size >= 8 && (aWon || bWon)) {
                            val (ca, cb) = h[7][0] to h[7][1]
                            val da = dist(ca, cb); val db = dist(cb, ca)
                            if (kotlin.math.abs(da - db) >= 15) {
                                acc.lead++
                                val aLeads = da < db
                                if ((aLeads && bWon) || (!aLeads && aWon)) acc.comeback++
                            }
                        }
                    }
                    acc
                }
            }.map { it.get() }
        } finally { pool.shutdown() }
        val all = Acc()
        for (a in parts) {
            all.rounds += a.rounds; all.decided += a.decided; all.burstSum += a.burstSum; all.burst25 += a.burst25
            all.burstN += a.burstN; all.lead += a.lead; all.comeback += a.comeback
            all.destroy += a.destroy; all.build += a.build; all.hp += a.hp
            all.late += a.late; all.survive100 += a.survive100; all.survive60 += a.survive60
            for (k in presets.indices) all.deckPts[k] += a.deckPts[k]
        }
        val r = all.rounds.sorted()
        val games = r.size
        val winRates = presets.indices.map { 100 * all.deckPts[it] / (n * (presets.size - 1)) }
        val mean = winRates.average()
        val sd = Math.sqrt(winRates.sumOf { (it - mean) * (it - mean) } / winRates.size)
        val wins = (all.destroy + all.build + all.hp).coerceAtLeast(1)
        val out = StringBuilder()
        out.appendLine("Tempo hry – cíl $winTarget, strop hradeb $maxWall, start hradu $startCastleConstructed, " +
                       "hradby $startWall; $n her na dvojici, ${(System.currentTimeMillis() - t0) / 1000} s")
        out.appendLine(String.format("  délka: průměr %.1f kola, medián %d, 10 %% her do %d. kola, do 12. kola %.1f %% her",
            r.average(), r[games / 2], r[games / 10], 100.0 * r.count { it <= 12 } / games))
        out.appendLine(String.format("  přejetí: vítězi 2 kola před koncem chybělo v průměru %.1f, ≥ 25 v %.1f %% rozhodnutých her",
            all.burstSum / all.burstN.coerceAtLeast(1), 100.0 * all.burst25 / all.burstN.coerceAtLeast(1)))
        out.appendLine(String.format("  obraty: po 8. kole vede jeden o ≥ 15 v %.1f %% her, z nich vyhraje ten druhý %.1f %%",
            100.0 * all.lead / games, 100.0 * all.comeback / all.lead.coerceAtLeast(1)))
        out.appendLine(String.format("  od 15. kola: hrad + hradby > 100 v %.1f %% stavů hráče, > 60 v %.1f %% (%d stavů)",
            100.0 * all.survive100 / all.late.coerceAtLeast(1), 100.0 * all.survive60 / all.late.coerceAtLeast(1), all.late))
        out.appendLine(String.format("  výhry: boření %.1f %%, stavba %.1f %%, výška hradu %.1f %%",
            100.0 * all.destroy / wins, 100.0 * all.build / wins, 100.0 * all.hp / wins))
        out.appendLine(String.format("  balance: rozptyl úspěšnosti presetů SD %.1f (min %.1f, max %.1f)",
            sd, winRates.min(), winRates.max()))
        out.appendLine("  presety: " + presets.indices.joinToString(", ") {
            "${presets[it].first.substringAfter(' ')} ${"%.1f".format(winRates[it])}" })
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-pacing.txt").writeText(out.toString()) }
    }

    @Test
    fun roundRobinOfPresets() {
        assumeTrue("Simulace se spouští jen s ENGINE_SIM=1", System.getenv("ENGINE_SIM") != null)
        val n = System.getenv("ENGINE_SIM_GAMES")?.toIntOrNull() ?: 400
        val presets = presetDecks
        val stats = presets.associate { it.first to DeckStats() }
        val pair = Array(presets.size) { DoubleArray(presets.size) }
        val t0 = System.currentTimeMillis()
        for (i in presets.indices) for (j in i + 1 until presets.size) {
            var pts = 0.0
            repeat(n) {
                val o = playGame(presets[i].second, presets[j].second)
                record(stats.getValue(presets[i].first), o, asA = true)
                record(stats.getValue(presets[j].first), o, asA = false)
                pts += if (isWinA(o.result)) 1.0 else if (isWinB(o.result)) 0.0 else 0.5
            }
            pair[i][j] = 100 * pts / n
            pair[j][i] = 100 - pair[i][j]
        }
        val ms = System.currentTimeMillis() - t0

        val out = StringBuilder()
        out.appendLine("Herní engine, round robin ${presets.size} presetů, $n her na dvojici (${ms / 1000} s), " +
                       "cíl $winTarget, strop hradeb $maxWall, start hradu $startCastleConstructed")
        out.appendLine()
        out.appendLine(String.format("%-16s %6s  %8s %8s %6s  %6s", "balíček", "výhry", "boření", "stavba", "hrad", "kola"))
        for ((name, _) in presets.sortedByDescending { stats.getValue(it.first).points }) {
            val s = stats.getValue(name)
            val w = s.wins.coerceAtLeast(1)
            out.appendLine(String.format("%-16s %5.1f %%  %7.1f%% %7.1f%% %5.1f%%  %6.1f",
                name, 100 * s.points / s.games, 100.0 * s.winDestroy / w, 100.0 * s.winBuild / w,
                100.0 * s.winHp / w, s.roundsOfWins.toDouble() / w))
        }
        out.appendLine()
        out.appendLine("Dvojice (řádek proti sloupci, % výher řádku):")
        out.appendLine(String.format("%-16s", "") + presets.indices.joinToString("") { String.format("%6d", it + 1) })
        for (i in presets.indices) {
            out.appendLine(String.format("%2d %-13s", i + 1, presets[i].first.take(13)) +
                presets.indices.joinToString("") { j -> if (i == j) "     -" else String.format("%6.1f", pair[i][j]) })
        }
        out.appendLine()
        out.appendLine("Karta, která hru dokončila (podíl z výher balíčku):")
        for ((name, _) in presets) {
            val s = stats.getValue(name)
            val top = s.finishers.entries.sortedByDescending { it.value }.take(6)
                .joinToString(", ") { "${it.key} ${"%.0f".format(100.0 * it.value / s.wins.coerceAtLeast(1))} %" }
            out.appendLine("  $name: $top")
        }
        println(out)
        File("build").takeIf { it.isDirectory }?.let { File(it, "engine-sim.txt").writeText(out.toString()) }
    }
}

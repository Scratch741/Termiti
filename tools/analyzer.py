#!/usr/bin/env python3
"""
Termiti Card Balance Analyzer
==============================
Simuluje N zápasů a vyhodnocuje silové křivky karet.

Použití:
  python analyzer.py                   # 5 000 her
  python analyzer.py --games 20000     # víc her = přesnější data
  python analyzer.py --focus C05       # detail jedné karty
  python analyzer.py --top 20          # zobrazit top/bottom N karet
  python analyzer.py --chaos           # detail chaos ekonomiky
"""

import random
import sys
import argparse
from collections import defaultdict

# Nastav UTF-8 výstup na Windows
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# ══════════════════════════════════════════════════════════════════════════════
# KONSTANTY (mirror PlayerState.kt + GameState.kt)
# ══════════════════════════════════════════════════════════════════════════════
CASTLE_START = 25
WALL_START   = 10
CASTLE_WIN   = 100     # výhera postavením hradu
HAND_SIZE    = 5       # počáteční líznutí
MAX_HAND     = 7       # max karet v ruce
DECK_SIZE    = 40      # karet v balíčku
MAX_TURNS    = 150     # bezpečnostní limit kol → remíza

MAGIC  = "MAGIC"
ATTACK = "ATTACK"
STONES = "STONES"
CHAOS  = "CHAOS"
ALL_RES = [MAGIC, ATTACK, STONES, CHAOS]

COMMON, RARE, EPIC, LEGENDARY = 1, 2, 3, 4
MAX_COPIES   = {COMMON: 4, RARE: 3, EPIC: 2, LEGENDARY: 1}
RARITY_LABEL = {COMMON: "Běžná", RARE: "Vzácná", EPIC: "Epická", LEGENDARY: "Legendární"}
RARITY_SHORT = {COMMON: "C", RARE: "R", EPIC: "E", LEGENDARY: "L"}
RES_ICON     = {MAGIC: "✨", ATTACK: "⚔️", STONES: "🪨", CHAOS: "🌀"}


# ══════════════════════════════════════════════════════════════════════════════
# EFEKTY – pomocné funkce (čitelná konstruktura)
# ══════════════════════════════════════════════════════════════════════════════
def atk_castle(n):           return ("atk_castle", n)
def atk_wall(n):             return ("atk_wall",   n)
def bld_wall(n):             return ("bld_wall",   n)
def bld_castle(n):           return ("bld_castle", n)
def add_res(t, n):           return ("add_res",    t, n)
def add_mine(t, n):          return ("add_mine",   t, n)
def steal_res(t, n):         return ("steal_res",  t, n)
def drain_res(t, n):         return ("drain_res",  t, n)
def destroy_mine(t, n):      return ("destroy_mine", t, n)
def steal_card(n):           return ("steal_card", n)
def burn_card(n):            return ("burn_card",  n)
def add_cards(cid, n):       return ("add_cards",  cid, n)
def cond(condition, effect): return ("cond",             condition, effect)
def res_above(t, v):         return ("cond_res_above",   t, v)
def wall_above(v):           return ("cond_wall_above",  v)
def wall_below(v):           return ("cond_wall_below",  v)
def castle_above(v):         return ("cond_castle_above", v)


# ══════════════════════════════════════════════════════════════════════════════
# KATALOG KARET  (zrcadlo Kotlin katalogu)
# ══════════════════════════════════════════════════════════════════════════════
class Card:
    __slots__ = ["id", "name", "cost", "cost_type", "rarity", "effects"]
    def __init__(self, cid, name, cost, cost_type, rarity, effects):
        self.id        = cid
        self.name      = name
        self.cost      = cost
        self.cost_type = cost_type
        self.rarity    = rarity
        self.effects   = effects

ALL_CARDS = [
    # ── Útok ──────────────────────────────────────────────────────────────────
    Card("001","Základní útok",    2,ATTACK,COMMON,    [atk_castle(5)]),
    Card("008","Šípy",             1,ATTACK,COMMON,    [atk_castle(3)]),
    Card("003","Ohnivá koule",     3,ATTACK,COMMON,    [atk_wall(6)]),
    Card("007","Katapult",         5,ATTACK,RARE,      [atk_wall(10)]),
    Card("006","Podmíněný útok",   3,ATTACK,RARE,      [cond(res_above(ATTACK,5), atk_castle(10))]),
    Card("017","Válečný sekyrník", 4,ATTACK,COMMON,    [atk_castle(7)]),
    Card("019","Zápalné šípy",     1,ATTACK,COMMON,    [atk_wall(4)]),
    Card("020","Beranidlo",        3,ATTACK,RARE,      [atk_wall(8)]),
    Card("021","Dělostřelectvo",   6,ATTACK,EPIC,      [atk_wall(18)]),
    Card("022","Přímý zásah",      3,ATTACK,RARE,      [atk_castle(8)]),
    Card("023","Dvojitý úder",     5,ATTACK,EPIC,      [atk_wall(6),  atk_castle(6)]),
    Card("024","Berserk",          4,ATTACK,RARE,      [cond(wall_below(5),  atk_castle(15))]),
    Card("025","Protiútok",        3,ATTACK,RARE,      [cond(wall_below(10), atk_castle(10))]),
    Card("026","Ostřelovač",       3,ATTACK,EPIC,      [atk_castle(5), cond(res_above(ATTACK,5), atk_castle(5))]),
    Card("027","Válečný buben",    2,ATTACK,COMMON,    [atk_castle(4), add_res(ATTACK,2)]),
    Card("046","Goblin",           1,ATTACK,COMMON,    [atk_castle(2), steal_res(MAGIC,1),  add_res(CHAOS,1)]),
    Card("047","Ogr",              3,ATTACK,RARE,      [atk_wall(6),   atk_castle(3)]),
    Card("048","Upír",             4,ATTACK,RARE,      [atk_castle(6), add_res(MAGIC,3)]),
    Card("049","Jed",              3,ATTACK,RARE,      [atk_castle(3), drain_res(MAGIC,3),  add_res(CHAOS,1)]),
    Card("050","Kobylky",          4,ATTACK,RARE,      [atk_wall(8),   drain_res(STONES,4)]),
    Card("051","Drak",            11,ATTACK,LEGENDARY, [atk_wall(12),  atk_castle(8),  add_res(CHAOS,2)]),
    Card("052","Démon",           15,ATTACK,LEGENDARY, [atk_castle(16),add_res(CHAOS,2)]),
    Card("053","Plamenomet",       4,ATTACK,RARE,      [atk_wall(10),  add_res(ATTACK,2)]),
    Card("054","Válečný pochod",   5,ATTACK,EPIC,      [atk_wall(5),   atk_castle(8),  add_res(ATTACK,2)]),
    Card("055","Mravenci",         2,ATTACK,EPIC,      [atk_wall(3),   add_res(ATTACK,2), cond(wall_below(5),atk_castle(8))]),
    Card("056","Nájezdník",        3,ATTACK,RARE,      [steal_res(ATTACK,3), atk_castle(4)]),
    Card("078","Upíří drak",       8,ATTACK,LEGENDARY, [atk_castle(10),steal_res(MAGIC,4),steal_res(ATTACK,4),add_res(CHAOS,2)]),
    Card("079","Obléhání",         5,ATTACK,EPIC,      [atk_wall(6),   atk_castle(6),  drain_res(MAGIC,3), add_res(CHAOS,2)]),
    # ── Stavba ────────────────────────────────────────────────────────────────
    Card("002","Kamenná zeď",      3,STONES,COMMON,    [bld_wall(8)]),
    Card("005","Posila hradu",     2,STONES,COMMON,    [bld_castle(3)]),
    Card("009","Pevné základy",    4,STONES,COMMON,    [bld_castle(6)]),
    Card("010","Palisáda",         2,STONES,COMMON,    [bld_wall(5)]),
    Card("018","Mohutná věž",      5,STONES,RARE,      [bld_wall(15)]),
    Card("028","Záplata",          1,STONES,COMMON,    [bld_castle(2)]),
    Card("029","Opevnění",         2,STONES,COMMON,    [bld_wall(6)]),
    Card("030","Kamenný val",      4,STONES,RARE,      [bld_wall(14)]),
    Card("031","Renovace",         3,STONES,COMMON,    [bld_castle(5)]),
    Card("032","Citadela",         6,STONES,EPIC,      [bld_castle(10)]),
    Card("033","Zemní val",        2,STONES,RARE,      [cond(wall_below(8),  bld_wall(12))]),
    Card("034","Opravář",          3,STONES,RARE,      [cond(wall_above(15), bld_castle(8))]),
    Card("035","Základní kámen",   3,STONES,COMMON,    [bld_wall(4),  bld_castle(2)]),
    Card("036","Hradní příkop",    3,STONES,EPIC,      [bld_wall(7),  cond(castle_above(50), bld_wall(5))]),
    Card("057","Bašta",            3,STONES,RARE,      [bld_wall(7),  add_res(STONES,1)]),
    Card("058","Obranný val",      5,STONES,EPIC,      [bld_wall(15)]),
    Card("059","Pevnostní hrad",   4,STONES,RARE,      [bld_wall(5),  bld_castle(4)]),
    Card("060","Chrám",            6,STONES,EPIC,      [bld_castle(12)]),
    Card("061","Tunely",           3,STONES,EPIC,      [cond(castle_above(40), bld_wall(12))]),
    Card("062","Obranná aliance",  5,STONES,EPIC,      [bld_wall(7),  bld_castle(4), add_res(STONES,2)]),
    Card("063","Věž strážní",      5,STONES,RARE,      [bld_wall(14)]),
    Card("064","Zásobník",         4,STONES,EPIC,      [cond(castle_above(40), bld_castle(10))]),
    # ── Okamžité zdroje ───────────────────────────────────────────────────────
    Card("004","Magie",            0,MAGIC, COMMON,    [add_res(MAGIC,4)]),
    Card("011","Zásoby kamene",    1,MAGIC, COMMON,    [add_res(STONES,4)]),
    Card("012","Mobilizace",       1,MAGIC, COMMON,    [add_res(ATTACK,4)]),
    Card("037","Rychlá magie",     1,MAGIC, COMMON,    [add_res(MAGIC,4)]),
    Card("038","Vojenský rozkaz",  2,MAGIC, RARE,      [add_res(ATTACK,7)]),
    Card("039","Stavební boom",    2,MAGIC, RARE,      [add_res(STONES,7)]),
    Card("040","Alchymie",         2,MAGIC, EPIC,      [cond(res_above(MAGIC,4), add_res(ATTACK,8))]),
    Card("041","Magické trio",     3,MAGIC, RARE,      [add_res(MAGIC,2), add_res(ATTACK,2), add_res(STONES,2)]),
    Card("074","Tržiště",          2,MAGIC, COMMON,    [add_res(MAGIC,2), add_res(ATTACK,2), add_res(STONES,2)]),
    # ── Doly ──────────────────────────────────────────────────────────────────
    Card("013","Magický pramen",   3,MAGIC, RARE,      [add_mine(MAGIC,1)]),
    Card("014","Kamenolom",        3,MAGIC, RARE,      [add_mine(STONES,1)]),
    Card("015","Výcvikový tábor",  3,MAGIC, RARE,      [add_mine(ATTACK,1)]),
    Card("016","Velký pramen",     5,MAGIC, EPIC,      [add_mine(MAGIC,2)]),
    Card("042","Velký kamenolom",  3,MAGIC, EPIC,      [add_mine(STONES,2)]),
    Card("043","Výcvikové centrum",4,MAGIC, EPIC,      [add_mine(ATTACK,2)]),
    Card("044","Trifekta dolů",    6,MAGIC, LEGENDARY, [add_mine(MAGIC,1),  add_mine(ATTACK,1), add_mine(STONES,1)]),
    Card("045","Zlatý důl",        7,MAGIC, LEGENDARY, [add_mine(MAGIC,3)]),
    Card("072","Zbrojnice",        4,MAGIC, EPIC,      [add_res(ATTACK,2),  add_mine(ATTACK,1)]),
    Card("073","Škola magie",      4,MAGIC, EPIC,      [add_res(MAGIC,2),   add_mine(MAGIC,1)]),
    Card("075","Zlaté doly",       5,MAGIC, EPIC,      [add_mine(MAGIC,2),  add_mine(STONES,1)]),
    Card("076","Vojenská základna",5,MAGIC, EPIC,      [add_mine(ATTACK,2), add_mine(STONES,1)]),
    Card("080","Velkovýroba",      6,MAGIC, LEGENDARY, [add_mine(MAGIC,2),  add_mine(ATTACK,2),add_mine(STONES,2), add_res(MAGIC,3)]),
    # ── Sabotáž a krádež ──────────────────────────────────────────────────────
    Card("065","Lupič",            2,MAGIC, COMMON,    [steal_res(ATTACK,3)]),
    Card("066","Zlatokop",         2,MAGIC, COMMON,    [steal_res(STONES,4)]),
    Card("067","Sabotér",          3,MAGIC, RARE,      [drain_res(STONES,5)]),
    Card("068","Demoralizace",     3,MAGIC, RARE,      [drain_res(ATTACK,5)]),
    Card("069","Dvojitý agent",    4,MAGIC, EPIC,      [steal_res(MAGIC,3), steal_res(ATTACK,3), add_res(CHAOS,1)]),
    Card("070","Krize zásobování", 5,MAGIC, EPIC,      [drain_res(STONES,5),drain_res(ATTACK,5)]),
    Card("071","Špión",            3,MAGIC, EPIC,      [steal_res(MAGIC,2), steal_res(ATTACK,2),steal_res(STONES,2),add_res(CHAOS,2)]),
    Card("077","Přeměna magie",    3,MAGIC, RARE,      [cond(res_above(MAGIC,8), add_res(ATTACK,10))]),
    # ── Chaos – generátory (platí MAGIC) ──────────────────────────────────────
    Card("C01","Chaotický jiskra", 2,MAGIC, RARE,      [add_res(CHAOS,3)]),
    Card("C02","Entropie",         3,MAGIC, EPIC,      [add_res(CHAOS,5),  drain_res(MAGIC,2)]),
    Card("C03","Chaotický důl",    4,MAGIC, LEGENDARY, [add_mine(CHAOS,1)]),
    Card("C04","Krádež chaosu",    2,MAGIC, RARE,      [steal_res(CHAOS,3)]),
    Card("C24","Temný rituál",     2,MAGIC, RARE,      [add_res(CHAOS,5)]),
    Card("C25","Nestabilní vír",   1,MAGIC, COMMON,    [add_res(CHAOS,2),  add_res(MAGIC,2)]),
    Card("C26","Krvavá oběť",      1,MAGIC, RARE,      [add_res(CHAOS,7)]),
    Card("C27","Odraz magie",      2,MAGIC, EPIC,      [cond(res_above(MAGIC,5), add_res(CHAOS,6))]),
    Card("C28","Chaotická trofej", 1,MAGIC, COMMON,    [steal_res(ATTACK,2), add_res(CHAOS,3)]),
    Card("C29","Bouřlivá mysl",    5,MAGIC, EPIC,      [add_res(CHAOS,3),  add_mine(CHAOS,1)]),
    Card("C30","Chrám chaosu",     7,MAGIC, LEGENDARY, [add_mine(CHAOS,2)]),
    Card("C31","Chaotický výměník",4,MAGIC, EPIC,      [add_res(CHAOS,4),  drain_res(MAGIC,2), drain_res(ATTACK,2), drain_res(STONES,2)]),
    # ── Chaos – bojové (platí CHAOS) ──────────────────────────────────────────
    Card("C05","Chaotický výbuch", 5,CHAOS, EPIC,      [atk_castle(15)]),
    Card("C06","Bouře chaosu",     6,CHAOS, LEGENDARY, [atk_wall(10),   atk_castle(10)]),
    Card("C07","Chaotický štít",   4,CHAOS, EPIC,      [bld_wall(20)]),
    Card("C08","Zázrak chaosu",    5,CHAOS, LEGENDARY, [bld_castle(15)]),
    Card("C09","Chaotická krize",  5,CHAOS, LEGENDARY, [drain_res(MAGIC,6),  drain_res(ATTACK,6), drain_res(STONES,6)]),
    Card("C10","Chaotický drak",   7,CHAOS, LEGENDARY, [atk_wall(15),   atk_castle(12)]),
    Card("C11","Chaos a řád",      5,CHAOS, EPIC,      [bld_castle(8),  bld_wall(8)]),
    Card("C12","Anarchie",         7,CHAOS, LEGENDARY, [steal_res(MAGIC,5),  steal_res(ATTACK,5), steal_res(STONES,5)]),
    Card("C13","Sabotáž",          5,CHAOS, EPIC,      [destroy_mine(MAGIC,1)]),
    Card("C14","Ničení kamenolomu",5,CHAOS, EPIC,      [destroy_mine(STONES,1)]),
    Card("C15","Zákeřnost",        5,CHAOS, EPIC,      [destroy_mine(ATTACK,1)]),
    Card("C16","Velká sabotáž",    7,CHAOS, LEGENDARY, [destroy_mine(MAGIC,1), destroy_mine(STONES,1)]),
    Card("C17","Telekineze",       3,CHAOS, EPIC,      [steal_card(1)]),
    Card("C18","Chaos loupe",      5,CHAOS, LEGENDARY, [steal_card(2)]),
    Card("C19","Spálená knihovna", 4,CHAOS, EPIC,      [burn_card(2)]),
    Card("C20","Prázdná mysl",     6,CHAOS, LEGENDARY, [burn_card(3)]),
    Card("C21","Replikace",        3,CHAOS, RARE,      [add_cards("008",2)]),
    Card("C22","Chaos manufaktura",4,CHAOS, EPIC,      [add_cards("C05",1)]),
    Card("C23","Klonování",        3,CHAOS, RARE,      [add_cards("001",2)]),
]

CARD_BY_ID = {c.id: c for c in ALL_CARDS}


# ══════════════════════════════════════════════════════════════════════════════
# HERNÍ STAV
# ══════════════════════════════════════════════════════════════════════════════
class PlayerState:
    __slots__ = ["castle","wall","res","mines","deck","hand","discard"]

    def __init__(self):
        self.castle  = CASTLE_START
        self.wall    = WALL_START
        self.res     = {MAGIC:0, ATTACK:0, STONES:0, CHAOS:0}
        self.mines   = {MAGIC:1, ATTACK:1, STONES:1}  # CHAOS nemá výchozí důl
        self.deck    = []
        self.hand    = []
        self.discard = []

    def generate_resources(self):
        for t, v in self.mines.items():
            self.res[t] = self.res.get(t, 0) + v

    def draw_cards(self, n):
        for _ in range(n):
            if self.deck and len(self.hand) < MAX_HAND:
                self.hand.append(self.deck.pop(0))


# ══════════════════════════════════════════════════════════════════════════════
# HERNÍ LOGIKA – efekty, podmínky, AI
# ══════════════════════════════════════════════════════════════════════════════
def check_condition(cond_tuple, me: PlayerState) -> bool:
    t = cond_tuple[0]
    if   t == "cond_res_above":   return me.res.get(cond_tuple[1], 0) > cond_tuple[2]
    elif t == "cond_wall_above":  return me.wall > cond_tuple[1]
    elif t == "cond_wall_below":  return me.wall < cond_tuple[1]
    elif t == "cond_castle_above":return me.castle > cond_tuple[1]
    return False


def apply_effect(eff, me: PlayerState, opp: PlayerState):
    t = eff[0]
    if   t == "atk_castle":
        opp.castle = max(0, opp.castle - eff[1])
    elif t == "atk_wall":
        taken = min(eff[1], opp.wall)
        opp.wall -= taken
        overflow = eff[1] - taken
        if overflow > 0: opp.castle = max(0, opp.castle - overflow)
    elif t == "bld_wall":
        me.wall = min(100, me.wall + eff[1])
    elif t == "bld_castle":
        me.castle = min(100, me.castle + eff[1])
    elif t == "add_res":
        me.res[eff[1]] = me.res.get(eff[1], 0) + eff[2]
    elif t == "add_mine":
        me.mines[eff[1]] = me.mines.get(eff[1], 0) + eff[2]
    elif t == "steal_res":
        taken = min(eff[2], opp.res.get(eff[1], 0))
        opp.res[eff[1]] = opp.res.get(eff[1], 0) - taken
        me.res[eff[1]]  = me.res.get(eff[1], 0) + taken
    elif t == "drain_res":
        drained = min(eff[2], opp.res.get(eff[1], 0))
        opp.res[eff[1]] = opp.res.get(eff[1], 0) - drained
    elif t == "destroy_mine":
        cur = opp.mines.get(eff[1], 0)
        if cur > 0: opp.mines[eff[1]] = max(0, cur - eff[2])
    elif t == "steal_card":
        for _ in range(eff[1]):
            if opp.hand:
                card = random.choice(opp.hand)
                opp.hand.remove(card)
                me.hand.append(card)
    elif t == "burn_card":
        for _ in range(eff[1]):
            if opp.hand:
                card = random.choice(opp.hand)
                opp.hand.remove(card)
                opp.discard.append(card)
    elif t == "add_cards":
        base_id  = eff[1].split("_")[0]
        template = CARD_BY_ID.get(base_id)
        if template:
            for _ in range(eff[2]): me.deck.append(template)
            random.shuffle(me.deck)
    elif t == "cond":
        if check_condition(eff[1], me):
            apply_effect(eff[2], me, opp)


def can_afford(card: Card, me: PlayerState) -> bool:
    return me.res.get(card.cost_type, 0) >= card.cost


def score_card(card: Card, me: PlayerState, opp: PlayerState) -> float:
    """Heuristické ohodnocení karty – zrcadlo Kotlin AI."""
    s = card.cost * 2.0
    opp_low   = opp.castle < 15
    me_low    = me.castle < 15
    me_nowall = me.wall < 5

    fx = card.effects[0] if card.effects else None
    if fx:
        t = fx[0]
        if   t == "atk_castle":    s += 22 if opp_low  else fx[1] * 0.6
        elif t == "atk_wall":      s += fx[1] * 0.45
        elif t == "bld_castle":    s += 20 if me_low   else fx[1] * 0.4
        elif t == "bld_wall":      s += 17 if me_nowall else fx[1] * 0.35
        elif t == "add_mine":      s += 9
        elif t == "steal_res":     s += 8
        elif t == "drain_res":     s += 6
        elif t == "add_res":       s += 3
        elif t == "destroy_mine":  s += 11
        elif t == "steal_card":    s += 9
        elif t == "burn_card":     s += 7
        elif t == "add_cards":     s += 4
        elif t == "cond":
            s += 13 if check_condition(fx[1], me) else -4

    s += random.uniform(-1.5, 1.5)  # malá náhodnost → ne-deterministická AI
    return s


def play_turn(me: PlayerState, opp: PlayerState, played_ids: list):
    """Jeden tah: generuj zdroje, zahraj kartu nebo čekej, doplň ruku."""
    me.generate_resources()

    affordable = [c for c in me.hand if can_afford(c, me)]
    if affordable:
        best = max(affordable, key=lambda c: score_card(c, me, opp))
        me.res[best.cost_type] -= best.cost
        me.hand.remove(best)
        me.discard.append(best)
        for eff in best.effects:
            apply_effect(eff, me, opp)
        played_ids.append(best.id)
    else:
        # Čekej – líhni kartu (spal při plné ruce)
        if me.deck:
            drawn = me.deck.pop(0)
            if len(me.hand) < MAX_HAND: me.hand.append(drawn)
            else:                       me.discard.append(drawn)

    # Doplň ruku na HAND_SIZE (ne víc než max)
    me.draw_cards(max(0, HAND_SIZE - len(me.hand)))


def check_win(me: PlayerState, opp: PlayerState) -> bool:
    return me.castle >= CASTLE_WIN or opp.castle <= 0


# ══════════════════════════════════════════════════════════════════════════════
# SIMULACE
# ══════════════════════════════════════════════════════════════════════════════
def random_deck() -> list:
    """Náhodný 40-kartový balíček respektující max kopie."""
    counts = defaultdict(int)
    pool   = list(ALL_CARDS)
    random.shuffle(pool)
    result = []
    for card in pool:
        if counts[card.id] < MAX_COPIES[card.rarity] and len(result) < DECK_SIZE:
            result.append(card)
            counts[card.id] += 1
    # Dopl, pokud je méně než 40 (rarity omezení)
    while len(result) < DECK_SIZE:
        card = random.choice(ALL_CARDS)
        if counts[card.id] < MAX_COPIES[card.rarity]:
            result.append(card)
            counts[card.id] += 1
    random.shuffle(result)
    return result


def init_player(deck: list) -> PlayerState:
    p = PlayerState()
    p.deck = list(deck)
    random.shuffle(p.deck)
    p.generate_resources()
    p.draw_cards(HAND_SIZE)
    return p


def simulate_game(deck_a: list, deck_b: list) -> tuple:
    """
    Vrátí: (winner: 0=A, 1=B, -1=remíza, turns, played_a[], played_b[])
    """
    pa, pb      = init_player(deck_a), init_player(deck_b)
    played_a, played_b = [], []

    for turn in range(MAX_TURNS):
        play_turn(pa, pb, played_a)
        if check_win(pa, pb): return (0, turn, played_a, played_b)
        if check_win(pb, pa): return (1, turn, played_a, played_b)

        play_turn(pb, pa, played_b)
        if check_win(pb, pa): return (1, turn, played_a, played_b)
        if check_win(pa, pb): return (0, turn, played_a, played_b)

        if not pa.deck and not pb.deck:
            return (-1, turn, played_a, played_b)

    return (-1, MAX_TURNS, played_a, played_b)


# ══════════════════════════════════════════════════════════════════════════════
# STATISTIKY
# ══════════════════════════════════════════════════════════════════════════════
def run_analysis(num_games: int, seed: int) -> tuple:
    random.seed(seed)

    # Na kartu: wins/losses/draws v balíčku, počet zahrání, počet výskytů v balíčku
    stats = {c.id: {"wins":0,"losses":0,"draws":0,"played":0,"in_deck":0}
             for c in ALL_CARDS}
    game_lengths = []
    draws = 0

    for gi in range(num_games):
        deck_a, deck_b = random_deck(), random_deck()
        ids_a = {c.id for c in deck_a}
        ids_b = {c.id for c in deck_b}

        winner, turns, pl_a, pl_b = simulate_game(deck_a, deck_b)
        game_lengths.append(turns)
        if winner == -1: draws += 1

        for cid in ids_a:
            s = stats[cid]
            s["in_deck"] += 1
            if   winner ==  0: s["wins"]   += 1
            elif winner ==  1: s["losses"] += 1
            else:              s["draws"]  += 1

        for cid in ids_b:
            s = stats[cid]
            s["in_deck"] += 1
            if   winner ==  1: s["wins"]   += 1
            elif winner ==  0: s["losses"] += 1
            else:              s["draws"]  += 1

        for cid in pl_a: stats[cid]["played"] += 1
        for cid in pl_b: stats[cid]["played"] += 1

        if (gi + 1) % 1000 == 0:
            pct = (gi + 1) / num_games * 100
            print(f"  >> {gi+1:>6,}/{num_games:,} her  [{pct:4.0f}%]", end="\r", flush=True)

    print(" " * 50, end="\r")
    return stats, game_lengths, draws


def compute_results(stats: dict) -> list:
    results = []
    for card in ALL_CARDS:
        s    = stats[card.id]
        tot  = s["wins"] + s["losses"] + s["draws"]
        if tot == 0: continue
        wr   = s["wins"] / tot * 100
        # Průměrný počet zahrání na hru, kde je karta v balíčku
        pr   = s["played"] / max(1, s["in_deck"])
        results.append({
            "card":     card,
            "win_rate": wr,
            "play_rate":pr,
            "total":    tot,
            "wins":     s["wins"],
            "losses":   s["losses"],
            "draws":    s["draws"],
            "in_deck":  s["in_deck"],
            "played":   s["played"],
        })
    results.sort(key=lambda x: x["win_rate"], reverse=True)
    return results


# ══════════════════════════════════════════════════════════════════════════════
# VÝSTUP
# ══════════════════════════════════════════════════════════════════════════════
W = 80

def bar(value, max_val=100, width=20, char="█", empty="░") -> str:
    filled = round(value / max_val * width)
    return char * filled + empty * (width - filled)

def fmt_row(rank, r, highlight="") -> str:
    c   = r["card"]
    ico = RES_ICON.get(c.cost_type, "?")
    rar = RARITY_SHORT[c.rarity]
    wr  = r["win_rate"]
    pr  = r["play_rate"]
    d   = wr - 50.0
    sgn = "+" if d >= 0 else ""
    b   = bar(wr, 100, 16)
    return (f"  {rank:>3}. [{c.id:>3}] {c.name:<22} "
            f"{ico}{c.cost:>2}{rar}  "
            f"Win:{wr:5.1f}% ({sgn}{d:+.1f})  "
            f"Play:{pr:4.2f}x  {b}{highlight}")

def section(title):
    print(f"\n{'─'*W}")
    print(f"  {title}")
    print(f"{'─'*W}")

def print_report(stats, game_lengths, draws, num_games, top_n, focus_id, show_chaos):
    results = compute_results(stats)

    avg_turns = sum(game_lengths) / len(game_lengths)
    draw_pct  = draws / num_games * 100

    print("=" * W)
    print(f"  TERMITI CARD BALANCE ANALYZER")
    print(f"  Simulováno: {num_games:,} her  |  Průměr: {avg_turns:.1f} kol  |  Remízy: {draw_pct:.1f}%")
    print("=" * W)
    print(f"  Format radku:  [ID] Nazev  ICON+cena+Vzacnost  WinRate (+-delta)  PlayRate  Graf")
    print(f"  WinRate = % her, kde balíček s touto kartou vyhrál (základ: 50 %)")
    print(f"  PlayRate= průměr zahrání za hru (karta v balíčku)")

    # ── Top N nejsilnějších ──────────────────────────────────────────────────
    section(f"🔴  NEJSILNĚJŠÍ KARTY  (top {top_n})")
    for i, r in enumerate(results[:top_n]):
        flag = "  ⚠ SILNÉ" if r["win_rate"] > 54 else ""
        print(fmt_row(i + 1, r, flag))

    # ── Bottom N nejslabších ─────────────────────────────────────────────────
    section(f"🟢  NEJSLABŠÍ KARTY  (bottom {top_n})")
    bottom = results[-top_n:]
    for i, r in enumerate(bottom):
        flag = "  ✗ SLABÉ" if r["win_rate"] < 46 else ""
        print(fmt_row(len(results) - top_n + i + 1, r, flag))

    # ── Přehled podle vzácnosti ──────────────────────────────────────────────
    section("📊  PRŮMĚR WIN RATE PODLE VZÁCNOSTI")
    for rar, label in [(COMMON,"Běžná"),(RARE,"Vzácná"),(EPIC,"Epická"),(LEGENDARY,"Legendární")]:
        rcs = [r for r in results if r["card"].rarity == rar]
        if not rcs: continue
        avg  = sum(r["win_rate"] for r in rcs) / len(rcs)
        avgp = sum(r["play_rate"] for r in rcs) / len(rcs)
        b    = bar(avg, 100, 18)
        print(f"  {label:<12} ({len(rcs):>2} karet):  {avg:.1f}%  {b}  PlayRate avg: {avgp:.2f}x")

    # ── Přehled podle resource type ──────────────────────────────────────────
    section("⚡  PRŮMĚR WIN RATE PODLE RESOURCE TYPU")
    for rt in ALL_RES:
        rcs = [r for r in results if r["card"].cost_type == rt]
        if not rcs: continue
        avg  = sum(r["win_rate"] for r in rcs) / len(rcs)
        avgp = sum(r["play_rate"] for r in rcs) / len(rcs)
        ico  = RES_ICON[rt]
        b    = bar(avg, 100, 18)
        print(f"  {ico} {rt:<8} ({len(rcs):>2} karet):  {avg:.1f}%  {b}  PlayRate avg: {avgp:.2f}x")

    # ── Chaos ekonomika ──────────────────────────────────────────────────────
    if show_chaos:
        section("🌀  DETAIL: CHAOS EKONOMIKA")
        gen_cards = [r for r in results
                     if any(e[0] == "add_res" and e[1] == CHAOS for e in r["card"].effects)
                     or any(e[0] == "add_mine" and e[1] == CHAOS for e in r["card"].effects)]
        pay_cards = [r for r in results if r["card"].cost_type == CHAOS]

        print(f"  Generátory chaosu ({len(gen_cards)} karet):")
        for r in sorted(gen_cards, key=lambda x: x["win_rate"], reverse=True):
            c = r["card"]
            total_chaos = sum(e[2] for e in c.effects if e[0]=="add_res" and e[1]==CHAOS)
            mine_chaos  = sum(e[2] for e in c.effects if e[0]=="add_mine" and e[1]==CHAOS)
            tag = f"+{total_chaos}🌀" if total_chaos else f"+{mine_chaos}🌀/kolo"
            print(f"    [{c.id:>3}] {c.name:<22}  {tag:<12}  Win:{r['win_rate']:.1f}%  Play:{r['play_rate']:.2f}x")

        print(f"\n  Karty platící chaosem ({len(pay_cards)} karet):")
        for r in sorted(pay_cards, key=lambda x: x["win_rate"], reverse=True):
            c = r["card"]
            print(f"    [{c.id:>3}] {c.name:<22}  cena:{c.cost}🌀  Win:{r['win_rate']:.1f}%  Play:{r['play_rate']:.2f}x")

    # ── Detail jedné karty ───────────────────────────────────────────────────
    if focus_id:
        matches = [r for r in results if r["card"].id.upper() == focus_id.upper()]
        if matches:
            r = matches[0]
            c = r["card"]
            rank = results.index(r) + 1
            section(f"🔍  DETAIL KARTY: [{c.id}] {c.name}")
            print(f"  Rank:      #{rank} z {len(results)}")
            print(f"  Win rate:  {r['win_rate']:.2f}%  (δ {r['win_rate']-50:+.2f}%)")
            print(f"  Play rate: {r['play_rate']:.3f}x  (avg zahrání / hru kde je v balíčku)")
            print(f"  Hry:       {r['total']:,}  |  Výhry: {r['wins']:,}  |  Prohry: {r['losses']:,}")
            print(f"  Cena:      {c.cost} {RES_ICON[c.cost_type]}  |  {RARITY_LABEL[c.rarity]}")
            print(f"  Efekty:    {c.effects}")
            if r["play_rate"] < 0.3:
                print(f"  ⚠ Karta je málo zahrávána – možná příliš drahá nebo nevhodná pro meta.")
            elif r["win_rate"] > 55:
                print(f"  ⚠ Karta výrazně přispívá k vítězství – zváž oslabení.")
            elif r["win_rate"] < 45:
                print(f"  ⚠ Karta negativně koreluje s výhrami – zváž posílení.")
        else:
            print(f"\n  ⚠ Karta '{focus_id}' nenalezena. Dostupné ID: {[c.id for c in ALL_CARDS[:10]]}...")

    # ── Doporučení ───────────────────────────────────────────────────────────
    section("💡  DOPORUČENÍ BALANCINGU")
    overpowered  = [r for r in results if r["win_rate"] > 54]
    underpowered = [r for r in results if r["win_rate"] < 46]
    unplayed     = [r for r in results if r["play_rate"] < 0.2]

    if overpowered:
        print(f"  Příliš silné ({len(overpowered)} karet) – zvažte zvýšení ceny nebo oslabení:")
        for r in overpowered[:8]:
            c = r["card"]
            print(f"    ↓ [{c.id}] {c.name}  Win:{r['win_rate']:.1f}%  cena:{c.cost} {RES_ICON[c.cost_type]}")

    if underpowered:
        print(f"\n  Příliš slabé ({len(underpowered)} karet) – zvažte snížení ceny nebo posílení:")
        for r in underpowered[:8]:
            c = r["card"]
            print(f"    ↑ [{c.id}] {c.name}  Win:{r['win_rate']:.1f}%  cena:{c.cost} {RES_ICON[c.cost_type]}")

    if unplayed:
        print(f"\n  Málo zahrávané ({len(unplayed)} karet) – pravděpodobně příliš drahé:")
        for r in unplayed[:8]:
            c = r["card"]
            print(f"    ? [{c.id}] {c.name}  Play:{r['play_rate']:.2f}x  cena:{c.cost} {RES_ICON[c.cost_type]}")

    print(f"\n{'='*W}\n")


# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════
def main():
    parser = argparse.ArgumentParser(description="Termiti Card Balance Analyzer")
    parser.add_argument("--games", type=int,  default=5000, help="Počet simulovaných her (default: 5000)")
    parser.add_argument("--seed",  type=int,  default=42,   help="Random seed pro reprodukovatelnost")
    parser.add_argument("--top",   type=int,  default=15,   help="Zobrazit top/bottom N karet (default: 15)")
    parser.add_argument("--focus", type=str,  default=None, help="Detail jedné karty podle ID (např. C05)")
    parser.add_argument("--chaos", action="store_true",     help="Zobrazit detail chaos ekonomiky")
    args = parser.parse_args()

    print(f"\n  Termiti Balance Analyzer")
    print(f"  Simuluji {args.games:,} her (seed={args.seed})...\n")
    stats, lengths, draws = run_analysis(args.games, args.seed)
    print_report(stats, lengths, draws, args.games, args.top, args.focus, args.chaos)


if __name__ == "__main__":
    main()

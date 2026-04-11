package com.example.termiti

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs get() = getApplication<Application>()
        .getSharedPreferences("termiti_decks", Context.MODE_PRIVATE)

    // Útočné karty  → platí ATTACK
    // Stavební karty → platí STONES
    // Ostatní (zdroje, doly) → platí MAGIC
    // Katalog všech dostupných karet
    val allCards = listOf(
        // ── Útok (platí ATTACK) ───────────────────────────────────────
        Card("001", "Základní útok",  "Zaútočí na nepřítele za 5.",              cost = 2, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(5))),
        Card("008", "Šípy",           "Zaútočí na nepřítele za 3.",              cost = 1, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(3))),
        Card("003", "Ohnivá koule",   "Přímý zásah ohněm: hrad −8.", cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackCastle(8))),
        Card("007", "Katapult",       "Zaútočí na nepřítele za 11.",             cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(11))),
        Card("006", "Podmíněný útok", "Pokud máš >5 útoku, udeř hrad za 10.",        cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.ATTACK, 5),
                CardEffect.AttackCastle(10)
            ))),
        Card("017", "Válečný sekyrník","Zaútočí za 8, ukradni 2 útoku soupeři.",   cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(8), CardEffect.StealResource(ResourceType.ATTACK, 2))),

        // ── Stavba (platí STONES) ─────────────────────────────────────
        Card("002", "Kamenná zeď",    "Postaví hradby +9.",                      cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(9))),
        Card("010", "Palisáda",       "Hradby +5, vrátí 1 kámen.",               cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(5), CardEffect.AddResource(ResourceType.STONES, 1))),
        Card("005", "Posila hradu",   "Opraví hrad o 4.",                        cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildCastle(4))),
        Card("009", "Pevné základy",  "Opraví hrad o 8.",                        cost = 4, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildCastle(8))),
        Card("018", "Mohutná věž",    "Postaví hradby +15.",                     cost = 5, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(15))),

        // ── Zdroje okamžité (platí MAGIC) ────────────────────────────
        Card("004", "Magie",          "Okamžitě +2 magie. [Combo]",              cost = 0, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 2)), isCombo = true),
        Card("011", "Zásoby kamene",  "Okamžitě +4 kameny. [Combo]",             cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.STONES, 4)), isCombo = true),
        Card("012", "Mobilizace",     "Okamžitě +3 útoku. [Combo]",              cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.ATTACK, 3)), isCombo = true),

        // ── Doly (platí MAGIC) ────────────────────────────────────────
        Card("013", "Magický pramen", "Trvale +1 důl magie/kolo.",               cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 1))),
        Card("014", "Kamenolom",      "Trvale +1 důl kamene/kolo.",              cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("015", "Výcvikový tábor","Trvale +1 důl útoku/kolo.",               cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddMine(ResourceType.ATTACK, 1))),
        Card("016", "Velký pramen",   "Trvale +2 doly magie/kolo.",              cost = 5, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 2))),

        // ── Útok – rozšíření ──────────────────────────────────────────
        Card("019", "Zápalné šípy",   "Poškodí jen hradby o 4.",                 cost = 1, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackWall(4))),
        Card("020", "Beranidlo",      "Poškodí jen hradby o 8.",                 cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackWall(8))),
        Card("021", "Dělostřelectvo", "Zaútočí na nepřítele za 18.",             cost = 6, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackPlayer(18))),
        Card("022", "Přímý zásah",    "Přímý zásah: hrad −8, ignoruje hradby.",  cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackCastle(8))),
        Card("023", "Dvojitý úder",   "Zaútočí na nepřítele za 12.",             cost = 5, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackPlayer(12))),
        Card("024", "Berserk",        "Pokud máš <5 hradeb, udeř hrad za 15.",  cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallBelow(5),
                CardEffect.AttackCastle(15)
            ))),
        Card("025", "Protiútok",      "Pokud máš <10 hradeb, udeř hrad za 10.", cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallBelow(10),
                CardEffect.AttackCastle(10)
            ))),
        Card("026", "Ostřelovač",     "Poškodí hrad o 5. Pokud >5 útoku, +5.",  cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.AttackCastle(5),
                CardEffect.ConditionalEffect(
                    Condition.ResourceAbove(ResourceType.ATTACK, 5),
                    CardEffect.AttackCastle(5)
                )
            )),
        Card("027", "Válečný buben",  "Zaútočí za 4 a přidá +2 útoku. [Combo]", cost = 2, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(4), CardEffect.AddResource(ResourceType.ATTACK, 2)), isCombo = true),

        // ── Stavba – rozšíření ────────────────────────────────────────
        Card("028", "Záplata",        "Opraví hrad o 3.",                        cost = 1, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildCastle(2))),
        Card("029", "Opevnění",       "Postaví hradby +6.",                      cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(6))),
        Card("030", "Kamenný val",    "Postaví hradby +14.",                     cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(14))),
        Card("031", "Renovace",       "Opraví hrad o 6.",                        cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildCastle(5))),
        Card("032", "Citadela",       "Opraví hrad o 13.",                       cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(13))),
        Card("033", "Zemní val",      "Pokud máš <8 hradeb, postav +12.",        cost = 2, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallBelow(8),
                CardEffect.BuildWall(12)
            ))),
        Card("034", "Opravář",        "Pokud máš >15 hradeb, oprav hrad o 8.",   cost = 3, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallAbove(15),
                CardEffect.BuildCastle(8)
            ))),
        Card("035", "Základní kámen", "Hradby +5 a hrad +3.",                    cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(5), CardEffect.BuildCastle(3))),
        Card("036", "Hradní příkop",  "Hradby +7. Pokud hrad >35, hradby +5.",   cost = 3, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.BuildWall(7),
                CardEffect.ConditionalEffect(
                    Condition.CastleAbove(35),
                    CardEffect.BuildWall(5)
                )
            )),

        // ── Zdroje – rozšíření ────────────────────────────────────────
        Card("037", "Rychlá magie",   "Okamžitě +4 magie. [Combo]",              cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 4)), isCombo = true),
        Card("038", "Vojenský rozkaz","Okamžitě +6 útoku. [Combo]",             cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.ATTACK, 6)), isCombo = true),
        Card("039", "Stavební boom",  "Okamžitě +6 kamene. [Combo]",            cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.STONES, 6)), isCombo = true),
        Card("040", "Alchymie",       "Pokud máš >4 magie, získej +8 útoku.",    cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 4),
                CardEffect.AddResource(ResourceType.ATTACK, 8)
            ))),
        Card("041", "Magické trio",   "+2 magie, +2 útoku, +2 kamene. [Combo]",  cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.AddResource(ResourceType.MAGIC,  2),
                CardEffect.AddResource(ResourceType.ATTACK, 2),
                CardEffect.AddResource(ResourceType.STONES, 2)
            ), isCombo = true),

        // ── Doly – rozšíření ──────────────────────────────────────────
        Card("042", "Velký kamenolom","Trvale +2 doly kamene/kolo.",             cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.STONES, 2))),
        Card("043", "Výcvikové centrum","Trvale +2 doly útoku/kolo.",            cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.ATTACK, 2))),
        Card("044", "Trifekta dolů",  "+1 důl magie, útoku i kamene/kolo.",      cost = 6, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(
                CardEffect.AddMine(ResourceType.MAGIC,  1),
                CardEffect.AddMine(ResourceType.ATTACK, 1),
                CardEffect.AddMine(ResourceType.STONES, 1)
            )),
        Card("045", "Očarované doly",      "Trvale +3 doly magie/kolo.",              cost = 7, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 3))),

        // ── Útok – Arcomage/Mravenci inspirace ───────────────────────
        Card("046", "Goblin",         "Zaútočí za 2, ukradni 1 magii, +1 chaosu.", cost = 1, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(2), CardEffect.StealResource(ResourceType.MAGIC, 1),
                CardEffect.AddResource(ResourceType.CHAOS, 1))),
        Card("047", "Ogr",            "Zaútočí na nepřítele za 9.",              cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(9))),
        Card("048", "Upír",           "Zaútočí za 6, získej +3 magie. [Combo]",  cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(6), CardEffect.AddResource(ResourceType.MAGIC, 3)), isCombo = true),
        Card("049", "Jed",            "Zaútočí za 3, soupeř ztratí 3 magie, +1 chaosu.", cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(3), CardEffect.DrainResource(ResourceType.MAGIC, 3),
                CardEffect.AddResource(ResourceType.CHAOS, 1))),
        Card("050", "Kobylky",        "Zaútočí za 8, soupeř ztratí 4 kameny.",   cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(8), CardEffect.DrainResource(ResourceType.STONES, 4))),
        Card("051", "Drak",           "Zaútočí za 14, hrad −8 přímo, +2 chaosu.", cost = 11, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackPlayer(14), CardEffect.AttackCastle(8),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("052", "Démon",          "Přímý zásah: hrad −16, ignoruje hradby. +2 chaosu.", cost = 15, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackCastle(16),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("053", "Plamenomet",     "Poškodí jen hradby o 10, získej +2 útoku.", cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackWall(10), CardEffect.AddResource(ResourceType.ATTACK, 2))),
        Card("054", "Válečný pochod", "Zaútočí na nepřítele za 13, +2 útoku.",   cost = 5, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackPlayer(13),
                CardEffect.AddResource(ResourceType.ATTACK, 2))),
        Card("055", "Mravenci",       "Zaútočí za 3, +2 útoku. Pokud <5 hradeb: hrad −8.", cost = 2, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.AttackPlayer(3),
                CardEffect.AddResource(ResourceType.ATTACK, 2),
                CardEffect.ConditionalEffect(Condition.WallBelow(5), CardEffect.AttackCastle(8))
            )),
        Card("056", "Nájezdník",      "Ukraď 3 útoku, zaútočí za 4.",            cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.StealResource(ResourceType.ATTACK, 3), CardEffect.AttackPlayer(4))),

        // ── Stavba – Arcomage/Mravenci inspirace ──────────────────────
        Card("057", "Bašta",          "Hradby +7, +1 kámen.",                    cost = 3, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(7), CardEffect.AddResource(ResourceType.STONES, 1))),
        Card("058", "Obranný val",    "Hradby +4.",                             cost = 0, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(4))),
        Card("059", "Pevnostní hrad", "Hradby +4 a hrad +6.",                    cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(4), CardEffect.BuildCastle(6))),
        Card("060", "Chrám",          "Hrad +18.",                               cost = 10, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(18))),
        Card("061", "Tunely",         "Pokud hrad >40, postav hradby +12.",       cost = 3, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(Condition.CastleAbove(40), CardEffect.BuildWall(12)))),
        Card("062", "Obranná aliance","Hradby +7, hrad +4, +2 kameny.",          cost = 5, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(7), CardEffect.BuildCastle(4),
                CardEffect.AddResource(ResourceType.STONES, 2))),
        Card("063", "Věž strážní",    "Hradby +14, trvale +1 důl kamene.",       cost = 5, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(14), CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("064", "Zásobník",       "Pokud hrad >40, oprav hrad o 10.",        cost = 3, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(Condition.CastleAbove(40), CardEffect.BuildCastle(10)))),

        // ── Sabotáž a krádež (platí MAGIC) ───────────────────────────
        Card("065", "Lupič",          "Ukraď 3 útoku od soupeře.",               cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.StealResource(ResourceType.ATTACK, 3))),
        Card("066", "Zlatokop",       "Ukraď 4 kameny od soupeře.",              cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.StealResource(ResourceType.STONES, 4))),
        Card("067", "Sabotér",        "Soupeř ztratí 5 kamenů.",                 cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.DrainResource(ResourceType.STONES, 5))),
        Card("068", "Demoralizace",   "Soupeř ztratí 5 útoku.",                  cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.DrainResource(ResourceType.ATTACK, 5))),
        Card("069", "Dvojitý agent",  "Ukraď 3 magie a 3 útoku, +1 chaosu.",   cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.StealResource(ResourceType.MAGIC, 3),
                CardEffect.StealResource(ResourceType.ATTACK, 3),
                CardEffect.AddResource(ResourceType.CHAOS, 1))),
        Card("070", "Krize zásobování","Soupeř ztratí 5 kamenů a 5 útoku.",     cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DrainResource(ResourceType.STONES, 5),
                CardEffect.DrainResource(ResourceType.ATTACK, 5))),
        Card("071", "Špión",          "Ukraď 2 od každého zdroje, +2 chaosu.",  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.StealResource(ResourceType.MAGIC, 2),
                CardEffect.StealResource(ResourceType.ATTACK, 2),
                CardEffect.StealResource(ResourceType.STONES, 2),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),

        // ── Zdroje + doly – Arcomage/Mravenci inspirace ──────────────
        Card("072", "Zbrojnice",      "+2 útoku, trvale +1 důl útoku.",          cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddResource(ResourceType.ATTACK, 2),
                CardEffect.AddMine(ResourceType.ATTACK, 1))),
        Card("073", "Škola magie",    "+2 magie, trvale +1 důl magie.",          cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 2),
                CardEffect.AddMine(ResourceType.MAGIC, 1))),
        Card("074", "Tržiště",        "+3 magie, +3 útoku, +3 kameny. [Combo]",  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 3),
                CardEffect.AddResource(ResourceType.ATTACK, 3),
                CardEffect.AddResource(ResourceType.STONES, 3)), isCombo = true),
        Card("075", "Zlaté doly",     "Trvale +2 magie a +1 kameny/kolo.",       cost = 6, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 2),
                CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("076", "Vojenská základna","Trvale +2 útoku a +1 kameny/kolo.",     cost = 6, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.ATTACK, 2),
                CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("077", "Přeměna magie",  "Pokud máš >8 magie, získej +10 útoku.",  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 8),
                CardEffect.AddResource(ResourceType.ATTACK, 10)
            ))),
        Card("078", "Upíří drak",     "Přímý zásah: hrad −10, ukradni 4 magie a 4 útoku, +2 chaosu.", cost = 8, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackCastle(10),
                CardEffect.StealResource(ResourceType.MAGIC, 4),
                CardEffect.StealResource(ResourceType.ATTACK, 4),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("079", "Obléhání",       "Zaútočí za 12, soupeř ztratí 3 magie, +2 chaosu.", cost = 5, costType = ResourceType.ATTACK, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackPlayer(12),
                CardEffect.DrainResource(ResourceType.MAGIC, 3),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("080", "Velkovýroba",    "+2 každý důl, +3 magie ihned.",           cost = 6, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 2),
                CardEffect.AddMine(ResourceType.ATTACK, 2),
                CardEffect.AddMine(ResourceType.STONES, 2),
                CardEffect.AddResource(ResourceType.MAGIC, 3))),

        // ── Stavba – nové posily (STONES buff) ─────────────────────────────

        Card("081", "Rychlá hradba", "Postaví hradby +7.", cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(7))),

        Card("082", "Masivní zeď", "Postaví hradby +16.", cost = 6, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(16))),

        Card("083", "Nouzové opevnění", "Pokud máš <10 hradeb, postav +16.", cost = 3, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallBelow(10),
                CardEffect.BuildWall(16)
            ))),

        Card("084", "Velká oprava", "Hradby +3 a hrad +7.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(
                CardEffect.BuildWall(3),
                CardEffect.BuildCastle(7)
            )),

        Card("085", "Královská obnova", "Oprav hrad o 15.", cost = 7, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(15))),

        Card("086", "Zednická rota", "Hradby +8 a hrad +6.", cost = 5, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.BuildWall(8),
                CardEffect.BuildCastle(6)
            )),

        Card("087", "Pevnost", "Hradby +10 a hrad +8.", cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.BuildWall(10),
                CardEffect.BuildCastle(8)
            )),

        Card("088", "Zesílené hradby", "Hradby +10. Pokud máš >30 hradu, +6 navíc.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.BuildWall(10),
                CardEffect.ConditionalEffect(
                    Condition.CastleAbove(30),
                    CardEffect.BuildWall(6)
                )
            )),

        Card("089", "Architekt", "Hradby +5, trvale +1 důl kamene/kolo.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.BuildWall(5),
                CardEffect.AddMine(ResourceType.STONES, 1)
            )),

        Card("090", "Velkostavba", "Trvale +2 doly kamene/kolo.", cost = 7, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.STONES, 2))),

        Card("091", "Barikády", "Hradby +9. Soupeř ztratí 2 útoku.", cost = 3, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.BuildWall(9),
                CardEffect.DrainResource(ResourceType.ATTACK, 2)
            )),

        Card("092", "Strategická výstavba", "Pokud máš >20 hradeb, postav +15.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallAbove(20),
                CardEffect.BuildWall(15)
            ))),

        Card("094", "Sklad materiálu", "Hradby +6, získej +2 kameny. [Combo]", cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(
                CardEffect.BuildWall(6),
                CardEffect.AddResource(ResourceType.STONES, 2)
            ), isCombo = true),

        Card("095", "Obchod s kamenem", "Získej +5 kamenů. [Combo]", cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.STONES, 5)), isCombo = true),

        Card("096", "Nedobytná pevnost", "Hradby +25.", cost = 10, costType = ResourceType.STONES, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.BuildWall(25))),

        Card("097", "Obnova království", "Hrad +25.", cost = 13, costType = ResourceType.STONES, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.BuildCastle(25))),

        // ── Chaos (platí CHAOS) ───────────────────────────────────────
        // Chaos se nezískává z dolů – pouze z karet. Karty platící Chaosem
        // jsou silné, ale vyžadují nejdřív Chaos nashromáždit.

        // Karty generující Chaos (platí MAGIC – vstupní bod do Chaos ekonomiky)
        Card("C01", "Chaotická jiskra",  "+2 chaosu. [Combo]",                    cost = 0, costType = ResourceType.MAGIC,  rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 2)), isCombo = true),
        Card("C02", "Entropie",          "+5 chaosu. Soupeř ztratí 2 magie.",     cost = 3, costType = ResourceType.MAGIC,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 5),
                CardEffect.DrainResource(ResourceType.MAGIC, 2))),
        Card("C03", "Chaotický důl",     "Trvale +1 důl chaosu/kolo.",            cost = 4, costType = ResourceType.MAGIC,  rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AddMine(ResourceType.CHAOS, 1))),
        Card("C04", "Krádež chaosu",     "Ukraď 3 chaos od soupeře. [Combo]",     cost = 2, costType = ResourceType.MAGIC,  rarity = Rarity.RARE,
            effects = listOf(CardEffect.StealResource(ResourceType.CHAOS, 3)), isCombo = true),

        // Karty platící Chaosem – silné efekty
        Card("C05", "Chaotický výbuch",  "Přímý zásah: hrad −15, ignoruje hradby.", cost = 6, costType = ResourceType.CHAOS,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackCastle(15))),
        Card("C06", "Bouře chaosu",      "Zaútočí na nepřítele za 20.",           cost = 6, costType = ResourceType.CHAOS,  rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackPlayer(20))),
        Card("C07", "Chaotický štít",    "Hradby +20.",                           cost = 4, costType = ResourceType.CHAOS,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(20))),
        Card("C08", "Zázrak chaosu",     "Hrad +15.",                             cost = 5, costType = ResourceType.CHAOS,  rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.BuildCastle(15))),
        Card("C09", "Chaotická krize",   "Soupeř ztratí 6 od každého zdroje.",   cost = 5, costType = ResourceType.CHAOS,  rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.DrainResource(ResourceType.MAGIC, 6),
                CardEffect.DrainResource(ResourceType.ATTACK, 6),
                CardEffect.DrainResource(ResourceType.STONES, 6))),
        Card("C10", "Chaotický drak",    "Zaútočí za 15, hrad −12 přímo.",        cost = 7, costType = ResourceType.CHAOS, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackPlayer(15), CardEffect.AttackCastle(12))),
        Card("C11", "Chaos a řád",       "Hrad +8 a hradby +8.",                  cost = 4, costType = ResourceType.CHAOS,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(8), CardEffect.BuildWall(8))),
        Card("C12", "Anarchie",          "Ukraď 5 od každého zdroje soupeře.",    cost = 7, costType = ResourceType.CHAOS,  rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.StealResource(ResourceType.MAGIC, 5),
                CardEffect.StealResource(ResourceType.ATTACK, 5),
                CardEffect.StealResource(ResourceType.STONES, 5))),

        // ── Chaos – ničení dolů ───────────────────────────────────────
        Card("C13", "Sabotáž",           "Znič 1 důl magie soupeře.",             cost = 5, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DestroyMine(ResourceType.MAGIC, 1))),
        Card("C14", "Ničení kamenolomu", "Znič 1 důl kamene soupeře.",            cost = 5, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DestroyMine(ResourceType.STONES, 1))),
        Card("C15", "Zákeřnost",         "Znič 1 útočný výcvik soupeře.",         cost = 5, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DestroyMine(ResourceType.ATTACK, 1))),
        Card("C16", "Velká sabotáž",     "Znič 1 důl magie a 1 důl kamene.",     cost = 7, costType = ResourceType.CHAOS, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.DestroyMine(ResourceType.MAGIC, 1),
                CardEffect.DestroyMine(ResourceType.STONES, 1))),

        // ── Chaos – krádež karet ──────────────────────────────────────
        Card("C17", "Telekineze",        "Ukraď 1 náhodnou kartu ze soupeřovy ruky.", cost = 3, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.StealCard(1))),
        Card("C18", "Chaos loupe",       "Ukraď 2 náhodné karty ze soupeřovy ruky.", cost = 5, costType = ResourceType.CHAOS, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.StealCard(2))),

        // ── Chaos – ničení karet ──────────────────────────────────────
        Card("C19", "Spálená knihovna",  "Znič 2 náhodné karty ze soupeřovy ruky.", cost = 4, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BurnCard(2))),
        Card("C20", "Prázdná mysl",      "Znič 3 náhodné karty ze soupeřovy ruky.", cost = 6, costType = ResourceType.CHAOS, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.BurnCard(3))),

        // ── Chaos – přidání karet do balíčku ─────────────────────────
        Card("C21", "Replikace",         "Přidej 3 kopie 'Šípy' do svého balíčku.",          cost = 1, costType = ResourceType.CHAOS, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddCardsToDeck("008", 2))),
        Card("C22", "Chaos manufaktura", "Přidej 2 'Chaos výbuch' do svého balíčku.",        cost = 2, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddCardsToDeck("C05", 2))),
        Card("C23", "Klonování",         "Přidej 2 kopie 'Základní útok' do svého balíčku.", cost = 1, costType = ResourceType.CHAOS, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddCardsToDeck("001", 2))),

        // ── Chaos – nové generátory ───────────────────────────────────
        Card("C24", "Temný rituál",      "+5 chaosu. [Combo]",                     cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 5)), isCombo = true),
        Card("C25", "Nestabilní vír",    "+2 chaosu a +2 magie. [Combo]",          cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 2),
                CardEffect.AddResource(ResourceType.MAGIC, 2)), isCombo = true),
        Card("C26", "Krvavá oběť",       "+4 chaosu. [Combo]",                     cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 4)), isCombo = true),
        Card("C27", "Odraz magie",       "Pokud máš >5 magie, získej +7 chaosu.", cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 5),
                CardEffect.AddResource(ResourceType.CHAOS, 7)
            ))),
        Card("C28", "Chaotická trofej",  "Ukraď 2 útoku, +3 chaosu. [Combo]",     cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.StealResource(ResourceType.ATTACK, 2),
                CardEffect.AddResource(ResourceType.CHAOS, 3)), isCombo = true),
        Card("C29", "Bouřlivá mysl",     "+3 chaosu a trvale +1 důl chaosu.",     cost = 5, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 3),
                CardEffect.AddMine(ResourceType.CHAOS, 1))),
        Card("C30", "Chrám chaosu",      "Trvale +2 doly chaosu/kolo.",           cost = 7, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AddMine(ResourceType.CHAOS, 2))),
        Card("C31", "Chaotický výměník", "+4 chaosu, soupeř ztratí 2 každého zdroje.", cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddResource(ResourceType.CHAOS, 4),
                CardEffect.DrainResource(ResourceType.MAGIC, 2),
                CardEffect.DrainResource(ResourceType.ATTACK, 2),
                CardEffect.DrainResource(ResourceType.STONES, 2))),
        // Vzájemná zkáza: chaos zasáhne obě strany – soupeř dostane více, ale ty taky zaplatíš.
        Card("C32", "Vzájemná zkáza",   "Soupeř −15 HP, vlastní hrad −7.",           cost = 5, costType = ResourceType.CHAOS, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AttackPlayer(15), CardEffect.BuildCastle(-7))),

        // ── Lízni karet ────────────────────────────────────────────────────────
        // Nová mechanika: DrawCard – líže karty z balíčku přímo do ruky.
        // Přebytečné karty (ruka plná) shoří jako u normálního lízání.
        Card("D01", "Průzkumník",      "Lízni 1 kartu. [Combo]",                     cost = 1, costType = ResourceType.MAGIC,  rarity = Rarity.COMMON,
            effects = listOf(CardEffect.DrawCard(1)), isCombo = true),
        Card("D02", "Věštba",          "Lízni 2 karty.",                              cost = 3, costType = ResourceType.MAGIC,  rarity = Rarity.RARE,
            effects = listOf(CardEffect.DrawCard(2))),
        Card("D03", "Kronika",         "Lízni 3 karty.",                              cost = 5, costType = ResourceType.MAGIC,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DrawCard(3))),
        Card("D04", "Bojová taktika",  "Zaútočí za 4. Lízni 1 kartu.",               cost = 2, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(4), CardEffect.DrawCard(1))),
        Card("D05", "Stavební plány",  "Hradby +4. Lízni 1 kartu. [Combo]",                  cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(4), CardEffect.DrawCard(1)), isCombo = true),
        Card("D06", "Elitní zvěd",     "Zaútočí za 8. Lízni 1 kartu.",               cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(8), CardEffect.DrawCard(1))),
        Card("D07", "Tajná knihovna",  "Lízni 2 karty. Trvale +1 důl magie.",        cost = 5, costType = ResourceType.MAGIC,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.DrawCard(2), CardEffect.AddMine(ResourceType.MAGIC, 1))),
        Card("D08", "Vize",            "Pokud máš >4 magie, líz 2 karty.",          cost = 2, costType = ResourceType.MAGIC,  rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 4),
                CardEffect.DrawCard(2)))),

        // ── Speciální útočné karty ────────────────────────────────────────────
        // Hod cihlou: obětuje část vlastních hradeb a hodí je na soupeřův hrad.
        // Čím méně hradeb máš, tím riskantnější – ale silné, když na nich nezáleží.
        Card("098", "Hod cihlou",      "Vlastní hradby −4, zaútočí na nepřítele za 11.", cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(-4), CardEffect.AttackPlayer(11))),

        // Vampirismus hradu: vysaje životy přímo z nepřátelského hradu do vlastního.
        // Na rozdíl od běžného útoku zároveň léčí – ideální při nízkém HP hradu.
        Card("099", "Vampirismus hradu","Ukradni 10 životů hradu soupeři.",          cost = 7, costType = ResourceType.MAGIC,  rarity = Rarity.EPIC,
            effects = listOf(CardEffect.StealCastle(10))),
        // Obléhací sání: útočný variant krádeže hradu – kombinuje přímý útok se sáním životů.
        Card("100", "Obléhací sání",    "Zaútočí za 5, ukradni 6 životů hradu.",     cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(5), CardEffect.StealCastle(6))),

        // ── Testovací karta s texturou ─────────────────────────────────────
        // artResId: až přidáš res/drawable/art_goblin.png, nahraď null za R.drawable.art_goblin
        // frameResId: rám se načítá automaticky z res/drawable/card_frame.png
        Card("T01", "Goblin šaman",
            description = "Vyčaruje magii z chaosu za cenu vlastní krve.",
            cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 5)),
            artResId = null,   // → nahraď: R.drawable.art_goblin
            type = "Kouzlo"
        ),
    )

    // ── Deck sloty ────────────────────────────────────────────────────────────
    val decks = androidx.compose.runtime.mutableStateListOf(
        Deck(0, "Balíček 1"),
        Deck(1, "Balíček 2"),
        Deck(2, "Balíček 3")
    )
    var activeDeckIndex = androidx.compose.runtime.mutableStateOf(0)
        private set

    init { loadDecks() }

    private fun saveDeck(index: Int) {
        val value = decks[index].cardCounts.entries
            .joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString("deck_$index", value).apply()
    }

    private fun loadDecks() {
        decks.forEachIndexed { i, deck ->
            val name = prefs.getString("deck_name_$i", deck.name) ?: deck.name
            val str  = prefs.getString("deck_$i", "") ?: ""
            val cardCounts = if (str.isNotEmpty()) {
                str.split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null)
                    else null
                }.toMap()
            } else emptyMap()
            decks[i] = deck.copy(name = name, cardCounts = cardCounts)
        }
        activeDeckIndex.value = prefs.getInt("active_deck", 0)
    }

    fun renameDeck(index: Int, name: String) {
        val trimmed = name.trim().take(20).ifEmpty { "Balíček ${index + 1}" }
        decks[index] = decks[index].copy(name = trimmed)
        prefs.edit().putString("deck_name_$index", trimmed).apply()
    }

    fun setActiveDeck(index: Int) {
        activeDeckIndex.value = index
        prefs.edit().putInt("active_deck", index).apply()
    }

    fun setCardCount(deckIndex: Int, cardId: String, count: Int) {
        val deck = decks[deckIndex]
        val maxCopies = allCards.find { it.id == cardId }?.rarity?.maxCopies ?: 1
        val newCounts = deck.cardCounts.toMutableMap()
        if (count <= 0) newCounts.remove(cardId) else newCounts[cardId] = count.coerceAtMost(maxCopies)
        decks[deckIndex] = deck.copy(cardCounts = newCounts)
        saveDeck(deckIndex)
    }

    fun clearDeck(deckIndex: Int) {
        decks[deckIndex] = decks[deckIndex].copy(cardCounts = emptyMap())
        saveDeck(deckIndex)
    }

    // ── Předpřipravené šablony (každá přesně 30 karet) ───────────────────────
    val presetTemplates: List<Pair<String, Map<String, Int>>> = listOf(

        // 1. ⚔️ Útočník – přímý útok pomocí ATTACK zdrojů (30 karet)
        "⚔️ Útočník" to mapOf(
            // Přímý útok (ATTACK)
            "027" to 2,   // Válečný buben  – útok −4 + 2 útoku, cena 2
            "046" to 4,   // Goblin         – útok −2 + krádež, cena 1
            "056" to 3,   // Nájezdník
            "022" to 2,   // Přímý zásah    – hrad −8, cena 3
            "026" to 2,
            "024" to 2,
            "021" to 1,
            "052" to 1,
            "051" to 1,
            "078" to 1,
            "055" to 2,   // Mravenci       – útok −3 + podmíněně hrad −8, cena 2
            "054" to 2,   // Válečný pochod – útok −13 + 2atk, cena 5
            // Generování ATTACK zdrojů (MAGIC)
            "004" to 2,   // Magie            – +4 magie, zdarma
            "038" to 3,   // Vojenský rozkaz – +7 útoku, cena 2
            "043" to 2,   // Výcvikové centrum – +2 doly útoku, cena 4
        ),

        // 2. 🏰 Obránce – postavit hrad na 100 HP pomocí STONES zdrojů (30 karet)
        "🏰 Obránce" to mapOf(
                "095"   to 4,   // Obchod s kamenem
                "057"   to 3,   // Bašta
                "083"   to 2,   // Nouzové opevnění
                "094"   to 4,   // Sklad materiálu
                "089"   to 3,   // Architekt
                "084"   to 4,   // Velká oprava
                "062"   to 2,   // Obranná aliance
                "032"   to 2,   // Citadela
                "085"   to 2,   // Královská obnova
                "096"   to 1,   // Nedobytná pevnost
                "097"   to 1,   // Obnova království
                "011"   to 2,   // Zásoby kamene
            ),  // 30 karet


        // 3. 💰 Ekonom – budovat doly pro zdrcující ekonomiku (30 karet)
        "💰 Ekonom" to mapOf(
                "046"   to 4,   // Goblin
                "095"   to 2,   // Obchod s kamenem
                "057"   to 3,   // Bašta
                "094"   to 3,   // Sklad materiálu
                "083"   to 2,   // Nouzové opevnění
                "062"   to 2,   // Obranná aliance
                "097"   to 1,   // Obnova království
                "037"   to 3,   // Rychlá magie
                "041"   to 2,   // Magické trio
                "013"   to 2,   // Magický pramen
                "042"   to 1,   // Velký kamenolom
                "080"   to 1,   // Velkovýroba
                "045"   to 1,   // Zlatý důl
                "C30"   to 1,   // Chrám chaosu
                "C22"   to 2,   // Chaos manufaktura
            ),  // 30 karet

        // 4. 🌀 Chaosmancer – Chaos ekonomika + sabotáž soupeře (30 karet)
        "🌀 Chaosmancer" to mapOf(
                "046"   to 4,   // Goblin
                "C26"   to 3,   // Krvavá oběť
                "C24"   to 3,   // Temný rituál
                "C27"   to 2,   // Odraz magie
                "C02"   to 2,   // Entropie
                "C03"   to 1,   // Chaotický důl
                "C31"   to 2,   // Chaotický výměník
                "C29"   to 2,   // Bouřlivá mysl
                "C19"   to 2,   // Spálená knihovna
                "C14"   to 1,   // Ničení kamenolomu
                "C13"   to 2,   // Sabotáž
                "C15"   to 2,   // Zákeřnost
                "C05"   to 2,   // Chaotický výbuch
                "C06"   to 1,   // Bouře chaosu
                "C10"   to 1,   // Chaotický drak
            ),  // 30 karet
    )

    fun loadPreset(deckIndex: Int, presetIndex: Int) {
        decks[deckIndex] = decks[deckIndex].copy(
            cardCounts = presetTemplates[presetIndex].second
        )
        saveDeck(deckIndex)
    }

    var gameState = androidx.compose.runtime.mutableStateOf(createInitialState())
        private set
    var log = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        private set
    var gameOver = androidx.compose.runtime.mutableStateOf<GameResult?>(null)
        private set
    var lastCard           = androidx.compose.runtime.mutableStateOf<Card?>(null);          private set
    var lastCardAction     = androidx.compose.runtime.mutableStateOf(CardAction.PLAYED);   private set
    var lastCardIsPlayer   = androidx.compose.runtime.mutableStateOf(true);                private set
    var cardHistory        = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    /** Karty ztracené hráčem kvůli BurnCard / StealCard AI (celá hra). */
    var lostToOpponent     = androidx.compose.runtime.mutableStateOf<List<CardHistoryEntry>>(emptyList()); private set
    // Combo: hráč zahrál combo kartu – kolo nepokračuje automaticky
    var isPlayerComboTurn = androidx.compose.runtime.mutableStateOf(false)
        private set

    // ── Mulligan ──────────────────────────────────────────────────────────────
    var isMulligan = androidx.compose.runtime.mutableStateOf(true)
        private set
    var mulliganSelected = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleMulliganCard(cardId: String) {
        val cur = mulliganSelected.value
        mulliganSelected.value = if (cardId in cur) cur - cardId else cur + cardId
    }

    fun confirmMulligan() {
        if (mulliganSelected.value.isEmpty()) { skipMulligan(); return }
        val old    = gameState.value
        val player = old.playerState.deepCopy()
        val ids    = mulliganSelected.value

        val returned = player.hand.filter { it.id in ids }
        player.hand.removeAll { it.id in ids }
        player.deck.addAll(returned)
        player.deck.shuffle()
        player.drawCards(returned.size)

        gameState.value        = old.copy(playerState = player)
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        maybeStartAiFirstTurn()
    }

    fun skipMulligan() {
        isMulligan.value       = false
        mulliganSelected.value = emptySet()
        maybeStartAiFirstTurn()
    }

    /**
     * Pokud AI začíná jako první hráč, spustí její tah automaticky po mulliganu.
     * Počáteční hráč (AI) nelíže první kartu – pravidlo stejné jako u hráče.
     * Hráč (druhý hráč) taktéž nelíže bonusovou kartu – oba začínají se 5 z mulliganu.
     */
    private fun maybeStartAiFirstTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.AI) {
            // Hráč začíná jako první → vygeneruj mu zdroje pro první tah
            val player = old.playerState.deepCopy()
            player.generateResources()
            gameState.value = old.copy(playerState = player)
            addLog("Hráč začíná jako první!")
            return
        }

        addLog("AI začíná jako první!")

        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()
        // AI je počáteční hráč → nesmí lízat první kartu (aiDrawsAtStart = false)
        // Hráč jako druhý hráč si lízne 1 kartu před svým prvním tahem (playerDrawsAtEnd = true)
        finishTurn(old, player, ai, aiDrawsAtStart = false, playerDrawsAtEnd = true)
    }

    // ── Náhodný balíček 30 karet (max 2 kopie každé karty) ───────────────────
    private fun randomDeck(): List<Card> =
        (allCards + allCards).shuffled().take(30)

    private fun createInitialState(): GameState {
        val activeDeck  = decks[activeDeckIndex.value]
        val playerCards = if (activeDeck.isValid) {
            activeDeck.toCardList(allCards)
        } else {
            randomDeck()
        }.withUniqueIds().shuffled()

        val playerState = PlayerState().also {
            it.deck.addAll(playerCards)
            it.drawCards(5)   // 5 karet na mulligan
        }
        val aiState = PlayerState().also {
            it.deck.addAll(randomDeck().withUniqueIds().shuffled())
            it.drawCards(5)   // AI taktéž
        }

        // Náhodně rozhodne, kdo začíná (zobrazí se po mulliganu)
        val firstPlayer = if (Random.nextBoolean()) ActivePlayer.PLAYER else ActivePlayer.AI
        return GameState(playerState = playerState, aiState = aiState, activePlayer = firstPlayer)
    }

    fun playCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Affordability check
        if ((player.resources[card.costType] ?: 0) < card.cost) {
            addLog("Nedostatek ${card.costType.label} pro: ${card.name}")
            return
        }

        // 1. Efekty (vč. podmínek) se vyhodnotí PŘED odečtením zdrojů
        // Před aplikací: zaznamenej nesplněné podmínky pro hráče
        card.effects.filterIsInstance<CardEffect.ConditionalEffect>().forEach { ce ->
            if (!checkCondition(ce.condition, player)) {
                addLog("${card.name}: podmínka nesplněna!")
            }
        }
        applyEffects(card.effects, player, ai, allCards)

        // 2. Zaplatit a přesunout kartu
        player.resources[card.costType] = (player.resources[card.costType] ?: 0) - card.cost
        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.PLAYED, isPlayer = true)
        addLog("Hráč zahrál: ${card.name}")
        playSoundForCard(card)

        val s1 = old.copy(playerState = player, aiState = ai)
        s1.checkWinCondition()?.let { result ->
            if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
            isPlayerComboTurn.value = false
            gameOver.value = result; gameState.value = s1; return
        }

        if (card.isCombo) {
            // Combo: neukončuj kolo, hráč může hrát dál
            isPlayerComboTurn.value = true
            gameState.value = s1
        } else {
            isPlayerComboTurn.value = false
            finishTurn(old, player, ai)
        }
    }

    /** Hráč explicitně ukončí tah (po sehrání combo karet). */
    fun endPlayerTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()
        isPlayerComboTurn.value = false
        addLog("Hráč ukončil tah")
        finishTurn(old, player, ai)
    }

    fun waitTurn() {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        // Čekat = přeskočit tah bez akce.
        // Karta se líže automaticky na začátku DALŠÍHO kola (v finishTurn).
        addLog("Hráč přeskočil kolo")
        isPlayerComboTurn.value = false
        finishTurn(old, player, ai, playerWaited = true)
    }

    fun discardCard(card: Card) {
        val old = gameState.value
        if (old.activePlayer != ActivePlayer.PLAYER) return
        val player = old.playerState.deepCopy()
        val ai     = old.aiState.deepCopy()

        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.DISCARDED, isPlayer = true)
        addLog("Hráč zahodil: ${card.name}")
        SoundManager.playDiscard()

        isPlayerComboTurn.value = false
        finishTurn(old, player, ai)
    }

    private fun finishTurn(
        old: GameState, player: PlayerState, ai: PlayerState,
        aiDrawsAtStart: Boolean = true,
        playerDrawsAtEnd: Boolean = true,
        playerWaited: Boolean = false
    ) {
        // Zablokuj hráče – AI je na tahu
        gameState.value = old.copy(
            playerState  = player,
            aiState      = ai,
            activePlayer = ActivePlayer.AI
        )

        viewModelScope.launch {
            delay((500L..1000L).random())

            // ── Tah AI ────────────────────────────────────────────────────────
            // AI dostane zdroje a líže 1 kartu na ZAČÁTKU svého tahu
            ai.generateResources()
            if (aiDrawsAtStart) ai.drawCards(1)

            // AI hraje v cyklu (podporuje combo karty)
            var aiContinues = true
            while (aiContinues) {
                val aiChoice = aiChooseAction(ai, player)
                when (aiChoice) {
                    is AiAction.Play -> {
                        val aiCard = aiChoice.card
                        applyEffects(aiCard.effects, ai, player, allCards) { card, action ->
                            recordOpponentLoss(card, action)
                        }
                        ai.resources[aiCard.costType] = (ai.resources[aiCard.costType] ?: 0) - aiCard.cost
                        ai.hand.remove(aiCard)
                        ai.discardPile.add(aiCard)
                        recordCard(aiCard, CardAction.PLAYED, isPlayer = false)
                        addLog("AI zahrála: ${aiCard.name}")
                        playSoundForCard(aiCard)

                        if (aiCard.isCombo) {
                            // Combo: krátká pauza + mezistate + pokračuj
                            val mid = old.copy(playerState = player, aiState = ai)
                            mid.checkWinCondition()?.let { result ->
                                if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
                                gameOver.value = result; gameState.value = mid; return@launch
                            }
                            gameState.value = mid   // vizuální mezistav
                            delay(450L)
                        } else {
                            aiContinues = false     // normální karta → konec tahu AI
                        }
                    }
                    is AiAction.Wait -> {
                        // AI čeká = přeskočí tah (líz byl na začátku tahu, žádný bonus)
                        addLog("AI čekala")
                        aiContinues = false
                        // Oba přeskočili kolo a oba mají prázdný balíček → rozhodne hrad
                        if (playerWaited && player.deck.isEmpty() && ai.deck.isEmpty()) {
                            val finalState = old.copy(playerState = player, aiState = ai)
                            val result = finalState.resolveByHp()
                            addLog("Oba přeskočili s prázdnými balíčky – konec hry!")
                            if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
                            gameOver.value = result; gameState.value = finalState; return@launch
                        }
                    }
                    is AiAction.Discard -> {
                        val toDiscard = aiChoice.card
                        ai.hand.remove(toDiscard)
                        ai.discardPile.add(toDiscard)
                        recordCard(toDiscard, CardAction.DISCARDED, isPlayer = false)
                        addLog("AI zahodila: ${toDiscard.name}")
                        aiContinues = false
                    }
                }
            }

            // ── Kontrola výhry po tahu AI ─────────────────────────────────────
            val s2 = old.copy(playerState = player, aiState = ai)
            s2.checkWinCondition()?.let { result ->
                if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
                gameOver.value = result; gameState.value = s2; return@launch
            }

            // ── Mezistav: hráč chvíli vidí AI s méně kartami (po sehrání, před lízem) ──
            gameState.value = s2
            delay(350L)

            // ── Konec kola: příprava hráčova tahu ────────────────────────────
            player.generateResources()
            if (playerDrawsAtEnd) {
                val burned = player.drawCards(1)
                burned.forEach { b -> addToHistory(b, CardAction.BURNED, isMine = true) }
            }

            // Kontrola po lízu: balíčky mohly dojít právě teď
            val s3 = old.copy(
                playerState  = player.deepCopy(),
                aiState      = ai.deepCopy(),
                currentTurn  = old.currentTurn + 1,
                activePlayer = ActivePlayer.PLAYER
            )
            s3.checkWinCondition()?.let { result ->
                if (result.isPlayerWin()) SoundManager.playWin() else SoundManager.playLose()
                gameOver.value = result; gameState.value = s3; return@launch
            }

            gameState.value = s3
        }
    }

    /** Karta ztracena hráčem kvůli efektu AI (BurnCard / StealCard). */
    private fun recordOpponentLoss(card: Card, action: CardAction) {
        addToHistory(card, action, isMine = true)
        val list = lostToOpponent.value.toMutableList()
        list.add(0, CardHistoryEntry(card, action, isMine = true))
        lostToOpponent.value = list
        val verb = if (action == CardAction.STOLEN) "ukradla" else "spálila"
        addLog("AI $verb: ${card.name}")
    }

    private fun addLog(message: String) {
        log.value = (log.value + message).takeLast(6)
    }

    private fun recordCard(card: Card, action: CardAction, isPlayer: Boolean) {
        lastCard.value         = card
        lastCardAction.value   = action
        lastCardIsPlayer.value = isPlayer
        addToHistory(card, action, isMine = isPlayer)
    }

    private fun addToHistory(card: Card, action: CardAction, isMine: Boolean) {
        val h = cardHistory.value.toMutableList()
        h.add(0, CardHistoryEntry(card, action, isMine))
        if (h.size > 20) h.removeAt(h.size - 1)
        cardHistory.value = h
    }

    fun restartGame() {
        gameOver.value          = null
        log.value               = emptyList()
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
        gameState.value         = createInitialState()
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
    }

    // ── AI heuristika ─────────────────────────────────────────────────────────

    private sealed class AiAction {
        data class Play(val card: Card)    : AiAction()
        data class Discard(val card: Card) : AiAction()
        object Wait                        : AiAction()
    }

    /**
     * Vybere akci AI podle situace na hracím poli.
     * Priority:
     *  1. Zahraj kartu, pokud na ni má zdroje a je „chytrá" volba
     *  2. Čekej, pokud v ruce není nic hratelného ale balíček není prázdný
     *  3. Zahoď nejlevnější kartu
     *
     * Skórování:
     *  - Každý efekt karty se ohodnotí samostatně (včetně rekurzivního vnitřního efektu podmínky).
     *  - Podmínkový efekt přidá skóre vnitřního efektu JEN tehdy, když podmínka platí; jinak 0.
     *  - Skóre = součet efektů − cena karty (vyšší cena = větší riziko).
     *  - Pokud má nejlepší dostupná karta záporné nebo nulové skóre (vše podmíněné a nesplněné),
     *    AI raději počká (aby příště lízla lepší kartu).
     */
    private fun aiChooseAction(ai: PlayerState, opponent: PlayerState): AiAction {
        val affordable = ai.hand.filter { (ai.resources[it.costType] ?: 0) >= it.cost }

        if (affordable.isEmpty()) {
            return if (ai.deck.isNotEmpty()) AiAction.Wait
            else ai.hand.minByOrNull { it.cost }?.let { AiAction.Discard(it) } ?: AiAction.Wait
        }

        // Situační příznaky
        val aiLowHp   = ai.castleHP   < 15
        val aiLowWall = ai.wallHP     < 5
        val oppLowHp  = opponent.castleHP < 20
        val chaos     = ai.resources[ResourceType.CHAOS] ?: 0

        // Rekurzivní ohodnocení jednoho efektu v kontextu stavu AI
        fun scoreEffect(fx: CardEffect): Int = when (fx) {
            is CardEffect.AttackPlayer   -> if (oppLowHp) 18 else 8
            is CardEffect.AttackCastle   -> if (oppLowHp) 20 else 6
            is CardEffect.AttackWall     -> 4
            // Záporný amount = poškození vlastního hradu (penalta)
            is CardEffect.BuildCastle    -> if (fx.amount >= 0) {
                if (aiLowHp) 18 else 4
            } else {
                fx.amount * 2  // záporné skóre za ztrátu HP hradu (závažnější než ztráta hradeb)
            }
            // Záporný amount = obětování vlastních hradeb (penalta)
            is CardEffect.BuildWall      -> if (fx.amount >= 0) {
                if (aiLowWall) 15 else 5
            } else {
                fx.amount  // záporné skóre za ztrátu hradeb
            }
            is CardEffect.AddMine        -> 9   // long-term value
            is CardEffect.StealResource  -> 7
            is CardEffect.DrainResource  -> 6
            is CardEffect.AddResource    -> 3
            is CardEffect.DestroyMine    -> 11  // very strong disruption
            is CardEffect.StealCard      -> 8
            is CardEffect.BurnCard       -> 6
            is CardEffect.AddCardsToDeck -> 4
            is CardEffect.DrawCard       -> fx.count * 5   // líz = více možností
            // Krádež hradu: poškodí soupeře A léčí vlastní hrad
            is CardEffect.StealCastle    -> fx.amount + (if (oppLowHp) 8 else 0) + (if (aiLowHp) 8 else 0)
            // Podmínkový efekt: skóruj vnitřní efekt pouze pokud podmínka platí; jinak 0
            is CardEffect.ConditionalEffect ->
                if (checkCondition(fx.condition, ai)) scoreEffect(fx.effect) else 0
        }

        // Celkové skóre karty = suma efektů − cena (vyšší cena = penalta)
        fun score(card: Card): Int {
            val effectScore = card.effects.sumOf { scoreEffect(it) }
            val chaosBlock  = if (card.costType == ResourceType.CHAOS && chaos < card.cost) 100 else 0
            val noise       = (-2..2).random()
            return effectScore - card.cost - chaosBlock + noise
        }

        // Předpočítej skóre jednou (score() obsahuje náhodu, nevolej dvakrát)
        val scored = affordable.map { it to score(it) }
        val (best, bestScore) = scored.maxByOrNull { it.second } ?: return AiAction.Wait

        // Pokud je i nejlepší karta nevýhodná (podmínka nesplněna, čisté náklady),
        // AI raději počká a lízne lepší kartu – neplýtvá tahem na bezcennou kartu
        if (bestScore <= 0 && ai.deck.isNotEmpty()) return AiAction.Wait

        return AiAction.Play(best)
    }

    // ── Aréna ─────────────────────────────────────────────────────────────────
    var arenaPhase  = androidx.compose.runtime.mutableStateOf<ArenaPhase?>(null)
        private set
    val arenaDraft  = androidx.compose.runtime.mutableStateListOf<Card>()
    var arenaOffers = androidx.compose.runtime.mutableStateOf<List<Card>>(emptyList())
        private set
    var arenaWins   = androidx.compose.runtime.mutableStateOf(0)
        private set

    fun startArena() {
        arenaDraft.clear()
        arenaWins.value  = 0
        arenaPhase.value = ArenaPhase.DRAFT
        generateArenaOffers()
    }

    private fun generateArenaOffers() {
        arenaOffers.value = allCards.shuffled().take(3)
    }

    fun pickArenaCard(card: Card) {
        arenaDraft.add(card)
        if (arenaDraft.size >= 30) startArenaBattle() else generateArenaOffers()
    }

    private fun startArenaBattle() {
        arenaPhase.value = ArenaPhase.BATTLE
        val ps = PlayerState().also {
            it.deck.addAll(arenaDraft.toList().withUniqueIds().shuffled())
            it.drawCards(5)
        }
        val ai = PlayerState().also {
            it.deck.addAll(randomDeck().withUniqueIds().shuffled())
            it.drawCards(5)
        }
        gameState.value         = GameState(playerState = ps, aiState = ai)
        gameOver.value          = null
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
        lostToOpponent.value    = emptyList()
        isPlayerComboTurn.value = false
        isMulligan.value        = true
        mulliganSelected.value  = emptySet()
        log.value               = emptyList()
    }

    fun onArenaWin() {
        arenaWins.value++
        startArenaBattle()
    }

    fun onArenaLose() {
        arenaPhase.value = ArenaPhase.ENDED
    }

    fun exitArena() {
        arenaPhase.value = null
        arenaDraft.clear()
        arenaWins.value  = 0
    }

    private fun playSoundForCard(card: Card) {
        when (card.effects.firstOrNull()) {
            is CardEffect.AttackCastle,
            is CardEffect.AttackWall,
            is CardEffect.StealResource,
            is CardEffect.DrainResource,
            is CardEffect.DestroyMine,
            is CardEffect.BurnCard       -> SoundManager.playAttack()
            is CardEffect.BuildCastle,
            is CardEffect.BuildWall      -> SoundManager.playBuild()
            is CardEffect.AddMine        -> SoundManager.playBuild()
            is CardEffect.AddResource,
            is CardEffect.AddCardsToDeck -> SoundManager.playResource()
            is CardEffect.StealCard      -> SoundManager.playAttack()
            else                         -> SoundManager.playCardPlay()
        }
    }
}

// Extension pro čitelné názvy zdrojů v logu
val ResourceType.label get() = when (this) {
    ResourceType.MAGIC  -> "magie"
    ResourceType.ATTACK -> "útoku"
    ResourceType.STONES -> "kamene"
    ResourceType.CHAOS  -> "chaosu"
}
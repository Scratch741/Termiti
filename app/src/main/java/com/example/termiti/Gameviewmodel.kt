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
        Card("003", "Ohnivá koule",   "Zaútočí na nepřítele za 6.",              cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(6))),
        Card("007", "Katapult",       "Zaútočí na nepřítele za 10.",             cost = 5, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AttackPlayer(10))),
        Card("006", "Podmíněný útok", "Pokud máš >5 útoku, udeř za 10.",        cost = 3, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.ATTACK, 5),
                CardEffect.AttackCastle(10)
            ))),
        Card("017", "Válečný sekyrník","Zaútočí na nepřítele za 7.",            cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AttackPlayer(7))),

        // ── Stavba (platí STONES) ─────────────────────────────────────
        Card("002", "Kamenná zeď",    "Postaví hradby +9.",                      cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(9))),
        Card("010", "Palisáda",       "Postaví hradby +5.",                      cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(5))),
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
        Card("032", "Citadela",       "Opraví hrad o 15.",                       cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(10))),
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
        Card("036", "Hradní příkop",  "Hradby +7. Pokud hrad >50, hradby +5.",   cost = 3, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.BuildWall(7),
                CardEffect.ConditionalEffect(
                    Condition.CastleAbove(50),
                    CardEffect.BuildWall(5)
                )
            )),

        // ── Zdroje – rozšíření ────────────────────────────────────────
        Card("037", "Rychlá magie",   "Okamžitě +4 magie. [Combo]",              cost = 1, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 4)), isCombo = true),
        Card("038", "Vojenský rozkaz","Okamžitě +7 útoku. [Combo]",             cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.ATTACK, 7)), isCombo = true),
        Card("039", "Stavební boom",  "Okamžitě +7 kamene. [Combo]",            cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.AddResource(ResourceType.STONES, 7)), isCombo = true),
        Card("040", "Alchymie",       "Pokud máš >4 magie, získej +8 útoku.",    cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 4),
                CardEffect.AddResource(ResourceType.ATTACK, 8)
            ))),
        Card("041", "Magické trio",   "+2 magie, +2 útoku, +2 kamene. [Combo]",  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.AddResource(ResourceType.MAGIC,  2),
                CardEffect.AddResource(ResourceType.ATTACK, 2),
                CardEffect.AddResource(ResourceType.STONES, 2)
            ), isCombo = true),

        // ── Doly – rozšíření ──────────────────────────────────────────
        Card("042", "Velký kamenolom","Trvale +2 doly kamene/kolo.",             cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.STONES, 2))),
        Card("043", "Výcvikové centrum","Trvale +2 doly útoku/kolo.",            cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.ATTACK, 2))),
        Card("044", "Trifekta dolů",  "+1 důl magie, útoku i kamene/kolo.",      cost = 6, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(
                CardEffect.AddMine(ResourceType.MAGIC,  1),
                CardEffect.AddMine(ResourceType.ATTACK, 1),
                CardEffect.AddMine(ResourceType.STONES, 1)
            )),
        Card("045", "Zlatý důl",      "Trvale +3 doly magie/kolo.",              cost = 7, costType = ResourceType.MAGIC, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 3))),

        // ── Útok – Arcomage/Mravenci inspirace ───────────────────────
        Card("046", "Goblin",         "Zaútočí za 2, ukraď 1 magii, +1 chaosu.", cost = 1, costType = ResourceType.ATTACK, rarity = Rarity.COMMON,
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
        Card("051", "Drak",           "Zaútočí za 12, hrad −8 přímo, +2 chaosu.", cost = 11, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackPlayer(12), CardEffect.AttackCastle(8),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("052", "Démon",          "Přímý zásah: hrad −16, ignoruje hradby. +2 chaosu.", cost = 15, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.AttackCastle(16),
                CardEffect.AddResource(ResourceType.CHAOS, 2))),
        Card("053", "Plamenomet",     "Poškodí jen hradby o 10, získej +2 útoku.", cost = 4, costType = ResourceType.ATTACK, rarity = Rarity.RARE,
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
        Card("058", "Obranný val",    "Hradby +15.",                             cost = 5, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(15))),
        Card("059", "Pevnostní hrad", "Hradby +5 a hrad +4.",                    cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(5), CardEffect.BuildCastle(4))),
        Card("060", "Chrám",          "Hrad +12.",                               cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(12))),
        Card("061", "Tunely",         "Pokud hrad >40, postav hradby +12.",       cost = 3, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(Condition.CastleAbove(40), CardEffect.BuildWall(12)))),
        Card("062", "Obranná aliance","Hradby +7, hrad +4, +2 kameny.",          cost = 5, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildWall(7), CardEffect.BuildCastle(4),
                CardEffect.AddResource(ResourceType.STONES, 2))),
        Card("063", "Věž strážní",    "Hradby +14.",                             cost = 5, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(14))),
        Card("064", "Zásobník",       "Pokud hrad >40, oprav hrad o 10.",        cost = 4, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.ConditionalEffect(Condition.CastleAbove(40), CardEffect.BuildCastle(10)))),

        // ── Sabotáž a krádež (platí MAGIC) ───────────────────────────
        Card("065", "Lupič",          "Ukraď 3 útoku od soupeře.",               cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.StealResource(ResourceType.ATTACK, 3))),
        Card("066", "Zlatokop",       "Ukraď 4 kameny od soupeře.",              cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.StealResource(ResourceType.STONES, 4))),
        Card("067", "Sabotér",        "Soupeř ztratí 5 kamenů.",                 cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.DrainResource(ResourceType.STONES, 5))),
        Card("068", "Demoralizace",   "Soupeř ztratí 5 útoku.",                  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.DrainResource(ResourceType.ATTACK, 5))),
        Card("069", "Dvojitý agent",  "Ukraď 3 magie a 3 útoku, +1 chaosu.",   cost = 4, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.StealResource(ResourceType.MAGIC, 3),
                CardEffect.StealResource(ResourceType.ATTACK, 3),
                CardEffect.AddResource(ResourceType.CHAOS, 1))),
        Card("070", "Krize zásobování","Soupeř ztratí 5 kamenů a 5 útoku.",     cost = 5, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
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
        Card("074", "Tržiště",        "+2 magie, +2 útoku, +2 kameny. [Combo]",  cost = 2, costType = ResourceType.MAGIC, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.MAGIC, 2),
                CardEffect.AddResource(ResourceType.ATTACK, 2),
                CardEffect.AddResource(ResourceType.STONES, 2)), isCombo = true),
        Card("075", "Zlaté doly",     "Trvale +2 magie a +1 kameny/kolo.",       cost = 5, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.MAGIC, 2),
                CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("076", "Vojenská základna","Trvale +2 útoku a +1 kameny/kolo.",     cost = 5, costType = ResourceType.MAGIC, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.AddMine(ResourceType.ATTACK, 2),
                CardEffect.AddMine(ResourceType.STONES, 1))),
        Card("077", "Přeměna magie",  "Pokud máš >8 magie, získej +10 útoku.",  cost = 3, costType = ResourceType.MAGIC, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.ResourceAbove(ResourceType.MAGIC, 8),
                CardEffect.AddResource(ResourceType.ATTACK, 10)
            ))),
        Card("078", "Upíří drak",     "Přímý zásah: hrad −10, ukraď 4 magie a 4 útoku, +2 chaosu.", cost = 8, costType = ResourceType.ATTACK, rarity = Rarity.LEGENDARY,
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

        Card("081", "Rychlá hradba", "Postaví hradby +10.", cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildWall(10))),

        Card("082", "Masivní zeď", "Postaví hradby +18.", cost = 6, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.BuildWall(18))),

        Card("083", "Nouzové opevnění", "Pokud máš <10 hradeb, postav +16.", cost = 3, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(CardEffect.ConditionalEffect(
                Condition.WallBelow(10),
                CardEffect.BuildWall(16)
            ))),

        Card("084", "Velká oprava", "Oprav hrad o 10.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.BuildCastle(10))),

        Card("085", "Královská obnova", "Oprav hrad o 18.", cost = 7, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(CardEffect.BuildCastle(18))),

        Card("086", "Zednická rota", "Hradby +8 a hrad +6.", cost = 4, costType = ResourceType.STONES, rarity = Rarity.RARE,
            effects = listOf(
                CardEffect.BuildWall(8),
                CardEffect.BuildCastle(6)
            )),

        Card("087", "Pevnost", "Hradby +12 a hrad +8.", cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
            effects = listOf(
                CardEffect.BuildWall(12),
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

        Card("090", "Velkostavba", "Trvale +2 doly kamene/kolo.", cost = 6, costType = ResourceType.STONES, rarity = Rarity.EPIC,
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

        Card("094", "Sklad materiálu", "Hradby +6, získej +3 kameny. [Combo]", cost = 3, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(
                CardEffect.BuildWall(6),
                CardEffect.AddResource(ResourceType.STONES, 3)
            ), isCombo = true),

        Card("095", "Obchod s kamenem", "Získej +6 kamenů. [Combo]", cost = 2, costType = ResourceType.STONES, rarity = Rarity.COMMON,
            effects = listOf(CardEffect.AddResource(ResourceType.STONES, 6)), isCombo = true),

        Card("096", "Nedobytná pevnost", "Hradby +25.", cost = 8, costType = ResourceType.STONES, rarity = Rarity.LEGENDARY,
            effects = listOf(CardEffect.BuildWall(25))),

        Card("097", "Obnova království", "Hrad +25.", cost = 9, costType = ResourceType.STONES, rarity = Rarity.LEGENDARY,
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
            val str = prefs.getString("deck_$i", "") ?: ""
            if (str.isNotEmpty()) {
                val cardCounts = str.split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null)
                    else null
                }.toMap()
                decks[i] = deck.copy(cardCounts = cardCounts)
            }
        }
        activeDeckIndex.value = prefs.getInt("active_deck", 0)
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
            // Oprava hradu (STONES)
            "028" to 3,   // Záplata        – hrad +2, cena 1
            "005" to 3,   // Posila hradu   – hrad +3, cena 2
            "031" to 3,   // Renovace       – hrad +5, cena 3
            "035" to 3,   // Základní kámen – hradby+4 + hrad+2, cena 3
            // Stavba hradeb (STONES)
            "010" to 3,   // Palisáda       – hradby +5, cena 2
            "002" to 3,   // Kamenná zeď    – hradby +8, cena 3
            "033" to 2,   // Zemní val      – podmíněně hradby +12, cena 2
            "034" to 2,   // Opravář        – podmíněně hrad +8, cena 3
            "057" to 2,   // Bašta          – hradby+7 + 1 kámen, cena 3
            "059" to 2,   // Pevnostní hrad – hradby+5 + hrad+4, cena 4
            "060" to 1,   // Chrám          – hrad +12, cena 6
            // Generování STONES zdrojů (MAGIC)
            "039" to 3    // Stavební boom  – +7 kamenů, cena 2
        ),

        // 3. 💰 Ekonom – budovat doly pro zdrcující ekonomiku (30 karet)
        "💰 Ekonom" to mapOf(
            // Základní generátory zdrojů (MAGIC)
            "004" to 4,   // Magie          – +4 magie, zdarma
            "037" to 4,   // Rychlá magie   – +5 magie, cena 1
            "074" to 3,   // Tržiště        – +2 každý, cena 2
            // Stavba dolů (MAGIC)
            "013" to 2,   // Magický pramen – +1 důl magie, cena 3
            "014" to 2,   // Kamenolom      – +1 důl kamene, cena 3
            "015" to 2,   // Výcvikový tábor – +1 důl útoku, cena 3
            "073" to 1,   // Škola magie    – +4 magie + důl magie, cena 4
            "072" to 1,   // Zbrojnice      – +5 útoku + důl útoku, cena 3
            "042" to 1,   // Velký kamenolom – +2 doly kamene, cena 4
            "043" to 1,   // Výcvikové centrum – +2 doly útoku, cena 4
            "044" to 1,   // Trifekta dolů  – +1 každý důl, cena 5 (LEGENDARY)
            "080" to 1,   // Velkovýroba    – +2 každý důl + 3 magie, cena 6 (LEGENDARY)
            // Konverzní karty
            "041" to 2,   // Magické trio   – +2 každý, cena 3
            "038" to 2,   // Vojenský rozkaz – +7 útoku, cena 2
            "039" to 3    // Stavební boom  – +7 kamenů, cena 2
        ),

        // 4. 🌀 Chaosmancer – Chaos ekonomika + sabotáž soupeře (30 karet)
        "🌀 Chaosmancer" to mapOf(
            // Generování chaosu (MAGIC)
            "C01" to 2,   // Chaotický jiskra – +3 chaosu, cena 2
            "C04" to 2,   // Krádež chaosu   – ukradni 3 chaos, cena 2
            "C02" to 2,   // Entropie         – +5 chaosu + drain, cena 3
            "C03" to 1,   // Chaotický důl    – důl chaosu (LEGENDARY), cena 5
            // Sabotáž dolů soupeře (CHAOS)
            "C13" to 2,   // Sabotáž          – znič důl magie, cena 5
            "C14" to 2,   // Ničení kamenolomu – znič důl kamene, cena 5
            "C15" to 2,   // Zákeřnost        – znič důl útoku, cena 5
            // Krádež a ničení karet (CHAOS)
            "C17" to 1,   // Telekineze       – ukraď 1 kartu, cena 4
            "C19" to 1,   // Spálená knihovna – znič 2 karty, cena 5
            // Silné chaos útoky (CHAOS)
            "C05" to 1,   // Chaotický výbuch – hrad −15, cena 5
            "C07" to 1,   // Chaotický štít   – hradby +20, cena 6
            "C11" to 1,   // Chaos a řád      – hrad+8 + hradby+8, cena 5
            // Legendární chaos karty
            "C06" to 1,   // Bouře chaosu     – útok −20, cena 6
            "C09" to 1,   // Chaotická krize  – drain 6 vše, cena 6
            "C16" to 1,   // Velká sabotáž    – znič 2 doly, cena 9
            // Magická základna
            "004" to 4,   // Magie            – +4 magie, zdarma
            "037" to 3,   // Rychlá magie     – +5 magie, cena 1
            "013" to 2    // Magický pramen   – +1 důl magie, cena 3
        )
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
        if (old.activePlayer != ActivePlayer.AI) return   // hráč začíná → normální tok

        val who = if (old.activePlayer == ActivePlayer.AI) "AI" else "Hráč"
        addLog("$who začíná jako první!")

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
            it.generateResources()
            it.drawCards(5)   // 5 karet na mulligan
        }
        val aiState = PlayerState().also {
            it.deck.addAll(randomDeck().withUniqueIds().shuffled())
            it.generateResources()
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
        applyEffects(card.effects, player, ai)

        // 2. Zaplatit a přesunout kartu
        player.resources[card.costType] = (player.resources[card.costType] ?: 0) - card.cost
        player.hand.remove(card)
        player.discardPile.add(card)
        recordCard(card, CardAction.PLAYED, isPlayer = true)
        addLog("Hráč zahrál: ${card.name}")
        playSoundForCard(card)

        val s1 = old.copy(playerState = player, aiState = ai)
        s1.checkWinCondition()?.let { result ->
            if (result == GameResult.PLAYER_CASTLE_BUILT || result == GameResult.AI_CASTLE_DESTROYED)
                SoundManager.playWin() else SoundManager.playLose()
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
        finishTurn(old, player, ai)
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
        playerDrawsAtEnd: Boolean = true
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
                        applyEffects(aiCard.effects, ai, player)
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
                                if (result == GameResult.PLAYER_CASTLE_BUILT || result == GameResult.AI_CASTLE_DESTROYED)
                                    SoundManager.playWin() else SoundManager.playLose()
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
                if (result == GameResult.PLAYER_CASTLE_BUILT || result == GameResult.AI_CASTLE_DESTROYED)
                    SoundManager.playWin() else SoundManager.playLose()
                gameOver.value = result; gameState.value = s2; return@launch
            }

            // ── Mezistav: hráč chvíli vidí AI s méně kartami (po sehrání, před lízem) ──
            // Stav se zobrazí před tím, než hráč dostane svou kartu na začátek kola.
            gameState.value = s2  // AI má N-1 karet (zahrála 1, svůj líz byl na začátku)
            delay(350L)           // krátká pauza – hráč vidí výsledek tahu AI

            // ── Konec kola: příprava hráčova tahu ────────────────────────────
            // Hráč lízne 1 kartu na "začátek" svého kola (kromě kola 1, kde
            // začíná s 5 z mulliganu – ale líz 1 je tu stejně OK, protože
            // hráč v kole 1 zahrál/čekal a má méně karet).
            // AI NELÍŽE znovu – doplnila si 1 na začátku SVÉHO tahu.
            // Spálené karty mají tak trvalý efekt.
            player.generateResources()
            if (playerDrawsAtEnd) {
                val burned = player.drawCards(1)
                burned.forEach { b -> addToHistory(b, CardAction.BURNED, isMine = true) }
            }

            gameState.value = old.copy(
                playerState  = player.deepCopy(),
                aiState      = ai.deepCopy(),   // AI stále bez extra lízu
                currentTurn  = old.currentTurn + 1,
                activePlayer = ActivePlayer.PLAYER
            )
        }
    }

    private fun applyEffects(effects: List<CardEffect>, self: PlayerState, opponent: PlayerState) {
        for (effect in effects) {
            when (effect) {
                is CardEffect.AddResource ->
                    self.resources[effect.type] = (self.resources[effect.type] ?: 0) + effect.amount
                is CardEffect.AddMine ->
                    self.mines[effect.type] = (self.mines[effect.type] ?: 0) + effect.amount
                is CardEffect.BuildWall ->
                    self.wallHP = (self.wallHP + effect.amount).coerceAtMost(100)
                is CardEffect.BuildCastle ->
                    self.castleHP = (self.castleHP + effect.amount).coerceAtMost(100)
                is CardEffect.AttackPlayer -> {
                    val dmg = effect.amount.coerceAtMost(opponent.wallHP)
                    opponent.wallHP -= dmg
                    val overflow = effect.amount - dmg
                    if (overflow > 0) opponent.castleHP -= overflow
                }
                is CardEffect.AttackWall ->
                    opponent.wallHP = (opponent.wallHP - effect.amount).coerceAtLeast(0)
                is CardEffect.AttackCastle ->
                    opponent.castleHP -= effect.amount
                is CardEffect.StealResource -> {
                    val taken = minOf(effect.amount, opponent.resources[effect.type] ?: 0)
                    opponent.resources[effect.type] = (opponent.resources[effect.type] ?: 0) - taken
                    self.resources[effect.type] = (self.resources[effect.type] ?: 0) + taken
                }
                is CardEffect.DrainResource -> {
                    val drained = minOf(effect.amount, opponent.resources[effect.type] ?: 0)
                    opponent.resources[effect.type] = (opponent.resources[effect.type] ?: 0) - drained
                }
                is CardEffect.ConditionalEffect ->
                    if (checkCondition(effect.condition, self))
                        applyEffects(listOf(effect.effect), self, opponent)
                is CardEffect.DestroyMine -> {
                    val current = opponent.mines[effect.type] ?: 0
                    if (current > 0)
                        opponent.mines[effect.type] = (current - effect.amount).coerceAtLeast(0)
                }
                is CardEffect.StealCard -> {
                    repeat(effect.count) {
                        if (opponent.hand.isNotEmpty()) {
                            val stolen = opponent.hand.random()
                            opponent.hand.remove(stolen)
                            self.hand.add(stolen)
                        }
                    }
                }
                is CardEffect.BurnCard -> {
                    repeat(effect.count) {
                        if (opponent.hand.isNotEmpty()) {
                            val burned = opponent.hand.random()
                            opponent.hand.remove(burned)
                            opponent.discardPile.add(burned)
                        }
                    }
                }
                is CardEffect.AddCardsToDeck -> {
                    val template = allCards.find { it.id == effect.cardId }
                    if (template != null) {
                        repeat(effect.count) { self.deck.add(template.copy()) }
                        self.deck.shuffle()
                    }
                }
            }
        }
    }

    private fun checkCondition(condition: Condition, player: PlayerState): Boolean = when (condition) {
        is Condition.ResourceAbove -> (player.resources[condition.type] ?: 0) > condition.threshold
        is Condition.WallAbove     -> player.wallHP > condition.threshold
        is Condition.WallBelow     -> player.wallHP < condition.threshold
        is Condition.CastleAbove   -> player.castleHP > condition.threshold
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
     */
    private fun aiChooseAction(ai: PlayerState, opponent: PlayerState): AiAction {
        val affordable = ai.hand.filter { (ai.resources[it.costType] ?: 0) >= it.cost }

        if (affordable.isEmpty()) {
            // Nic k zahrání – čekej nebo zahoď
            return if (ai.deck.isNotEmpty()) AiAction.Wait
            else ai.hand.minByOrNull { it.cost }?.let { AiAction.Discard(it) } ?: AiAction.Wait
        }

        // Situační hodnocení
        val aiLowHp     = ai.castleHP < 15
        val aiLowWall   = ai.wallHP   < 5
        val oppLowHp    = opponent.castleHP < 20
        val chaos       = ai.resources[ResourceType.CHAOS] ?: 0

        // Skórovací funkce karty
        fun score(card: Card): Int {
            var s = card.cost * 2  // základ = cena karty
            val fx = card.effects.firstOrNull()

            // Přidej bonus podle situace
            when (fx) {
                is CardEffect.AttackPlayer  -> if (oppLowHp) s += 18 else s += 8
                is CardEffect.AttackCastle  -> if (oppLowHp) s += 20 else s += 5
                is CardEffect.AttackWall    -> s += 4
                is CardEffect.BuildCastle   -> if (aiLowHp) s += 18 else s += 3
                is CardEffect.BuildWall     -> if (aiLowWall) s += 15 else s += 4
                is CardEffect.AddMine       -> s += 8  // doly jsou dlouhodobě silné
                is CardEffect.StealResource -> s += 7
                is CardEffect.DrainResource -> s += 6
                is CardEffect.AddResource   -> s += 3
                is CardEffect.DestroyMine   -> s += 10  // ničení dolů je velmi silné
                is CardEffect.StealCard     -> s += 8   // krádež karty je výhodná
                is CardEffect.BurnCard      -> s += 6   // zničení karty oslabuje soupeře
                is CardEffect.AddCardsToDeck -> s += 4  // zlepšení balíčku
                is CardEffect.ConditionalEffect -> {
                    // Vyhodnoť, jestli podmínka platí
                    val cond = (fx as CardEffect.ConditionalEffect).condition
                    s += if (checkCondition(cond, ai)) 12 else -5
                }
                else -> {}
            }

            // Chaos karty – nedávej je, pokud nemáš dost chaosu
            if (card.costType == ResourceType.CHAOS && chaos < card.cost) s -= 100

            // Lehká náhoda, aby AI nebyla deterministická
            s += (-2..2).random()
            return s
        }

        val best = affordable.maxByOrNull { score(it) } ?: affordable.first()

        // Pokud je nejlepší karta stále slabá, občas raději čekej (10% šance)
        if (score(best) < 5 && ai.deck.isNotEmpty() && (0..9).random() == 0)
            return AiAction.Wait

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
            it.generateResources()
            it.drawCards(5)
        }
        val ai = PlayerState().also {
            it.deck.addAll(randomDeck().withUniqueIds().shuffled())
            it.generateResources()
            it.drawCards(5)
        }
        gameState.value         = GameState(playerState = ps, aiState = ai)
        gameOver.value          = null
        lastCard.value          = null
        lastCardAction.value    = CardAction.PLAYED
        lastCardIsPlayer.value  = true
        cardHistory.value       = emptyList()
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
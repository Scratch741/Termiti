package com.example.termiti

import androidx.annotation.DrawableRes
import kotlinx.serialization.Serializable

@Serializable
enum class QuestType {
    WIN_GAMES,     // Vyhraj X her (jakýkoliv mód)
    WIN_ONLINE,    // Vyhraj X online her
    PLAY_CARDS,    // Zahraj X karet
    DEAL_DAMAGE,   // Způsob X poškození nepřátelského hradu
    WIN_CAMPAIGN   // Poraž X soupeřů v kampani
}

@Serializable
data class DailyQuest(
    val id        : String,
    val type      : QuestType,
    val target    : Int,
    val progress  : Int     = 0,
    val rewardGold: Int,
    val rewardXp  : Int,
    val rewardGems: Int     = 0,
    val claimed   : Boolean = false
) {
    val completed: Boolean get() = progress >= target
    val canClaim : Boolean get() = completed && !claimed

    fun label(): String {
        val s = LanguageManager.currentStrings
        return when (type) {
            QuestType.WIN_GAMES    -> s.questWinGames.format(target)
            QuestType.WIN_ONLINE   -> s.questWinOnline.format(target)
            QuestType.PLAY_CARDS   -> s.questPlayCards.format(target)
            QuestType.DEAL_DAMAGE  -> s.questDealDamage.format(target)
            QuestType.WIN_CAMPAIGN -> s.questWinCampaign.format(target)
        }
    }

    fun icon(): String = when (type) {
        QuestType.WIN_GAMES    -> "⚔️"
        QuestType.WIN_ONLINE   -> "🌐"
        QuestType.PLAY_CARDS   -> "🃏"
        QuestType.DEAL_DAMAGE  -> "💥"
        QuestType.WIN_CAMPAIGN -> "🏆"
    }

    @DrawableRes
    fun iconRes(): Int = when (type) {
        QuestType.WIN_GAMES    -> R.drawable.utok_icon
        QuestType.WIN_ONLINE   -> R.drawable.trophy_icon
        QuestType.PLAY_CARDS   -> R.drawable.card_icon
        QuestType.DEAL_DAMAGE  -> R.drawable.explode_icon
        QuestType.WIN_CAMPAIGN -> R.drawable.trophy_icon
    }
}

// ── Pool šablon questů ────────────────────────────────────────────────────────
// id je prázdné — QuestManager přiřadí UUID při generování
internal val QUEST_POOL: List<DailyQuest> = listOf(
    DailyQuest(id = "", type = QuestType.WIN_GAMES,    target = 1,   rewardGold = 50,  rewardXp = 30),
    DailyQuest(id = "", type = QuestType.WIN_GAMES,    target = 3,   rewardGold = 120, rewardXp = 75),
    DailyQuest(id = "", type = QuestType.WIN_GAMES,    target = 5,   rewardGold = 200, rewardXp = 100),
    DailyQuest(id = "", type = QuestType.WIN_ONLINE,   target = 1,   rewardGold = 80,  rewardXp = 50,  rewardGems = 1),
    DailyQuest(id = "", type = QuestType.WIN_ONLINE,   target = 3,   rewardGold = 200, rewardXp = 100, rewardGems = 2),
    DailyQuest(id = "", type = QuestType.PLAY_CARDS,   target = 15,  rewardGold = 40,  rewardXp = 20),
    DailyQuest(id = "", type = QuestType.PLAY_CARDS,   target = 30,  rewardGold = 80,  rewardXp = 40),
    DailyQuest(id = "", type = QuestType.PLAY_CARDS,   target = 50,  rewardGold = 130, rewardXp = 60),
    DailyQuest(id = "", type = QuestType.DEAL_DAMAGE,  target = 50,  rewardGold = 60,  rewardXp = 35),
    DailyQuest(id = "", type = QuestType.DEAL_DAMAGE,  target = 100, rewardGold = 110, rewardXp = 60),
    DailyQuest(id = "", type = QuestType.DEAL_DAMAGE,  target = 200, rewardGold = 180, rewardXp = 90),
    DailyQuest(id = "", type = QuestType.WIN_CAMPAIGN, target = 1,   rewardGold = 70,  rewardXp = 40),
    DailyQuest(id = "", type = QuestType.WIN_CAMPAIGN, target = 3,   rewardGold = 150, rewardXp = 80),
)

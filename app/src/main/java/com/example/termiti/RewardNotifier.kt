package com.example.termiti

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RewardNotifier {
    data class RewardEvent(
        val xp      : Int     = 0,
        val gold    : Int     = 0,
        val gems    : Int     = 0,
        val levelUp : Boolean = false,
        val newLevel: Int     = 0,
        val source  : String  = ""   // např. "🆙 Level 5!" nebo "🎯 Quest dokončen"
    )

    private val _events = MutableSharedFlow<RewardEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<RewardEvent> = _events.asSharedFlow()

    fun emit(event: RewardEvent) { _events.tryEmit(event) }
}

package com.example.mvi

import com.example.mvi.api.MVIAction
import com.example.mvi.api.MVIIntent
import com.example.mvi.api.MVIState

// 1. Define State, Intent, Action
data class CounterState(val count: Int = 0) : MVIState
sealed interface CounterIntent : MVIIntent {
    object Increment : CounterIntent
    object Decrement : CounterIntent

    // 新增：用于触发回放的 Intent
    data class ReplayIntents(val intentsToReplay: List<CounterIntent>) : CounterIntent

    data object ResetStateIntent : CounterIntent
}

sealed interface CounterAction : MVIAction {
    data class ShowToast(val message: String) : CounterAction
}

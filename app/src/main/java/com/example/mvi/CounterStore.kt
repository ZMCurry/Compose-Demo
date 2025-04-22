package com.example.mvi

import com.example.mvi.api.*
import com.example.mvi.core.BaseStore

class CounterStore(
    plugins: List<StorePlugin<CounterState, CounterIntent, CounterAction>> = emptyList()
) : BaseStore<CounterState, CounterIntent, CounterAction>(
    initialState = CounterState(),
    plugins = plugins
) {
    override suspend fun reduce(currentState: CounterState, intent: CounterIntent): Pair<CounterState, CounterAction?> {
        return when (intent) {
            is CounterIntent.Increment -> {
                val newState = currentState.copy(count = currentState.count + 1)
                val action = if (newState.count % 5 == 0) CounterAction.ShowToast("Count is a multiple of 5!") else null
                newState to action
            }

            is CounterIntent.Decrement -> {
                currentState.copy(count = currentState.count - 1) to null
            }
            // 处理回放 Intent
            is CounterIntent.ReplayIntents -> {
                println("--- Starting Replay ---")
                // 1. 重置状态到初始状态
                var replayState = initialState // 使用局部变量累积状态
                var lastAction: CounterAction? = null

                // 2. 依次应用所有需要回放的 Intent
                intent.intentsToReplay.forEach { replayIntent ->
                    println("Replaying Intent: $replayIntent")
                    // 调用 reduce 逻辑来计算下一个状态和动作
                    // 注意：这里我们直接调用 reduce 逻辑，而不是递归分发 intent
                    // 需要确保 reduce 是纯函数或处理好副作用
                    val (nextState, nextAction) = reduceInternal(replayState, replayIntent) // 使用一个内部方法避免无限递归
                    replayState = nextState
                    if (nextAction != null) {
                        lastAction = nextAction // 只保留最后一个 Action，或者你可以收集所有 Action
                        // 注意：在真实回放中，Action 的处理可能需要更复杂的逻辑
                        // 这里简化处理，只返回最后一个 Action
                        println("Action during replay: $nextAction")
                    }
                }
                println("--- Replay Finished ---")
                // 3. 返回回放后的最终状态和最后一个 Action
                replayState to lastAction
            }
            // 如果有其他 Intent 类型，需要在这里添加 case
            // else -> currentState to null // 或者抛出异常
        } as Pair<CounterState, CounterAction?>
    }

    // 创建一个内部 reduce 方法，避免 ReplayIntents 无限递归调用 reduce
    private suspend fun reduceInternal(currentState: CounterState, intent: CounterIntent): Pair<CounterState, CounterAction?> {
        return when (intent) {
            is CounterIntent.Increment -> {
                val newState = currentState.copy(count = currentState.count + 1)
                val action = if (newState.count % 5 == 0) CounterAction.ShowToast("Count is a multiple of 5!") else null
                newState to action
            }
            is CounterIntent.Decrement -> {
                currentState.copy(count = currentState.count - 1) to null
            }
            // ReplayIntents 不应该在 internal reduce 中处理
            is CounterIntent.ReplayIntents -> currentState to null // 或者抛出错误
        }
    }
}
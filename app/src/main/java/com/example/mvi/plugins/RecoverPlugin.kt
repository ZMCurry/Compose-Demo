package com.example.mvi.plugins


import com.example.mvi.ExceptionHandlerResult
import com.example.mvi.api.MVIAction
import com.example.mvi.api.MVIIntent
import com.example.mvi.api.MVIState
import com.example.mvi.api.Store
import com.example.mvi.api.StorePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 辅助接口，用于让插件访问 Action 通道
interface ActionPublisher<A : MVIAction> {
    val actionChannel: kotlinx.coroutines.channels.SendChannel<A>
}

// 辅助接口，用于让插件分发 Intent
interface IntentDispatcher<I : MVIIntent> {
    suspend fun dispatchIntent(intent: I)
}


class RecoverPlugin<S : MVIState, I : MVIIntent, A : MVIAction>(
    private val resetIntent: I?, // 传入用于重置状态的 Intent
    private val onErrorAction: ((Exception) -> A?)? = null
) : StorePlugin<S, I, A> {

    override val name: String = "RecoverPlugin"

    private var storeScope: CoroutineScope? = null

    override suspend fun onStart(scope: CoroutineScope, store: Store<S, I, A>) {
        storeScope = scope
    }

    override suspend fun onException(e: Exception, store: Store<S, I, A>): ExceptionHandlerResult<I, A> {
        println("[$name] Caught exception: ${e.message}")

        // 优先尝试生成恢复 Intent
        resetIntent?.let {
            println("[$name] Handling exception by requesting DispatchIntent: $it")
            return ExceptionHandlerResult.DispatchIntent(it)
        }

        // 其次尝试生成错误 Action
        onErrorAction?.invoke(e)?.let { action ->
            println("[$name] Handling exception by requesting SendAction: $action")
            return ExceptionHandlerResult.SendAction(action)
        }

        // 如果以上都没有配置，则标记为已处理（不重抛，但也不做恢复）
        println("[$name] Handling exception by marking as Handled.")
        return ExceptionHandlerResult.Handled
    }
}
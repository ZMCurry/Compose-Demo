package com.example.mvi.plugins


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

    // 实现新的 onException 签名
    override suspend fun onException(e: Exception, store: Store<S, I, A>): Exception? {
        println("[$name] Caught exception: ${e.message}")

        // 1. 尝试发送错误 Action (如果配置了)
        onErrorAction?.invoke(e)?.let { errorAction ->
            storeScope?.launch {
                (store as? ActionPublisher<A>)?.actionChannel?.send(errorAction)
                println("[$name] Sent error action: $errorAction")
            }
        }

        // 2. 尝试分发重置状态的 Intent (如果配置了) - 这就是恢复动作
        resetIntent?.let { intentToDispatch ->
            storeScope?.launch {
                (store as? IntentDispatcher<I>)?.dispatchIntent(intentToDispatch)
                println("[$name] Dispatched recovery intent: $intentToDispatch")
            }
        }

        // 返回 null 表示异常已被处理，Store 不应停止
        return null
    }
}
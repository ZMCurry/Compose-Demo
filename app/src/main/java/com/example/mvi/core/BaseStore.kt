package com.example.mvi.core

import com.example.mvi.ExceptionHandlerResult
import com.example.mvi.api.MVIAction
import com.example.mvi.api.MVIIntent
import com.example.mvi.api.MVIState
import com.example.mvi.api.Store
import com.example.mvi.api.StoreEvent
import com.example.mvi.api.StorePlugin
import com.example.mvi.plugins.ActionPublisher
import com.example.mvi.plugins.IntentDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * A basic implementation of the Store interface.
 *
 * @param initialState The initial state of the store.
 * @param plugins A list of plugins to install.
 * @param reducer The function responsible for handling intents and updating the state.
 *                It receives the current state and an intent, and should return a pair
 *                containing the new state and an optional action to emit.
 * @param dispatcher The CoroutineDispatcher to run the store's main processing loop on. Defaults to Dispatchers.Default.
 * @param actionBufferCapacity Capacity of the buffer for actions.
 */
public abstract class BaseStore<S : MVIState, I : MVIIntent, A : MVIAction>(
    internal val initialState: S,
    internal val plugins: List<StorePlugin<S, I, A>> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val actionBufferCapacity: Int = Channel.BUFFERED, // Or Channel.UNLIMITED, Channel.CONFLATED
) : Store<S, I, A>, CoroutineScope, ActionPublisher<A>, IntentDispatcher<I>  {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    // Using Channel for actions by default, similar to FlowMVI's Distribute/Restrict behaviors
    private val _actions = Channel<A>(actionBufferCapacity)
    override val actions: Flow<A> = _actions.receiveAsFlow()

    private val intentChannel = Channel<I>(Channel.UNLIMITED)

    private val storeJob = AtomicReference<Job?>()

    // CoroutineScope implementation
    private var _scope: CoroutineScope? = null
    override val scope: CoroutineScope? get() = _scope
    override val coroutineContext: CoroutineContext
        get() = dispatcher + SupervisorJob(storeJob.get()) + CoroutineName("BaseStore<${initialState::class.simpleName}>")


    // 假设 intentChannel 是内部可写的 Channel
    protected val intentChannelInternal: Channel<I> = Channel(Channel.UNLIMITED)

    // 实现 IntentDispatcher 接口
    override suspend fun dispatchIntent(intent: I) {
        intentChannelInternal.send(intent)
    }

    // 实现 ActionPublisher 接口
    override val actionChannel: SendChannel<A>
        get() = _actions // 假设 _actions 是 Channel<A>

    override fun start(scope: CoroutineScope): Job {
        // ... existing setup ...
        val newJob = scope.launch(coroutineContext) {
            // ... plugin onStart ...

            // 使用内部 channel 接收 intent
            intentChannelInternal.receiveAsFlow().collect { intent ->
                try {
                    // ... plugin onIntent ...
                    publishEvent(StoreEvent.IntentReceived(intent))

                    // ... 调用 reduce ...
                    val (newState, action) = reduce(_state.value, intent)

                    // ... 更新状态, 发送 action, 调用 plugin onStateChange/onAction ...

                } catch (e: Exception) {
                    var finalResult: ExceptionHandlerResult<I, A> = ExceptionHandlerResult.Rethrow(e) // 默认重抛

                    // 遍历插件处理异常
                    for (plugin in plugins) {
                        try {
                            val result = plugin.onException(e, this)
                            // 如果有插件处理了（非 Rethrow），就采纳它的结果并停止遍历
                            if (result !is ExceptionHandlerResult.Rethrow) {
                                finalResult = result
                                break // 第一个处理的插件优先
                            } else {
                                // 如果插件要求 Rethrow，更新要抛出的异常（可能被包装过）
                                finalResult = result
                            }
                        } catch (pluginError: Exception) {
                            println("Exception in plugin ${plugin.name} during onException: ${pluginError.message}")
                            // 插件自身的错误不应阻止原始异常的处理，但需要记录
                        }
                    }

                    when (finalResult) {
                        is ExceptionHandlerResult.Handled -> {
                            publishEvent(StoreEvent.ExceptionCaught(e)) // 记录原始异常
                            println("Exception caught and handled by plugin: ${e.message}")
                        }
                        is ExceptionHandlerResult.DispatchIntent<*> -> {
                            publishEvent(StoreEvent.ExceptionCaught(e)) // 记录原始异常
                            println("Exception handled, dispatching recovery intent: ${finalResult.intent}")
                            val recoveryIntent = finalResult.intent as I // 类型转换
                            dispatchIntent(recoveryIntent)
                            // 新增：记录恢复性 Intent 分发事件
                            publishEvent(StoreEvent.RecoveryIntentDispatched(recoveryIntent))
                        }
                        is ExceptionHandlerResult.SendAction<*> -> {
                            publishEvent(StoreEvent.ExceptionCaught(e)) // 记录原始异常
                            println("Exception handled, sending recovery action: ${finalResult.action}")
                            val recoveryAction = finalResult.action as A // 类型转换
                            actionChannel.send(recoveryAction)
                            // 新增：记录恢复性 Action 发送事件
                            publishEvent(StoreEvent.RecoveryActionSent(recoveryAction))
                        }
                        is ExceptionHandlerResult.Rethrow -> {
                            publishEvent(StoreEvent.ExceptionCaught(finalResult.exception))
                            println("Exception not handled or plugin requested rethrow: ${finalResult.exception.message}")
                            throw finalResult.exception // 重新抛出异常
                        }
                    }
                }
            }
        }

        newJob.invokeOnCompletion { error ->
            _scope = null // Clear scope on completion
            runBlocking { // Use runBlocking for cleanup if needed, be careful
                runPluginsSafely(this@BaseStore) { it.onStop(error) }
                publishEvent(StoreEvent.StoreStopped(state.value, error))
            }
            storeJob.set(null)
            _actions.close(error) // Close action channel
            intentChannel.close(error) // Close intent channel
            println("Store stopped. Error: $error")
        }

        if (!storeJob.compareAndSet(null, newJob)) {
            // Already started by another thread, cancel the new job
            newJob.cancel("Store already started by another call.")
        }
        return storeJob.get()!!
    }

    override fun intent(intent: I) {
        if (scope?.isActive != true) {
            println("Store is not running. Intent $intent dropped.")
            return
        }
        if (!intentChannel.trySend(intent).isSuccess) {
            println("Failed to send intent $intent. Intent channel might be full or closed.")
        }
    }

    override fun stop() {
        storeJob.getAndSet(null)?.cancel("Store stopped manually.")
    }

    /**
     * Abstract function to be implemented by subclasses to handle state reduction.
     * @param currentState The current state.
     * @param intent The intent to process.
     * @return A Pair containing the new state and an optional action to emit.
     */
    protected abstract suspend fun reduce(currentState: S, intent: I): Pair<S, A?>

    // --- Helper Functions ---

    private suspend fun publishEvent(event: StoreEvent<S, I, A>) {
        plugins.forEach { plugin ->
            try {
                plugin.onEvent(event)
            } catch (e: Exception) {
                println("Exception in plugin ${plugin.name ?: plugin::class.simpleName} during onEvent: ${e.message}")
                // Decide how to handle plugin errors during event publishing
            }
        }
    }

    protected suspend fun <R> runPluginsSafely(store: Store<S, I, A>, block: suspend (StorePlugin<S, I, A>) -> R): R? {
        var result: R? = null
        for (plugin in plugins) {
            try {
                result = block(plugin)
            } catch (pluginError: Exception) {
                println("Exception in plugin ${plugin.name}: ${pluginError.message}")
                // 可以选择是否让插件错误影响主流程
            }
        }
        return result // 返回最后一个插件的结果（适用于 onException）
    }
}
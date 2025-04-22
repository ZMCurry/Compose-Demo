package com.example.mvi.core

import com.example.mvi.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
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
    val plugins: List<StorePlugin<S, I, A>> = emptyList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val actionBufferCapacity: Int = Channel.BUFFERED, // Or Channel.UNLIMITED, Channel.CONFLATED
) : Store<S, I, A>, CoroutineScope {

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


    override fun start(scope: CoroutineScope): Job {
        val currentJob = storeJob.get()
        if (currentJob != null && currentJob.isActive) {
            println("Store is already started.")
            return currentJob
        }

        val newJob = scope.launch(coroutineContext) {
            _scope = this // Assign the scope
            _state.value = initialState // Reset state on start
            runPluginsSafely(this) { it.onStart(this, this@BaseStore) }
            publishEvent(StoreEvent.StoreStarted(initialState))

            // Main processing loop
            intentChannel.receiveAsFlow().collect { intent ->
                try {
                    val processedIntent = runPluginsSafely(this) { it.onIntent(intent) } ?: return@collect
                    publishEvent(StoreEvent.IntentReceived(processedIntent))

                    val currentState = state.value
                    val (newState, action) = reduce(currentState, processedIntent)

                    val finalState = runPluginsSafely(this) { it.onState(currentState, newState) } ?: newState

                    if (currentState != finalState) {
                        _state.value = finalState
                        publishEvent(StoreEvent.StateChanged(currentState, finalState))
                    }

                    if (action != null) {
                        val processedAction = runPluginsSafely(this) { it.onAction(action) } ?: action
                        _actions.send(processedAction)
                        publishEvent(StoreEvent.ActionSent(processedAction))
                    }
                } catch (e: Exception) {
                    val handledError = runPluginsSafely(this) { it.onException(e) }
                    if (handledError != null) {
                        publishEvent(StoreEvent.ExceptionCaught(handledError))
                        // Decide if you want to crash or just log
                        println("Exception caught by plugin: ${handledError.message}")
                        // throw handledError // Optionally rethrow
                    } else {
                        publishEvent(StoreEvent.ExceptionCaught(e))
                        println("Unhandled exception in Store: ${e.message}")
                        throw e // Rethrow if no plugin handled it
                    }
                }
            }
        }

        newJob.invokeOnCompletion { error ->
            _scope = null // Clear scope on completion
            runBlocking { // Use runBlocking for cleanup if needed, be careful
                runPluginsSafely(this) { it.onStop(error) }
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

    // Helper to run plugin functions safely
    private suspend inline fun <T> runPluginsSafely(
        scope: CoroutineScope,
        crossinline block: suspend (StorePlugin<S, I, A>) -> T?
    ): T? {
        var result: T? = null
        plugins.forEach { plugin ->
            try {
                result = block(plugin)
            } catch (e: Exception) {
                println("Exception in plugin ${plugin.name ?: plugin::class.simpleName}: ${e.message}")
                val handledError = plugin.onException(e) // Allow plugin to handle its own error
                if (handledError != null) publishEvent(StoreEvent.ExceptionCaught(handledError))
                // Potentially rethrow or log, depending on desired behavior for plugin errors
            }
        }
        return result
    }
}
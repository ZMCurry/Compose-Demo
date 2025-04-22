package com.example.mvi.api

import kotlinx.coroutines.CoroutineScope

/**
 * Interface for Store plugins, allowing interception and modification of Store behavior.
 */
public interface StorePlugin<S : MVIState, I : MVIIntent, A : MVIAction> {

    /** Plugin name, used for identification and debugging. */
    public val name: String? get() = null

    /** Called when the Store is started, before processing any intents. */
    public suspend fun onStart(scope: CoroutineScope, store: Store<S, I, A>): Unit = Unit

    /** Called before an Intent is processed. Can modify or drop the intent by returning null. */
    public suspend fun onIntent(intent: I): I? = intent

    /** Called after the state has been updated. Can modify the new state. */
    public suspend fun onState(oldState: S, newState: S): S = newState

    /** Called before an Action is sent. Can modify or drop the action by returning null. */
    public suspend fun onAction(action: A): A? = action

    /** Called when an exception occurs during intent processing or state reduction. Can handle or rethrow. */
    public suspend fun onException(error: Throwable): Throwable? = error

    /** Called when the Store is stopped (cancelled or completed). */
    public suspend fun onStop(error: Throwable?): Unit = Unit

    /** Called whenever any StoreEvent occurs. Useful for generic event handling like logging. */
    public suspend fun onEvent(event: StoreEvent<S, I, A>): Unit = Unit
}
package com.example.mvi.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The core MVI component responsible for managing state, processing intents, and emitting actions.
 */
public interface Store<S : MVIState, I : MVIIntent, A : MVIAction> {

    /** The current state of the Store. */
    public val state: StateFlow<S>

    /** A flow of one-time actions emitted by the Store. */
    public val actions: Flow<A>

    /** The CoroutineScope the Store operates in. Null if not started. */
    public val scope: CoroutineScope?

    /** Starts the Store within the given CoroutineScope. Returns the Job managing the Store's lifecycle. */
    public fun start(scope: CoroutineScope): Job

    /** Sends an intent to the Store for processing. */
    public fun intent(intent: I)

    /** Stops the Store and cancels its Job. */
    public fun stop()
}
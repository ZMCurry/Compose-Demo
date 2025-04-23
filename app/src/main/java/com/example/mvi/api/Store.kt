package com.example.mvi.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The core MVI component responsible for managing state, processing intents, and emitting actions.
 */
 interface Store<S : MVIState, I : MVIIntent, A : MVIAction> {

    /** The current state of the Store. */
     val state: StateFlow<S>

    /** A flow of one-time actions emitted by the Store. */
     val actions: Flow<A>

    /** The CoroutineScope the Store operates in. Null if not started. */
     val scope: CoroutineScope?

    /** Starts the Store within the given CoroutineScope. Returns the Job managing the Store's lifecycle. */
     fun start(scope: CoroutineScope): Job

    /** Sends an intent to the Store for processing. */
     fun intent(intent: I)

    /** Stops the Store and cancels its Job. */
     fun stop()
}
package com.example.mvi.api

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents an event that occurred within the Store, used for logging and debugging.
 */
sealed class StoreEvent<out S : MVIState, out I : MVIIntent, out A : MVIAction>(
    val timestamp: Instant = Clock.System.now()
) {
    data class StoreStarted<S : MVIState, I : MVIIntent, A : MVIAction>(
        val initialState: S
    ) : StoreEvent<S, I, A>()

    data class IntentReceived<S : MVIState, I : MVIIntent, A : MVIAction>(
        val intent: I
    ) : StoreEvent<S, I, A>()

    data class StateChanged<S : MVIState, I : MVIIntent, A : MVIAction>(
        val oldState: S,
        val newState: S
    ) : StoreEvent<S, I, A>()

    data class ActionSent<S : MVIState, I : MVIIntent, A : MVIAction>(
        val action: A
    ) : StoreEvent<S, I, A>()

    data class ExceptionCaught<S : MVIState, I : MVIIntent, A : MVIAction>(
        val error: Throwable
    ) : StoreEvent<S, I, A>()

    data class StoreStopped<S : MVIState, I : MVIIntent, A : MVIAction>(
        val finalState: S,
        val error: Throwable? = null
    ) : StoreEvent<S, I, A>()

    data class RecoveryIntentDispatched<I : MVIIntent>(val intent: I) :
        StoreEvent<Nothing, I, Nothing>()

    /** An Action was sent as a result of handling an exception. */
    data class RecoveryActionSent<A : MVIAction>(val action: A) : StoreEvent<Nothing, Nothing, A>()

    override fun toString(): String {
        return "${this::class.simpleName}(timestamp=$timestamp, data=${getDataString()})"
    }

    private fun getDataString(): String = when (this) {
        is StoreStarted<*, *, *> -> "initialState=$initialState"
        is IntentReceived<*, *, *> -> "intent=$intent"
        is StateChanged<*, *, *> -> "oldState=$oldState, newState=$newState"
        is ActionSent<*, *, *> -> "action=$action"
        is ExceptionCaught<*, *, *> -> "error=$error"
        is StoreStopped<*, *, *> -> "finalState=$finalState, error=$error"
        is RecoveryActionSent<*> -> TODO()
        is RecoveryIntentDispatched<*> -> TODO()
    }
}
package com.example.mvi.plugins

import com.example.mvi.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple plugin that records all StoreEvents.
 * Provides access to the recorded events via the `recordedEvents` StateFlow.
 * Note: In a real application, consider limiting the size of the event list.
 */
class EventRecorderPlugin<S : MVIState, I : MVIIntent, A : MVIAction>(
    private val bufferSize: Int = 100 // Limit the number of stored events
) : StorePlugin<S, I, A> {

    override val name: String = "EventRecorderPlugin"

    private val _recordedEvents = MutableStateFlow<List<StoreEvent<S, I, A>>>(emptyList())
    val recordedEvents: StateFlow<List<StoreEvent<S, I, A>>> = _recordedEvents.asStateFlow()

    private val mutex = Mutex()

    override suspend fun onEvent(event: StoreEvent<S, I, A>) {
        mutex.withLock {
            _recordedEvents.update { currentList ->
                val newList = currentList + event
                if (newList.size > bufferSize) {
                    newList.takeLast(bufferSize) // Keep only the most recent events
                } else {
                    newList
                }
            }
        }
        // Optional: Print events to console for immediate feedback
        println("[${name}] Event: $event")
    }

    // Optional: Clear events on store start/stop if desired
    override suspend fun onStart(scope: CoroutineScope, store: Store<S, I, A>) {
        // clearEvents() // Uncomment to clear history on each start
    }

    fun clearEvents() {
        _recordedEvents.value = emptyList()
    }

    fun getEvents(): List<StoreEvent<S, I, A>> = recordedEvents.value
}
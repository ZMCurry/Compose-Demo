package com.example.mvi.android // 假设放在 android 相关的包下

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvi.api.MVIAction
import com.example.mvi.api.MVIIntent
import com.example.mvi.api.MVIState
import com.example.mvi.api.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A base ViewModel class that integrates with a Store.
 * It manages the Store's lifecycle tied to the ViewModel's scope.
 *
 * @param S The type of the MVI State.
 * @param I The type of the MVI Intent.
 * @param A The type of the MVI Action.
 * @property store The MVI Store instance managed by this ViewModel.
 */
abstract class BaseMviViewModel<S : MVIState, I : MVIIntent, A : MVIAction>(
    protected val store: Store<S, I, A>
) : ViewModel() {

    /**
     * The StateFlow representing the current state of the Store.
     * UI layers should collect this flow to observe state changes.
     */
    val state: StateFlow<S>
        get() = store.state

    /**
     * The SharedFlow representing one-time actions emitted by the Store.
     * UI layers should collect this flow to handle side effects like showing toasts or navigation.
     */
    val actions: Flow<A> // Use Flow here, as SharedFlow is a Flow
        get() = store.actions

    init {
        // Start the store automatically when the ViewModel is initialized.
        // The store's job will be cancelled automatically when viewModelScope is cancelled.
        store.start(viewModelScope)
    }

    /**
     * Dispatches an intent to the Store for processing.
     *
     * @param intent The MVIIntent to dispatch.
     */
    fun dispatch(intent: I) {
        store.intent(intent)
    }

    // No need to explicitly call store.stop() here,
    // as the store's Job is launched within viewModelScope and will be cancelled
    // when the ViewModel is cleared. The BaseStore's invokeOnCompletion handles cleanup.
}
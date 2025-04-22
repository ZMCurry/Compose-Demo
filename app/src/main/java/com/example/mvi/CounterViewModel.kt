package com.example.mvi

// 在你的 Android 应用模块中
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mvi.android.BaseMviViewModel
import com.example.mvi.api.StorePlugin // 引入你的插件
import com.example.mvi.plugins.EventRecorderPlugin // 引入你的插件

// 假设 CounterState, CounterIntent, CounterAction, CounterStore 已定义

class CounterViewModel(
    plugins: List<StorePlugin<CounterState, CounterIntent, CounterAction>>
) : BaseMviViewModel<CounterState, CounterIntent, CounterAction>(
    store = CounterStore(plugins = plugins)
) {
    // 提供访问 EventRecorder 的方法，以便 UI 可以获取事件
    fun getEventRecorder(): EventRecorderPlugin<CounterState, CounterIntent, CounterAction>? {
        // 假设 eventRecorder 是我们传入的那个实例
        // 注意：这种查找方式比较脆弱，更好的方式是在 BaseMviViewModel 或 Store 中直接提供访问
        return (store as CounterStore).plugins.find { it is EventRecorderPlugin<*, *, *> } as? EventRecorderPlugin<CounterState, CounterIntent, CounterAction>
    }
}

val eventRecorder = EventRecorderPlugin<CounterState, CounterIntent, CounterAction>()

class CounterViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CounterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // 确保将 eventRecorder 实例传入
            return CounterViewModel(plugins = listOf(eventRecorder)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

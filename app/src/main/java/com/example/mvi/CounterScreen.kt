// 示例：在 Composable 中使用
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mvi.CounterAction
import com.example.mvi.CounterIntent
import com.example.mvi.CounterViewModel
import com.example.mvi.CounterViewModelFactory
import com.example.mvi.api.StoreEvent

@Composable
fun CounterScreen(
    // 使用 ViewModel Factory 获取 ViewModel 实例
    viewModel: CounterViewModel = viewModel(factory = CounterViewModelFactory()) // 使用之前的 Factory
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current // 获取 Context 用于 Toast

    // 获取 EventRecorderPlugin 实例
    val eventRecorder = viewModel.getEventRecorder()
    // 收集记录的事件以供显示
    val recordedEvents by eventRecorder?.recordedEvents?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) }

    // 处理 Actions
    LaunchedEffect(Unit) {
        viewModel.actions.collect { action ->
            when (action) {
                is CounterAction.ShowToast -> {
                    Toast.makeText(context, "Action: ${action.message}", Toast.LENGTH_SHORT).show()
                    println("Action: ${action.message}")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Count: ${state.count}")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.dispatch(CounterIntent.Increment) }) {
                Text("Increment")
            }
            Button(onClick = { viewModel.dispatch(CounterIntent.Decrement) }) {
                Text("Decrement")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 添加回放按钮
        Button(onClick = {
            val intentsToReplay = recordedEvents
                .mapNotNull { event ->
                    // 从记录的事件中提取 Intent
                    if (event is StoreEvent.IntentReceived<*, *, *> && event.intent is CounterIntent) {
                        event.intent as CounterIntent
                    } else {
                        null
                    }
                }
                // 过滤掉 ReplayIntents 本身，防止无限循环
                .filterNot { it is CounterIntent.ReplayIntents }

            if (intentsToReplay.isNotEmpty()) {
                viewModel.dispatch(CounterIntent.ReplayIntents(intentsToReplay))
            } else {
                Toast.makeText(context, "No intents recorded to replay", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Replay All Intents")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Text("Recorded Events:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // 显示事件列表 (可选)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recordedEvents) { event ->
                Text(event.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
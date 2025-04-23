package com.example.mvi

import com.example.mvi.api.MVIAction
import com.example.mvi.api.MVIIntent


/**
 * 表示 StorePlugin 处理 onException 后的结果。
 * @param I Intent 类型
 * @param A Action 类型
 */
sealed interface ExceptionHandlerResult<out I : MVIIntent, out A : MVIAction> {
    /** 异常已被处理，Store 应继续运行，不采取额外行动。 */
    data object Handled : ExceptionHandlerResult<Nothing, Nothing>

    /** 异常已被处理，Store 应分发此 Intent 以进行恢复。 */
    data class DispatchIntent<I : MVIIntent>(val intent: I) : ExceptionHandlerResult<I, Nothing>

    /** 异常已被处理，Store 应发送此 Action。 */
    data class SendAction<A : MVIAction>(val action: A) : ExceptionHandlerResult<Nothing, A>

    /** 插件未处理此异常，或要求 Store 重新抛出它。 */
    data class Rethrow(val exception: Exception) : ExceptionHandlerResult<Nothing, Nothing>
}
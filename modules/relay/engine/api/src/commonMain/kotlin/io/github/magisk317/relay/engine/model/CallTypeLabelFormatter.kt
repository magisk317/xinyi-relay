package io.github.magisk317.relay.engine.model

/**
 * 通话类型标签。正文（MessageFormatter）与标题（SenderTemplateRenderer）共用，
 * 保证同一变量 {{CALL_TYPE}} 在两条渲染管线输出一致。
 */
object CallTypeLabelFormatter {
    private const val CALL_TYPE_ANSWERED_EXTERNALLY = 7

    fun format(callType: Int): String {
        return when (callType) {
            1 -> "来电"
            2 -> "去电"
            3 -> "未接"
            4 -> "语音信箱"
            5 -> "拒接"
            6 -> "拦截"
            CALL_TYPE_ANSWERED_EXTERNALLY -> "异地接听"
            else -> ""
        }
    }
}

package io.github.magisk317.relay.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.utils.XLog

class AutoInputAccessibilityService : AccessibilityService() {

    private var receiverRegistered = false

    private val autoInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SystemInputInjectorHook.resolveActionAutoInput()) return

            val code = intent.getStringExtra("code").orEmpty()
            val autoEnter = intent.getBooleanExtra("autoEnter", false)
            val inputIntervalMs = intent.getLongExtra("inputIntervalMs", 0L).coerceAtLeast(0L)
            val attemptId = intent.getLongExtra("attemptId", -1L).takeIf { it >= 0L }
            XLog.w(
                "Accessibility auto input request received: attemptId=%d code_len=%d autoEnter=%s inputIntervalMs=%d ordered=%s",
                attemptId ?: -1L,
                code.length,
                autoEnter,
                inputIntervalMs,
                isOrderedBroadcast,
            )

            if (code.isBlank()) {
                XLog.w("Accessibility auto input ignored: empty code")
                return
            }

            val result = handleAutoInput(code, autoEnter)
            XLog.w(
                "Accessibility auto input result: attemptId=%d success=%s strategy=%s reason=%s",
                attemptId ?: -1L,
                result.success,
                result.strategy,
                result.reason,
            )
            if (result.success && isOrderedBroadcast) {
                abortBroadcast()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerAutoInputReceiver()
        XLog.w("Accessibility auto input service connected")
    }

    override fun onDestroy() {
        unregisterAutoInputReceiver()
        XLog.w("Accessibility auto input service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun registerAutoInputReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(SystemInputInjectorHook.resolveActionAutoInput()).apply {
            priority = RECEIVER_PRIORITY_ACCESSIBILITY
        }
        ContextCompat.registerReceiver(
            this,
            autoInputReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterAutoInputReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(autoInputReceiver) }
        receiverRegistered = false
    }

    private fun handleAutoInput(
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        val root = rootInActiveWindow ?: return AutoInputResult(false, "none", "no_active_window")
        XLog.w(
            "Accessibility active window: pkg=%s class=%s",
            root.packageName?.toString().orEmpty().ifBlank { "<unknown>" },
            root.className?.toString().orEmpty().ifBlank { "<unknown>" },
        )

        val focusedNode = findFocusedEditableNode(root)
        if (focusedNode != null) {
            val focusedResult = setNodeText(focusedNode, code, autoEnter)
            if (focusedResult.success) {
                return focusedResult.copy(strategy = "focused_node")
            }
            XLog.w("Accessibility focused-node input failed: reason=%s", focusedResult.reason)
        }

        val editableNodes = collectEditableNodes(root)
        if (editableNodes.isEmpty()) {
            return AutoInputResult(false, "none", "no_editable_node")
        }

        val groupedResult = fillEditableGroup(editableNodes, code, autoEnter)
        if (groupedResult.success) {
            return groupedResult
        }

        val singleResult = setNodeText(editableNodes.first(), code, autoEnter)
        if (singleResult.success) {
            return singleResult.copy(strategy = "best_editable_node")
        }

        return groupedResult
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && canSetText(inputFocus)) {
            return inputFocus
        }
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && canSetText(accessibilityFocus)) {
            return accessibilityFocus
        }
        return null
    }

    private fun collectEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val result = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (canSetText(node)) {
                result += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
            .distinctBy { buildNodeIdentity(it) }
            .sortedWith(
                compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                    .thenBy { nodeTop(it) }
                    .thenBy { nodeLeft(it) },
            )
    }

    private fun fillEditableGroup(
        nodes: List<AccessibilityNodeInfo>,
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        if (code.length !in 4..8) {
            return AutoInputResult(false, "group_nodes", "group_strategy_not_applicable")
        }
        val charNodes = nodes.filter { nodeTextLength(it) <= 1 }
        if (charNodes.size < code.length) {
            return AutoInputResult(false, "group_nodes", "not_enough_editable_nodes")
        }

        for ((index, char) in code.withIndex()) {
            val result = setNodeText(
                node = charNodes[index],
                text = char.toString(),
                autoEnter = autoEnter && index == code.lastIndex,
            )
            if (!result.success) {
                XLog.w("Accessibility grouped input failed at index=%d reason=%s", index, result.reason)
                return AutoInputResult(false, "group_nodes", "group_set_text_failed")
            }
        }
        return AutoInputResult(true, "group_nodes", "ok")
    }

    private fun setNodeText(
        node: AccessibilityNodeInfo,
        text: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        return runCatching {
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.refresh()
            }
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setTextSucceeded = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (!setTextSucceeded) {
                return AutoInputResult(false, "set_text", "action_set_text_failed")
            }
            if (autoEnter) {
                tryImeEnter(node)
            }
            AutoInputResult(true, "set_text", "ok")
        }.getOrElse { throwable ->
            XLog.w("Accessibility set text failed: %s", throwable.message ?: throwable.javaClass.simpleName)
            AutoInputResult(false, "set_text", "set_text_exception")
        }
    }

    private fun tryImeEnter(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            XLog.w("Accessibility auto enter skipped: android version too low")
            return
        }
        val imeEnterActionId = runCatching {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        }.getOrNull() ?: return
        if (node.actionList.none { it.id == imeEnterActionId }) {
            XLog.w("Accessibility auto enter skipped: ime action unsupported")
            return
        }
        val result = node.performAction(imeEnterActionId)
        XLog.w("Accessibility auto enter invoked: success=%s", result)
    }

    private fun canSetText(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        if (!node.isEnabled) return false
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return true
        if (node.isEditable) return true
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true)
    }

    private fun buildNodeIdentity(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return listOf(
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            node.packageName?.toString().orEmpty(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
        ).joinToString("|")
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).top

    private fun nodeLeft(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).left

    private fun nodeTextLength(node: AccessibilityNodeInfo): Int {
        val hintLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.length
        } else {
            null
        }
        return node.text?.length ?: hintLength ?: 0
    }

    private data class AutoInputResult(
        val success: Boolean,
        val strategy: String,
        val reason: String,
    )

    private companion object {
        private const val RECEIVER_PRIORITY_ACCESSIBILITY = 1000
    }
}

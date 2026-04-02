package io.github.magisk317.relay.xp.hook.forward

import android.content.Intent
import java.util.Collections
import java.util.IdentityHashMap

internal data class SmsForwardSimRouting(
    val simSlot: Int? = null,
    val subId: Int? = null,
) {
    fun hasValue(): Boolean = simSlot != null || subId != null
}

internal object SmsForwardSimRoutingResolver {
    private const val EXTRA_SIM_SLOT = "sim_slot"
    private const val EXTRA_SUB_ID = "sub_id"
    private const val MAX_TEXT_SNAPSHOT_LENGTH = 120

    private val slotTextRegex = Regex(
        """\b(?:phoneId|phondId|slotId|slotIndex|simSlot|simId)\s*[=:]\s*(-?\d+)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val subIdTextRegex = Regex(
        """\b(?:subId|subscriptionId)\s*[=:]\s*(-?\d+)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val slotMethodNames = listOf(
        "getSlotIndex",
        "getSlotId",
        "getPhoneId",
        "getSimSlot",
        "getSimId",
    )
    private val slotFieldNames = listOf(
        "slotIndex",
        "mSlotIndex",
        "slotId",
        "mSlotId",
        "phoneId",
        "mPhoneId",
        "simSlot",
        "mSimSlot",
        "simId",
        "mSimId",
    )
    private val subIdMethodNames = listOf(
        "getSubId",
        "getSubscriptionId",
    )
    private val subIdFieldNames = listOf(
        "subId",
        "mSubId",
        "subscriptionId",
        "mSubscriptionId",
    )
    private val nestedMethodNames = listOf(
        "getPhone",
        "getDefaultPhone",
        "getPhoneExt",
        "getTracker",
        "getInboundSmsTracker",
        "getSmsTracker",
        "getDispatcher",
        "getDispatchersController",
        "getSender",
        "getOwner",
    )
    private val nestedFieldNames = listOf(
        "mPhone",
        "phone",
        "mDefaultPhone",
        "defaultPhone",
        "mPhoneExt",
        "phoneExt",
        "mTracker",
        "tracker",
        "mInboundSmsTracker",
        "inboundSmsTracker",
        "mSmsTracker",
        "smsTracker",
        "mDispatcher",
        "dispatcher",
        "mDispatchersController",
        "dispatchersController",
        "sender",
        "mSender",
        "owner",
        "mOwner",
    )

    fun readFromIntent(intent: Intent): SmsForwardSimRouting {
        val simSlot = readIntExtra(
            intent,
            EXTRA_SIM_SLOT,
            "slot",
            "simId",
            "sim_id",
            "simSlot",
            "android.telephony.extra.SLOT_INDEX",
        )?.takeIf { it >= 0 }
        val subId = readIntExtra(
            intent,
            EXTRA_SUB_ID,
            "subscription",
            "subscription_id",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "android.telephony.extra.SUBSCRIPTION_ID",
        )?.takeIf { it > 0 }
        return SmsForwardSimRouting(simSlot = simSlot, subId = subId)
    }

    fun ensureSimRoutingExtras(
        intent: Intent,
        handler: Any?,
        args: Array<Any?>?,
    ): SmsForwardSimRouting? {
        val existing = readFromIntent(intent)
        if (existing.hasValue()) return existing

        val resolved = resolve(handler = handler, args = args) ?: return null
        resolved.simSlot?.let { intent.putExtra(EXTRA_SIM_SLOT, it) }
        resolved.subId?.let { intent.putExtra(EXTRA_SUB_ID, it) }
        return resolved
    }

    fun resolve(
        handler: Any?,
        args: Array<Any?>?,
    ): SmsForwardSimRouting? {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val fromArgs = args.orEmpty()
            .asSequence()
            .mapNotNull { resolveFromTarget(it, depth = 2, visited = visited).takeIf(SmsForwardSimRouting::hasValue) }
            .firstOrNull()
        val fromHandler = resolveFromTarget(handler, depth = 3, visited = visited)
        return merge(fromArgs, fromHandler).takeIf(SmsForwardSimRouting::hasValue)
    }

    fun debugSnapshot(
        handler: Any?,
        args: Array<Any?>?,
    ): String {
        val parts = buildList {
            handler?.let { add(describeTarget("handler", it)) }
            args.orEmpty().forEachIndexed { index, arg ->
                if (arg != null) {
                    add(describeTarget("arg[$index]", arg))
                }
            }
        }
        return parts.joinToString(separator = " | ").ifBlank { "<no_targets>" }
    }

    private fun describeTarget(
        label: String,
        target: Any,
    ): String {
        val direct = SmsForwardSimRouting(
            simSlot = extractSlot(target),
            subId = extractSubId(target),
        )
        val children = extractObjects(target, nestedMethodNames, nestedFieldNames)
        val childClasses = children.joinToString(prefix = "[", postfix = "]") { it.javaClass.simpleName }
        return buildString {
            append(label)
            append('=')
            append(target.javaClass.name)
            append("(slot=")
            append(direct.simSlot?.toString() ?: "<none>")
            append(",subId=")
            append(direct.subId?.toString() ?: "<none>")
            append(",children=")
            append(childClasses)
            append(",text=")
            append(summarizeText(target))
            append(')')
        }
    }

    private fun resolveFromTarget(
        target: Any?,
        depth: Int,
        visited: MutableSet<Any>,
    ): SmsForwardSimRouting {
        if (target == null) return SmsForwardSimRouting()
        if (!visited.add(target)) return SmsForwardSimRouting()

        var merged = SmsForwardSimRouting(
            simSlot = extractSlot(target),
            subId = extractSubId(target),
        )
        if (depth <= 0) return merged

        extractObjects(target, nestedMethodNames, nestedFieldNames).forEach { child ->
            merged = merge(merged, resolveFromTarget(child, depth = depth - 1, visited = visited))
            if (merged.simSlot != null && merged.subId != null) {
                return merged
            }
        }
        return merged
    }

    private fun merge(
        primary: SmsForwardSimRouting?,
        fallback: SmsForwardSimRouting?,
    ): SmsForwardSimRouting {
        return SmsForwardSimRouting(
            simSlot = primary?.simSlot ?: fallback?.simSlot,
            subId = primary?.subId ?: fallback?.subId,
        )
    }

    private fun extractSlot(target: Any): Int? {
        return extractInt(target, slotMethodNames, slotFieldNames, slotTextRegex)?.takeIf { it >= 0 }
    }

    private fun extractSubId(target: Any): Int? {
        return extractInt(target, subIdMethodNames, subIdFieldNames, subIdTextRegex)?.takeIf { it > 0 }
    }

    private fun extractInt(
        target: Any,
        methodNames: List<String>,
        fieldNames: List<String>,
        textRegex: Regex,
    ): Int? {
        methodNames.forEach { name ->
            val value = invokeNoArg(target, name)?.coerceIntOrNull()
            if (value != null) return value
        }
        fieldNames.forEach { name ->
            val value = readField(target, name)?.coerceIntOrNull()
            if (value != null) return value
        }
        textRegex.find(target.toString())?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun extractObjects(
        target: Any,
        methodNames: List<String>,
        fieldNames: List<String>,
    ): List<Any> {
        val values = mutableListOf<Any>()
        methodNames.forEach { name ->
            invokeNoArg(target, name)?.let(values::add)
        }
        fieldNames.forEach { name ->
            readField(target, name)?.let(values::add)
        }
        return values.distinctBy { System.identityHashCode(it) }
    }

    private fun invokeNoArg(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            val method = runCatching {
                clazz.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
            }.getOrNull()
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(target)
                }.getOrNull()
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun readField(target: Any, name: String): Any? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            val field = runCatching {
                clazz.declaredFields.firstOrNull { it.name == name }
            }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun Any.coerceIntOrNull(): Int? {
        return when (this) {
            is Int -> this
            is Number -> toInt()
            is String -> trim().toIntOrNull()
            else -> null
        }
    }

    private fun summarizeText(target: Any): String {
        return target.toString()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_TEXT_SNAPSHOT_LENGTH)
    }

    private fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        for (key in keys) {
            if (!intent.hasExtra(key)) continue
            val intValue = intent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = intent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            intent.getStringExtra(key)?.trim()?.toIntOrNull()?.let { return it }
        }
        return null
    }
}

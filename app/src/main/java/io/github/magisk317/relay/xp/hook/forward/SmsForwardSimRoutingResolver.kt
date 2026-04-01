package io.github.magisk317.relay.xp.hook.forward

import android.content.Intent

internal data class SmsForwardSimRouting(
    val simSlot: Int? = null,
    val subId: Int? = null,
) {
    fun hasValue(): Boolean = simSlot != null || subId != null
}

internal object SmsForwardSimRoutingResolver {
    private const val EXTRA_SIM_SLOT = "sim_slot"
    private const val EXTRA_SUB_ID = "sub_id"
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
    private val phoneMethodNames = listOf("getPhone")
    private val phoneFieldNames = listOf("mPhone", "phone")

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
        val fromArgs = args.orEmpty()
            .asSequence()
            .mapNotNull { resolveFromTarget(it, allowNestedPhone = true).takeIf(SmsForwardSimRouting::hasValue) }
            .firstOrNull()
        val fromHandler = resolveFromTarget(handler, allowNestedPhone = true)
        return merge(fromArgs, fromHandler).takeIf(SmsForwardSimRouting::hasValue)
    }

    private fun resolveFromTarget(
        target: Any?,
        allowNestedPhone: Boolean,
    ): SmsForwardSimRouting {
        if (target == null) return SmsForwardSimRouting()

        val direct = SmsForwardSimRouting(
            simSlot = extractSlot(target),
            subId = extractSubId(target),
        )
        if (!allowNestedPhone) return direct

        val phone = extractObject(target, phoneMethodNames, phoneFieldNames)
        val fromPhone = resolveFromTarget(phone, allowNestedPhone = false)
        return merge(direct, fromPhone)
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
        return extractInt(target, slotMethodNames, slotFieldNames)?.takeIf { it >= 0 }
    }

    private fun extractSubId(target: Any): Int? {
        return extractInt(target, subIdMethodNames, subIdFieldNames)?.takeIf { it > 0 }
    }

    private fun extractInt(
        target: Any,
        methodNames: List<String>,
        fieldNames: List<String>,
    ): Int? {
        methodNames.forEach { name ->
            val value = invokeNoArg(target, name)?.toIntOrNull()
            if (value != null) return value
        }
        fieldNames.forEach { name ->
            val value = readField(target, name)?.toIntOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun extractObject(
        target: Any,
        methodNames: List<String>,
        fieldNames: List<String>,
    ): Any? {
        methodNames.forEach { name ->
            invokeNoArg(target, name)?.let { return it }
        }
        fieldNames.forEach { name ->
            readField(target, name)?.let { return it }
        }
        return null
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

    private fun Any.toIntOrNull(): Int? {
        return when (this) {
            is Int -> this
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> null
        }
    }

    private fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        for (key in keys) {
            if (!intent.hasExtra(key)) continue
            val intValue = intent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = intent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            intent.getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }
}

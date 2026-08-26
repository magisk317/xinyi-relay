package io.github.magisk317.relay.xp.hook

import android.content.Context
import io.github.magisk317.smscode.runtime.verification.VerificationRuntimeContext
import io.github.magisk317.smscode.xposed.utils.XLog

internal data class SmsHookRuntimeContext(
    override val phoneContext: Context,
    override val pluginContext: Context,
) : VerificationRuntimeContext

internal class SmsHookRuntimeSession(
    private val applicationId: String,
    private val fallbackPackageName: String? = null,
    private val pluginContextResolver: (Context, String) -> Context? = { phoneContext, applicationId ->
        SmsHookBridgeHelper.resolvePluginContext(
            phoneContext = phoneContext,
            currentPluginContext = null,
            applicationId = applicationId,
        )
    },
    private val heartbeatRecorder: (Context, Context, String, String) -> Unit = { pluginContext, phoneContext, packageName, source ->
        SmsHookBridgeHelper.recordSmsHookHeartbeat(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            packageName = packageName,
            source = source,
        )
    },
) {
    @Volatile
    private var phoneContext: Context? = null

    @Volatile
    private var pluginContext: Context? = null

    fun initialize(context: Context): SmsHookRuntimeContext? = synchronized(this) {
        if (phoneContext == null) {
            phoneContext = context
        }
        currentOrResolveLocked()
    }

    fun currentOrResolve(): SmsHookRuntimeContext? = synchronized(this) {
        currentOrResolveLocked()
    }

    /**
     * Try to resolve runtime context, using [inboundSmsHandler] as a fallback source for
     * phoneContext if the constructor hook never fired (e.g., after a hot module update
     * without phone process restart).
     *
     * InboundSmsHandler extends StateMachine which holds mContext.
     */
    fun currentOrResolveWithFallback(inboundSmsHandler: Any?): SmsHookRuntimeContext? = synchronized(this) {
        val existing = currentOrResolveLocked()
        if (existing != null) return existing
        // Fallback: try to extract context from the InboundSmsHandler instance
        if (phoneContext == null && inboundSmsHandler != null) {
            val fallbackContext = extractContextFromHandler(inboundSmsHandler)
            if (fallbackContext != null) {
                phoneContext = fallbackContext
                XLog.w(
                    "SmsHookRuntimeSession: recovered phoneContext from handler instance (constructor hook may not have fired)",
                )
            }
        }
        currentOrResolveLocked()
    }

    fun recordHeartbeat(source: String): SmsHookRuntimeContext? {
        val runtime = currentOrResolve() ?: return null
        val packageName = runtime.phoneContext.packageName.ifBlank { fallbackPackageName.orEmpty() }
        heartbeatRecorder(runtime.pluginContext, runtime.phoneContext, packageName, source)
        return runtime
    }

    private fun currentOrResolveLocked(): SmsHookRuntimeContext? {
        val resolvedPhoneContext = phoneContext ?: return null
        val resolvedPluginContext = pluginContext
            ?: pluginContextResolver(resolvedPhoneContext, applicationId)?.also { pluginContext = it }
            ?: return null
        return SmsHookRuntimeContext(
            phoneContext = resolvedPhoneContext,
            pluginContext = resolvedPluginContext,
        )
    }

    private fun extractContextFromHandler(handler: Any): Context? {
        return runCatching {
            // InboundSmsHandler extends StateMachine; StateMachine has getHandler().getLooper()
            // or mContext field. Try known field names.
            var clazz: Class<*>? = handler.javaClass
            while (clazz != null) {
                val contextField = clazz.declaredFields.firstOrNull { field ->
                    Context::class.java.isAssignableFrom(field.type) &&
                        (field.name == "mContext" || field.name == "context")
                }
                if (contextField != null) {
                    contextField.isAccessible = true
                    return@runCatching contextField.get(handler) as? Context
                }
                clazz = clazz.superclass
            }
            null
        }.getOrNull()
    }
}

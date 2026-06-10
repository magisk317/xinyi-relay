package io.github.magisk317.relay.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object AppInitExecution {
    private const val LOG_TAG = "relay"

    fun runWhenUserUnlocked(
        application: Application,
        scope: CoroutineScope,
        taskName: String,
        block: suspend () -> Unit,
    ) {
        if (isUserUnlocked(application)) {
            Log.i(LOG_TAG, "$taskName start immediately: user unlocked")
            runSafely(scope, taskName, block)
            return
        }

        Log.w(
            LOG_TAG,
            "$taskName deferred: user locked, wait for ACTION_USER_UNLOCKED sdk=${Build.VERSION.SDK_INT}",
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_USER_UNLOCKED) return
                unregisterReceiverSafely(application, this)
                Log.i(LOG_TAG, "$taskName resumed after user unlock broadcast")
                runSafely(scope, taskName, block)
            }
        }
        registerUserUnlockedReceiver(application, receiver)

        if (isUserUnlocked(application)) {
            unregisterReceiverSafely(application, receiver)
            Log.i(LOG_TAG, "$taskName resumed after late user unlock check")
            runSafely(scope, taskName, block)
        }
    }

    private fun runSafely(
        scope: CoroutineScope,
        taskName: String,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            Log.i(LOG_TAG, "$taskName executing")
            runCatching { block() }.onFailure { error ->
                Log.e(LOG_TAG, "$taskName failed", error)
            }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val userManager = context.getSystemService(UserManager::class.java) ?: return true
        return userManager.isUserUnlocked
    }

    private fun registerUserUnlockedReceiver(
        application: Application,
        receiver: BroadcastReceiver,
    ) {
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiverSafely(
        application: Application,
        receiver: BroadcastReceiver,
    ) {
        runCatching { application.unregisterReceiver(receiver) }
    }
}

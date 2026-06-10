package io.github.magisk317.relay.app

import android.app.Application

/**
 * Interface for application initializers.
 */
interface AppInitializer {
    fun init(application: Application)
}

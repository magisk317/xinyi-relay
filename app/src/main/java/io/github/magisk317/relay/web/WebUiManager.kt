package io.github.magisk317.relay.web

import android.content.Context
import io.github.magisk317.relay.service.WebUiServiceManager

class WebUiManager(private val context: Context) {
    private val delegate by lazy { WebUiServiceManager(context) }

    fun start() {
        delegate.start()
    }

    fun stop() {
        delegate.stop()
    }
}

package com.github.magisk317.smscode.common.serialization

import kotlinx.serialization.json.Json

object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }
}

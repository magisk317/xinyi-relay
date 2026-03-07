package com.github.magisk317.smscode.ui.app.base

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * base activity
 */
abstract class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
    }
}

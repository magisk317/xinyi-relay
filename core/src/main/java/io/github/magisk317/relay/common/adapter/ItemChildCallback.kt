package io.github.magisk317.relay.common.adapter

import android.view.View

fun interface ItemChildCallback<E> {
    fun onItemChildClicked(childView: View, item: E, position: Int)
}

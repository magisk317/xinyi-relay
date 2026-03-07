package com.github.magisk317.smscode.common.adapter

import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.View

interface ItemCallback<E> {
    fun onItemClicked(itemView: View, item: E, position: Int)
    fun onItemLongClicked(itemView: View, item: E, position: Int): Boolean
    fun onCreateItemContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?, item: E, position: Int)
}

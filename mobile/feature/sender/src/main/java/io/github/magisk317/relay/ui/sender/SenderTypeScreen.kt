package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.mobilefeature.sender.BuildConfig
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

@Composable
fun SenderTypeScreen(
    onBack: () -> Unit,
    onAddClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val supportedTypes = mutableListOf(
        SenderType.DINGTALK_GROUP_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_GROUP_ROBOT),
        SenderType.DINGTALK_INNER_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_INNER_ROBOT),
        SenderType.FEISHU to getSenderTypeName(context, SenderType.FEISHU),
        SenderType.FEISHU_APP to getSenderTypeName(context, SenderType.FEISHU_APP),
        SenderType.WEWORK_ROBOT to getSenderTypeName(context, SenderType.WEWORK_ROBOT),
        SenderType.WEWORK_AGENT to getSenderTypeName(context, SenderType.WEWORK_AGENT),
        SenderType.TELEGRAM to getSenderTypeName(context, SenderType.TELEGRAM),
        SenderType.WEBHOOK to getSenderTypeName(context, SenderType.WEBHOOK),
        SenderType.SERVERCHAN to getSenderTypeName(context, SenderType.SERVERCHAN),
        SenderType.PUSHPLUS to getSenderTypeName(context, SenderType.PUSHPLUS),
        SenderType.GOTIFY to getSenderTypeName(context, SenderType.GOTIFY),
        SenderType.NTFY to getSenderTypeName(context, SenderType.NTFY),
        SenderType.BARK to getSenderTypeName(context, SenderType.BARK),
        SenderType.PUSHDEER to getSenderTypeName(context, SenderType.PUSHDEER),
        SenderType.MATRIX to getSenderTypeName(context, SenderType.MATRIX),
        SenderType.YUNHU to getSenderTypeName(context, SenderType.YUNHU),
        SenderType.EMAIL to getSenderTypeName(context, SenderType.EMAIL),
        SenderType.URL_SCHEME to getSenderTypeName(context, SenderType.URL_SCHEME),
        SenderType.SOCKET to getSenderTypeName(context, SenderType.SOCKET),
    )
    if (BuildConfig.ENABLE_SMS_CHANNEL) {
        supportedTypes.add(1, SenderType.SMS to getSenderTypeName(context, SenderType.SMS))
    }

    val senderTypeBody: @Composable (PaddingValues) -> Unit = { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(supportedTypes, key = { it.first }) { (type, name) ->
                Button(
                    onClick = { onAddClick(type) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(name)
                }
            }
        }
    }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> SenderTypeScreenMiuix(
            title = stringResource(R.string.sender_add_type_title),
            onBack = onBack,
            body = senderTypeBody,
        )

        UiKitStyle.Expressive -> SenderTypeScreenMaterial(
            title = stringResource(R.string.sender_add_type_title),
            onBack = onBack,
            body = senderTypeBody,
        )
    }
}

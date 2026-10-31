package io.github.magisk317.relay.ui.record

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.relay.ui.common.AppIconBitmapImage
import io.github.magisk317.uikit.preference.AppCheckbox
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.WorkspaceListDivider
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.WorkspaceListItemDefaults
import io.github.magisk317.uikit.surface.pageSurfaceColor
import io.github.magisk317.uikit.surface.swipeRevealSurface
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.AppShapeRole
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.appShape
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Modifier.recordBenchmarkTag(enabled: Boolean): Modifier =
    if (enabled) testTag(BENCHMARK_RECORDS_LIST) else this

private const val BENCHMARK_RECORDS_LIST = "xinyi_benchmark_records_list"

private fun compactSenderTitle(sender: String?, fallback: String): String {
    val raw = sender?.takeIf { it.isNotBlank() } ?: fallback
    return raw.trim().take(8)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSplitColumn(
    title: String,
    emptyHint: String,
    list: List<ReadRecordData>,
    recordTab: Int,
    packageLabels: Map<String, String>,
    recordIcons: Map<String, Bitmap>,
    defaultSmsPackage: String?,
    defaultDialerPackage: String?,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onActivateSelection: (Long) -> Unit,
    onCopyCode: (ReadRecordData) -> Unit,
    onShowDetail: (ReadRecordData) -> Unit,
    onDelete: (ReadRecordData) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    listScrollModifier: Modifier = Modifier,
    listState: LazyListState,
    scrollChromeState: ScrollChromeState? = null,
    isActive: Boolean = true,
    scrollToTopSignal: Int = 0,
    benchmarkTagsEnabled: Boolean = true,
) {
    ReportLazyListScrollToChrome(listState, scrollChromeState)
    io.github.magisk317.uikit.surface.ScrollToTopEffect(listState, scrollToTopSignal)
    val isMiuix = currentUiKitStyle() == UiKitStyle.Miuix
    AppSurface(
        modifier = modifier,
        shape = appShape(AppShapeRole.Large),
        color = Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = title,
                        role = AppTextRole.Subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = list.size.toString(),
                        role = AppTextRole.Footnote,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
                WorkspaceListDivider()
            }
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordBenchmarkTag(benchmarkTagsEnabled)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = emptyHint,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordBenchmarkTag(benchmarkTagsEnabled)
                        .then(listScrollModifier),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(if (isMiuix) 12.dp else 0.dp),
                    contentPadding = listContentPadding,
                ) {
                    items(list, key = { it.id }) { smsMsg ->
                        val isSelected = selectedIds.contains(smsMsg.id)
                        if (isSelectionMode) {
                            if (smsMsg.msgType == ReadRecordData.MSG_TYPE_APP_NOTIFY) {
                                AppNotificationItem(
                                    smsMsg = smsMsg,
                                    isSelectionMode = true,
                                    isSelected = isSelected,
                                    onClick = { onToggleSelection(smsMsg.id) },
                                    onLongClick = {},
                                    onDetailClick = { onShowDetail(smsMsg) },
                                    packageLabels = packageLabels,
                                    recordIcons = recordIcons,
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                CodeRecordItem(
                                    smsMsg = smsMsg,
                                    recordTab = recordTab,
                                    defaultSmsPackage = defaultSmsPackage,
                                    defaultDialerPackage = defaultDialerPackage,
                                    isSelectionMode = true,
                                    isSelected = isSelected,
                                    onClick = { onToggleSelection(smsMsg.id) },
                                    onLongClick = {},
                                    onDetailClick = { onShowDetail(smsMsg) },
                                    recordIcons = recordIcons,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(isActive, dismissState.currentValue) {
                                if (isActive && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    onDelete(smsMsg)
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = recordRowDismissEnabled(isActive, isSelectionMode),
                                enableDismissFromEndToStart = recordRowDismissEnabled(isActive, isSelectionMode),
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .then(
                                                swipeRevealSurface(
                                                    color = appColor(AppColorRole.ErrorContainer),
                                                ),
                                            )
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = if (dismissState.dismissDirection ==
                                            SwipeToDismissBoxValue.StartToEnd
                                        ) {
                                            Alignment.CenterStart
                                        } else {
                                            Alignment.CenterEnd
                                        },
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove),
                                            tint = appColor(AppColorRole.OnErrorContainer),
                                        )
                                    }
                                },
                                content = {
                                    if (smsMsg.msgType == ReadRecordData.MSG_TYPE_APP_NOTIFY) {
                                        AppNotificationItem(
                                            smsMsg = smsMsg,
                                            isSelectionMode = false,
                                            isSelected = false,
                                            onClick = { onCopyCode(smsMsg) },
                                            onLongClick = { onActivateSelection(smsMsg.id) },
                                            onDetailClick = { onShowDetail(smsMsg) },
                                            packageLabels = packageLabels,
                                            recordIcons = recordIcons,
                                            modifier = Modifier.animateItem(),
                                        )
                                    } else {
                                        CodeRecordItem(
                                            smsMsg = smsMsg,
                                            recordTab = recordTab,
                                            defaultSmsPackage = defaultSmsPackage,
                                            defaultDialerPackage = defaultDialerPackage,
                                            isSelectionMode = false,
                                            isSelected = false,
                                            onClick = { onCopyCode(smsMsg) },
                                            onLongClick = { onActivateSelection(smsMsg.id) },
                                            onDetailClick = { onShowDetail(smsMsg) },
                                            recordIcons = recordIcons,
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                },
                            )
                        }
                        // Miuix rows are gapped cards (MiPush/XSC rhythm); dividers belong to
                        // the Expressive full-bleed rows only.
                        if (!isMiuix) {
                            WorkspaceListDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Container colour for a record row hosted inside a swipe-to-dismiss box.
 *
 * Expressive rows are full-bleed and transparent by default, but the dismiss reveal paints the
 * whole row with `ErrorContainer`, so a transparent row leaves that red permanently visible.
 * Painting the page surface keeps the row opaque while staying visually identical to the page
 * it sits on. Miuix keeps [Color.Transparent] because its branch already falls back to an
 * opaque `surfaceContainer` card.
 */
@Composable
internal fun recordRowContainerColor(): Color =
    if (currentUiKitStyle() == UiKitStyle.Miuix) Color.Transparent else pageSurfaceColor()

@Composable
fun CodeRecordItem(
    smsMsg: ReadRecordData,
    recordTab: Int,
    defaultSmsPackage: String?,
    defaultDialerPackage: String?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    recordIcons: Map<String, Bitmap>,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat(RECORD_DATE_FORMAT, Locale.getDefault()) }
    val showAppIcon = recordTab == 0 && !smsMsg.packageName.isNullOrBlank()

    val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
        .trim()
        .trim('【', '】', '[', ']')
    val iconPackageName = remember(
        smsMsg.packageName,
        smsMsg.msgType,
        showAppIcon,
        defaultSmsPackage,
        defaultDialerPackage,
    ) {
        when {
            showAppIcon && !smsMsg.packageName.isNullOrBlank() -> smsMsg.packageName
            smsMsg.msgType == ReadRecordData.MSG_TYPE_CALL_NOTIFY -> defaultDialerPackage
            smsMsg.msgType == ReadRecordData.MSG_TYPE_SMS -> defaultSmsPackage
            else -> null
        }
    }

    WorkspaceListItem(
        modifier = modifier,
        containerColor = if (isSelected) {
            appColor(AppColorRole.PrimaryContainer)
        } else {
            recordRowContainerColor()
        },
        onClick = onClick,
        onLongClick = onLongClick,
        leadingWidth = WorkspaceListItemDefaults.iconColumnWidth,
        leadingContent = {
            AppIconBitmapImage(
                bitmap = iconPackageName?.let(recordIcons::get),
                size = RECORD_ICON_SIZE,
                contentDescription = stringResource(R.string.sms_icon_description),
                fallbackIcon = when {
                    smsMsg.msgType == ReadRecordData.MSG_TYPE_CALL_NOTIFY -> Icons.Default.Call
                    else -> Icons.Default.Email
                },
            )
        },
        selected = isSelected,
        selectionMode = isSelectionMode,
        selectionContent = {
            AppCheckbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
            )
        },
        supportingContent = {
            val forwardStatusAnnotated = resolveForwardStatusAnnotated(smsMsg)
            AppText(
                text = buildAnnotatedString {
                    append("${stringResource(R.string.detail_forward_status)}: ")
                    append(forwardStatusAnnotated)
                },
                role = AppTextRole.Footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasCode = !smsMsg.smsCode.isNullOrBlank()
            val codeOrSender = when {
                hasCode -> smsMsg.smsCode.orEmpty()
                else -> compactSenderTitle(smsMsg.sender, fallbackLabel)
            }
            AppText(
                text = codeOrSender,
                role = AppTextRole.Title,
                color = appColor(AppColorRole.Primary),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            AppText(
                text = dateFormatter.format(Date(smsMsg.date)),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        val body = smsMsg.body
        if (!body.isNullOrEmpty()) {
            AppText(
                text = body,
                role = AppTextRole.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onDetailClick() },
            )
        }
    }
}

@Composable
fun AppNotificationItem(
    smsMsg: ReadRecordData,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    packageLabels: Map<String, String>,
    recordIcons: Map<String, Bitmap>,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat(RECORD_DATE_FORMAT, Locale.getDefault()) }

    val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
        .trim()
        .trim('【', '】', '[', ']')
    val appLabel = smsMsg.packageName?.let(packageLabels::get)
    val displayLabel = appLabel ?: fallbackLabel

    WorkspaceListItem(
        modifier = modifier,
        containerColor = if (isSelected) {
            appColor(AppColorRole.PrimaryContainer)
        } else {
            recordRowContainerColor()
        },
        onClick = onClick,
        onLongClick = onLongClick,
        leadingWidth = WorkspaceListItemDefaults.iconColumnWidth,
        leadingContent = {
            AppIconBitmapImage(
                bitmap = smsMsg.packageName?.let(recordIcons::get),
                size = APP_NOTIFY_ICON_SIZE,
                contentDescription = stringResource(R.string.sms_icon_description),
            )
        },
        selected = isSelected,
        selectionMode = isSelectionMode,
        selectionContent = {
            AppCheckbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
            )
        },
        supportingContent = {
            val forwardStatusAnnotated = resolveForwardStatusAnnotated(smsMsg)
            AppText(
                text = buildAnnotatedString {
                    append("${stringResource(R.string.detail_forward_status)}: ")
                    append(forwardStatusAnnotated)
                },
                role = AppTextRole.Footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            AppText(
                text = displayLabel,
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            AppText(
                text = dateFormatter.format(Date(smsMsg.date)),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
        AppText(
            text = smsMsg.sender ?: "",
            role = AppTextRole.Body,
            color = appColor(AppColorRole.OnSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val body = smsMsg.body
        if (!body.isNullOrEmpty()) {
            AppText(
                text = body,
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onDetailClick() },
            )
        }
    }
}
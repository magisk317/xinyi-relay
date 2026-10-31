package io.github.magisk317.relay.ui.home.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.forward.engine.model.Sender
import io.github.magisk317.forward.engine.sender.SenderType
import io.github.magisk317.forward.engine.service.AppConfigRepository
import io.github.magisk317.relay.mobilefeature.scheduled.BuildConfig
import io.github.magisk317.forward.sender.SenderSettingSanitizer
import io.github.magisk317.forward.sender.sanitizeSenderLenient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ScheduledReminderViewModel(
    configRepository: AppConfigRepository,
) : ViewModel() {
    val senderList: StateFlow<List<Sender>> = configRepository.getAllSendersFlow()
        .map { list ->
            val sanitizedList = list.map { sender ->
                SenderSettingSanitizer.sanitizeSenderLenient(sender)
            }
            if (BuildConfig.ENABLE_SMS_CHANNEL) {
                sanitizedList
            } else {
                sanitizedList.filterNot { it.type == SenderType.SMS }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}

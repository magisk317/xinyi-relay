package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleEvaluator

/**
 * `Sender` is `@Parcelize` and therefore Android-only, so the entry points that
 * accept it stay in androidMain as extensions over the shared implementations.
 */
fun SenderSettingSanitizer.sanitizeSenderLenient(sender: Sender): Sender {
    val safeJson = sanitizeJsonLenient(sender.type, sender.jsonSetting)
    val safeSchedule = SenderActiveScheduleEvaluator.sanitize(sender.activeSchedule)
    return if (safeJson == sender.jsonSetting && safeSchedule == sender.activeSchedule) {
        sender
    } else {
        sender.copy(jsonSetting = safeJson, activeSchedule = safeSchedule)
    }
}

fun SenderSettingDrafts.fromSender(sender: Sender): SenderSettingDraft =
    fromJson(sender.type, sender.jsonSetting)

fun SenderSettingDrafts.fromSenderWithDefaults(sender: Sender): SenderSettingDraft =
    fromSender(sender).withSchemaDefaults()

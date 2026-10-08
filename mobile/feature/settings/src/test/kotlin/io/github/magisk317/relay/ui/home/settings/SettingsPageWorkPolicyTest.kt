package io.github.magisk317.relay.ui.home.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsPageWorkPolicyTest {

    @Test
    fun inactiveSettingsPage_suspendsAllPageOwnedWork() {
        val policy = settingsPageWorkPolicy(isActive = false)

        assertFalse(policy.loadSnapshots)
        assertFalse(policy.inspectLauncherIcon)
        assertFalse(policy.publishLauncherMirror)
        assertFalse(policy.collectBackupEvents)
        assertFalse(policy.inspectBackup)
    }

    @Test
    fun activeSettingsPage_enablesAllPageOwnedWork() {
        val policy = settingsPageWorkPolicy(isActive = true)

        assertTrue(policy.loadSnapshots)
        assertTrue(policy.inspectLauncherIcon)
        assertTrue(policy.publishLauncherMirror)
        assertTrue(policy.collectBackupEvents)
        assertTrue(policy.inspectBackup)
    }

    @Test
    fun advancedPage_exposesBenchmarkTagsOnlyWhileActiveAndEnabled() {
        assertTrue(advancedPageWorkPolicy(isActive = true, benchmarkTagsEnabled = true).exposeBenchmarkTags)
        assertFalse(advancedPageWorkPolicy(isActive = true, benchmarkTagsEnabled = false).exposeBenchmarkTags)
        assertFalse(advancedPageWorkPolicy(isActive = false, benchmarkTagsEnabled = true).exposeBenchmarkTags)
        assertFalse(advancedPageWorkPolicy(isActive = false, benchmarkTagsEnabled = true).enableScrolling)
    }

    @Test
    fun backupEventQueue_retainsOrderedResultsUntilCollectionResumes() {
        val queue = SettingsBackupEventQueue()
        val first = SettingsBackupEvent.BackupResult(success = false)
        val second = SettingsBackupEvent.BackupResult(success = true)

        queue.emit(first)
        queue.emit(second)

        val pending = queue.events.value
        assertEquals(listOf(first, second), pending.map(PendingSettingsBackupEvent::event))

        queue.acknowledge(pending.first().id)
        assertEquals(listOf(second), queue.events.value.map(PendingSettingsBackupEvent::event))
    }

    @Test
    fun backupEvent_remainsPendingUntilExplicitAcknowledgement() {
        val queue = SettingsBackupEventQueue()
        val result = SettingsBackupEvent.BackupResult(success = true)

        queue.emit(result)

        assertEquals(result, queue.events.value.single().event)
        assertEquals(1, queue.events.value.size)
    }

    @Test
    fun duplicateImportUri_isCoalescedWhileFirstRequestIsPending() {
        val queue = SettingsBackupEventQueue()
        val event = SettingsBackupEvent.ImportDialogConfirm("content://backup/same")

        assertTrue(queue.emit(event))
        assertFalse(queue.emit(event))

        assertEquals(listOf(event), queue.events.value.map(PendingSettingsBackupEvent::event))
    }

    @Test
    fun importUri_canBeQueuedAgainAfterPreviousRequestIsAcknowledged() {
        val queue = SettingsBackupEventQueue()
        val event = SettingsBackupEvent.ImportDialogConfirm("content://backup/retry")

        assertTrue(queue.emit(event))
        queue.acknowledge(queue.events.value.single().id)

        assertTrue(queue.emit(event))
        assertEquals(listOf(event), queue.events.value.map(PendingSettingsBackupEvent::event))
    }

    @Test
    fun dialogBindings_initializeOnlyWhenTheirOwnEventIdChanges() {
        val bindings = SettingsBackupDialogEventBindings()
            .bindRestore(eventId = 11L)
            .bindBackupInspection(eventId = 22L)

        assertFalse(bindings.needsRestoreInitialization(eventId = 11L))
        assertTrue(bindings.needsRestoreInitialization(eventId = 12L))
        assertFalse(bindings.needsBackupInspectionInitialization(eventId = 22L))
        assertTrue(bindings.needsBackupInspectionInitialization(eventId = 23L))
    }

    @Test
    fun clearingRestoreBinding_doesNotClearBackupInspectionBinding() {
        val bindings = SettingsBackupDialogEventBindings()
            .bindRestore(eventId = 11L)
            .bindBackupInspection(eventId = 22L)
            .clearRestore()

        assertNull(bindings.restoreEventId)
        assertEquals(22L, bindings.backupInspectionEventId)
    }
}

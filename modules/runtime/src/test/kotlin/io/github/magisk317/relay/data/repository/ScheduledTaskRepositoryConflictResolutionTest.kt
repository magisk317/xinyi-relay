package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardScheduledTaskDao
import io.github.magisk317.forward.db.ForwardScheduledTaskEntity
import io.github.magisk317.relay.domain.schedule.ScheduledTaskManager
import io.github.magisk317.forward.engine.model.ScheduledTask
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScheduledTaskRepositoryConflictResolutionTest {

    @Test
    fun resolvingConflictUsesOneTransactionAndOneBatchWrite() = runBlocking {
        val dao = mockk<ForwardScheduledTaskDao>(relaxed = true)
        val database = mockk<ForwardDatabase>()
        val manager = mockk<ScheduledTaskManager>(relaxed = true)
        var transactionCount = 0
        every { database.forwardScheduledTaskDao() } returns dao
        coEvery { dao.getByConflictId("conflict-a") } returns listOf(
            task(id = 1L, syncId = "task-local", side = "local"),
            task(id = 2L, syncId = "task-remote", side = "remote"),
        )

        val repository = ScheduledTaskRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            database = database,
            taskManagerOverride = manager,
            transactionRunner = { block ->
                transactionCount += 1
                block()
            },
        )

        repository.resolveSyncConflict(
            conflictId = "conflict-a",
            resolvedTasks = listOf(
                ScheduledTask(id = 1L, syncId = "task-local", name = "Local", status = 1),
            ),
        )

        assertEquals(1, transactionCount)
        coVerify(exactly = 1) { dao.deleteByConflictId("conflict-a") }
        coVerify(exactly = 1) {
            dao.upsertAll(match { rows ->
                rows.size == 2 && rows.count { it.syncDeleted && it.status == 0 && it.conflictId == null } == 1 &&
                    rows.count { !it.syncDeleted && it.conflictId == null } == 1
            })
        }
    }

    private fun task(id: Long, syncId: String, side: String) = ForwardScheduledTaskEntity(
        id = id,
        name = side,
        taskType = "sms",
        cronExpression = "0 8 * * *",
        simSlot = 1,
        mobiles = "10086",
        content = side,
        status = 0,
        syncId = syncId,
        conflictId = "conflict-a",
        conflictSide = side,
        conflictPreviousStatus = 1,
    )
}

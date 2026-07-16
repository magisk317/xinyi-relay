DROP INDEX IF EXISTS device_config_commands_single_pending_idx;

-- Multiple ordered pending commands per device are allowed, but two pending
-- commands must never claim the same target_revision for a device. The command
-- write path serializes per device via pg_advisory_xact_lock; this partial
-- unique index is defense-in-depth against a concurrency regression.
CREATE UNIQUE INDEX IF NOT EXISTS device_config_commands_pending_target_idx
    ON device_config_commands(device_id, target_revision)
    WHERE status = 'pending';

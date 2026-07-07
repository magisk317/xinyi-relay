CREATE TABLE device_config_mirrors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(device_id)
);

CREATE TABLE device_config_commands (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    base_revision BIGINT NOT NULL,
    target_revision BIGINT NOT NULL,
    mutation JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary TEXT NOT NULL DEFAULT '',
    actor_type TEXT NOT NULL,
    actor_id BIGINT,
    status TEXT NOT NULL DEFAULT 'pending',
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ
);

CREATE TABLE device_config_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    command_id BIGINT REFERENCES device_config_commands(id) ON DELETE SET NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    event_type TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id BIGINT,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX device_config_commands_single_pending_idx
    ON device_config_commands(device_id)
    WHERE status = 'pending';

CREATE INDEX device_config_commands_device_status_created_idx
    ON device_config_commands(device_id, status, created_at, id);

CREATE INDEX device_config_audit_logs_device_created_idx
    ON device_config_audit_logs(device_id, created_at DESC, id DESC);

INSERT INTO device_config_mirrors (user_id, device_id, revision, snapshot, updated_at)
SELECT
    devices.user_id,
    devices.id,
    COALESCE(latest_snapshot.revision, 0),
    COALESCE(latest_snapshot.content, '{}'::jsonb),
    NOW()
FROM devices
LEFT JOIN LATERAL (
    SELECT revision, content
    FROM config_snapshots
    WHERE user_id = devices.user_id
    ORDER BY revision DESC
    LIMIT 1
) AS latest_snapshot ON TRUE
ON CONFLICT (device_id) DO NOTHING;

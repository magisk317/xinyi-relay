CREATE TABLE device_actions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    actor_type TEXT NOT NULL,
    actor_id BIGINT,
    status TEXT NOT NULL DEFAULT 'pending',
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acked_at TIMESTAMPTZ
);

CREATE INDEX device_actions_device_status_created_idx
    ON device_actions(device_id, status, created_at, id);

CREATE INDEX device_actions_device_created_idx
    ON device_actions(device_id, created_at DESC, id DESC);

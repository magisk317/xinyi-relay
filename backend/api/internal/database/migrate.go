package database

import (
	"context"
	"fmt"
)

var schemaStatements = []string{
	`
	CREATE TABLE IF NOT EXISTS users (
		id BIGSERIAL PRIMARY KEY,
		username TEXT NOT NULL UNIQUE,
		password_hash TEXT NOT NULL,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS web_sessions (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		token_hash TEXT NOT NULL UNIQUE,
		csrf_token TEXT NOT NULL,
		expires_at TIMESTAMPTZ NOT NULL,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
		last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS devices (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		device_name TEXT NOT NULL,
		device_model TEXT NOT NULL DEFAULT '',
		platform TEXT NOT NULL DEFAULT 'android',
		app_version TEXT NOT NULL DEFAULT '',
		display_name TEXT NOT NULL DEFAULT '',
		enabled BOOLEAN NOT NULL DEFAULT TRUE,
		token_hash TEXT UNIQUE,
		revoked_at TIMESTAMPTZ,
		last_seen_at TIMESTAMPTZ,
		local_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
		capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
		updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS device_bind_codes (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		code_hash TEXT NOT NULL UNIQUE,
		expires_at TIMESTAMPTZ NOT NULL,
		used_at TIMESTAMPTZ,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS config_snapshots (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		revision BIGINT NOT NULL,
		content JSONB NOT NULL DEFAULT '{}'::jsonb,
		updated_by_type TEXT NOT NULL,
		updated_by_id BIGINT,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
		UNIQUE(user_id, revision)
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS config_audit_logs (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		revision BIGINT NOT NULL,
		actor_type TEXT NOT NULL,
		actor_id BIGINT,
		summary TEXT NOT NULL,
		created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
	)
	`,
	`
	CREATE TABLE IF NOT EXISTS relay_records (
		id BIGSERIAL PRIMARY KEY,
		user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
		device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
		event_id TEXT,
		record_type TEXT NOT NULL,
		sender TEXT NOT NULL DEFAULT '',
		body TEXT NOT NULL DEFAULT '',
		sms_code TEXT NOT NULL DEFAULT '',
		package_name TEXT NOT NULL DEFAULT '',
		msg_type INTEGER NOT NULL DEFAULT 0,
		call_type INTEGER NOT NULL DEFAULT 0,
		occurred_at TIMESTAMPTZ NOT NULL,
		uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
		metadata JSONB NOT NULL DEFAULT '{}'::jsonb
	)
	`,
	`
	CREATE UNIQUE INDEX IF NOT EXISTS relay_records_device_event_id_idx
		ON relay_records(device_id, event_id)
		WHERE event_id IS NOT NULL
	`,
}

func (db *Database) migrate(ctx context.Context) error {
	for _, statement := range schemaStatements {
		if _, err := db.Pool.Exec(ctx, statement); err != nil {
			return fmt.Errorf("apply schema statement: %w", err)
		}
	}
	return nil
}

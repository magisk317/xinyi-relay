package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

const (
	DeviceConfigCommandStatusPending = "pending"
	DeviceConfigCommandStatusApplied = "applied"
	DeviceConfigCommandStatusFailed  = "failed"
	DeviceConfigCommandStatusStale   = "stale"
)

// deviceConfigLockNamespace namespaces the per-device transaction advisory lock
// so it cannot collide with the migration advisory lock (which uses the
// single-argument pg_advisory_lock form). We use the two-argument
// pg_advisory_xact_lock(namespace, deviceID) form to serialize all mirror /
// command writes for a single device without blocking other devices.
const deviceConfigLockNamespace int32 = 0x78696e79 // "xiny"

// lockDeviceConfig acquires a transaction-scoped advisory lock for the device so
// concurrent mirror/command writers are serialized. The lock is released
// automatically when the transaction commits or rolls back. This is what makes
// the read-then-write revision math in the command/mirror/ack paths safe under
// concurrency.
func lockDeviceConfig(ctx context.Context, q deviceConfigQuerier, deviceID int64) error {
	_, err := q.Exec(ctx, `SELECT pg_advisory_xact_lock($1, $2)`, deviceConfigLockNamespace, deviceID)
	return err
}

type DeviceConfigMirror struct {
	UserID    int64           `json:"userId"`
	DeviceID  int64           `json:"deviceId"`
	Revision  int64           `json:"revision"`
	Snapshot  json.RawMessage `json:"snapshot"`
	UpdatedAt time.Time       `json:"updatedAt"`
}

type DeviceConfigCommand struct {
	ID             int64           `json:"id"`
	UserID         int64           `json:"userId"`
	DeviceID       int64           `json:"deviceId"`
	BaseRevision   int64           `json:"baseRevision"`
	TargetRevision int64           `json:"targetRevision"`
	Mutation       json.RawMessage `json:"mutation"`
	Summary        string          `json:"summary"`
	ActorType      string          `json:"actorType"`
	ActorID        int64           `json:"actorId"`
	Status         string          `json:"status"`
	FailureReason  *string         `json:"failureReason"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
	AppliedAt      *time.Time      `json:"appliedAt"`
}

type DeviceConfigState struct {
	DeviceID        int64                 `json:"deviceId"`
	Revision        int64                 `json:"revision"`
	Snapshot        json.RawMessage       `json:"snapshot"`
	PendingCommands []DeviceConfigCommand `json:"pendingCommands"`
	UpdatedAt       time.Time             `json:"updatedAt"`
}

type DeviceConfigAuditLog struct {
	ID        int64     `json:"id"`
	DeviceID  int64     `json:"deviceId"`
	CommandID *int64    `json:"commandId"`
	Revision  int64     `json:"revision"`
	EventType string    `json:"eventType"`
	ActorType string    `json:"actorType"`
	ActorID   int64     `json:"actorId"`
	Summary   string    `json:"summary"`
	CreatedAt time.Time `json:"createdAt"`
}

type deviceConfigQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

type rowScanner interface {
	Scan(dest ...any) error
}

func (s *Store) GetDeviceConfigState(ctx context.Context, userID, deviceID int64) (DeviceConfigState, error) {
	mirror, err := s.ensureDeviceConfigMirror(ctx, s.db.Pool, userID, deviceID)
	if err != nil {
		return DeviceConfigState{}, err
	}

	pending, err := s.listDeviceConfigCommands(ctx, s.db.Pool, userID, deviceID, DeviceConfigCommandStatusPending)
	if err != nil {
		return DeviceConfigState{}, err
	}

	return DeviceConfigState{
		DeviceID:        mirror.DeviceID,
		Revision:        mirror.Revision,
		Snapshot:        normalizeDeviceConfigJSON(mirror.Snapshot),
		PendingCommands: pending,
		UpdatedAt:       mirror.UpdatedAt,
	}, nil
}

func (s *Store) CreateDeviceConfigCommand(
	ctx context.Context,
	userID, deviceID int64,
	baseRevision int64,
	mutation json.RawMessage,
	summary string,
	actorType string,
	actorID int64,
) (DeviceConfigCommand, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceConfigCommand{}, err
	}
	defer tx.Rollback(ctx)

	if err := lockDeviceConfig(ctx, tx, deviceID); err != nil {
		return DeviceConfigCommand{}, err
	}

	mirror, err := s.ensureDeviceConfigMirror(ctx, tx, userID, deviceID)
	if err != nil {
		return DeviceConfigCommand{}, err
	}
	var latestPendingTargetRevision int64
	if err := tx.QueryRow(
		ctx,
		`SELECT COALESCE(MAX(target_revision), 0) FROM device_config_commands
		  WHERE user_id = $1 AND device_id = $2 AND status = $3`,
		userID,
		deviceID,
		DeviceConfigCommandStatusPending,
	).Scan(&latestPendingTargetRevision); err != nil {
		return DeviceConfigCommand{}, err
	}
	expectedBaseRevision := mirror.Revision
	if latestPendingTargetRevision > expectedBaseRevision {
		expectedBaseRevision = latestPendingTargetRevision
	}
	if baseRevision != expectedBaseRevision {
		return DeviceConfigCommand{}, ErrStaleBaseRevision
	}

	command, err := scanDeviceConfigCommand(
		tx.QueryRow(
			ctx,
			`INSERT INTO device_config_commands (
				user_id, device_id, base_revision, target_revision, mutation, summary,
				actor_type, actor_id, status, created_at, updated_at
			)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
			RETURNING
				id, user_id, device_id, base_revision, target_revision, mutation, summary,
				actor_type, COALESCE(actor_id, 0), status, failure_reason, created_at, updated_at, applied_at`,
			userID,
			deviceID,
			baseRevision,
			expectedBaseRevision+1,
			normalizeDeviceConfigJSON(mutation),
			defaultDeviceConfigCommandSummary(summary),
			actorType,
			nullableActorID(actorID),
			DeviceConfigCommandStatusPending,
		),
	)
	if err != nil {
		return DeviceConfigCommand{}, err
	}

	if err := s.insertDeviceConfigAuditLog(
		ctx,
		tx,
		userID,
		deviceID,
		&command.ID,
		command.TargetRevision,
		"command.queued",
		actorType,
		actorID,
		command.Summary,
	); err != nil {
		return DeviceConfigCommand{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return DeviceConfigCommand{}, err
	}
	return command, nil
}

func (s *Store) ListDeviceConfigAuditLogs(
	ctx context.Context,
	userID, deviceID int64,
	limit, offset int32,
) ([]DeviceConfigAuditLog, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT id, device_id, command_id, revision, event_type, actor_type,
		        COALESCE(actor_id, 0), summary, created_at
		   FROM device_config_audit_logs
		  WHERE user_id = $1 AND device_id = $2
		  ORDER BY created_at DESC, id DESC
		  LIMIT $3 OFFSET $4`,
		userID,
		deviceID,
		limit,
		offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []DeviceConfigAuditLog
	for rows.Next() {
		log, scanErr := scanDeviceConfigAuditLog(rows)
		if scanErr != nil {
			return nil, scanErr
		}
		logs = append(logs, log)
	}
	return logs, rows.Err()
}

func (s *Store) UpsertDeviceConfigMirror(
	ctx context.Context,
	userID, deviceID int64,
	revision int64,
	snapshot json.RawMessage,
	actorType string,
	actorID int64,
	summary string,
) (DeviceConfigMirror, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceConfigMirror{}, err
	}
	defer tx.Rollback(ctx)

	if err := lockDeviceConfig(ctx, tx, deviceID); err != nil {
		return DeviceConfigMirror{}, err
	}

	current, err := s.ensureDeviceConfigMirror(ctx, tx, userID, deviceID)
	if err != nil {
		return DeviceConfigMirror{}, err
	}
	if revision < current.Revision {
		return DeviceConfigMirror{}, ErrOutdatedMirrorRevision
	}

	mirror, err := scanDeviceConfigMirror(
		tx.QueryRow(
			ctx,
			`INSERT INTO device_config_mirrors (user_id, device_id, revision, snapshot, updated_at)
			 VALUES ($1, $2, $3, $4, NOW())
			 ON CONFLICT (device_id)
			 DO UPDATE SET revision = EXCLUDED.revision, snapshot = EXCLUDED.snapshot, updated_at = NOW()
			 RETURNING user_id, device_id, revision, snapshot, updated_at`,
			userID,
			deviceID,
			revision,
			normalizeDeviceConfigJSON(snapshot),
		),
	)
	if err != nil {
		return DeviceConfigMirror{}, err
	}

	staleRows, err := tx.Query(
		ctx,
		`UPDATE device_config_commands
		    SET status = $4,
		        failure_reason = $5,
		        updated_at = NOW()
		  WHERE user_id = $1
		    AND device_id = $2
		    AND status = $3
		    AND base_revision < $6
		RETURNING
			id, user_id, device_id, base_revision, target_revision, mutation, summary,
			actor_type, COALESCE(actor_id, 0), status, failure_reason, created_at, updated_at, applied_at`,
		userID,
		deviceID,
		DeviceConfigCommandStatusPending,
		DeviceConfigCommandStatusStale,
		"stale_base_revision",
		revision,
	)
	if err != nil {
		return DeviceConfigMirror{}, err
	}
	defer staleRows.Close()
	for staleRows.Next() {
		command, scanErr := scanDeviceConfigCommand(staleRows)
		if scanErr != nil {
			return DeviceConfigMirror{}, scanErr
		}
		if err := s.insertDeviceConfigAuditLog(
			ctx,
			tx,
			userID,
			deviceID,
			&command.ID,
			mirror.Revision,
			"command.stale",
			actorType,
			actorID,
			fmt.Sprintf("command %d became stale after local revision %d", command.ID, mirror.Revision),
		); err != nil {
			return DeviceConfigMirror{}, err
		}
	}
	if err := staleRows.Err(); err != nil {
		return DeviceConfigMirror{}, err
	}

	if err := s.insertDeviceConfigAuditLog(
		ctx,
		tx,
		userID,
		deviceID,
		nil,
		mirror.Revision,
		"mirror.updated",
		actorType,
		actorID,
		defaultDeviceConfigMirrorSummary(summary, mirror.Revision),
	); err != nil {
		return DeviceConfigMirror{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return DeviceConfigMirror{}, err
	}
	return mirror, nil
}

func (s *Store) AckDeviceConfigCommand(
	ctx context.Context,
	userID, deviceID, commandID int64,
	appliedRevision int64,
	status string,
	failureReason string,
	snapshot json.RawMessage,
	actorType string,
	actorID int64,
) (DeviceConfigCommand, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return DeviceConfigCommand{}, err
	}
	defer tx.Rollback(ctx)

	if err := lockDeviceConfig(ctx, tx, deviceID); err != nil {
		return DeviceConfigCommand{}, err
	}

	command, err := scanDeviceConfigCommand(
		tx.QueryRow(
			ctx,
			`SELECT
				id, user_id, device_id, base_revision, target_revision, mutation, summary,
				actor_type, COALESCE(actor_id, 0), status, failure_reason, created_at, updated_at, applied_at
			   FROM device_config_commands
			  WHERE user_id = $1 AND device_id = $2 AND id = $3`,
			userID,
			deviceID,
			commandID,
		),
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeviceConfigCommand{}, ErrNotFound
	}
	if err != nil {
		return DeviceConfigCommand{}, err
	}
	if command.Status != DeviceConfigCommandStatusPending {
		return DeviceConfigCommand{}, ErrConflict
	}

	switch status {
	case DeviceConfigCommandStatusApplied:
		if appliedRevision == 0 {
			appliedRevision = command.TargetRevision
		}
		mirror, mirrorErr := s.ensureDeviceConfigMirror(ctx, tx, userID, deviceID)
		if mirrorErr != nil {
			return DeviceConfigCommand{}, mirrorErr
		}
		if appliedRevision < mirror.Revision {
			return DeviceConfigCommand{}, ErrOutdatedMirrorRevision
		}
		if _, execErr := tx.Exec(
			ctx,
			`UPDATE device_config_mirrors
			    SET revision = $3, snapshot = $4, updated_at = NOW()
			  WHERE user_id = $1 AND device_id = $2`,
			userID,
			deviceID,
			appliedRevision,
			normalizeDeviceConfigJSON(snapshot),
		); execErr != nil {
			return DeviceConfigCommand{}, execErr
		}
		updateTag, execErr := tx.Exec(
			ctx,
			`UPDATE device_config_commands
				    SET status = $4,
				        failure_reason = NULL,
				        updated_at = NOW(),
				        applied_at = NOW()
				  WHERE user_id = $1
				    AND device_id = $2
				    AND id = $3
				    AND status = $5`,
			userID,
			deviceID,
			commandID,
			DeviceConfigCommandStatusApplied,
			DeviceConfigCommandStatusPending,
		)
		if execErr != nil {
			return DeviceConfigCommand{}, execErr
		}
		if updateTag.RowsAffected() != 1 {
			return DeviceConfigCommand{}, ErrConflict
		}
		if err := s.insertDeviceConfigAuditLog(
			ctx,
			tx,
			userID,
			deviceID,
			&commandID,
			appliedRevision,
			"command.applied",
			actorType,
			actorID,
			fmt.Sprintf("command %d applied at revision %d", commandID, appliedRevision),
		); err != nil {
			return DeviceConfigCommand{}, err
		}
	case DeviceConfigCommandStatusFailed:
		reason := strings.TrimSpace(failureReason)
		if reason == "" {
			reason = "command_failed"
		}
		updateTag, execErr := tx.Exec(
			ctx,
			`UPDATE device_config_commands
				    SET status = $4,
				        failure_reason = $5,
				        updated_at = NOW()
				  WHERE user_id = $1
				    AND device_id = $2
				    AND id = $3
				    AND status = $6`,
			userID,
			deviceID,
			commandID,
			DeviceConfigCommandStatusFailed,
			reason,
			DeviceConfigCommandStatusPending,
		)
		if execErr != nil {
			return DeviceConfigCommand{}, execErr
		}
		if updateTag.RowsAffected() != 1 {
			return DeviceConfigCommand{}, ErrConflict
		}
		if err := s.insertDeviceConfigAuditLog(
			ctx,
			tx,
			userID,
			deviceID,
			&commandID,
			command.BaseRevision,
			"command.failed",
			actorType,
			actorID,
			fmt.Sprintf("command %d failed: %s", commandID, reason),
		); err != nil {
			return DeviceConfigCommand{}, err
		}
	default:
		return DeviceConfigCommand{}, ErrUnsupportedCommandStatus
	}

	updated, err := scanDeviceConfigCommand(
		tx.QueryRow(
			ctx,
			`SELECT
				id, user_id, device_id, base_revision, target_revision, mutation, summary,
				actor_type, COALESCE(actor_id, 0), status, failure_reason, created_at, updated_at, applied_at
			   FROM device_config_commands
			  WHERE user_id = $1 AND device_id = $2 AND id = $3`,
			userID,
			deviceID,
			commandID,
		),
	)
	if err != nil {
		return DeviceConfigCommand{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return DeviceConfigCommand{}, err
	}
	return updated, nil
}

func (s *Store) ensureDeviceConfigMirror(
	ctx context.Context,
	q deviceConfigQuerier,
	userID, deviceID int64,
) (DeviceConfigMirror, error) {
	mirror, err := scanDeviceConfigMirror(
		q.QueryRow(
			ctx,
			`WITH inserted AS (
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
				WHERE devices.user_id = $1 AND devices.id = $2
				ON CONFLICT (device_id) DO NOTHING
				RETURNING user_id, device_id, revision, snapshot, updated_at
			)
			SELECT user_id, device_id, revision, snapshot, updated_at
			  FROM inserted
			UNION ALL
			SELECT user_id, device_id, revision, snapshot, updated_at
			  FROM device_config_mirrors
			 WHERE user_id = $1 AND device_id = $2
			LIMIT 1`,
			userID,
			deviceID,
		),
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeviceConfigMirror{}, ErrNotFound
	}
	return mirror, err
}

func (s *Store) listDeviceConfigCommands(
	ctx context.Context,
	q deviceConfigQuerier,
	userID, deviceID int64,
	status string,
) ([]DeviceConfigCommand, error) {
	rows, err := q.Query(
		ctx,
		`SELECT
			id, user_id, device_id, base_revision, target_revision, mutation, summary,
			actor_type, COALESCE(actor_id, 0), status, failure_reason, created_at, updated_at, applied_at
		   FROM device_config_commands
		  WHERE user_id = $1 AND device_id = $2 AND status = $3
		  ORDER BY created_at ASC, id ASC`,
		userID,
		deviceID,
		status,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var commands []DeviceConfigCommand
	for rows.Next() {
		command, scanErr := scanDeviceConfigCommand(rows)
		if scanErr != nil {
			return nil, scanErr
		}
		commands = append(commands, command)
	}
	return commands, rows.Err()
}

func (s *Store) insertDeviceConfigAuditLog(
	ctx context.Context,
	q deviceConfigQuerier,
	userID, deviceID int64,
	commandID *int64,
	revision int64,
	eventType string,
	actorType string,
	actorID int64,
	summary string,
) error {
	_, err := q.Exec(
		ctx,
		`INSERT INTO device_config_audit_logs (
			user_id, device_id, command_id, revision, event_type, actor_type, actor_id, summary, created_at
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())`,
		userID,
		deviceID,
		commandID,
		revision,
		eventType,
		actorType,
		nullableActorID(actorID),
		summary,
	)
	return err
}

func scanDeviceConfigMirror(scanner rowScanner) (DeviceConfigMirror, error) {
	var mirror DeviceConfigMirror
	err := scanner.Scan(
		&mirror.UserID,
		&mirror.DeviceID,
		&mirror.Revision,
		&mirror.Snapshot,
		&mirror.UpdatedAt,
	)
	mirror.Snapshot = normalizeDeviceConfigJSON(mirror.Snapshot)
	return mirror, err
}

func scanDeviceConfigCommand(scanner rowScanner) (DeviceConfigCommand, error) {
	var command DeviceConfigCommand
	err := scanner.Scan(
		&command.ID,
		&command.UserID,
		&command.DeviceID,
		&command.BaseRevision,
		&command.TargetRevision,
		&command.Mutation,
		&command.Summary,
		&command.ActorType,
		&command.ActorID,
		&command.Status,
		&command.FailureReason,
		&command.CreatedAt,
		&command.UpdatedAt,
		&command.AppliedAt,
	)
	command.Mutation = normalizeDeviceConfigJSON(command.Mutation)
	return command, err
}

func scanDeviceConfigAuditLog(scanner rowScanner) (DeviceConfigAuditLog, error) {
	var log DeviceConfigAuditLog
	err := scanner.Scan(
		&log.ID,
		&log.DeviceID,
		&log.CommandID,
		&log.Revision,
		&log.EventType,
		&log.ActorType,
		&log.ActorID,
		&log.Summary,
		&log.CreatedAt,
	)
	return log, err
}

func normalizeDeviceConfigJSON(data json.RawMessage) json.RawMessage {
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" || trimmed == "null" {
		return json.RawMessage(`{}`)
	}
	return data
}

func defaultDeviceConfigCommandSummary(summary string) string {
	trimmed := strings.TrimSpace(summary)
	if trimmed == "" {
		return "device config command queued"
	}
	return trimmed
}

func defaultDeviceConfigMirrorSummary(summary string, revision int64) string {
	trimmed := strings.TrimSpace(summary)
	if trimmed != "" {
		return trimmed
	}
	return fmt.Sprintf("device config mirror updated to revision %d", revision)
}

func nullableActorID(actorID int64) any {
	if actorID <= 0 {
		return nil
	}
	return actorID
}

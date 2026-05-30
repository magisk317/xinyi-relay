package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
)

type ConfigSnapshot struct {
	Revision int64           `json:"revision"`
	Content  json.RawMessage `json:"content"`
}

type ConfigAuditLog struct {
	ID        int64     `json:"id"`
	Revision  int64     `json:"revision"`
	ActorType string    `json:"actorType"`
	ActorID   int64     `json:"actorId"`
	Summary   string    `json:"summary"`
	CreatedAt time.Time `json:"createdAt"`
}

func (s *Store) GetConfigSnapshot(ctx context.Context, userID int64) (ConfigSnapshot, error) {
	var snapshot ConfigSnapshot
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT revision, content
		   FROM config_snapshots
		  WHERE user_id = $1
		  ORDER BY revision DESC
		  LIMIT 1`,
		userID,
	).Scan(&snapshot.Revision, &snapshot.Content)
	if errors.Is(err, pgx.ErrNoRows) {
		return ConfigSnapshot{Revision: 0, Content: json.RawMessage(`{}`)}, nil
	}
	return snapshot, err
}

func (s *Store) PutConfigSnapshot(
	ctx context.Context,
	userID int64,
	baseRevision int64,
	content json.RawMessage,
	actorType string,
	actorID int64,
) (ConfigSnapshot, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return ConfigSnapshot{}, err
	}
	defer tx.Rollback(ctx)

	var currentRevision int64
	err = tx.QueryRow(
		ctx,
		`SELECT COALESCE(MAX(revision), 0) FROM config_snapshots WHERE user_id = $1`,
		userID,
	).Scan(&currentRevision)
	if err != nil {
		return ConfigSnapshot{}, err
	}
	if currentRevision != baseRevision {
		return ConfigSnapshot{}, ErrConflict
	}

	nextRevision := currentRevision + 1
	if _, err := tx.Exec(
		ctx,
		`INSERT INTO config_snapshots (user_id, revision, content, updated_by_type, updated_by_id)
		 VALUES ($1, $2, $3, $4, $5)`,
		userID,
		nextRevision,
		content,
		actorType,
		actorID,
	); err != nil {
		return ConfigSnapshot{}, err
	}

	summary := fmt.Sprintf("config revision %d committed by %s", nextRevision, actorType)
	if _, err := tx.Exec(
		ctx,
		`INSERT INTO config_audit_logs (user_id, revision, actor_type, actor_id, summary)
		 VALUES ($1, $2, $3, $4, $5)`,
		userID,
		nextRevision,
		actorType,
		actorID,
		summary,
	); err != nil {
		return ConfigSnapshot{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return ConfigSnapshot{}, err
	}
	return ConfigSnapshot{Revision: nextRevision, Content: content}, nil
}

func (s *Store) ListConfigAuditLogs(
	ctx context.Context,
	userID int64,
	limit int32,
	offset int32,
) ([]ConfigAuditLog, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT id, revision, actor_type, COALESCE(actor_id, 0), summary, created_at
		   FROM config_audit_logs
		  WHERE user_id = $1
		  ORDER BY created_at DESC, id DESC
		  LIMIT $2 OFFSET $3`,
		userID,
		limit,
		offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []ConfigAuditLog
	for rows.Next() {
		var log ConfigAuditLog
		if err := rows.Scan(
			&log.ID,
			&log.Revision,
			&log.ActorType,
			&log.ActorID,
			&log.Summary,
			&log.CreatedAt,
		); err != nil {
			return nil, err
		}
		logs = append(logs, log)
	}
	return logs, rows.Err()
}

package store

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
)

type BindCode struct {
	ID        int64
	UserID    int64
	CodeHash  string
	ExpiresAt time.Time
	UsedAt    *time.Time
}

func (s *Store) CreateBindCode(ctx context.Context, userID int64, codeHash string, expiresAt time.Time) (BindCode, error) {
	var bindCode BindCode
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO device_bind_codes (user_id, code_hash, expires_at) VALUES ($1, $2, $3)
		 RETURNING id, user_id, code_hash, expires_at, used_at`,
		userID,
		codeHash,
		expiresAt,
	).Scan(&bindCode.ID, &bindCode.UserID, &bindCode.CodeHash, &bindCode.ExpiresAt, &bindCode.UsedAt)
	return bindCode, err
}

func (s *Store) ConsumeBindCode(ctx context.Context, codeHash string) (BindCode, error) {
	var bindCode BindCode
	err := s.db.Pool.QueryRow(
		ctx,
		`UPDATE device_bind_codes
		 SET used_at = NOW()
		 WHERE code_hash = $1 AND used_at IS NULL AND expires_at > NOW()
		 RETURNING id, user_id, code_hash, expires_at, used_at`,
		codeHash,
	).Scan(&bindCode.ID, &bindCode.UserID, &bindCode.CodeHash, &bindCode.ExpiresAt, &bindCode.UsedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return BindCode{}, ErrNotFound
	}
	return bindCode, err
}

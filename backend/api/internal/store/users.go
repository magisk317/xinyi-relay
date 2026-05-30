package store

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
)

type User struct {
	ID           int64
	Username     string
	PasswordHash string
	CreatedAt    time.Time
}

func (s *Store) UserCount(ctx context.Context) (int64, error) {
	var count int64
	if err := s.db.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM users`).Scan(&count); err != nil {
		return 0, err
	}
	return count, nil
}

func (s *Store) CreateUser(ctx context.Context, username string, passwordHash string) (User, error) {
	var user User
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO users (username, password_hash) VALUES ($1, $2) RETURNING id, username, password_hash, created_at`,
		username,
		passwordHash,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.CreatedAt)
	return user, err
}

func (s *Store) GetUserByUsername(ctx context.Context, username string) (User, error) {
	var user User
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT id, username, password_hash, created_at FROM users WHERE username = $1`,
		username,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return user, err
}

func (s *Store) GetUserByID(ctx context.Context, userID int64) (User, error) {
	var user User
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT id, username, password_hash, created_at FROM users WHERE id = $1`,
		userID,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return user, err
}

func (s *Store) UpdateUserPassword(ctx context.Context, userID int64, passwordHash string) error {
	commandTag, err := s.db.Pool.Exec(
		ctx,
		`UPDATE users SET password_hash = $2 WHERE id = $1`,
		userID,
		passwordHash,
	)
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

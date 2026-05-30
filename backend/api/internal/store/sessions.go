package store

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
)

type Session struct {
	ID        int64
	UserID    int64
	Username  string
	TokenHash string
	CSRFToken string
	ExpiresAt time.Time
}

type DesktopAuthRequest struct {
	ID          int64
	UserID      int64
	CodeHash    string
	RedirectURI string
	State       string
	ClientName  string
	ExpiresAt   time.Time
	UsedAt      *time.Time
	CreatedAt   time.Time
}

type DesktopSession struct {
	ID               int64
	UserID           int64
	Username         string
	ClientName       string
	AccessTokenHash  string
	RefreshTokenHash string
	ExpiresAt        time.Time
	RefreshExpiresAt time.Time
	LastSeenAt       time.Time
	RevokedAt        *time.Time
}

func (s *Store) CreateSession(
	ctx context.Context,
	user User,
	tokenHash string,
	csrfToken string,
	expiresAt time.Time,
) (Session, error) {
	var session Session
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO web_sessions (user_id, token_hash, csrf_token, expires_at) VALUES ($1, $2, $3, $4)
		 RETURNING id, user_id, token_hash, csrf_token, expires_at`,
		user.ID,
		tokenHash,
		csrfToken,
		expiresAt,
	).Scan(&session.ID, &session.UserID, &session.TokenHash, &session.CSRFToken, &session.ExpiresAt)
	session.Username = user.Username
	return session, err
}

func (s *Store) CreateDesktopAuthRequest(
	ctx context.Context,
	userID int64,
	codeHash string,
	redirectURI string,
	state string,
	clientName string,
	expiresAt time.Time,
) (DesktopAuthRequest, error) {
	var request DesktopAuthRequest
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO desktop_auth_requests (user_id, code_hash, redirect_uri, state, client_name, expires_at)
		 VALUES ($1, $2, $3, $4, $5, $6)
		 RETURNING id, user_id, code_hash, redirect_uri, state, client_name, expires_at, used_at, created_at`,
		userID,
		codeHash,
		redirectURI,
		state,
		clientName,
		expiresAt,
	).Scan(
		&request.ID,
		&request.UserID,
		&request.CodeHash,
		&request.RedirectURI,
		&request.State,
		&request.ClientName,
		&request.ExpiresAt,
		&request.UsedAt,
		&request.CreatedAt,
	)
	return request, err
}

func (s *Store) ConsumeDesktopAuthRequest(ctx context.Context, codeHash string) (DesktopAuthRequest, error) {
	var request DesktopAuthRequest
	err := s.db.Pool.QueryRow(
		ctx,
		`UPDATE desktop_auth_requests
		    SET used_at = NOW()
		  WHERE code_hash = $1 AND used_at IS NULL AND expires_at > NOW()
		  RETURNING id, user_id, code_hash, redirect_uri, state, client_name, expires_at, used_at, created_at`,
		codeHash,
	).Scan(
		&request.ID,
		&request.UserID,
		&request.CodeHash,
		&request.RedirectURI,
		&request.State,
		&request.ClientName,
		&request.ExpiresAt,
		&request.UsedAt,
		&request.CreatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DesktopAuthRequest{}, ErrNotFound
	}
	return request, err
}

func (s *Store) CreateDesktopSession(
	ctx context.Context,
	user User,
	clientName string,
	accessTokenHash string,
	refreshTokenHash string,
	expiresAt time.Time,
	refreshExpiresAt time.Time,
) (DesktopSession, error) {
	var session DesktopSession
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO desktop_sessions (user_id, client_name, access_token_hash, refresh_token_hash, expires_at, refresh_expires_at)
		 VALUES ($1, $2, $3, $4, $5, $6)
		 RETURNING id, user_id, client_name, access_token_hash, refresh_token_hash, expires_at, refresh_expires_at, last_seen_at, revoked_at`,
		user.ID,
		clientName,
		accessTokenHash,
		refreshTokenHash,
		expiresAt,
		refreshExpiresAt,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.ClientName,
		&session.AccessTokenHash,
		&session.RefreshTokenHash,
		&session.ExpiresAt,
		&session.RefreshExpiresAt,
		&session.LastSeenAt,
		&session.RevokedAt,
	)
	session.Username = user.Username
	return session, err
}

func (s *Store) GetSessionByTokenHash(ctx context.Context, tokenHash string) (Session, error) {
	var session Session
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT ws.id, ws.user_id, u.username, ws.token_hash, ws.csrf_token, ws.expires_at
		 FROM web_sessions ws
		 JOIN users u ON u.id = ws.user_id
		 WHERE ws.token_hash = $1 AND ws.expires_at > NOW()`,
		tokenHash,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.Username,
		&session.TokenHash,
		&session.CSRFToken,
		&session.ExpiresAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNotFound
	}
	return session, err
}

func (s *Store) GetDesktopSessionByAccessTokenHash(ctx context.Context, accessTokenHash string) (DesktopSession, error) {
	var session DesktopSession
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT ds.id, ds.user_id, u.username, ds.client_name, ds.access_token_hash, ds.refresh_token_hash,
		        ds.expires_at, ds.refresh_expires_at, ds.last_seen_at, ds.revoked_at
		   FROM desktop_sessions ds
		   JOIN users u ON u.id = ds.user_id
		  WHERE ds.access_token_hash = $1 AND ds.revoked_at IS NULL AND ds.expires_at > NOW()`,
		accessTokenHash,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.Username,
		&session.ClientName,
		&session.AccessTokenHash,
		&session.RefreshTokenHash,
		&session.ExpiresAt,
		&session.RefreshExpiresAt,
		&session.LastSeenAt,
		&session.RevokedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DesktopSession{}, ErrNotFound
	}
	return session, err
}

func (s *Store) GetDesktopSessionByRefreshTokenHash(ctx context.Context, refreshTokenHash string) (DesktopSession, error) {
	var session DesktopSession
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT ds.id, ds.user_id, u.username, ds.client_name, ds.access_token_hash, ds.refresh_token_hash,
		        ds.expires_at, ds.refresh_expires_at, ds.last_seen_at, ds.revoked_at
		   FROM desktop_sessions ds
		   JOIN users u ON u.id = ds.user_id
		  WHERE ds.refresh_token_hash = $1 AND ds.revoked_at IS NULL AND ds.refresh_expires_at > NOW()`,
		refreshTokenHash,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.Username,
		&session.ClientName,
		&session.AccessTokenHash,
		&session.RefreshTokenHash,
		&session.ExpiresAt,
		&session.RefreshExpiresAt,
		&session.LastSeenAt,
		&session.RevokedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DesktopSession{}, ErrNotFound
	}
	return session, err
}

func (s *Store) TouchSession(ctx context.Context, sessionID int64) error {
	_, err := s.db.Pool.Exec(ctx, `UPDATE web_sessions SET last_seen_at = NOW() WHERE id = $1`, sessionID)
	return err
}

func (s *Store) TouchDesktopSession(ctx context.Context, sessionID int64) error {
	_, err := s.db.Pool.Exec(ctx, `UPDATE desktop_sessions SET last_seen_at = NOW() WHERE id = $1`, sessionID)
	return err
}

func (s *Store) DeleteSessionByTokenHash(ctx context.Context, tokenHash string) error {
	_, err := s.db.Pool.Exec(ctx, `DELETE FROM web_sessions WHERE token_hash = $1`, tokenHash)
	return err
}

func (s *Store) RotateDesktopSession(
	ctx context.Context,
	sessionID int64,
	accessTokenHash string,
	refreshTokenHash string,
	expiresAt time.Time,
	refreshExpiresAt time.Time,
) (DesktopSession, error) {
	var session DesktopSession
	err := s.db.Pool.QueryRow(
		ctx,
		`UPDATE desktop_sessions
		    SET access_token_hash = $2,
		        refresh_token_hash = $3,
		        expires_at = $4,
		        refresh_expires_at = $5,
		        last_seen_at = NOW()
		  WHERE id = $1 AND revoked_at IS NULL
		  RETURNING id, user_id, client_name, access_token_hash, refresh_token_hash, expires_at, refresh_expires_at, last_seen_at, revoked_at`,
		sessionID,
		accessTokenHash,
		refreshTokenHash,
		expiresAt,
		refreshExpiresAt,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.ClientName,
		&session.AccessTokenHash,
		&session.RefreshTokenHash,
		&session.ExpiresAt,
		&session.RefreshExpiresAt,
		&session.LastSeenAt,
		&session.RevokedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DesktopSession{}, ErrNotFound
	}
	user, err := s.GetUserByID(ctx, session.UserID)
	if err != nil {
		return DesktopSession{}, err
	}
	session.Username = user.Username
	return session, nil
}

func (s *Store) DeleteDesktopSessionByAccessTokenHash(ctx context.Context, accessTokenHash string) error {
	_, err := s.db.Pool.Exec(
		ctx,
		`UPDATE desktop_sessions SET revoked_at = NOW() WHERE access_token_hash = $1 AND revoked_at IS NULL`,
		accessTokenHash,
	)
	return err
}

func (s *Store) DeleteDesktopSessionByRefreshTokenHash(ctx context.Context, refreshTokenHash string) error {
	_, err := s.db.Pool.Exec(
		ctx,
		`UPDATE desktop_sessions SET revoked_at = NOW() WHERE refresh_token_hash = $1 AND revoked_at IS NULL`,
		refreshTokenHash,
	)
	return err
}

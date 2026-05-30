package http

import (
	"context"
	"encoding/json"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// The interfaces below describe the persistence surface the HTTP server
// depends on, segregated by domain. They let handlers depend on behaviour
// rather than the concrete *store.Store, which makes the server unit-testable
// with fakes. *store.Store satisfies dataStore (asserted at the bottom).

// userStore covers user account persistence.
type userStore interface {
	UserCount(ctx context.Context) (int64, error)
	CreateUser(ctx context.Context, username string, passwordHash string) (store.User, error)
	GetUserByUsername(ctx context.Context, username string) (store.User, error)
	GetUserByID(ctx context.Context, userID int64) (store.User, error)
	UpdateUserPassword(ctx context.Context, userID int64, passwordHash string) error
}

// sessionStore covers web + desktop session and desktop-auth persistence.
type sessionStore interface {
	CreateSession(ctx context.Context, user store.User, tokenHash string, csrfToken string, expiresAt time.Time) (store.Session, error)
	GetSessionByTokenHash(ctx context.Context, tokenHash string) (store.Session, error)
	TouchSession(ctx context.Context, sessionID int64) error
	DeleteSessionByTokenHash(ctx context.Context, tokenHash string) error
	CreateDesktopAuthRequest(ctx context.Context, userID int64, codeHash string, redirectURI string, state string, clientName string, expiresAt time.Time) (store.DesktopAuthRequest, error)
	ConsumeDesktopAuthRequest(ctx context.Context, codeHash string) (store.DesktopAuthRequest, error)
	CreateDesktopSession(ctx context.Context, user store.User, clientName string, accessTokenHash string, refreshTokenHash string, expiresAt time.Time, refreshExpiresAt time.Time) (store.DesktopSession, error)
	GetDesktopSessionByAccessTokenHash(ctx context.Context, accessTokenHash string) (store.DesktopSession, error)
	GetDesktopSessionByRefreshTokenHash(ctx context.Context, refreshTokenHash string) (store.DesktopSession, error)
	TouchDesktopSession(ctx context.Context, sessionID int64) error
	RotateDesktopSession(ctx context.Context, sessionID int64, accessTokenHash string, refreshTokenHash string, expiresAt time.Time, refreshExpiresAt time.Time) (store.DesktopSession, error)
	DeleteDesktopSessionByAccessTokenHash(ctx context.Context, accessTokenHash string) error
	DeleteDesktopSessionByRefreshTokenHash(ctx context.Context, refreshTokenHash string) error
}

// bindCodeStore covers device bind-code persistence.
type bindCodeStore interface {
	CreateBindCode(ctx context.Context, userID int64, codeHash string, expiresAt time.Time) (store.BindCode, error)
	ConsumeBindCode(ctx context.Context, codeHash string) (store.BindCode, error)
}

// deviceStore covers device lifecycle persistence.
type deviceStore interface {
	CreateDevice(ctx context.Context, userID int64, deviceName string, deviceModel string, platform string, appVersion string, tokenHash string) (store.Device, error)
	GetDeviceByTokenHash(ctx context.Context, tokenHash string) (store.Device, error)
	ListDevicesByUser(ctx context.Context, userID int64) ([]store.Device, error)
	UpdateDeviceHeartbeat(ctx context.Context, deviceID int64, appVersion string, localAddresses json.RawMessage, capabilities json.RawMessage) error
	PatchDevice(ctx context.Context, userID, deviceID int64, displayName *string, enabled *bool) (store.Device, error)
	RevokeDevice(ctx context.Context, userID, deviceID int64) error
}

// configStore covers config snapshot + audit persistence.
type configStore interface {
	GetConfigSnapshot(ctx context.Context, userID int64) (store.ConfigSnapshot, error)
	PutConfigSnapshot(ctx context.Context, userID int64, baseRevision int64, content json.RawMessage, actorType string, actorID int64) (store.ConfigSnapshot, error)
	ListConfigAuditLogs(ctx context.Context, userID int64, limit int32, offset int32) ([]store.ConfigAuditLog, error)
}

// recordStore covers relay-record persistence + retention.
type recordStore interface {
	InsertRelayRecords(ctx context.Context, userID int64, deviceID int64, records []store.RelayRecord) (int64, error)
	ListRelayRecords(ctx context.Context, userID int64, limit int32, offset int32, deviceID *int64) ([]store.RelayRecord, error)
	GetRelayRecord(ctx context.Context, userID int64, recordID int64) (store.RelayRecord, error)
	PruneRelayRecords(ctx context.Context, userID int64, retention store.RecordsRetention) (int64, error)
}

// dataStore is the aggregate persistence interface the HTTP server depends on.
type dataStore interface {
	userStore
	sessionStore
	bindCodeStore
	deviceStore
	configStore
	recordStore
}

// Compile-time assertion that the concrete store satisfies the aggregate
// interface, so a signature drift fails the build instead of a test.
var _ dataStore = (*store.Store)(nil)

package http

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// panicStore implements the whole dataStore interface with methods that panic
// with the method name. Tests embed it so any handler call into a method the
// test did not stub fails with an actionable message instead of a generic
// nil-pointer panic from an embedded nil interface.
type panicStore struct{}

func (panicStore) UserCount(context.Context) (int64, error) { panic("unimplemented: UserCount") }
func (panicStore) CreateUser(context.Context, string, string) (store.User, error) {
	panic("unimplemented: CreateUser")
}
func (panicStore) GetUserByUsername(context.Context, string) (store.User, error) {
	panic("unimplemented: GetUserByUsername")
}
func (panicStore) GetUserByID(context.Context, int64) (store.User, error) {
	panic("unimplemented: GetUserByID")
}
func (panicStore) UpdateUserPassword(context.Context, int64, string) error {
	panic("unimplemented: UpdateUserPassword")
}
func (panicStore) CreateSession(context.Context, store.User, string, string, time.Time) (store.Session, error) {
	panic("unimplemented: CreateSession")
}
func (panicStore) GetSessionByTokenHash(context.Context, string) (store.Session, error) {
	panic("unimplemented: GetSessionByTokenHash")
}
func (panicStore) TouchSession(context.Context, int64) error { panic("unimplemented: TouchSession") }
func (panicStore) DeleteSessionByTokenHash(context.Context, string) error {
	panic("unimplemented: DeleteSessionByTokenHash")
}
func (panicStore) CreateDesktopAuthRequest(context.Context, int64, string, string, string, string, time.Time) (store.DesktopAuthRequest, error) {
	panic("unimplemented: CreateDesktopAuthRequest")
}
func (panicStore) ConsumeDesktopAuthRequest(context.Context, string) (store.DesktopAuthRequest, error) {
	panic("unimplemented: ConsumeDesktopAuthRequest")
}
func (panicStore) CreateDesktopSession(context.Context, store.User, string, string, string, time.Time, time.Time) (store.DesktopSession, error) {
	panic("unimplemented: CreateDesktopSession")
}
func (panicStore) GetDesktopSessionByAccessTokenHash(context.Context, string) (store.DesktopSession, error) {
	panic("unimplemented: GetDesktopSessionByAccessTokenHash")
}
func (panicStore) GetDesktopSessionByRefreshTokenHash(context.Context, string) (store.DesktopSession, error) {
	panic("unimplemented: GetDesktopSessionByRefreshTokenHash")
}
func (panicStore) TouchDesktopSession(context.Context, int64) error {
	panic("unimplemented: TouchDesktopSession")
}
func (panicStore) RotateDesktopSession(context.Context, int64, string, string, time.Time, time.Time) (store.DesktopSession, error) {
	panic("unimplemented: RotateDesktopSession")
}
func (panicStore) DeleteDesktopSessionByAccessTokenHash(context.Context, string) error {
	panic("unimplemented: DeleteDesktopSessionByAccessTokenHash")
}
func (panicStore) DeleteDesktopSessionByRefreshTokenHash(context.Context, string) error {
	panic("unimplemented: DeleteDesktopSessionByRefreshTokenHash")
}
func (panicStore) CreateBindCode(context.Context, int64, string, time.Time) (store.BindCode, error) {
	panic("unimplemented: CreateBindCode")
}
func (panicStore) ConsumeBindCode(context.Context, string) (store.BindCode, error) {
	panic("unimplemented: ConsumeBindCode")
}
func (panicStore) CreateDevice(context.Context, int64, string, string, string, string, string) (store.Device, error) {
	panic("unimplemented: CreateDevice")
}
func (panicStore) GetDeviceByTokenHash(context.Context, string) (store.Device, error) {
	panic("unimplemented: GetDeviceByTokenHash")
}
func (panicStore) ListDevicesByUser(context.Context, int64) ([]store.Device, error) {
	panic("unimplemented: ListDevicesByUser")
}
func (panicStore) UpdateDeviceHeartbeat(context.Context, int64, string, json.RawMessage, json.RawMessage) error {
	panic("unimplemented: UpdateDeviceHeartbeat")
}
func (panicStore) PatchDevice(context.Context, int64, int64, *string, *bool) (store.Device, error) {
	panic("unimplemented: PatchDevice")
}
func (panicStore) RevokeDevice(context.Context, int64, int64) error {
	panic("unimplemented: RevokeDevice")
}
func (panicStore) GetConfigSnapshot(context.Context, int64) (store.ConfigSnapshot, error) {
	panic("unimplemented: GetConfigSnapshot")
}
func (panicStore) PutConfigSnapshot(context.Context, int64, int64, json.RawMessage, string, int64) (store.ConfigSnapshot, error) {
	panic("unimplemented: PutConfigSnapshot")
}
func (panicStore) ListConfigAuditLogs(context.Context, int64, int32, int32) ([]store.ConfigAuditLog, error) {
	panic("unimplemented: ListConfigAuditLogs")
}
func (panicStore) InsertRelayRecords(context.Context, int64, int64, []store.RelayRecord) (int64, error) {
	panic("unimplemented: InsertRelayRecords")
}
func (panicStore) ListRelayRecords(context.Context, int64, int32, int32, *int64) ([]store.RelayRecord, error) {
	panic("unimplemented: ListRelayRecords")
}
func (panicStore) GetRelayRecord(context.Context, int64, int64) (store.RelayRecord, error) {
	panic("unimplemented: GetRelayRecord")
}
func (panicStore) PruneRelayRecords(context.Context, int64, store.RecordsRetention) (int64, error) {
	panic("unimplemented: PruneRelayRecords")
}

// fakeDataStore stubs only the methods a test exercises; all other dataStore
// methods inherit panicStore's actionable panics. This demonstrates the server
// is decoupled from the concrete *store.Store via the dataStore interface.
type fakeDataStore struct {
	panicStore
	userCount    int64
	userCountErr error
}

func (f fakeDataStore) UserCount(context.Context) (int64, error) {
	return f.userCount, f.userCountErr
}

func TestHandleSystemInfoUsesStore(t *testing.T) {
	s := &Server{
		cfg: config.Config{
			AppEnv:       "test",
			LocalBaseURL: "http://localhost:8080",
			DatabaseURL:  "postgres://example/db",
		},
		store: fakeDataStore{userCount: 7},
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/system/info", nil)
	rec := httptest.NewRecorder()
	s.handleSystemInfo(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var resp systemInfoResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.UserCount != 7 {
		t.Errorf("expected UserCount 7, got %d", resp.UserCount)
	}
	if !resp.DatabaseReady {
		t.Errorf("expected DatabaseReady true when DatabaseURL is set")
	}
	if resp.AppEnv != "test" {
		t.Errorf("expected AppEnv test, got %q", resp.AppEnv)
	}
}

func TestHandleSystemInfoStoreError(t *testing.T) {
	s := &Server{
		cfg:   config.Config{},
		store: fakeDataStore{userCountErr: errors.New("boom")},
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/system/info", nil)
	rec := httptest.NewRecorder()
	s.handleSystemInfo(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 on store error, got %d", rec.Code)
	}
}

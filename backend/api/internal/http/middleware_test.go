package http

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// authFakeStore stubs the lookups the auth attempts use. Each attempt ignores
// the actual token hash, so tests only configure the returned value/error.
type authFakeStore struct {
	panicStore

	session    store.Session
	sessionErr error
	user       store.User
	userErr    error

	desktop    store.DesktopSession
	desktopErr error

	device    store.Device
	deviceErr error

	snapshot store.ConfigSnapshot
}

func (f authFakeStore) GetSessionByTokenHash(context.Context, string) (store.Session, error) {
	return f.session, f.sessionErr
}
func (f authFakeStore) TouchSession(context.Context, int64) error { return nil }
func (f authFakeStore) GetUserByUsername(context.Context, string) (store.User, error) {
	return f.user, f.userErr
}
func (f authFakeStore) GetUserByID(context.Context, int64) (store.User, error) {
	return f.user, f.userErr
}
func (f authFakeStore) GetDesktopSessionByAccessTokenHash(context.Context, string) (store.DesktopSession, error) {
	return f.desktop, f.desktopErr
}
func (f authFakeStore) TouchDesktopSession(context.Context, int64) error { return nil }
func (f authFakeStore) GetDeviceByTokenHash(context.Context, string) (store.Device, error) {
	return f.device, f.deviceErr
}
func (f authFakeStore) GetConfigSnapshot(context.Context, int64) (store.ConfigSnapshot, error) {
	return f.snapshot, nil
}

func withCookie(r *http.Request) *http.Request {
	r.AddCookie(&http.Cookie{Name: "relay_session", Value: "cookie-token"})
	return r
}

func withBearer(r *http.Request) *http.Request {
	r.Header.Set("Authorization", "Bearer some-token")
	return r
}

// spyAuth runs mw against req and returns the resolved authContext (if the
// handler was reached) plus the recorded response.
func spyAuth(mw func(authHandler) http.HandlerFunc, req *http.Request) (*authContext, *httptest.ResponseRecorder) {
	var got *authContext
	handler := mw(func(w http.ResponseWriter, r *http.Request, auth authContext) {
		captured := auth
		got = &captured
		w.WriteHeader(http.StatusNoContent)
	})
	rec := httptest.NewRecorder()
	handler(rec, req)
	return got, rec
}

func consoleMW(s *Server) func(authHandler) http.HandlerFunc {
	return s.requireAuth("authentication required", s.attemptDesktopSession, s.attemptSession)
}

func configMW(s *Server) func(authHandler) http.HandlerFunc {
	return s.requireAuth("authentication required", s.attemptDesktopSession, s.attemptDevice, s.attemptSession)
}

func TestRequireAuthNoCredential(t *testing.T) {
	s := &Server{store: authFakeStore{}}
	got, rec := spyAuth(consoleMW(s), httptest.NewRequest(http.MethodGet, "/x", nil))
	if got != nil {
		t.Fatalf("handler should not be reached without credentials")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", rec.Code)
	}
}

func TestRequireAuthSessionCookie(t *testing.T) {
	s := &Server{store: authFakeStore{
		session: store.Session{ID: 1, Username: "alice", CSRFToken: "csrf"},
		user:    store.User{ID: 9, Username: "alice"},
	}}
	got, rec := spyAuth(consoleMW(s), withCookie(httptest.NewRequest(http.MethodGet, "/x", nil)))
	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if got == nil || got.Kind != authKindSession || got.User.ID != 9 {
		t.Fatalf("expected session auth for user 9, got %+v", got)
	}
}

func TestRequireAuthDesktopBeatsDevice(t *testing.T) {
	// A valid desktop session must win over device since it is tried first and
	// the device lookup must not even be consulted (panicStore would panic).
	s := &Server{store: authFakeStore{
		desktop: store.DesktopSession{ID: 5, UserID: 9},
		user:    store.User{ID: 9, Username: "alice"},
	}}
	got, rec := spyAuth(configMW(s), withBearer(httptest.NewRequest(http.MethodGet, "/x", nil)))
	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if got == nil || got.Kind != authKindDesktop || got.DesktopSession.ID != 5 {
		t.Fatalf("expected desktop auth, got %+v", got)
	}
}

func TestRequireAuthFallsThroughToDevice(t *testing.T) {
	// Bearer is present but not a desktop session -> fall through to device.
	s := &Server{store: authFakeStore{
		desktopErr: store.ErrNotFound,
		device:     store.Device{ID: 7, UserID: 9},
	}}
	got, rec := spyAuth(configMW(s), withBearer(httptest.NewRequest(http.MethodGet, "/x", nil)))
	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if got == nil || got.Kind != authKindDevice || got.Device.ID != 7 || got.User.ID != 9 {
		t.Fatalf("expected device auth for user 9, got %+v", got)
	}
}

func TestRequireAuthConsoleInvalidBearerFallsBackToCookie(t *testing.T) {
	// Documented benign behavior: an invalid Bearer alongside a valid cookie is
	// accepted via the cookie rather than rejected.
	s := &Server{store: authFakeStore{
		desktopErr: store.ErrNotFound,
		session:    store.Session{ID: 1, Username: "alice"},
		user:       store.User{ID: 9, Username: "alice"},
	}}
	req := withBearer(withCookie(httptest.NewRequest(http.MethodGet, "/x", nil)))
	got, rec := spyAuth(consoleMW(s), req)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", rec.Code)
	}
	if got == nil || got.Kind != authKindSession {
		t.Fatalf("expected session fallback, got %+v", got)
	}
}

func TestRequireAuthInfrastructureError(t *testing.T) {
	// A non-ErrNotFound error stops the chain and maps to 500.
	s := &Server{store: authFakeStore{
		desktopErr: errors.New("db down"),
	}}
	got, rec := spyAuth(configMW(s), withBearer(httptest.NewRequest(http.MethodGet, "/x", nil)))
	if got != nil {
		t.Fatalf("handler should not be reached on infra error")
	}
	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", rec.Code)
	}
}

func TestWithDeviceUnknownToken(t *testing.T) {
	s := &Server{store: authFakeStore{deviceErr: store.ErrNotFound}}
	got, rec := spyAuth(s.requireAuth("device token required", s.attemptDevice),
		withBearer(httptest.NewRequest(http.MethodPost, "/x", nil)))
	if got != nil {
		t.Fatalf("handler should not be reached for unknown device token")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", rec.Code)
	}
}

func TestConfigSnapshotActor(t *testing.T) {
	cases := []struct {
		name      string
		auth      authContext
		wantType  string
		wantActor int64
	}{
		{"session", authContext{Kind: authKindSession, User: store.User{ID: 9}}, "web_session", 9},
		{"desktop", authContext{Kind: authKindDesktop, DesktopSession: store.DesktopSession{ID: 5}}, "desktop_session", 5},
		{"device", authContext{Kind: authKindDevice, Device: store.Device{ID: 7}}, "device", 7},
		{"unknown", authContext{Kind: authKind("future"), User: store.User{ID: 9}}, "future", 9},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			gotType, gotActor := configSnapshotActor(tc.auth)
			if gotType != tc.wantType || gotActor != tc.wantActor {
				t.Fatalf("got (%q,%d), want (%q,%d)", gotType, gotActor, tc.wantType, tc.wantActor)
			}
		})
	}
}

func TestHandleConfigSnapshotGetViaDevice(t *testing.T) {
	// End-to-end through withConfigAuth: a device Bearer reaches the unified
	// GET handler and serves the snapshot for the device's user.
	s := &Server{store: authFakeStore{
		desktopErr: store.ErrNotFound, // force fall-through to the device attempt
		device:     store.Device{ID: 7, UserID: 9},
		snapshot:   store.ConfigSnapshot{Revision: 3, Content: json.RawMessage(`{"a":1}`)},
	}}
	req := withBearer(httptest.NewRequest(http.MethodGet, "/api/v1/config/snapshot", nil))
	rec := httptest.NewRecorder()
	s.withConfigAuth(s.handleConfigSnapshot)(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var resp configSnapshotResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if resp.Revision != 3 {
		t.Fatalf("expected revision 3, got %d", resp.Revision)
	}
}

func TestHandleConfigSnapshotPutSessionRequiresCSRF(t *testing.T) {
	s := &Server{store: authFakeStore{
		session: store.Session{ID: 1, Username: "alice", CSRFToken: "expected"},
		user:    store.User{ID: 9, Username: "alice"},
	}}
	req := withCookie(httptest.NewRequest(http.MethodPut, "/api/v1/config/snapshot", nil))
	rec := httptest.NewRecorder()
	s.withConfigAuth(s.handleConfigSnapshot)(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 without CSRF token, got %d", rec.Code)
	}
}

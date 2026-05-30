package http

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
)

// fakeDataStore satisfies dataStore by embedding the interface (nil). Methods
// that a test does not override panic when called, so each test only stubs the
// behaviour it exercises. This demonstrates the server is decoupled from the
// concrete *store.Store via the dataStore interface.
type fakeDataStore struct {
	dataStore
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

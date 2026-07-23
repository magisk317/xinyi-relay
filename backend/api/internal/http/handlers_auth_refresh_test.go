package http

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type refreshReplayStore struct {
	panicStore
	session         store.DesktopSession
	expectedOldHash string
	rotateErr       error
	revokeCalls     int
}

func (s *refreshReplayStore) GetDesktopSessionByRefreshTokenHash(
	_ context.Context,
	hash string,
) (store.DesktopSession, error) {
	s.expectedOldHash = hash
	return s.session, nil
}

func (s *refreshReplayStore) RotateDesktopSession(
	_ context.Context,
	sessionID int64,
	expectedRefreshTokenHash string,
	_ string,
	_ string,
	_ time.Time,
	_ time.Time,
) (store.DesktopSession, error) {
	if sessionID != s.session.ID || expectedRefreshTokenHash != s.expectedOldHash {
		return store.DesktopSession{}, store.ErrNotFound
	}
	return store.DesktopSession{}, s.rotateErr
}

func (s *refreshReplayStore) RevokeDesktopSession(_ context.Context, sessionID int64) error {
	s.revokeCalls++
	return nil
}

func TestDesktopRefreshDoesNotRevokeWinnerWhenTokenCASLoses(t *testing.T) {
	fake := &refreshReplayStore{
		session:   store.DesktopSession{ID: 42, UserID: 7},
		rotateErr: store.ErrNotFound,
	}
	s := &Server{store: fake}
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/auth/desktop/refresh",
		bytes.NewBufferString(`{"refreshToken":"old-refresh-token"}`),
	)
	rec := httptest.NewRecorder()

	s.handleDesktopAuthRefresh(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for a replayed refresh token, got %d: %s", rec.Code, rec.Body.String())
	}
	if fake.expectedOldHash != security.HashToken("old-refresh-token") {
		t.Fatalf("expected the presented token hash to participate in the CAS")
	}
	if fake.revokeCalls != 0 {
		t.Fatalf("CAS loser must not revoke the concurrently refreshed session")
	}
}

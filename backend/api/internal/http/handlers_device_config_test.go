package http

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/realtime"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type deviceConfigFakeStore struct {
	panicStore
	state      store.DeviceConfigState
	stateErr   error
	command    store.DeviceConfigCommand
	commandErr error
	ackCommand store.DeviceConfigCommand
	ackErr     error
	logs       []store.DeviceConfigAuditLog
	logsErr    error
}

func (f deviceConfigFakeStore) GetDeviceConfigState(context.Context, int64, int64) (store.DeviceConfigState, error) {
	return f.state, f.stateErr
}

func (f deviceConfigFakeStore) CreateDeviceConfigCommand(context.Context, int64, int64, int64, json.RawMessage, string, string, int64) (store.DeviceConfigCommand, error) {
	return f.command, f.commandErr
}

func (f deviceConfigFakeStore) ListDeviceConfigAuditLogs(context.Context, int64, int64, int32, int32) ([]store.DeviceConfigAuditLog, error) {
	return f.logs, f.logsErr
}

func (f deviceConfigFakeStore) AckDeviceConfigCommand(context.Context, int64, int64, int64, int64, string, string, json.RawMessage, string, int64) (store.DeviceConfigCommand, error) {
	return f.ackCommand, f.ackErr
}

func TestHandleDeviceConfigGet(t *testing.T) {
	s := &Server{
		store: deviceConfigFakeStore{
			state: store.DeviceConfigState{
				DeviceID:  7,
				Revision:  4,
				Snapshot:  json.RawMessage(`{"senders":[]}`),
				UpdatedAt: time.Unix(1_700_000_000, 0).UTC(),
			},
		},
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/7/config", nil)
	rec := httptest.NewRecorder()
	s.handleDeviceConfig(rec, req, authContext{User: store.User{ID: 9}}, 7)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var resp deviceConfigStateResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.DeviceID != 7 || resp.Revision != 4 {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestHandleDeviceConfigCommandsPostRequiresCSRF(t *testing.T) {
	s := &Server{store: deviceConfigFakeStore{}, hub: realtime.NewHub()}
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/config/commands",
		bytes.NewBufferString(`{"baseRevision":4,"summary":"apps:update","mutation":{"operations":[]}}`),
	)
	rec := httptest.NewRecorder()

	s.handleDeviceConfigCommands(
		rec,
		req,
		authContext{
			Kind:    authKindSession,
			User:    store.User{ID: 9},
			Session: store.Session{CSRFToken: "expected"},
		},
		7,
	)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403, got %d", rec.Code)
	}
}

func TestHandleDeviceConfigCommandsPostQueuesCommand(t *testing.T) {
	s := &Server{
		store: deviceConfigFakeStore{
			command: store.DeviceConfigCommand{
				ID:             11,
				BaseRevision:   4,
				TargetRevision: 5,
				Mutation:       json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]}]}`),
				Summary:        "apps:update",
				ActorType:      "web_session",
				ActorID:        9,
				Status:         store.DeviceConfigCommandStatusPending,
				CreatedAt:      time.Unix(1_700_000_100, 0).UTC(),
				UpdatedAt:      time.Unix(1_700_000_100, 0).UTC(),
			},
		},
		hub: realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/config/commands",
		bytes.NewBufferString(`{"baseRevision":4,"summary":"apps:update","mutation":{"operations":[{"type":"replace_senders","senders":[]}]}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleDeviceConfigCommands(
		rec,
		req,
		authContext{
			Kind:    authKindSession,
			User:    store.User{ID: 9},
			Session: store.Session{CSRFToken: "expected"},
		},
		7,
	)

	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d", rec.Code)
	}
	var resp deviceConfigCommandItem
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.ID != 11 || resp.TargetRevision != 5 || resp.Status != store.DeviceConfigCommandStatusPending {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestHandleDeviceConfigCommandsPostRejectsLegacyReplaceRoot(t *testing.T) {
	s := &Server{store: deviceConfigFakeStore{}, hub: realtime.NewHub()}
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/config/commands",
		bytes.NewBufferString(`{"baseRevision":4,"summary":"legacy","mutation":{"operations":[{"type":"replace_root","snapshot":{"senders":[]}}]}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleDeviceConfigCommands(
		rec,
		req,
		authContext{
			Kind:    authKindSession,
			User:    store.User{ID: 9},
			Session: store.Session{CSRFToken: "expected"},
		},
		7,
	)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
	if !bytes.Contains(rec.Body.Bytes(), []byte("unsupported_config_mutation")) {
		t.Fatalf("expected unsupported_config_mutation error, got %s", rec.Body.String())
	}
}

func TestHandleAgentConfigCommandsAckRejectsUnsupportedStatus(t *testing.T) {
	s := &Server{
		store: deviceConfigFakeStore{
			ackErr: store.ErrUnsupportedCommandStatus,
		},
		hub: realtime.NewHub(),
	}
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/agent/config/commands:ack",
		bytes.NewBufferString(`{"commandId":11,"status":"ignored","appliedRevision":4,"mirrorContent":{}}`),
	)
	rec := httptest.NewRecorder()

	s.handleAgentConfigCommandsAck(
		rec,
		req,
		authContext{
			Kind:   authKindDevice,
			Device: store.Device{ID: 7, UserID: 9},
		},
	)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
	if !bytes.Contains(rec.Body.Bytes(), []byte("unsupported command status")) {
		t.Fatalf("expected unsupported command status error, got %s", rec.Body.String())
	}
}

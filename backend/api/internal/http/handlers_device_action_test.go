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

type deviceActionFakeStore struct {
	panicStore
	action    store.DeviceAction
	createErr error
	actions   []store.DeviceAction
	listErr   error
	pending   []store.DeviceAction
	ackAction store.DeviceAction
	ackErr    error
}

func (f deviceActionFakeStore) CreateDeviceAction(context.Context, int64, int64, string, json.RawMessage, string, int64) (store.DeviceAction, error) {
	return f.action, f.createErr
}

func (f deviceActionFakeStore) ListDeviceActions(context.Context, int64, int64, int32, int32) ([]store.DeviceAction, error) {
	return f.actions, f.listErr
}

func (f deviceActionFakeStore) ListPendingDeviceActions(context.Context, int64, int64) ([]store.DeviceAction, error) {
	return f.pending, nil
}

func (f deviceActionFakeStore) AckDeviceAction(context.Context, int64, int64, int64, string, string) (store.DeviceAction, error) {
	return f.ackAction, f.ackErr
}

func sampleDeviceAction() store.DeviceAction {
	return store.DeviceAction{
		ID:        21,
		UserID:    9,
		DeviceID:  7,
		Action:    store.DeviceActionSetRingerMode,
		Params:    json.RawMessage(`{"mode":"silent"}`),
		ActorType: "web_session",
		ActorID:   9,
		Status:    store.DeviceActionStatusPending,
		CreatedAt: time.Unix(1_700_000_200, 0).UTC(),
		UpdatedAt: time.Unix(1_700_000_200, 0).UTC(),
	}
}

func TestHandleQueueDeviceActionCreated(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{action: sampleDeviceAction()},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/actions",
		bytes.NewBufferString(`{"action":"SET_RINGER_MODE","params":{"mode":"silent"}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleQueueDeviceAction(
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
	var resp deviceActionItem
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.ID != 21 || resp.Action != store.DeviceActionSetRingerMode || resp.Status != store.DeviceActionStatusPending {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestHandleQueueDeviceActionRequiresCSRF(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/actions",
		bytes.NewBufferString(`{"action":"SET_RINGER_MODE","params":{"mode":"silent"}}`),
	)
	rec := httptest.NewRecorder()

	s.handleQueueDeviceAction(
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

func TestHandleQueueDeviceActionRejectsUnsupportedAction(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{createErr: store.ErrUnsupportedDeviceAction},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/actions",
		bytes.NewBufferString(`{"action":"WIPE_DEVICE","params":{}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleQueueDeviceAction(
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
	if !bytes.Contains(rec.Body.Bytes(), []byte("unsupported_action")) {
		t.Fatalf("expected unsupported_action error, got %s", rec.Body.String())
	}
}

func TestHandleQueueDeviceActionRejectsInvalidParams(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{createErr: store.ErrInvalidDeviceActionParams},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/7/actions",
		bytes.NewBufferString(`{"action":"SET_RINGER_MODE","params":{"mode":"loud"}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleQueueDeviceAction(
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
	if !bytes.Contains(rec.Body.Bytes(), []byte("invalid_params")) {
		t.Fatalf("expected invalid_params error, got %s", rec.Body.String())
	}
}

func TestHandleQueueDeviceActionDeviceNotFound(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{createErr: store.ErrNotFound},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/devices/999/actions",
		bytes.NewBufferString(`{"action":"SET_RINGER_MODE","params":{"mode":"silent"}}`),
	)
	req.Header.Set("X-CSRF-Token", "expected")
	rec := httptest.NewRecorder()

	s.handleQueueDeviceAction(
		rec,
		req,
		authContext{
			Kind:    authKindSession,
			User:    store.User{ID: 9},
			Session: store.Session{CSRFToken: "expected"},
		},
		999,
	)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", rec.Code)
	}
}

func TestHandleListDeviceActions(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{
			actions: []store.DeviceAction{sampleDeviceAction()},
		},
	}

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices/7/actions?limit=10&offset=0", nil)
	rec := httptest.NewRecorder()
	s.handleListDeviceActions(rec, req, authContext{User: store.User{ID: 9}}, 7)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var resp deviceActionsResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(resp.Actions) != 1 || resp.Actions[0].ID != 21 || resp.Limit != 10 {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestHandleAgentDeviceActionAckDone(t *testing.T) {
	done := sampleDeviceAction()
	done.Status = store.DeviceActionStatusDone
	s := &Server{
		store: deviceActionFakeStore{ackAction: done},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/agent/actions:ack",
		bytes.NewBufferString(`{"actionId":21,"status":"done"}`),
	)
	rec := httptest.NewRecorder()

	s.handleAgentDeviceActionAck(
		rec,
		req,
		authContext{Kind: authKindDevice, Device: store.Device{ID: 7, UserID: 9}},
	)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var resp deviceActionItem
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.Status != store.DeviceActionStatusDone {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestHandleAgentDeviceActionAckRejectsUnsupportedStatus(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{ackErr: store.ErrUnsupportedDeviceActionStatus},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/agent/actions:ack",
		bytes.NewBufferString(`{"actionId":21,"status":"pending"}`),
	)
	rec := httptest.NewRecorder()

	s.handleAgentDeviceActionAck(
		rec,
		req,
		authContext{Kind: authKindDevice, Device: store.Device{ID: 7, UserID: 9}},
	)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}
	if !bytes.Contains(rec.Body.Bytes(), []byte("unsupported action status")) {
		t.Fatalf("expected unsupported action status error, got %s", rec.Body.String())
	}
}

func TestHandleAgentDeviceActionAckConflict(t *testing.T) {
	s := &Server{
		store: deviceActionFakeStore{ackErr: store.ErrConflict},
		hub:   realtime.NewHub(),
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/agent/actions:ack",
		bytes.NewBufferString(`{"actionId":21,"status":"done"}`),
	)
	rec := httptest.NewRecorder()

	s.handleAgentDeviceActionAck(
		rec,
		req,
		authContext{Kind: authKindDevice, Device: store.Device{ID: 7, UserID: 9}},
	)

	if rec.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d", rec.Code)
	}
}

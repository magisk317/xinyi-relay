package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"testing"
	"time"
)

func createDeviceActionFixture(t *testing.T, s *Store) (int64, int64) {
	t.Helper()
	ctx := context.Background()
	username := fmt.Sprintf("deviceaction_%d", time.Now().UnixNano())
	user, err := s.CreateUser(ctx, username, "hash")
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	device, err := s.CreateDevice(ctx, user.ID, "dev", "model", "android", "1.0", username+"_token")
	if err != nil {
		t.Fatalf("create device: %v", err)
	}
	return user.ID, device.ID
}

func TestValidateDeviceActionAcceptsWhitelistedActionsAndParams(t *testing.T) {
	cases := []struct {
		action string
		params string
	}{
		{DeviceActionSetRingerMode, `{"mode":"normal"}`},
		{DeviceActionSetRingerMode, `{"mode":"silent"}`},
		{DeviceActionSetRingerMode, `{"mode":"vibrate"}`},
		{DeviceActionSetInterruptionFilter, `{"filter":"all"}`},
		{DeviceActionSetInterruptionFilter, `{"filter":"priority"}`},
		{DeviceActionSetInterruptionFilter, `{"filter":"none"}`},
		{DeviceActionSetInterruptionFilter, `{"filter":"alarms"}`},
	}
	for _, tc := range cases {
		if err := ValidateDeviceAction(tc.action, json.RawMessage(tc.params)); err != nil {
			t.Fatalf("expected %s %s to be valid, got %v", tc.action, tc.params, err)
		}
	}
}

func TestValidateDeviceActionRejectsUnknownAction(t *testing.T) {
	if err := ValidateDeviceAction("WIPE_DEVICE", json.RawMessage(`{}`)); !errors.Is(err, ErrUnsupportedDeviceAction) {
		t.Fatalf("expected ErrUnsupportedDeviceAction, got %v", err)
	}
}

func TestValidateDeviceActionRejectsInvalidParams(t *testing.T) {
	cases := []struct {
		action string
		params string
	}{
		{DeviceActionSetRingerMode, `{}`},
		{DeviceActionSetRingerMode, `{"mode":"loud"}`},
		{DeviceActionSetRingerMode, `{"volume":3}`},
		{DeviceActionSetInterruptionFilter, `{"filter":"unknown"}`},
		{DeviceActionSetRingerMode, `{"mode":3}`},
		{DeviceActionSetRingerMode, `not-json`},
	}
	for _, tc := range cases {
		if err := ValidateDeviceAction(tc.action, json.RawMessage(tc.params)); !errors.Is(err, ErrInvalidDeviceActionParams) {
			t.Fatalf("expected ErrInvalidDeviceActionParams for %s %s, got %v", tc.action, tc.params, err)
		}
	}
}

func TestCreateDeviceActionRejectsUnsupportedActionBeforeInsert(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	if _, err := s.CreateDeviceAction(ctx, userID, deviceID, "WIPE_DEVICE", json.RawMessage(`{}`), "web_session", 9); !errors.Is(err, ErrUnsupportedDeviceAction) {
		t.Fatalf("expected ErrUnsupportedDeviceAction, got %v", err)
	}
}

func TestCreateDeviceActionRejectsInvalidParams(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	if _, err := s.CreateDeviceAction(ctx, userID, deviceID, DeviceActionSetRingerMode, json.RawMessage(`{"mode":"loud"}`), "web_session", 9); !errors.Is(err, ErrInvalidDeviceActionParams) {
		t.Fatalf("expected ErrInvalidDeviceActionParams, got %v", err)
	}
}

func TestCreateDeviceActionRejectsForeignDevice(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	_, deviceID := createDeviceActionFixture(t, s)
	otherUsername := fmt.Sprintf("deviceaction_other_%d", time.Now().UnixNano())
	otherUser, err := s.CreateUser(ctx, otherUsername, "hash")
	if err != nil {
		t.Fatalf("create other user: %v", err)
	}

	if _, err := s.CreateDeviceAction(ctx, otherUser.ID, deviceID, DeviceActionSetRingerMode, json.RawMessage(`{"mode":"silent"}`), "web_session", otherUser.ID); !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound for foreign device, got %v", err)
	}
}

func TestDeviceActionLifecycleQueueListAck(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	created, err := s.CreateDeviceAction(
		ctx,
		userID,
		deviceID,
		DeviceActionSetRingerMode,
		json.RawMessage(`{"mode":"silent"}`),
		"web_session",
		userID,
	)
	if err != nil {
		t.Fatalf("create device action: %v", err)
	}
	if created.Status != DeviceActionStatusPending {
		t.Fatalf("expected pending status, got %s", created.Status)
	}

	pending, err := s.ListPendingDeviceActions(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("list pending device actions: %v", err)
	}
	if len(pending) != 1 || pending[0].ID != created.ID {
		t.Fatalf("expected 1 pending action, got %+v", pending)
	}

	recent, err := s.ListDeviceActions(ctx, userID, deviceID, 50, 0)
	if err != nil {
		t.Fatalf("list device actions: %v", err)
	}
	if len(recent) != 1 || recent[0].ID != created.ID {
		t.Fatalf("expected 1 recent action, got %+v", recent)
	}

	acked, err := s.AckDeviceAction(ctx, userID, deviceID, created.ID, DeviceActionStatusDone, "")
	if err != nil {
		t.Fatalf("ack device action: %v", err)
	}
	if acked.Status != DeviceActionStatusDone {
		t.Fatalf("expected done status, got %s", acked.Status)
	}
	if acked.AckedAt == nil {
		t.Fatalf("expected acked_at to be set")
	}

	pending, err = s.ListPendingDeviceActions(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("list pending after ack: %v", err)
	}
	if len(pending) != 0 {
		t.Fatalf("expected no pending actions after ack, got %+v", pending)
	}
}

func TestAckDeviceActionFailedDefaultsFailureReason(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	created, err := s.CreateDeviceAction(
		ctx,
		userID,
		deviceID,
		DeviceActionSetInterruptionFilter,
		json.RawMessage(`{"filter":"priority"}`),
		"web_session",
		userID,
	)
	if err != nil {
		t.Fatalf("create device action: %v", err)
	}

	acked, err := s.AckDeviceAction(ctx, userID, deviceID, created.ID, DeviceActionStatusFailed, "  ")
	if err != nil {
		t.Fatalf("ack failed device action: %v", err)
	}
	if acked.Status != DeviceActionStatusFailed {
		t.Fatalf("expected failed status, got %s", acked.Status)
	}
	if acked.FailureReason == nil || *acked.FailureReason != "action_failed" {
		t.Fatalf("expected default failure reason action_failed, got %+v", acked.FailureReason)
	}
}

func TestAckDeviceActionRejectsUnsupportedStatus(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	created, err := s.CreateDeviceAction(
		ctx,
		userID,
		deviceID,
		DeviceActionSetRingerMode,
		json.RawMessage(`{"mode":"vibrate"}`),
		"web_session",
		userID,
	)
	if err != nil {
		t.Fatalf("create device action: %v", err)
	}

	if _, err := s.AckDeviceAction(ctx, userID, deviceID, created.ID, DeviceActionStatusPending, ""); !errors.Is(err, ErrUnsupportedDeviceActionStatus) {
		t.Fatalf("expected ErrUnsupportedDeviceActionStatus, got %v", err)
	}
}

func TestAckDeviceActionRejectsSecondAck(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	created, err := s.CreateDeviceAction(
		ctx,
		userID,
		deviceID,
		DeviceActionSetRingerMode,
		json.RawMessage(`{"mode":"normal"}`),
		"web_session",
		userID,
	)
	if err != nil {
		t.Fatalf("create device action: %v", err)
	}
	if _, err := s.AckDeviceAction(ctx, userID, deviceID, created.ID, DeviceActionStatusDone, ""); err != nil {
		t.Fatalf("first ack device action: %v", err)
	}

	if _, err := s.AckDeviceAction(ctx, userID, deviceID, created.ID, DeviceActionStatusFailed, "boom"); !errors.Is(err, ErrConflict) {
		t.Fatalf("expected ErrConflict on second ack, got %v", err)
	}
}

func TestAckDeviceActionRejectsUnknownAction(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceActionFixture(t, s)

	if _, err := s.AckDeviceAction(ctx, userID, deviceID, 999999, DeviceActionStatusDone, ""); !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound for unknown action, got %v", err)
	}
}

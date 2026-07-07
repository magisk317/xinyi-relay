package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"testing"
	"time"
)

func createDeviceConfigFixture(t *testing.T, s *Store) (int64, int64) {
	t.Helper()
	ctx := context.Background()
	username := fmt.Sprintf("devicecfg_%d", time.Now().UnixNano())
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

func createDeviceConfigDeviceForUser(t *testing.T, s *Store, userID int64, name string) int64 {
	t.Helper()
	device, err := s.CreateDevice(
		context.Background(),
		userID,
		name,
		"model",
		"android",
		"1.0",
		fmt.Sprintf("%s_%d_token", name, time.Now().UnixNano()),
	)
	if err != nil {
		t.Fatalf("create device %s: %v", name, err)
	}
	return device.ID
}

func TestDeviceConfigInitializesFromLegacySnapshot(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	expectedSnapshot := `{"senders":[{"id":1,"name":"alpha"}]}`
	if _, err := s.db.Pool.Exec(
		ctx,
		`INSERT INTO config_snapshots (user_id, revision, content, updated_by_type, updated_by_id)
		 VALUES ($1, $2, $3::jsonb, 'migration', NULL)`,
		userID,
		5,
		expectedSnapshot,
	); err != nil {
		t.Fatalf("insert legacy snapshot: %v", err)
	}

	state, err := s.GetDeviceConfigState(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("get device config state: %v", err)
	}

	if state.Revision != 5 {
		t.Fatalf("expected revision 5, got %d", state.Revision)
	}
	if len(state.PendingCommands) != 0 {
		t.Fatalf("expected no pending commands, got %d", len(state.PendingCommands))
	}
	assertJSONEq(t, expectedSnapshot, state.Snapshot)
}

func TestDeviceConfigLegacySnapshotInitializesEachDeviceAndMirrorsStayIsolated(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, firstDeviceID := createDeviceConfigFixture(t, s)
	secondDeviceID := createDeviceConfigDeviceForUser(t, s, userID, "second")

	expectedSnapshot := `{"senders":[{"id":1,"name":"legacy"}]}`
	if _, err := s.db.Pool.Exec(
		ctx,
		`INSERT INTO config_snapshots (user_id, revision, content, updated_by_type, updated_by_id)
		 VALUES ($1, $2, $3::jsonb, 'migration', NULL)`,
		userID,
		5,
		expectedSnapshot,
	); err != nil {
		t.Fatalf("insert legacy snapshot: %v", err)
	}

	firstState, err := s.GetDeviceConfigState(ctx, userID, firstDeviceID)
	if err != nil {
		t.Fatalf("get first device config state: %v", err)
	}
	secondState, err := s.GetDeviceConfigState(ctx, userID, secondDeviceID)
	if err != nil {
		t.Fatalf("get second device config state: %v", err)
	}
	if firstState.Revision != 5 || secondState.Revision != 5 {
		t.Fatalf("expected both devices at revision 5, got first=%d second=%d", firstState.Revision, secondState.Revision)
	}
	assertJSONEq(t, expectedSnapshot, firstState.Snapshot)
	assertJSONEq(t, expectedSnapshot, secondState.Snapshot)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		firstDeviceID,
		5,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[{"id":9,"name":"first"}]}]}`),
		"first:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("queue first device command: %v", err)
	}
	if command.TargetRevision != 6 {
		t.Fatalf("expected first command target revision 6, got %d", command.TargetRevision)
	}
	if _, err := s.UpsertDeviceConfigMirror(
		ctx,
		userID,
		firstDeviceID,
		7,
		json.RawMessage(`{"senders":[{"id":7,"name":"first-local"}]}`),
		"device",
		firstDeviceID,
		"android_local_commit",
	); err != nil {
		t.Fatalf("upsert first device mirror: %v", err)
	}

	firstState, err = s.GetDeviceConfigState(ctx, userID, firstDeviceID)
	if err != nil {
		t.Fatalf("reload first device config state: %v", err)
	}
	secondState, err = s.GetDeviceConfigState(ctx, userID, secondDeviceID)
	if err != nil {
		t.Fatalf("reload second device config state: %v", err)
	}
	if firstState.Revision != 7 {
		t.Fatalf("expected first device revision 7, got %d", firstState.Revision)
	}
	if len(firstState.PendingCommands) != 0 {
		t.Fatalf("expected first device stale queue to be hidden, got %d pending", len(firstState.PendingCommands))
	}
	assertJSONEq(t, `{"senders":[{"id":7,"name":"first-local"}]}`, firstState.Snapshot)
	if secondState.Revision != 5 {
		t.Fatalf("expected second device revision 5, got %d", secondState.Revision)
	}
	assertJSONEq(t, expectedSnapshot, secondState.Snapshot)
	if len(secondState.PendingCommands) != 0 {
		t.Fatalf("expected second device queue empty, got %d pending", len(secondState.PendingCommands))
	}

	secondCommand, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		secondDeviceID,
		5,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[{"id":2,"name":"second"}]}]}`),
		"second:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("queue second device command: %v", err)
	}
	if secondCommand.TargetRevision != 6 {
		t.Fatalf("expected second command target revision 6, got %d", secondCommand.TargetRevision)
	}
}

func TestMirrorUpdateStalesOlderPendingCommands(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]}]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create device config command: %v", err)
	}

	_, err = s.UpsertDeviceConfigMirror(
		ctx,
		userID,
		deviceID,
		2,
		json.RawMessage(`{"senders":[{"id":2}]}`),
		"device",
		deviceID,
		"android_local_commit",
	)
	if err != nil {
		t.Fatalf("upsert device config mirror: %v", err)
	}

	state, err := s.GetDeviceConfigState(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("get device config state: %v", err)
	}
	if state.Revision != 2 {
		t.Fatalf("expected revision 2, got %d", state.Revision)
	}
	if len(state.PendingCommands) != 0 {
		t.Fatalf("expected pending queue to be empty, got %d commands", len(state.PendingCommands))
	}

	var status string
	var failureReason *string
	if err := s.db.Pool.QueryRow(
		ctx,
		`SELECT status, failure_reason FROM device_config_commands
		  WHERE user_id = $1 AND device_id = $2 AND id = $3`,
		userID,
		deviceID,
		command.ID,
	).Scan(&status, &failureReason); err != nil {
		t.Fatalf("reload command: %v", err)
	}
	if status != DeviceConfigCommandStatusStale {
		t.Fatalf("expected stale status, got %s", status)
	}
	if failureReason == nil || *failureReason != "stale_base_revision" {
		t.Fatalf("expected stale_base_revision failure, got %+v", failureReason)
	}

	logs, err := s.ListDeviceConfigAuditLogs(ctx, userID, deviceID, 20, 0)
	if err != nil {
		t.Fatalf("list audit logs: %v", err)
	}
	assertHasAuditEvent(t, logs, "command.stale")
	assertHasAuditEvent(t, logs, "mirror.updated")
}

func TestDeviceConfigCommandQueueAllowsSequentialPendingCommands(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	first, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]} ]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create first device config command: %v", err)
	}
	second, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		1,
		json.RawMessage(`{"operations":[{"type":"replace_device_apps","deviceId":1,"apps":[]} ]}`),
		"apps:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create second device config command: %v", err)
	}

	if first.TargetRevision != 1 {
		t.Fatalf("expected first target revision 1, got %d", first.TargetRevision)
	}
	if second.TargetRevision != 2 {
		t.Fatalf("expected second target revision 2, got %d", second.TargetRevision)
	}

	state, err := s.GetDeviceConfigState(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("get device config state: %v", err)
	}
	if len(state.PendingCommands) != 2 {
		t.Fatalf("expected 2 pending commands, got %d", len(state.PendingCommands))
	}
	if state.PendingCommands[0].ID != first.ID || state.PendingCommands[1].ID != second.ID {
		t.Fatalf("unexpected pending queue order: %+v", state.PendingCommands)
	}
}

func TestDeviceConfigCommandQueueRejectsStaleBaseBehindPendingHead(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	if _, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]} ]}`),
		"senders:update",
		"web_session",
		9,
	); err != nil {
		t.Fatalf("create first device config command: %v", err)
	}

	if _, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_device_apps","deviceId":1,"apps":[]} ]}`),
		"apps:update",
		"web_session",
		9,
	); err != ErrStaleBaseRevision {
		t.Fatalf("expected ErrStaleBaseRevision, got %v", err)
	}
}

func TestAckAppliedCommandUpdatesMirrorAndAudit(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[{"id":1,"type":4,"name":"alpha","jsonSetting":"{}","status":1,"receiveCode":1,"receiveNonCode":1,"receiveAppNotify":1,"receiveCallNotify":0}]}]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create device config command: %v", err)
	}

	updated, err := s.AckDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		command.ID,
		1,
		DeviceConfigCommandStatusApplied,
		"",
		json.RawMessage(`{"senders":[{"id":1}]}`),
		"device",
		deviceID,
	)
	if err != nil {
		t.Fatalf("ack command: %v", err)
	}
	if updated.Status != DeviceConfigCommandStatusApplied {
		t.Fatalf("expected applied status, got %s", updated.Status)
	}

	state, err := s.GetDeviceConfigState(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("get device config state: %v", err)
	}
	if state.Revision != 1 {
		t.Fatalf("expected revision 1, got %d", state.Revision)
	}
	assertJSONEq(t, `{"senders":[{"id":1}]}`, state.Snapshot)

	logs, err := s.ListDeviceConfigAuditLogs(ctx, userID, deviceID, 20, 0)
	if err != nil {
		t.Fatalf("list audit logs: %v", err)
	}
	assertHasAuditEvent(t, logs, "command.applied")
}

func TestAckFailedCommandStoresFailureReasonAndAudit(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[{"id":1,"type":4,"name":"alpha","jsonSetting":"{}","status":1,"receiveCode":1,"receiveNonCode":1,"receiveAppNotify":1,"receiveCallNotify":0}]}]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create device config command: %v", err)
	}

	updated, err := s.AckDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		command.ID,
		0,
		DeviceConfigCommandStatusFailed,
		"boom",
		json.RawMessage(`{}`),
		"device",
		deviceID,
	)
	if err != nil {
		t.Fatalf("ack failed command: %v", err)
	}
	if updated.Status != DeviceConfigCommandStatusFailed {
		t.Fatalf("expected failed status, got %s", updated.Status)
	}
	if updated.FailureReason == nil || *updated.FailureReason != "boom" {
		t.Fatalf("expected failure reason boom, got %+v", updated.FailureReason)
	}

	logs, err := s.ListDeviceConfigAuditLogs(ctx, userID, deviceID, 20, 0)
	if err != nil {
		t.Fatalf("list audit logs: %v", err)
	}
	assertHasAuditEvent(t, logs, "command.failed")
}

func TestAckCommandRejectsUnsupportedStatus(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]} ]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create device config command: %v", err)
	}

	if _, err := s.AckDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		command.ID,
		0,
		"ignored",
		"",
		json.RawMessage(`{}`),
		"device",
		deviceID,
	); !errors.Is(err, ErrUnsupportedCommandStatus) {
		t.Fatalf("expected ErrUnsupportedCommandStatus, got %v", err)
	}
}

func TestAckCommandRejectsSecondAckWithoutMutatingMirror(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()
	userID, deviceID := createDeviceConfigFixture(t, s)

	command, err := s.CreateDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		0,
		json.RawMessage(`{"operations":[{"type":"replace_senders","senders":[]} ]}`),
		"senders:update",
		"web_session",
		9,
	)
	if err != nil {
		t.Fatalf("create device config command: %v", err)
	}
	if _, err := s.AckDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		command.ID,
		1,
		DeviceConfigCommandStatusApplied,
		"",
		json.RawMessage(`{"senders":[{"id":1}]}`),
		"device",
		deviceID,
	); err != nil {
		t.Fatalf("first ack command: %v", err)
	}

	if _, err := s.AckDeviceConfigCommand(
		ctx,
		userID,
		deviceID,
		command.ID,
		2,
		DeviceConfigCommandStatusApplied,
		"",
		json.RawMessage(`{"senders":[{"id":2}]}`),
		"device",
		deviceID,
	); !errors.Is(err, ErrConflict) {
		t.Fatalf("expected ErrConflict on second ack, got %v", err)
	}

	state, err := s.GetDeviceConfigState(ctx, userID, deviceID)
	if err != nil {
		t.Fatalf("get device config state: %v", err)
	}
	if state.Revision != 1 {
		t.Fatalf("expected revision to stay 1, got %d", state.Revision)
	}
	assertJSONEq(t, `{"senders":[{"id":1}]}`, state.Snapshot)
}

func assertHasAuditEvent(t *testing.T, logs []DeviceConfigAuditLog, expected string) {
	t.Helper()
	for _, log := range logs {
		if log.EventType == expected {
			return
		}
	}
	t.Fatalf("expected audit event %s, got %+v", expected, logs)
}

func assertJSONEq(t *testing.T, expected string, actual json.RawMessage) {
	t.Helper()
	var expectedValue any
	var actualValue any
	if err := json.Unmarshal([]byte(expected), &expectedValue); err != nil {
		t.Fatalf("unmarshal expected json: %v", err)
	}
	if err := json.Unmarshal(actual, &actualValue); err != nil {
		t.Fatalf("unmarshal actual json: %v", err)
	}
	if !reflect.DeepEqual(expectedValue, actualValue) {
		t.Fatalf("json mismatch\nexpected=%s\nactual=%s", expected, string(actual))
	}
}

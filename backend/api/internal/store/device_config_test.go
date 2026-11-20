package store

import (
	"testing"
	"time"
)

func TestDeviceHeartbeatFreshnessIncludesFifteenMinuteBoundary(t *testing.T) {
	now := time.Date(2026, time.November, 20, 12, 0, 0, 0, time.UTC)
	cases := []struct {
		name     string
		lastSeen time.Time
		want     bool
	}{
		{name: "exactly fifteen minutes", lastSeen: now.Add(-15 * time.Minute), want: true},
		{name: "one nanosecond stale", lastSeen: now.Add(-15*time.Minute - time.Nanosecond), want: false},
		{name: "recent", lastSeen: now.Add(-14*time.Minute - 59*time.Second), want: true},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			if got := deviceHeartbeatIsRecent(test.lastSeen, now); got != test.want {
				t.Fatalf("deviceHeartbeatIsRecent() = %t, want %t", got, test.want)
			}
		})
	}
}

func TestDeviceHeartbeatFreshnessCapabilityIsSeparateFromAge(t *testing.T) {
	now := time.Date(2026, time.November, 20, 12, 0, 0, 0, time.UTC)
	lastSeen := now.Add(-time.Minute)
	if !deviceHeartbeatIsRecent(lastSeen, now) {
		t.Fatal("recent heartbeat should satisfy the age gate independently of capabilities")
	}
}

func TestScheduledTaskMutationDetection(t *testing.T) {
	if !mutationContainsScheduledTasks([]byte(`{"operations":[{"type":"replace_scheduled_tasks","tasks":[]}]}`)) {
		t.Fatal("scheduled task operation was not detected")
	}
	if mutationContainsScheduledTasks([]byte(`{"operations":[{"type":"replace_senders","senders":[]}]}`)) {
		t.Fatal("unrelated config operation was classified as a scheduled task update")
	}
}

func TestDeviceConfigAckRetryPolicy(t *testing.T) {
	reason := "local_dirty"
	cases := []struct {
		name            string
		command         DeviceConfigCommand
		appliedRevision int64
		status          string
		failureReason   string
		want            bool
	}{
		{
			name: "same applied revision may retry after response loss",
			command: DeviceConfigCommand{
				TargetRevision: 7,
				Status:         DeviceConfigCommandStatusApplied,
			},
			appliedRevision: 7,
			status:          DeviceConfigCommandStatusApplied,
			want:            true,
		},
		{
			name: "different applied revision conflicts",
			command: DeviceConfigCommand{
				TargetRevision: 7,
				Status:         DeviceConfigCommandStatusApplied,
			},
			appliedRevision: 8,
			status:          DeviceConfigCommandStatusApplied,
			want:            false,
		},
		{
			name: "same failed result may retry",
			command: DeviceConfigCommand{
				Status:        DeviceConfigCommandStatusFailed,
				FailureReason: &reason,
			},
			status:        DeviceConfigCommandStatusFailed,
			failureReason: reason,
			want:          true,
		},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			if got := isIdempotentDeviceConfigAck(test.command, test.appliedRevision, test.status, test.failureReason); got != test.want {
				t.Fatalf("isIdempotentDeviceConfigAck() = %t, want %t", got, test.want)
			}
		})
	}
}

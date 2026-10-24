package store

import (
	"context"
	"errors"
	"fmt"
	"testing"
	"time"
)

// PatchDevice must leave fields untouched when their pointer is nil and only
// apply what the caller actually supplied.
func TestPatchDeviceKeepsUnsuppliedFieldsIntegration(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	suffix := time.Now().UnixNano()
	user, err := s.CreateUser(ctx, fmt.Sprintf("patch_%d", suffix), "hash")
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	device, err := s.CreateDevice(ctx, user.ID, "dev", "model", "android", "1.0", fmt.Sprintf("token_%d", suffix))
	if err != nil {
		t.Fatalf("create device: %v", err)
	}
	if !device.Enabled {
		t.Fatalf("new device should start enabled: %+v", device)
	}

	// Only the display name is supplied; enabled must stay true.
	renamed := "Renamed Device"
	patched, err := s.PatchDevice(ctx, user.ID, device.ID, &renamed, nil)
	if err != nil {
		t.Fatalf("patch display name: %v", err)
	}
	if patched.DisplayName != renamed {
		t.Fatalf("display name = %q, want %q", patched.DisplayName, renamed)
	}
	if !patched.Enabled {
		t.Fatalf("enabled flipped to false without being supplied")
	}

	// Only enabled is supplied; display name must survive.
	disabled := false
	patched, err = s.PatchDevice(ctx, user.ID, device.ID, nil, &disabled)
	if err != nil {
		t.Fatalf("patch enabled: %v", err)
	}
	if patched.Enabled {
		t.Fatalf("enabled = true, want false")
	}
	if patched.DisplayName != renamed {
		t.Fatalf("display name = %q, want it preserved as %q", patched.DisplayName, renamed)
	}

	// Another user must not be able to patch this device.
	other, err := s.CreateUser(ctx, fmt.Sprintf("patch_other_%d", suffix), "hash")
	if err != nil {
		t.Fatalf("create other user: %v", err)
	}
	if _, err := s.PatchDevice(ctx, other.ID, device.ID, &renamed, nil); !errors.Is(err, ErrNotFound) {
		t.Fatalf("cross-user patch error = %v, want ErrNotFound", err)
	}

	// Patching a device that does not exist reports not found.
	if _, err := s.PatchDevice(ctx, user.ID, device.ID+100000, &renamed, nil); !errors.Is(err, ErrNotFound) {
		t.Fatalf("missing device patch error = %v, want ErrNotFound", err)
	}
}

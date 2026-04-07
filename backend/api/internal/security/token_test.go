package security

import "testing"

func TestNewOpaqueToken(t *testing.T) {
	token, hash, err := NewOpaqueToken()
	if err != nil {
		t.Fatalf("NewOpaqueToken failed: %v", err)
	}
	if token == "" || hash == "" {
		t.Fatalf("expected token and hash to be non-empty")
	}
	if HashToken(token) != hash {
		t.Fatalf("expected token hash to match")
	}
}

func TestNewBindCode(t *testing.T) {
	code, hash, err := NewBindCode()
	if err != nil {
		t.Fatalf("NewBindCode failed: %v", err)
	}
	if len(code) < 8 {
		t.Fatalf("expected bind code to have reasonable length, got %q", code)
	}
	if HashToken(code) != hash {
		t.Fatalf("expected bind code hash to match")
	}
}

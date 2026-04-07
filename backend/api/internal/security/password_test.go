package security

import "testing"

func TestHashAndVerifyPassword(t *testing.T) {
	hash, err := HashPassword("relay-secret")
	if err != nil {
		t.Fatalf("HashPassword failed: %v", err)
	}

	valid, err := VerifyPassword(hash, "relay-secret")
	if err != nil {
		t.Fatalf("VerifyPassword failed: %v", err)
	}
	if !valid {
		t.Fatalf("expected password to verify")
	}

	invalid, err := VerifyPassword(hash, "wrong")
	if err != nil {
		t.Fatalf("VerifyPassword wrong password failed: %v", err)
	}
	if invalid {
		t.Fatalf("expected wrong password to fail")
	}
}

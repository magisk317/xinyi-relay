package http

import (
	"net/http/httptest"
	"testing"
	"time"
)

func TestRateLimiterBlocksAfterMax(t *testing.T) {
	l := newRateLimiter(3, time.Minute)
	for i := 0; i < 3; i++ {
		if !l.Allow("k") {
			t.Fatalf("attempt %d should be allowed", i+1)
		}
	}
	if l.Allow("k") {
		t.Fatal("4th attempt should be blocked")
	}
	// A different key is tracked independently.
	if !l.Allow("other") {
		t.Fatal("independent key should be allowed")
	}
}

func TestRateLimiterWindowResets(t *testing.T) {
	now := time.Unix(0, 0)
	l := newRateLimiter(1, time.Minute)
	l.now = func() time.Time { return now }

	if !l.Allow("k") {
		t.Fatal("first attempt should be allowed")
	}
	if l.Allow("k") {
		t.Fatal("second attempt within window should be blocked")
	}
	now = now.Add(time.Minute + time.Second)
	if !l.Allow("k") {
		t.Fatal("attempt after window should be allowed again")
	}
}

func TestRateLimiterReset(t *testing.T) {
	l := newRateLimiter(1, time.Minute)
	if !l.Allow("k") {
		t.Fatal("first attempt should be allowed")
	}
	if l.Allow("k") {
		t.Fatal("second attempt should be blocked")
	}
	l.Reset("k")
	if !l.Allow("k") {
		t.Fatal("attempt after reset should be allowed")
	}
}

func TestRateLimiterDisabled(t *testing.T) {
	for _, l := range []*rateLimiter{
		newRateLimiter(0, time.Minute),
		newRateLimiter(5, 0),
		nil,
	} {
		for i := 0; i < 100; i++ {
			if !l.Allow("k") {
				t.Fatal("disabled limiter must always allow")
			}
		}
	}
}

func TestClientIP(t *testing.T) {
	cases := []struct {
		name       string
		remoteAddr string
		xff        string
		xRealIP    string
		want       string
	}{
		{"remote addr only", "203.0.113.5:54321", "", "", "203.0.113.5"},
		{"x-forwarded-for first", "10.0.0.1:1", "198.51.100.7, 10.0.0.1", "", "198.51.100.7"},
		{"x-real-ip", "10.0.0.1:1", "", "198.51.100.9", "198.51.100.9"},
		{"xff precedence over real-ip", "10.0.0.1:1", "198.51.100.7", "198.51.100.9", "198.51.100.7"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest("POST", "/api/v1/auth/login", nil)
			r.RemoteAddr = tc.remoteAddr
			if tc.xff != "" {
				r.Header.Set("X-Forwarded-For", tc.xff)
			}
			if tc.xRealIP != "" {
				r.Header.Set("X-Real-IP", tc.xRealIP)
			}
			if got := clientIP(r); got != tc.want {
				t.Fatalf("clientIP = %q, want %q", got, tc.want)
			}
		})
	}
}

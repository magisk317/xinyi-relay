package http

import (
	"net/http/httptest"
	"testing"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
)

func TestCheckWSOrigin(t *testing.T) {
	cfg := config.Config{
		CORSOrigin:    "https://app.example.com",
		LocalBaseURL:  "https://localhost:8443",
		PublicBaseURL: "",
	}
	s := &Server{cfg: cfg}

	cases := []struct {
		name   string
		origin string
		want   bool
	}{
		{"no origin (non-browser client)", "", true},
		{"configured cors origin", "https://app.example.com", true},
		{"configured local base url", "https://localhost:8443", true},
		{"path is ignored", "https://app.example.com/some/path", true},
		{"disallowed origin", "https://evil.example.com", false},
		{"scheme mismatch", "http://app.example.com", false},
		{"port mismatch", "https://localhost:9999", false},
		{"malformed origin without scheme", "not-a-url", false},
		{"scheme only without host", "https://", false},
		{"unparseable origin", "http://[::1", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			r := httptest.NewRequest("GET", "/api/v1/realtime/ws", nil)
			if tc.origin != "" {
				r.Header.Set("Origin", tc.origin)
			}
			if got := s.checkWSOrigin(r); got != tc.want {
				t.Fatalf("checkWSOrigin(%q) = %v, want %v", tc.origin, got, tc.want)
			}
		})
	}
}

func TestCheckWSOriginWildcardAllowsAll(t *testing.T) {
	s := &Server{cfg: config.Config{CORSOrigin: "*", LocalBaseURL: "https://localhost:8443"}}
	r := httptest.NewRequest("GET", "/api/v1/realtime/ws", nil)
	r.Header.Set("Origin", "https://anything.example.com")
	if !s.checkWSOrigin(r) {
		t.Fatal("wildcard CORS should allow any origin")
	}
}

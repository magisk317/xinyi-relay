package http

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
)

func corsHeaders(t *testing.T, origin string) http.Header {
	t.Helper()
	s := &Server{cfg: config.Config{CORSOrigin: origin}}
	h := s.withCORS(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, httptest.NewRequest(http.MethodGet, "/api/v1/anything", nil))
	return rr.Result().Header
}

func TestCORSSpecificOriginSendsCredentials(t *testing.T) {
	hdr := corsHeaders(t, "https://app.example.com")
	if got := hdr.Get("Access-Control-Allow-Origin"); got != "https://app.example.com" {
		t.Fatalf("Allow-Origin = %q, want the configured origin", got)
	}
	if got := hdr.Get("Access-Control-Allow-Credentials"); got != "true" {
		t.Fatalf("Allow-Credentials = %q, want true for a specific origin", got)
	}
	if got := hdr.Get("Vary"); got != "Origin" {
		t.Fatalf("Vary = %q, want Origin", got)
	}
}

func TestCORSWildcardOriginOmitsCredentials(t *testing.T) {
	hdr := corsHeaders(t, "*")
	if got := hdr.Get("Access-Control-Allow-Origin"); got != "*" {
		t.Fatalf("Allow-Origin = %q, want *", got)
	}
	// A wildcard origin must never advertise credential support.
	if got := hdr.Get("Access-Control-Allow-Credentials"); got != "" {
		t.Fatalf("Allow-Credentials = %q, want empty for wildcard origin", got)
	}
}

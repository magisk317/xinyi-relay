package http

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, errorResponse{Error: message})
}

func writeHTML(w http.ResponseWriter, status int, html string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write([]byte(html))
}

// Maximum accepted request body sizes per endpoint class. They bound how much
// memory a single request can force the server to read, mitigating trivial DoS
// via oversized payloads.
const (
	maxAuthBodyBytes    int64 = 64 << 10 // 64 KiB: login, bootstrap, change password, desktop token exchanges
	maxDeviceBodyBytes  int64 = 64 << 10 // 64 KiB: device register / heartbeat / patch
	maxConfigBodyBytes  int64 = 1 << 20  // 1 MiB: config snapshot
	maxRecordsBodyBytes int64 = 8 << 20  // 8 MiB: relay records batch upload
)

func decodeJSON(w http.ResponseWriter, r *http.Request, target any, maxBytes int64) error {
	r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	// Reject trailing data after the first JSON value (e.g. `{...}garbage`)
	// so partially-valid bodies are not silently accepted.
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("unexpected trailing data after JSON body")
		}
		return err
	}
	return nil
}

// writeDecodeError maps a decodeJSON failure to a response: 413 when the body
// exceeds the configured limit, 400 otherwise.
func writeDecodeError(w http.ResponseWriter, err error) {
	var maxErr *http.MaxBytesError
	if errors.As(err, &maxErr) {
		writeError(w, http.StatusRequestEntityTooLarge, "request body too large")
		return
	}
	writeError(w, http.StatusBadRequest, "invalid payload")
}

func readLimitQuery(r *http.Request, fallback int32) int32 {
	value := r.URL.Query().Get("limit")
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	if parsed > 500 {
		return 500
	}
	return int32(parsed)
}

func readOffsetQuery(r *http.Request) int32 {
	value := r.URL.Query().Get("offset")
	if value == "" {
		return 0
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed < 0 {
		return 0
	}
	return int32(parsed)
}

func readOptionalInt64Query(r *http.Request, key string) (*int64, error) {
	value := r.URL.Query().Get(key)
	if value == "" {
		return nil, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return nil, err
	}
	return &parsed, nil
}

func extractBearerToken(r *http.Request) string {
	header := r.Header.Get("Authorization")
	if header == "" {
		return ""
	}
	const prefix = "Bearer "
	if !strings.HasPrefix(header, prefix) {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(header, prefix))
}

func pathID(path string, prefix string, suffix string) (int64, error) {
	value := strings.TrimPrefix(path, prefix)
	if suffix != "" {
		value = strings.TrimSuffix(value, suffix)
	}
	value = strings.Trim(value, "/")
	if value == "" {
		return 0, errors.New("missing id")
	}
	return strconv.ParseInt(value, 10, 64)
}

func verifyCSRF(r *http.Request, session store.Session) bool {
	header := r.Header.Get("X-CSRF-Token")
	if header == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(header), []byte(session.CSRFToken)) == 1
}

func requestIsSecure(r *http.Request) bool {
	if proto := strings.TrimSpace(r.Header.Get("X-Forwarded-Proto")); proto != "" {
		return strings.EqualFold(proto, "https")
	}
	return r.TLS != nil
}

func sessionCookie(r *http.Request, expiresAt time.Time, token string) *http.Cookie {
	secure := requestIsSecure(r)
	return &http.Cookie{
		Name:     "relay_session",
		Value:    token,
		Path:     "/",
		HttpOnly: true,
		Secure:   secure,
		SameSite: http.SameSiteStrictMode,
		Expires:  expiresAt,
		MaxAge:   int(time.Until(expiresAt).Seconds()),
	}
}

func clearSessionCookie(r *http.Request) *http.Cookie {
	secure := requestIsSecure(r)
	return &http.Cookie{
		Name:     "relay_session",
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		Secure:   secure,
		SameSite: http.SameSiteStrictMode,
		MaxAge:   -1,
		Expires:  time.Unix(0, 0),
	}
}

func hashBearerToken(r *http.Request) string {
	token := extractBearerToken(r)
	if token == "" {
		return ""
	}
	return security.HashToken(token)
}

func validateLoopbackRedirectURL(raw string) (*url.URL, error) {
	value := strings.TrimSpace(raw)
	if value == "" {
		return nil, errors.New("redirect uri is required")
	}

	parsed, err := url.Parse(value)
	if err != nil {
		return nil, fmt.Errorf("parse redirect uri: %w", err)
	}
	if parsed.Scheme != "http" {
		return nil, errors.New("redirect uri must use http")
	}

	host := parsed.Hostname()
	switch host {
	case "localhost", "127.0.0.1", "::1":
	default:
		ip := net.ParseIP(host)
		if ip == nil || !ip.IsLoopback() {
			return nil, errors.New("redirect uri must target a loopback address")
		}
	}

	return parsed, nil
}

// originAllowed reports whether origin matches any entry in allowed, comparing
// only scheme + host (including port) and ignoring path/case differences.
func originAllowed(origin string, allowed []string) bool {
	want, err := normalizeOrigin(origin)
	if err != nil {
		return false
	}
	for _, a := range allowed {
		if got, err := normalizeOrigin(a); err == nil && got == want {
			return true
		}
	}
	return false
}

// normalizeOrigin reduces a URL or Origin header value to a canonical
// "scheme://host[:port]" form for comparison.
func normalizeOrigin(raw string) (string, error) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil {
		return "", err
	}
	if u.Scheme == "" || u.Host == "" {
		return "", fmt.Errorf("invalid origin %q", raw)
	}
	return strings.ToLower(u.Scheme + "://" + u.Host), nil
}

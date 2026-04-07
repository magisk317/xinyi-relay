package http

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/websocket"

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

func decodeJSON(r *http.Request, target any) error {
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	return decoder.Decode(target)
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

var websocketUpgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

package http

import (
	"context"
	"net/http"
	"strings"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) bootstrapAdminIfNeeded(ctx context.Context) error {
	username := strings.TrimSpace(s.cfg.AdminUsername)
	password := s.cfg.AdminPassword
	if username == "" || password == "" {
		return nil
	}

	count, err := s.store.UserCount(ctx)
	if err != nil {
		return err
	}
	if count > 0 {
		return nil
	}

	passwordHash, err := security.HashPassword(password)
	if err != nil {
		return err
	}
	_, err = s.store.CreateUser(ctx, username, passwordHash)
	return err
}

func (s *Server) handleBootstrapAdmin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload bootstrapAdminRequest
	if err := decodeJSON(r, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	payload.Username = strings.TrimSpace(payload.Username)
	if payload.Username == "" || payload.Password == "" {
		writeError(w, http.StatusBadRequest, "username and password are required")
		return
	}

	count, err := s.store.UserCount(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "count users failed")
		return
	}
	if count > 0 {
		writeError(w, http.StatusConflict, "admin already initialized")
		return
	}

	passwordHash, err := security.HashPassword(payload.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "hash password failed")
		return
	}
	user, err := s.store.CreateUser(r.Context(), payload.Username, passwordHash)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "create admin failed")
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{
		"ok":       true,
		"userId":   user.ID,
		"username": user.Username,
	})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload loginRequest
	if err := decodeJSON(r, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	payload.Username = strings.TrimSpace(payload.Username)
	if payload.Username == "" || payload.Password == "" {
		writeError(w, http.StatusBadRequest, "username and password are required")
		return
	}

	user, err := s.store.GetUserByUsername(r.Context(), payload.Username)
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusUnauthorized, "invalid credentials")
			return
		}
		writeError(w, http.StatusInternalServerError, "user lookup failed")
		return
	}

	valid, err := security.VerifyPassword(user.PasswordHash, payload.Password)
	if err != nil || !valid {
		writeError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}

	sessionToken, sessionTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "session token generation failed")
		return
	}
	csrfToken, csrfHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "csrf token generation failed")
		return
	}
	_ = csrfHash

	expiresAt := time.Now().Add(30 * 24 * time.Hour)
	session, err := s.store.CreateSession(r.Context(), user, sessionTokenHash, csrfToken, expiresAt)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "create session failed")
		return
	}

	http.SetCookie(w, sessionCookie(r, expiresAt, sessionToken))
	writeJSON(w, http.StatusOK, loginResponse{
		Authenticated: true,
		Username:      session.Username,
		CSRFToken:     session.CSRFToken,
	})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !verifyCSRF(r, auth.Session) {
		writeError(w, http.StatusForbidden, "invalid csrf token")
		return
	}
	if cookie, err := r.Cookie("relay_session"); err == nil && cookie.Value != "" {
		_ = s.store.DeleteSessionByTokenHash(r.Context(), security.HashToken(cookie.Value))
	}
	http.SetCookie(w, clearSessionCookie(r))
	writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
}

func (s *Server) handleChangePassword(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !verifyCSRF(r, auth.Session) {
		writeError(w, http.StatusForbidden, "invalid csrf token")
		return
	}

	var payload changePasswordRequest
	if err := decodeJSON(r, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	if payload.CurrentPassword == "" || payload.NewPassword == "" {
		writeError(w, http.StatusBadRequest, "currentPassword and newPassword are required")
		return
	}

	valid, err := security.VerifyPassword(auth.User.PasswordHash, payload.CurrentPassword)
	if err != nil || !valid {
		writeError(w, http.StatusUnauthorized, "invalid current password")
		return
	}

	passwordHash, err := security.HashPassword(payload.NewPassword)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "hash password failed")
		return
	}
	if err := s.store.UpdateUserPassword(r.Context(), auth.User.ID, passwordHash); err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "user not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "update password failed")
		return
	}

	writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	cookie, err := r.Cookie("relay_session")
	if err != nil || cookie.Value == "" {
		writeJSON(w, http.StatusOK, meResponse{Authenticated: false})
		return
	}
	session, err := s.store.GetSessionByTokenHash(r.Context(), security.HashToken(cookie.Value))
	if err != nil {
		if err == store.ErrNotFound {
			writeJSON(w, http.StatusOK, meResponse{Authenticated: false})
			return
		}
		writeError(w, http.StatusInternalServerError, "session lookup failed")
		return
	}
	user, err := s.store.GetUserByUsername(r.Context(), session.Username)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "user lookup failed")
		return
	}
	_ = s.store.TouchSession(r.Context(), session.ID)
	writeJSON(w, http.StatusOK, meResponse{
		Authenticated: true,
		Username:      user.Username,
		CSRFToken:     session.CSRFToken,
	})
}

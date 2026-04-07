package http

import (
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) withSession(next func(http.ResponseWriter, *http.Request, authContext)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie("relay_session")
		if err != nil || cookie.Value == "" {
			writeError(w, http.StatusUnauthorized, "session required")
			return
		}

		session, err := s.store.GetSessionByTokenHash(r.Context(), security.HashToken(cookie.Value))
		if err != nil {
			if err == store.ErrNotFound {
				writeError(w, http.StatusUnauthorized, "invalid session")
				return
			}
			writeError(w, http.StatusInternalServerError, "session lookup failed")
			return
		}
		_ = s.store.TouchSession(r.Context(), session.ID)

		user, err := s.store.GetUserByUsername(r.Context(), session.Username)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "user lookup failed")
			return
		}

		auth := authContext{
			Kind:    authKindSession,
			User:    user,
			Session: session,
		}
		next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
	}
}

func (s *Server) withDevice(next func(http.ResponseWriter, *http.Request, authContext)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tokenHash := hashBearerToken(r)
		if tokenHash == "" {
			writeError(w, http.StatusUnauthorized, "device token required")
			return
		}

		device, err := s.store.GetDeviceByTokenHash(r.Context(), tokenHash)
		if err != nil {
			if err == store.ErrNotFound {
				writeError(w, http.StatusUnauthorized, "invalid device token")
				return
			}
			writeError(w, http.StatusInternalServerError, "device lookup failed")
			return
		}

		auth := authContext{
			Kind:   authKindDevice,
			Device: device,
			User: store.User{
				ID: device.UserID,
			},
		}
		next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
	}
}

package http

import (
	"errors"
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) authenticateSession(r *http.Request) (authContext, error) {
	cookie, err := r.Cookie("relay_session")
	if err != nil || cookie.Value == "" {
		return authContext{}, store.ErrNotFound
	}

	session, err := s.store.GetSessionByTokenHash(r.Context(), security.HashToken(cookie.Value))
	if err != nil {
		return authContext{}, err
	}
	_ = s.store.TouchSession(r.Context(), session.ID)

	user, err := s.store.GetUserByUsername(r.Context(), session.Username)
	if err != nil {
		return authContext{}, err
	}

	return authContext{
		Kind:    authKindSession,
		User:    user,
		Session: session,
	}, nil
}

func (s *Server) authenticateDesktopSession(r *http.Request) (authContext, error) {
	tokenHash := hashBearerToken(r)
	if tokenHash == "" {
		return authContext{}, store.ErrNotFound
	}

	session, err := s.store.GetDesktopSessionByAccessTokenHash(r.Context(), tokenHash)
	if err != nil {
		return authContext{}, err
	}
	_ = s.store.TouchDesktopSession(r.Context(), session.ID)

	user, err := s.store.GetUserByID(r.Context(), session.UserID)
	if err != nil {
		return authContext{}, err
	}

	return authContext{
		Kind:           authKindDesktop,
		User:           user,
		DesktopSession: session,
	}, nil
}

func (s *Server) withSession(next func(http.ResponseWriter, *http.Request, authContext)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		auth, err := s.authenticateSession(r)
		if err != nil {
			if errors.Is(err, store.ErrNotFound) {
				writeError(w, http.StatusUnauthorized, "session required")
				return
			}
			writeError(w, http.StatusInternalServerError, "session lookup failed")
			return
		}
		next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
	}
}

func (s *Server) withDesktopSession(next func(http.ResponseWriter, *http.Request, authContext)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		auth, err := s.authenticateDesktopSession(r)
		if err != nil {
			if errors.Is(err, store.ErrNotFound) {
				writeError(w, http.StatusUnauthorized, "desktop session required")
				return
			}
			writeError(w, http.StatusInternalServerError, "desktop session lookup failed")
			return
		}
		next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
	}
}

func (s *Server) withConsoleAuth(next func(http.ResponseWriter, *http.Request, authContext)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if extractBearerToken(r) != "" {
			auth, err := s.authenticateDesktopSession(r)
			if err != nil {
				if errors.Is(err, store.ErrNotFound) {
					writeError(w, http.StatusUnauthorized, "invalid desktop session")
					return
				}
				writeError(w, http.StatusInternalServerError, "desktop session lookup failed")
				return
			}
			next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
			return
		}

		auth, err := s.authenticateSession(r)
		if err != nil {
			if errors.Is(err, store.ErrNotFound) {
				writeError(w, http.StatusUnauthorized, "session required")
				return
			}
			writeError(w, http.StatusInternalServerError, "session lookup failed")
			return
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

package http

import (
	"errors"
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// errNoCredential signals that an authAttempt found none of its credential in
// the request (e.g. no cookie / no Bearer token), so the caller should move on
// to the next attempt rather than treat it as an authentication failure.
var errNoCredential = errors.New("auth: no credential presented")

// authHandler is a route handler that receives the resolved authContext.
type authHandler = func(http.ResponseWriter, *http.Request, authContext)

// authAttempt tries to authenticate a request with a single credential type.
//
// It returns:
//   - (auth, nil)              on success;
//   - (_, errNoCredential)     when its credential is not present (try next);
//   - (_, store.ErrNotFound)   when the credential is present but unknown/invalid
//     (try next — desktop and device share the Bearer namespace);
//   - (_, otherErr)            on an infrastructure failure (stop, 500).
type authAttempt func(*http.Request) (authContext, error)

func (s *Server) attemptSession(r *http.Request) (authContext, error) {
	cookie, err := r.Cookie("relay_session")
	if err != nil || cookie.Value == "" {
		return authContext{}, errNoCredential
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

func (s *Server) attemptDesktopSession(r *http.Request) (authContext, error) {
	tokenHash := hashBearerToken(r)
	if tokenHash == "" {
		return authContext{}, errNoCredential
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

func (s *Server) attemptDevice(r *http.Request) (authContext, error) {
	tokenHash := hashBearerToken(r)
	if tokenHash == "" {
		return authContext{}, errNoCredential
	}

	device, err := s.store.GetDeviceByTokenHash(r.Context(), tokenHash)
	if err != nil {
		return authContext{}, err
	}

	return authContext{
		Kind:   authKindDevice,
		Device: device,
		User: store.User{
			ID: device.UserID,
		},
	}, nil
}

// resolveAuth runs the attempts in order and returns the first successful
// authentication. Attempts whose credential is absent (errNoCredential) or
// present-but-invalid (store.ErrNotFound) are skipped; an infrastructure error
// stops the chain. When every attempt is exhausted it returns store.ErrNotFound.
func (s *Server) resolveAuth(r *http.Request, attempts ...authAttempt) (authContext, error) {
	for _, attempt := range attempts {
		auth, err := attempt(r)
		switch {
		case err == nil:
			return auth, nil
		case errors.Is(err, errNoCredential), errors.Is(err, store.ErrNotFound):
			continue
		default:
			return authContext{}, err
		}
	}
	return authContext{}, store.ErrNotFound
}

// requireAuth builds a middleware that gates a handler behind the given
// credential attempts, performing the single err->HTTP-status mapping for all
// routes. realm is the message returned on a 401.
func (s *Server) requireAuth(realm string, attempts ...authAttempt) func(authHandler) http.HandlerFunc {
	return func(next authHandler) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			auth, err := s.resolveAuth(r, attempts...)
			if err != nil {
				if errors.Is(err, store.ErrNotFound) || errors.Is(err, errNoCredential) {
					writeError(w, http.StatusUnauthorized, realm)
					return
				}
				writeError(w, http.StatusInternalServerError, "authentication failed")
				return
			}
			next(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
		}
	}
}

func (s *Server) withSession(next authHandler) http.HandlerFunc {
	return s.requireAuth("session required", s.attemptSession)(next)
}

func (s *Server) withDesktopSession(next authHandler) http.HandlerFunc {
	return s.requireAuth("desktop session required", s.attemptDesktopSession)(next)
}

// withConsoleAuth accepts either a desktop Bearer token or a Web session cookie.
func (s *Server) withConsoleAuth(next authHandler) http.HandlerFunc {
	return s.requireAuth("authentication required", s.attemptDesktopSession, s.attemptSession)(next)
}

func (s *Server) withDevice(next authHandler) http.HandlerFunc {
	return s.requireAuth("device token required", s.attemptDevice)(next)
}

// withConfigAuth accepts a desktop Bearer token, a device Bearer token, or a Web
// session cookie — the union consumed by the config snapshot endpoint.
func (s *Server) withConfigAuth(next authHandler) http.HandlerFunc {
	return s.requireAuth("authentication required", s.attemptDesktopSession, s.attemptDevice, s.attemptSession)(next)
}

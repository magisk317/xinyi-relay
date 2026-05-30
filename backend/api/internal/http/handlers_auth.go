package http

import (
	"context"
	"fmt"
	"html/template"
	"net/http"
	"strings"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

var desktopAuthPageTemplate = template.Must(template.New("desktop-auth").Parse(`<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Xinyi Relay Desktop Login</title>
    <style>
      body{margin:0;font-family:"Avenir Next","Segoe UI",sans-serif;background:linear-gradient(180deg,#091221,#101c33);color:#edf4ff}
      main{min-height:100vh;display:grid;place-items:center;padding:32px}
      article{width:min(680px,100%);border-radius:28px;padding:32px;border:1px solid rgba(142,163,205,.16);background:linear-gradient(180deg,rgba(17,28,51,.94),rgba(8,16,31,.92));box-shadow:0 26px 70px -42px rgba(0,0,0,.58)}
      .eyebrow{text-transform:uppercase;letter-spacing:.24em;font-size:.75rem;color:#7ddcc4}
      h1{font-size:clamp(2rem,4vw,3rem);letter-spacing:-.05em;margin:12px 0 0}
      p{line-height:1.7;color:#a5b6d6}
      label{display:grid;gap:8px;margin-top:16px}
      input{padding:14px 16px;border-radius:16px;border:1px solid rgba(123,146,190,.2);background:rgba(4,11,20,.68);color:inherit}
      button{margin-top:20px;min-height:46px;padding:0 20px;border:0;border-radius:999px;background:linear-gradient(135deg,#6d9bff,#4de2b8);color:#091221;font-weight:700;cursor:pointer}
      .meta,.error{margin-top:16px;padding:14px 16px;border-radius:18px}
      .meta{background:rgba(255,255,255,.04)}
      .error{background:rgba(255,102,131,.16);color:#ffd6df}
      strong{color:#edf4ff}
    </style>
  </head>
  <body>
    <main>
      <article>
        <div class="eyebrow">Desktop Auth Handoff</div>
        <h1>{{ .Title }}</h1>
        <p>{{ .Description }}</p>
        {{ if .Error }}<div class="error">{{ .Error }}</div>{{ end }}
        <div class="meta">
          <div><strong>Desktop client:</strong> {{ .ClientName }}</div>
          <div><strong>Callback:</strong> {{ .RedirectURI }}</div>
          {{ if .ExistingUsername }}<div><strong>Using active Web session:</strong> {{ .ExistingUsername }}</div>{{ end }}
        </div>
        <form method="post" action="/api/v1/auth/desktop/start">
          <input type="hidden" name="redirect_uri" value="{{ .RedirectURI }}" />
          <input type="hidden" name="state" value="{{ .State }}" />
          <input type="hidden" name="client_name" value="{{ .ClientName }}" />
          {{ if not .ExistingUsername }}
            <label>
              <span>Username</span>
              <input type="text" name="username" value="{{ .Username }}" autocomplete="username" required />
            </label>
            <label>
              <span>Password</span>
              <input type="password" name="password" autocomplete="current-password" required />
            </label>
          {{ end }}
          <button type="submit">{{ .ButtonLabel }}</button>
        </form>
      </article>
    </main>
  </body>
</html>`))

type desktopAuthPageData struct {
	Title            string
	Description      string
	ButtonLabel      string
	ClientName       string
	RedirectURI      string
	State            string
	Username         string
	ExistingUsername string
	Error            string
}

// loginAttemptAllowed enforces brute-force protection on password endpoints,
// limiting attempts per client IP and per username. It returns false when
// either limit has been exceeded.
func (s *Server) loginAttemptAllowed(r *http.Request, username string) bool {
	ipOK := s.loginLimiter.Allow("ip:" + clientIP(r, s.cfg.TrustProxyHeaders))
	userOK := s.loginLimiter.Allow("user:" + strings.ToLower(strings.TrimSpace(username)))
	return ipOK && userOK
}

// loginAttemptSucceeded clears the limiter counters after a successful login so
// a legitimate user is not penalised for earlier mistyped passwords.
func (s *Server) loginAttemptSucceeded(r *http.Request, username string) {
	s.loginLimiter.Reset("ip:" + clientIP(r, s.cfg.TrustProxyHeaders))
	s.loginLimiter.Reset("user:" + strings.ToLower(strings.TrimSpace(username)))
}

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

	if !s.loginAttemptAllowed(r, payload.Username) {
		writeError(w, http.StatusTooManyRequests, "too many login attempts, please try again later")
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

	s.loginAttemptSucceeded(r, payload.Username)

	sessionToken, sessionTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "session token generation failed")
		return
	}
	csrfToken, _, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "csrf token generation failed")
		return
	}

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

func (s *Server) handleDesktopAuthStart(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		values := r.URL.Query()
		redirectURI := values.Get("redirect_uri")
		state := strings.TrimSpace(values.Get("state"))
		clientName := strings.TrimSpace(values.Get("client_name"))
		if clientName == "" {
			clientName = "Xinyi Relay Desktop"
		}
		if _, err := validateLoopbackRedirectURL(redirectURI); err != nil || state == "" {
			writeError(w, http.StatusBadRequest, "desktop redirect and state are required")
			return
		}

		data := desktopAuthPageData{
			Title:       "Authorize your desktop client",
			Description: "Sign in here or reuse your active Web session. The backend will hand a one-time code back to the loopback listener inside the desktop app.",
			ButtonLabel: "Approve Desktop Session",
			ClientName:  clientName,
			RedirectURI: redirectURI,
			State:       state,
		}
		if auth, err := s.authenticateSession(r); err == nil {
			data.ExistingUsername = auth.User.Username
		}

		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		_ = desktopAuthPageTemplate.Execute(w, data)
	case http.MethodPost:
		if err := r.ParseForm(); err != nil {
			writeError(w, http.StatusBadRequest, "invalid form payload")
			return
		}

		payload := desktopLoginPageRequest{
			Username:    strings.TrimSpace(r.FormValue("username")),
			Password:    r.FormValue("password"),
			RedirectURI: r.FormValue("redirect_uri"),
			State:       strings.TrimSpace(r.FormValue("state")),
			ClientName:  strings.TrimSpace(r.FormValue("client_name")),
		}
		if payload.ClientName == "" {
			payload.ClientName = "Xinyi Relay Desktop"
		}

		redirectTarget, err := validateLoopbackRedirectURL(payload.RedirectURI)
		if err != nil || payload.State == "" {
			writeError(w, http.StatusBadRequest, "desktop redirect and state are required")
			return
		}

		user, nextCookie, err := s.resolveDesktopStartUser(r, payload)
		if err != nil {
			data := desktopAuthPageData{
				Title:       "Authorize your desktop client",
				Description: "The browser handoff requires a valid backend login before the desktop app can exchange a session.",
				ButtonLabel: "Try Again",
				ClientName:  payload.ClientName,
				RedirectURI: payload.RedirectURI,
				State:       payload.State,
				Username:    payload.Username,
				Error:       err.Error(),
			}
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			_ = desktopAuthPageTemplate.Execute(w, data)
			return
		}

		code, codeHash, err := security.NewOpaqueToken()
		if err != nil {
			writeError(w, http.StatusInternalServerError, "desktop code generation failed")
			return
		}
		expiresAt := time.Now().Add(5 * time.Minute)
		if _, err := s.store.CreateDesktopAuthRequest(
			r.Context(),
			user.ID,
			codeHash,
			payload.RedirectURI,
			payload.State,
			payload.ClientName,
			expiresAt,
		); err != nil {
			writeError(w, http.StatusInternalServerError, "desktop auth request creation failed")
			return
		}
		if nextCookie != nil {
			http.SetCookie(w, nextCookie)
		}

		query := redirectTarget.Query()
		query.Set("code", code)
		query.Set("state", payload.State)
		redirectTarget.RawQuery = query.Encode()
		http.Redirect(w, r, redirectTarget.String(), http.StatusSeeOther)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) resolveDesktopStartUser(r *http.Request, payload desktopLoginPageRequest) (store.User, *http.Cookie, error) {
	if auth, err := s.authenticateSession(r); err == nil {
		return auth.User, nil, nil
	}
	if payload.Username == "" || payload.Password == "" {
		return store.User{}, nil, fmt.Errorf("username and password are required")
	}

	if !s.loginAttemptAllowed(r, payload.Username) {
		return store.User{}, nil, fmt.Errorf("too many login attempts, please try again later")
	}

	user, err := s.store.GetUserByUsername(r.Context(), payload.Username)
	if err != nil {
		if err == store.ErrNotFound {
			return store.User{}, nil, fmt.Errorf("invalid credentials")
		}
		return store.User{}, nil, fmt.Errorf("user lookup failed")
	}
	valid, err := security.VerifyPassword(user.PasswordHash, payload.Password)
	if err != nil || !valid {
		return store.User{}, nil, fmt.Errorf("invalid credentials")
	}

	s.loginAttemptSucceeded(r, payload.Username)

	sessionToken, sessionTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		return store.User{}, nil, fmt.Errorf("session token generation failed")
	}
	csrfToken, _, err := security.NewOpaqueToken()
	if err != nil {
		return store.User{}, nil, fmt.Errorf("csrf token generation failed")
	}
	expiresAt := time.Now().Add(30 * 24 * time.Hour)
	if _, err := s.store.CreateSession(r.Context(), user, sessionTokenHash, csrfToken, expiresAt); err != nil {
		return store.User{}, nil, fmt.Errorf("create session failed")
	}

	return user, sessionCookie(r, expiresAt, sessionToken), nil
}

func (s *Server) handleDesktopAuthExchange(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload desktopExchangeRequest
	if err := decodeJSON(r, &payload); err != nil || strings.TrimSpace(payload.Code) == "" {
		writeError(w, http.StatusBadRequest, "desktop exchange code is required")
		return
	}

	request, err := s.store.ConsumeDesktopAuthRequest(r.Context(), security.HashToken(strings.TrimSpace(payload.Code)))
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusUnauthorized, "invalid or expired desktop exchange code")
			return
		}
		writeError(w, http.StatusInternalServerError, "desktop exchange failed")
		return
	}

	user, err := s.store.GetUserByID(r.Context(), request.UserID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "user lookup failed")
		return
	}

	accessToken, accessTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "access token generation failed")
		return
	}
	refreshToken, refreshTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "refresh token generation failed")
		return
	}

	expiresAt := time.Now().Add(12 * time.Hour)
	refreshExpiresAt := time.Now().Add(90 * 24 * time.Hour)
	session, err := s.store.CreateDesktopSession(
		r.Context(),
		user,
		request.ClientName,
		accessTokenHash,
		refreshTokenHash,
		expiresAt,
		refreshExpiresAt,
	)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "desktop session creation failed")
		return
	}

	writeJSON(w, http.StatusOK, desktopSessionResponse{
		Authenticated:    true,
		Username:         session.Username,
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		ExpiresAt:        session.ExpiresAt,
		RefreshExpiresAt: session.RefreshExpiresAt,
	})
}

func (s *Server) handleDesktopAuthRefresh(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload desktopRefreshRequest
	if err := decodeJSON(r, &payload); err != nil || strings.TrimSpace(payload.RefreshToken) == "" {
		writeError(w, http.StatusBadRequest, "refresh token is required")
		return
	}

	current, err := s.store.GetDesktopSessionByRefreshTokenHash(r.Context(), security.HashToken(strings.TrimSpace(payload.RefreshToken)))
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusUnauthorized, "invalid desktop refresh token")
			return
		}
		writeError(w, http.StatusInternalServerError, "desktop session lookup failed")
		return
	}

	accessToken, accessTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "access token generation failed")
		return
	}
	refreshToken, refreshTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "refresh token generation failed")
		return
	}

	expiresAt := time.Now().Add(12 * time.Hour)
	refreshExpiresAt := time.Now().Add(90 * 24 * time.Hour)
	session, err := s.store.RotateDesktopSession(
		r.Context(),
		current.ID,
		accessTokenHash,
		refreshTokenHash,
		expiresAt,
		refreshExpiresAt,
	)
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusUnauthorized, "desktop session no longer exists")
			return
		}
		writeError(w, http.StatusInternalServerError, "desktop session refresh failed")
		return
	}

	writeJSON(w, http.StatusOK, desktopSessionResponse{
		Authenticated:    true,
		Username:         session.Username,
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		ExpiresAt:        session.ExpiresAt,
		RefreshExpiresAt: session.RefreshExpiresAt,
	})
}

func (s *Server) handleDesktopAuthLogout(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	if token := extractBearerToken(r); token != "" {
		_ = s.store.DeleteDesktopSessionByAccessTokenHash(r.Context(), security.HashToken(token))
		writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
		return
	}

	var payload desktopLogoutRequest
	if err := decodeJSON(r, &payload); err != nil || strings.TrimSpace(payload.RefreshToken) == "" {
		writeError(w, http.StatusBadRequest, "desktop access or refresh token is required")
		return
	}

	_ = s.store.DeleteDesktopSessionByRefreshTokenHash(r.Context(), security.HashToken(strings.TrimSpace(payload.RefreshToken)))
	writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
}

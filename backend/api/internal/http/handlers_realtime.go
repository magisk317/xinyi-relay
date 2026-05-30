package http

import (
	"log"
	"net/http"
	"strings"
	"time"
)

// checkWSOrigin guards the WebSocket upgrade against cross-site hijacking. It
// allows requests without an Origin header (non-browser clients such as the
// Android agent and desktop app), allows everything when CORS is explicitly a
// wildcard, and otherwise requires the Origin to match a configured origin.
func (s *Server) checkWSOrigin(r *http.Request) bool {
	origin := strings.TrimSpace(r.Header.Get("Origin"))
	if origin == "" {
		return true
	}
	if s.cfg.CORSOrigin == "*" {
		return true
	}
	return originAllowed(origin, s.allowedOrigins())
}

// allowedOrigins returns the configured origins permitted for browser-based
// access (CORS frontend origin plus the local/public base URLs).
func (s *Server) allowedOrigins() []string {
	candidates := []string{s.cfg.CORSOrigin, s.cfg.LocalBaseURL, s.cfg.PublicBaseURL}
	out := make([]string, 0, len(candidates))
	for _, c := range candidates {
		if c = strings.TrimSpace(c); c != "" && c != "*" {
			out = append(out, c)
		}
	}
	return out
}

// warnInvalidAllowedOrigins logs configured origins that cannot be normalized so
// a misconfigured RELAY_CORS_ORIGIN/RELAY_LOCAL_BASE_URL/RELAY_PUBLIC_BASE_URL
// (e.g. missing scheme) is visible instead of silently rejecting browser WS
// clients at upgrade time.
func (s *Server) warnInvalidAllowedOrigins() {
	for _, o := range s.allowedOrigins() {
		if _, err := normalizeOrigin(o); err != nil {
			log.Printf("[security] configured WS allowed origin %q is not a valid scheme://host URL and will be ignored; browser WebSocket clients using it will be rejected: %v", o, err)
		}
	}
}

func (s *Server) handleRealtimeWS(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	conn, err := s.wsUpgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	s.hub.Register(auth.User.ID, conn)
	defer s.hub.Unregister(auth.User.ID, conn)

	_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	})

	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			break
		}
	}
}

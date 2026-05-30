package http

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	"github.com/magisk317/xinyi-relay/backend/api/internal/database"
	"github.com/magisk317/xinyi-relay/backend/api/internal/realtime"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type Server struct {
	cfg          config.Config
	db           *database.Database
	store        *store.Store
	hub          *realtime.Hub
	server       *http.Server
	loginLimiter *rateLimiter
	wsUpgrader   websocket.Upgrader
}

type systemInfoResponse struct {
	Service       string `json:"service"`
	AppEnv        string `json:"appEnv"`
	LocalBaseURL  string `json:"localBaseUrl"`
	PublicBaseURL string `json:"publicBaseUrl"`
	DatabaseReady bool   `json:"databaseReady"`
	UserCount     int64  `json:"userCount"`
	Time          string `json:"time"`
}

func NewServer(ctx context.Context, cfg config.Config) (*Server, error) {
	db, err := database.Open(ctx, cfg)
	if err != nil {
		return nil, err
	}

	s := &Server{
		cfg:          cfg,
		db:           db,
		store:        store.New(db),
		hub:          realtime.NewHub(),
		loginLimiter: newRateLimiter(cfg.LoginRateLimitMax, cfg.LoginRateLimitWindow),
	}
	s.wsUpgrader = websocket.Upgrader{CheckOrigin: s.checkWSOrigin}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", s.handleHealth)
	mux.HandleFunc("/api/v1/system/info", s.handleSystemInfo)
	mux.HandleFunc("/api/v1/bootstrap/admin", s.handleBootstrapAdmin)
	mux.HandleFunc("/api/v1/auth/login", s.handleLogin)
	mux.HandleFunc("/api/v1/auth/logout", s.withSession(s.handleLogout))
	mux.HandleFunc("/api/v1/auth/password", s.withSession(s.handleChangePassword))
	mux.HandleFunc("/api/v1/auth/me", s.handleMe)
	mux.HandleFunc("/api/v1/auth/desktop/start", s.handleDesktopAuthStart)
	mux.HandleFunc("/api/v1/auth/desktop/exchange", s.handleDesktopAuthExchange)
	mux.HandleFunc("/api/v1/auth/desktop/refresh", s.handleDesktopAuthRefresh)
	mux.HandleFunc("/api/v1/auth/desktop/logout", s.handleDesktopAuthLogout)
	mux.HandleFunc("/api/v1/devices/bind-codes", s.withConsoleAuth(s.handleCreateBindCode))
	mux.HandleFunc("/api/v1/agent/register", s.handleAgentRegister)
	mux.HandleFunc("/api/v1/agent/heartbeat", s.withDevice(s.handleAgentHeartbeat))
	mux.HandleFunc("/api/v1/agent/records:batch", s.withDevice(s.handleAgentRecordsBatch))
	mux.HandleFunc("/api/v1/devices", s.withConsoleAuth(s.handleDevices))
	mux.HandleFunc("/api/v1/devices/", s.withConsoleAuth(s.handleDeviceByID))
	mux.HandleFunc("/api/v1/config/snapshot", s.handleConfigSnapshot)
	mux.HandleFunc("/api/v1/config/audit", s.withConsoleAuth(s.handleConfigAuditLogs))
	mux.HandleFunc("/api/v1/records", s.withConsoleAuth(s.handleRecords))
	mux.HandleFunc("/api/v1/records/", s.withConsoleAuth(s.handleRecordByID))
	mux.HandleFunc("/api/v1/realtime/ws", s.withConsoleAuth(s.handleRealtimeWS))

	s.server = &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           s.withLogging(s.withCORS(mux)),
		ReadHeaderTimeout: 5 * time.Second,
	}

	return s, nil
}

func (s *Server) ListenAndServe() error {
	log.Printf("relay backend listening on %s", s.cfg.HTTPAddr)
	return s.server.ListenAndServe()
}

func (s *Server) BootstrapAdminIfNeeded(ctx context.Context) error {
	return s.bootstrapAdminIfNeeded(ctx)
}

func (s *Server) Shutdown(ctx context.Context) error {
	defer s.db.Close()
	return s.server.Shutdown(ctx)
}

func (s *Server) withLogging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path, time.Since(start))
	})
}

func (s *Server) withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := s.cfg.CORSOrigin
		if origin != "" {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			// Credentials cannot be combined with a wildcard origin; browsers
			// reject that pairing and it would also be unsafe. Only advertise
			// credential support when a specific origin is configured.
			if origin != "*" {
				w.Header().Set("Access-Control-Allow-Credentials", "true")
				// Add (not Set) so we don't clobber a Vary value set by other middleware.
				w.Header().Add("Vary", "Origin")
			}
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-CSRF-Token, Authorization")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
		}
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"service": "relay-backend",
	})
}

func (s *Server) handleSystemInfo(w http.ResponseWriter, r *http.Request) {
	userCount, err := s.store.UserCount(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "count users failed")
		return
	}
	writeJSON(w, http.StatusOK, systemInfoResponse{
		Service:       "relay-backend",
		AppEnv:        s.cfg.AppEnv,
		LocalBaseURL:  s.cfg.LocalBaseURL,
		PublicBaseURL: s.cfg.PublicBaseURL,
		DatabaseReady: s.cfg.DatabaseURL != "",
		UserCount:     userCount,
		Time:          time.Now().UTC().Format(time.RFC3339),
	})
}

func parseJSONMap(data json.RawMessage) json.RawMessage {
	if len(data) == 0 || string(data) == "null" {
		return json.RawMessage(`{}`)
	}
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" {
		return json.RawMessage(`{}`)
	}
	return data
}

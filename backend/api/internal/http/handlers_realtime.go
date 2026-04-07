package http

import (
	"net/http"
	"time"
)

func (s *Server) handleRealtimeWS(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	conn, err := websocketUpgrader.Upgrade(w, r, nil)
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

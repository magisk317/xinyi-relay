package http

import (
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) handleConfigSnapshot(w http.ResponseWriter, r *http.Request) {
	if tokenHash := hashBearerToken(r); tokenHash != "" {
		if auth, err := s.authenticateDesktopSession(r); err == nil {
			s.handleConfigSnapshotForDesktop(w, r.WithContext(withAuthContext(r.Context(), auth)), auth)
			return
		} else if err != nil && err != store.ErrNotFound {
			writeError(w, http.StatusInternalServerError, "desktop session lookup failed")
			return
		}
		s.withDevice(s.handleConfigSnapshotForDevice)(w, r)
		return
	}
	s.withSession(s.handleConfigSnapshotForSession)(w, r)
}

func (s *Server) handleConfigSnapshotForSession(w http.ResponseWriter, r *http.Request, auth authContext) {
	switch r.Method {
	case http.MethodGet:
		s.respondConfigSnapshot(w, r, auth.User.ID)
	case http.MethodPut:
		if !verifyCSRF(r, auth.Session) {
			writeError(w, http.StatusForbidden, "invalid csrf token")
			return
		}
		s.acceptConfigSnapshot(w, r, auth.User.ID, "web_session", auth.User.ID)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) handleConfigSnapshotForDevice(w http.ResponseWriter, r *http.Request, auth authContext) {
	switch r.Method {
	case http.MethodGet:
		s.respondConfigSnapshot(w, r, auth.Device.UserID)
	case http.MethodPut:
		s.acceptConfigSnapshot(w, r, auth.Device.UserID, "device", auth.Device.ID)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) handleConfigSnapshotForDesktop(w http.ResponseWriter, r *http.Request, auth authContext) {
	switch r.Method {
	case http.MethodGet:
		s.respondConfigSnapshot(w, r, auth.User.ID)
	case http.MethodPut:
		s.acceptConfigSnapshot(w, r, auth.User.ID, string(auth.Kind), auth.DesktopSession.ID)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) respondConfigSnapshot(w http.ResponseWriter, r *http.Request, userID int64) {
	snapshot, err := s.store.GetConfigSnapshot(r.Context(), userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load config failed")
		return
	}
	writeJSON(w, http.StatusOK, configSnapshotResponse{
		Revision: snapshot.Revision,
		Snapshot: snapshot.Content,
	})
}

func (s *Server) acceptConfigSnapshot(
	w http.ResponseWriter,
	r *http.Request,
	userID int64,
	actorType string,
	actorID int64,
) {
	var payload configSnapshotRequest
	if err := decodeJSON(r, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	payload.Snapshot = parseJSONMap(payload.Snapshot)

	snapshot, err := s.store.PutConfigSnapshot(
		r.Context(),
		userID,
		payload.BaseRevision,
		payload.Snapshot,
		actorType,
		actorID,
	)
	if err != nil {
		if err == store.ErrConflict {
			current, currentErr := s.store.GetConfigSnapshot(r.Context(), userID)
			if currentErr != nil {
				writeError(w, http.StatusConflict, "config revision conflict")
				return
			}
			writeJSON(w, http.StatusConflict, configSnapshotResponse{
				Revision: current.Revision,
				Snapshot: current.Content,
			})
			return
		}
		writeError(w, http.StatusInternalServerError, "save config failed")
		return
	}

	s.hub.Broadcast(userID, "config.updated", map[string]any{
		"revision": snapshot.Revision,
	})
	writeJSON(w, http.StatusOK, configSnapshotResponse{
		Revision: snapshot.Revision,
		Snapshot: snapshot.Content,
	})
}

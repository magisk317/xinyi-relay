package http

import (
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// handleConfigSnapshot serves the per-user config snapshot to whichever client
// authenticated (Web session, desktop session, or device). withConfigAuth has
// already resolved the credential; here we only branch on the kind to derive
// the audit actor and to enforce CSRF on the Web session path.
func (s *Server) handleConfigSnapshot(w http.ResponseWriter, r *http.Request, auth authContext) {
	switch r.Method {
	case http.MethodGet:
		s.respondConfigSnapshot(w, r, auth.User.ID)
	case http.MethodPut:
		if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
			writeError(w, http.StatusForbidden, "invalid csrf token")
			return
		}
		actorType, actorID := configSnapshotActor(auth)
		s.acceptConfigSnapshot(w, r, auth.User.ID, actorType, actorID)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

// configSnapshotActor maps the authenticated principal to the (actorType,
// actorID) recorded in the config audit log.
func configSnapshotActor(auth authContext) (string, int64) {
	switch auth.Kind {
	case authKindDevice:
		return "device", auth.Device.ID
	case authKindDesktop:
		return string(auth.Kind), auth.DesktopSession.ID
	case authKindSession:
		return "web_session", auth.User.ID
	default:
		// Surface any future/unknown kind by its raw value rather than
		// collapsing it into "web_session", so it stays distinguishable in
		// the audit log.
		return string(auth.Kind), auth.User.ID
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
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
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

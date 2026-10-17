package http

import (
	"net/http"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

// handleDeviceActions serves POST (queue a one-shot action) and GET (recent
// action history) on /api/v1/devices/{id}/actions.
func (s *Server) handleDeviceActions(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	switch r.Method {
	case http.MethodPost:
		s.handleQueueDeviceAction(w, r, auth, deviceID)
	case http.MethodGet:
		s.handleListDeviceActions(w, r, auth, deviceID)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) handleQueueDeviceAction(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
		writeError(w, http.StatusForbidden, "invalid csrf token")
		return
	}

	var payload deviceActionRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	actorType, actorID := configCommandActor(auth)
	item, err := s.store.CreateDeviceAction(
		r.Context(),
		auth.User.ID,
		deviceID,
		payload.Action,
		parseJSONMap(payload.Params),
		actorType,
		actorID,
	)
	if err != nil {
		switch err {
		case store.ErrNotFound:
			writeError(w, http.StatusNotFound, "device not found")
		case store.ErrUnsupportedDeviceAction:
			writeError(w, http.StatusBadRequest, "unsupported_action")
		case store.ErrInvalidDeviceActionParams:
			writeError(w, http.StatusBadRequest, "invalid_params")
		default:
			writeError(w, http.StatusInternalServerError, "queue device action failed")
		}
		return
	}

	s.hub.Broadcast(auth.User.ID, "device.action.updated", map[string]any{
		"deviceId": deviceID,
		"actionId": item.ID,
		"status":   item.Status,
	})
	writeJSON(w, http.StatusCreated, toDeviceActionItem(item))
}

func (s *Server) handleListDeviceActions(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	limit := readLimitQuery(r, 50)
	offset := readOffsetQuery(r)
	actions, err := s.store.ListDeviceActions(r.Context(), auth.User.ID, deviceID, limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load device actions failed")
		return
	}
	items := make([]deviceActionItem, 0, len(actions))
	for _, item := range actions {
		items = append(items, toDeviceActionItem(item))
	}
	writeJSON(w, http.StatusOK, deviceActionsResponse{
		Actions: items,
		Limit:   limit,
		Offset:  offset,
	})
}

// handleAgentDeviceActionAck lets the device report the one-shot execution
// result of a pending action.
func (s *Server) handleAgentDeviceActionAck(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload deviceActionAckRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	item, err := s.store.AckDeviceAction(
		r.Context(),
		auth.Device.UserID,
		auth.Device.ID,
		payload.ActionID,
		payload.Status,
		payload.FailureReason,
	)
	if err != nil {
		switch err {
		case store.ErrNotFound:
			writeError(w, http.StatusNotFound, "device action not found")
		case store.ErrConflict:
			writeError(w, http.StatusConflict, "device action ack conflict")
		case store.ErrUnsupportedDeviceActionStatus:
			writeError(w, http.StatusBadRequest, "unsupported action status")
		default:
			writeError(w, http.StatusInternalServerError, "ack device action failed")
		}
		return
	}

	s.hub.Broadcast(auth.Device.UserID, "device.action.updated", map[string]any{
		"deviceId": auth.Device.ID,
		"actionId": item.ID,
		"status":   item.Status,
	})
	writeJSON(w, http.StatusOK, toDeviceActionItem(item))
}

func toDeviceActionItem(item store.DeviceAction) deviceActionItem {
	return deviceActionItem{
		ID:            item.ID,
		Action:        item.Action,
		Params:        item.Params,
		Status:        item.Status,
		FailureReason: item.FailureReason,
		ActorType:     item.ActorType,
		ActorID:       item.ActorID,
		CreatedAt:     item.CreatedAt,
	}
}

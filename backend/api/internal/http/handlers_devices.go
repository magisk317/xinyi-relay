package http

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/security"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) handleCreateBindCode(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
		writeError(w, http.StatusForbidden, "invalid csrf token")
		return
	}

	code, codeHash, err := security.NewBindCode()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "bind code generation failed")
		return
	}
	expiresAt := time.Now().Add(10 * time.Minute)
	if _, err := s.store.CreateBindCode(r.Context(), auth.User.ID, codeHash, expiresAt); err != nil {
		writeError(w, http.StatusInternalServerError, "create bind code failed")
		return
	}

	writeJSON(w, http.StatusCreated, bindCodeResponse{
		Code:      code,
		ExpiresAt: expiresAt,
	})
}

func (s *Server) handleAgentRegister(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload agentRegisterRequest
	if err := decodeJSON(w, r, &payload, maxDeviceBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}
	payload.BindCode = strings.TrimSpace(payload.BindCode)
	payload.DeviceName = strings.TrimSpace(payload.DeviceName)
	if payload.BindCode == "" || payload.DeviceName == "" {
		writeError(w, http.StatusBadRequest, "bindCode and deviceName are required")
		return
	}
	if payload.Platform == "" {
		payload.Platform = "android"
	}

	bindCode, err := s.store.ConsumeBindCode(r.Context(), security.HashToken(payload.BindCode))
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusUnauthorized, "invalid or expired bind code")
			return
		}
		writeError(w, http.StatusInternalServerError, "consume bind code failed")
		return
	}

	deviceToken, deviceTokenHash, err := security.NewOpaqueToken()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "device token generation failed")
		return
	}
	device, err := s.store.CreateDevice(
		r.Context(),
		bindCode.UserID,
		payload.DeviceName,
		payload.DeviceModel,
		payload.Platform,
		payload.AppVersion,
		deviceTokenHash,
	)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "create device failed")
		return
	}

	s.hub.Broadcast(bindCode.UserID, "device.registered", map[string]any{
		"deviceId":    device.ID,
		"displayName": device.DisplayName,
		"deviceName":  device.DeviceName,
	})

	writeJSON(w, http.StatusCreated, agentRegisterResponse{
		UserID:      bindCode.UserID,
		DeviceID:    device.ID,
		DeviceToken: deviceToken,
	})
}

func (s *Server) handleAgentHeartbeat(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload heartbeatRequest
	if err := decodeJSON(w, r, &payload, maxDeviceBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	addresses, _ := json.Marshal(payload.LocalAddresses)
	capabilities := parseJSONMap(payload.Capabilities)
	if err := s.store.UpdateDeviceHeartbeat(r.Context(), auth.Device.ID, payload.AppVersion, addresses, capabilities); err != nil {
		writeError(w, http.StatusInternalServerError, "update heartbeat failed")
		return
	}

	s.hub.Broadcast(auth.Device.UserID, "device.heartbeat", map[string]any{
		"deviceId":       auth.Device.ID,
		"appVersion":     payload.AppVersion,
		"localAddresses": payload.LocalAddresses,
	})

	writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
}

func (s *Server) handleDevices(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	devices, err := s.store.ListDevicesByUser(r.Context(), auth.User.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "list devices failed")
		return
	}
	filtered := make([]store.Device, 0, len(devices))
	for _, device := range devices {
		if device.RevokedAt != nil {
			continue
		}
		filtered = append(filtered, device)
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"devices": filtered,
	})
}

func (s *Server) handleDeviceByID(w http.ResponseWriter, r *http.Request, auth authContext) {
	switch {
	case r.Method == http.MethodPatch:
		deviceID, err := pathID(r.URL.Path, "/api/v1/devices/", "")
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid device id")
			return
		}
		if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
			writeError(w, http.StatusForbidden, "invalid csrf token")
			return
		}
		var payload patchDeviceRequest
		if err := decodeJSON(w, r, &payload, maxDeviceBodyBytes); err != nil {
			writeDecodeError(w, err)
			return
		}
		device, err := s.store.PatchDevice(r.Context(), auth.User.ID, deviceID, payload.DisplayName, payload.Enabled)
		if err != nil {
			if err == store.ErrNotFound {
				writeError(w, http.StatusNotFound, "device not found")
				return
			}
			writeError(w, http.StatusInternalServerError, "patch device failed")
			return
		}
		s.hub.Broadcast(auth.User.ID, "device.updated", map[string]any{
			"deviceId":    device.ID,
			"displayName": device.DisplayName,
			"enabled":     device.Enabled,
		})
		writeJSON(w, http.StatusOK, device)
	case r.Method == http.MethodPost && strings.HasSuffix(r.URL.Path, "/revoke"):
		deviceID, err := pathID(r.URL.Path, "/api/v1/devices/", "/revoke")
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid device id")
			return
		}
		if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
			writeError(w, http.StatusForbidden, "invalid csrf token")
			return
		}
		if err := s.store.RevokeDevice(r.Context(), auth.User.ID, deviceID); err != nil {
			if err == store.ErrNotFound {
				writeError(w, http.StatusNotFound, "device not found")
				return
			}
			writeError(w, http.StatusInternalServerError, "revoke device failed")
			return
		}
		s.hub.Broadcast(auth.User.ID, "device.revoked", map[string]any{
			"deviceId": deviceID,
		})
		writeJSON(w, http.StatusOK, simpleOKResponse{OK: true})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

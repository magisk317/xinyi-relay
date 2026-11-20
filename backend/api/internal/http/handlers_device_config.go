package http

import (
	"encoding/json"
	"net/http"
	"regexp"
	"strconv"
	"strings"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

var allowedDeviceConfigMutationOperations = map[string]struct{}{
	"replace_senders":         {},
	"replace_device_apps":     {},
	"replace_scheduled_tasks": {},
}

func isValidCronExpression(expression string) bool {
	fields := strings.Fields(expression)
	if len(fields) != 5 {
		return false
	}
	limits := [][2]int{{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 7}}
	for index, field := range fields {
		allowQuestion := index == 2 || index == 4
		if !isValidCronField(field, limits[index][0], limits[index][1], allowQuestion) {
			return false
		}
	}
	return true
}

func isValidCronField(field string, min, max int, allowQuestion bool) bool {
	if field == "*" || (allowQuestion && field == "?") {
		return true
	}
	if strings.TrimSpace(field) != field || field == "" {
		return false
	}
	for _, part := range strings.Split(field, ",") {
		if part == "" {
			return false
		}
		rangeAndStep := strings.SplitN(part, "/", 2)
		rangePart := rangeAndStep[0]
		if len(rangeAndStep) == 2 {
			step, err := strconv.Atoi(rangeAndStep[1])
			if err != nil || step <= 0 {
				return false
			}
		}
		start, end := min, max
		switch {
		case rangePart == "*":
		case strings.Contains(rangePart, "-"):
			bounds := strings.SplitN(rangePart, "-", 2)
			if len(bounds) != 2 || bounds[0] == "" || bounds[1] == "" {
				return false
			}
			var err error
			start, err = strconv.Atoi(bounds[0])
			if err != nil {
				return false
			}
			end, err = strconv.Atoi(bounds[1])
			if err != nil {
				return false
			}
		default:
			var err error
			start, err = strconv.Atoi(rangePart)
			if err != nil {
				return false
			}
			end = start
		}
		if start < min || end > max || start > end {
			return false
		}
	}
	return true
}

var uuidPattern = regexp.MustCompile(`(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

func (s *Server) handleDeviceConfig(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	state, err := s.store.GetDeviceConfigState(r.Context(), auth.User.ID, deviceID)
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "device config not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "load device config failed")
		return
	}
	writeJSON(w, http.StatusOK, toDeviceConfigStateResponse(state))
}

func (s *Server) handleDeviceConfigCommands(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if auth.Kind == authKindSession && !verifyCSRF(r, auth.Session) {
		writeError(w, http.StatusForbidden, "invalid csrf token")
		return
	}

	var payload deviceConfigCommandRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}
	mutation := parseJSONMap(payload.Mutation)
	if !isSupportedDeviceConfigMutation(mutation) {
		writeError(w, http.StatusBadRequest, "unsupported_config_mutation")
		return
	}
	actorType, actorID := configCommandActor(auth)
	command, err := s.store.CreateDeviceConfigCommand(
		r.Context(),
		auth.User.ID,
		deviceID,
		payload.BaseRevision,
		mutation,
		payload.Summary,
		actorType,
		actorID,
	)
	if err != nil {
		switch err {
		case store.ErrNotFound:
			writeError(w, http.StatusNotFound, "device not found")
		case store.ErrStaleBaseRevision:
			writeError(w, http.StatusConflict, "stale_base_revision")
		case store.ErrDeviceOffline:
			writeError(w, http.StatusConflict, "device_offline")
		case store.ErrDeviceCapabilityMissing:
			writeError(w, http.StatusConflict, "device_capability_required")
		default:
			writeError(w, http.StatusInternalServerError, "queue device config command failed")
		}
		return
	}

	s.hub.Broadcast(auth.User.ID, "device.config.command.updated", map[string]any{
		"deviceId":  deviceID,
		"commandId": command.ID,
		"status":    command.Status,
	})
	writeJSON(w, http.StatusCreated, toDeviceConfigCommandItem(command))
}

func (s *Server) handleDeviceConfigAuditLogs(w http.ResponseWriter, r *http.Request, auth authContext, deviceID int64) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	limit := readLimitQuery(r, 50)
	offset := readOffsetQuery(r)
	logs, err := s.store.ListDeviceConfigAuditLogs(r.Context(), auth.User.ID, deviceID, limit, offset)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load device config audit logs failed")
		return
	}
	items := make([]deviceConfigAuditLogItem, 0, len(logs))
	for _, item := range logs {
		items = append(items, deviceConfigAuditLogItem{
			ID:        item.ID,
			DeviceID:  item.DeviceID,
			CommandID: item.CommandID,
			Revision:  item.Revision,
			EventType: item.EventType,
			ActorType: item.ActorType,
			ActorID:   item.ActorID,
			Summary:   item.Summary,
			CreatedAt: item.CreatedAt,
		})
	}
	writeJSON(w, http.StatusOK, deviceConfigAuditLogsResponse{
		Logs:   items,
		Limit:  limit,
		Offset: offset,
	})
}

func (s *Server) handleAgentConfigMirror(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload agentConfigMirrorRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	if _, err := s.store.UpsertDeviceConfigMirror(
		r.Context(),
		auth.Device.UserID,
		auth.Device.ID,
		payload.LocalRevision,
		parseJSONMap(payload.MirrorContent),
		"device",
		auth.Device.ID,
		payload.Summary,
	); err != nil {
		switch err {
		case store.ErrNotFound:
			writeError(w, http.StatusNotFound, "device config mirror not found")
		case store.ErrOutdatedMirrorRevision:
			writeError(w, http.StatusConflict, "outdated_mirror_revision")
		default:
			writeError(w, http.StatusInternalServerError, "update device config mirror failed")
		}
		return
	}

	state, err := s.store.GetDeviceConfigState(r.Context(), auth.Device.UserID, auth.Device.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "reload device config mirror failed")
		return
	}

	s.hub.Broadcast(auth.Device.UserID, "device.config.updated", map[string]any{
		"deviceId": auth.Device.ID,
		"revision": state.Revision,
	})
	writeJSON(w, http.StatusOK, toDeviceConfigStateResponse(state))
}

func (s *Server) handleAgentConfigCommandsPull(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload agentConfigCommandsPullRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	state, err := s.store.GetDeviceConfigState(r.Context(), auth.Device.UserID, auth.Device.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load device config commands failed")
		return
	}

	pendingActions, err := s.store.ListPendingDeviceActions(r.Context(), auth.Device.UserID, auth.Device.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "load device actions failed")
		return
	}

	response := toAgentConfigCommandsPullResponse(state, pendingActions)
	if payload.LocalRevision > 0 && payload.LocalRevision >= state.Revision {
		response.MirrorContent = parseJSONMap(nil)
	}
	writeJSON(w, http.StatusOK, response)
}

func (s *Server) handleAgentConfigCommandsAck(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload agentConfigCommandsAckRequest
	if err := decodeJSON(w, r, &payload, maxConfigBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	command, err := s.store.AckDeviceConfigCommand(
		r.Context(),
		auth.Device.UserID,
		auth.Device.ID,
		payload.CommandID,
		payload.AppliedRevision,
		normalizeAckStatus(payload.Status),
		payload.FailureReason,
		parseJSONMap(payload.MirrorContent),
		"device",
		auth.Device.ID,
	)
	if err != nil {
		switch err {
		case store.ErrNotFound:
			writeError(w, http.StatusNotFound, "device config command not found")
		case store.ErrConflict, store.ErrOutdatedMirrorRevision:
			writeError(w, http.StatusConflict, "device config command ack conflict")
		case store.ErrUnsupportedCommandStatus:
			writeError(w, http.StatusBadRequest, "unsupported command status")
		default:
			writeError(w, http.StatusInternalServerError, "ack device config command failed")
		}
		return
	}

	if command.Status == store.DeviceConfigCommandStatusApplied {
		s.hub.Broadcast(auth.Device.UserID, "device.config.updated", map[string]any{
			"deviceId": auth.Device.ID,
			"revision": command.TargetRevision,
		})
	}
	s.hub.Broadcast(auth.Device.UserID, "device.config.command.updated", map[string]any{
		"deviceId":  auth.Device.ID,
		"commandId": command.ID,
		"status":    command.Status,
	})
	writeJSON(w, http.StatusOK, toDeviceConfigCommandItem(command))
}

func toDeviceConfigStateResponse(state store.DeviceConfigState) deviceConfigStateResponse {
	items := make([]deviceConfigCommandItem, 0, len(state.PendingCommands))
	for _, command := range state.PendingCommands {
		items = append(items, toDeviceConfigCommandItem(command))
	}
	return deviceConfigStateResponse{
		DeviceID:        state.DeviceID,
		Revision:        state.Revision,
		MirrorContent:   state.Snapshot,
		PendingCommands: items,
		UpdatedAt:       state.UpdatedAt,
	}
}

func toAgentConfigCommandsPullResponse(state store.DeviceConfigState, pendingActions []store.DeviceAction) agentConfigCommandsPullResponse {
	items := make([]deviceConfigCommandItem, 0, len(state.PendingCommands))
	for _, command := range state.PendingCommands {
		items = append(items, toDeviceConfigCommandItem(command))
	}
	actionItems := make([]deviceActionItem, 0, len(pendingActions))
	for _, action := range pendingActions {
		actionItems = append(actionItems, toDeviceActionItem(action))
	}
	return agentConfigCommandsPullResponse{
		DeviceID:        state.DeviceID,
		Revision:        state.Revision,
		MirrorContent:   state.Snapshot,
		PendingCommands: items,
		PendingActions:  actionItems,
		UpdatedAt:       state.UpdatedAt,
	}
}

func toDeviceConfigCommandItem(command store.DeviceConfigCommand) deviceConfigCommandItem {
	return deviceConfigCommandItem{
		ID:             command.ID,
		BaseRevision:   command.BaseRevision,
		TargetRevision: command.TargetRevision,
		Mutation:       command.Mutation,
		Summary:        command.Summary,
		ActorType:      command.ActorType,
		ActorID:        command.ActorID,
		Status:         command.Status,
		FailureReason:  command.FailureReason,
		CreatedAt:      command.CreatedAt,
		UpdatedAt:      command.UpdatedAt,
		AppliedAt:      command.AppliedAt,
	}
}

func normalizeAckStatus(status string) string {
	switch strings.TrimSpace(status) {
	case "", store.DeviceConfigCommandStatusApplied:
		return store.DeviceConfigCommandStatusApplied
	case store.DeviceConfigCommandStatusFailed:
		return store.DeviceConfigCommandStatusFailed
	default:
		return strings.TrimSpace(status)
	}
}

func isSupportedDeviceConfigMutation(mutation json.RawMessage) bool {
	var payload struct {
		Operations []struct {
			Type  string          `json:"type"`
			Tasks json.RawMessage `json:"tasks"`
		} `json:"operations"`
	}
	if err := json.Unmarshal(mutation, &payload); err != nil {
		return false
	}
	if len(payload.Operations) == 0 {
		return false
	}
	for _, operation := range payload.Operations {
		if _, ok := allowedDeviceConfigMutationOperations[operation.Type]; !ok {
			return false
		}
		if operation.Type == "replace_scheduled_tasks" && !isValidScheduledTaskMutation(operation.Tasks) {
			return false
		}
	}
	return true
}

func isValidScheduledTaskMutation(tasksJSON json.RawMessage) bool {
	var tasks []struct {
		SyncID                 string  `json:"syncId"`
		ConflictID             *string `json:"conflictId"`
		ConflictSide           string  `json:"conflictSide"`
		ConflictPreviousStatus *int    `json:"conflictPreviousStatus"`
		TaskType               string  `json:"taskType"`
		CronExpression         string  `json:"cronExpression"`
		SimSlot                int     `json:"simSlot"`
		Mobiles                string  `json:"mobiles"`
		Content                string  `json:"content"`
		Status                 int     `json:"status"`
	}
	if len(tasksJSON) == 0 || string(tasksJSON) == "null" || json.Unmarshal(tasksJSON, &tasks) != nil {
		return false
	}
	for _, task := range tasks {
		if !isUUID(task.SyncID) || task.TaskType != "sms" || task.SimSlot < 0 || task.SimSlot > 2 ||
			(task.Status != 0 && task.Status != 1) || !isValidCronExpression(task.CronExpression) {
			return false
		}
		if task.ConflictID == nil || *task.ConflictID == "" {
			if task.ConflictSide != "" || task.ConflictPreviousStatus != nil {
				return false
			}
		} else if !isUUID(*task.ConflictID) || (task.ConflictSide != "local" && task.ConflictSide != "remote") ||
			(task.ConflictPreviousStatus == nil || (*task.ConflictPreviousStatus != 0 && *task.ConflictPreviousStatus != 1)) || task.Status != 0 {
			return false
		}
	}
	return true
}

func isUUID(value string) bool {
	return uuidPattern.MatchString(value)
}

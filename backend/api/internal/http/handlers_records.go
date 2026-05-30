package http

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

func (s *Server) handleAgentRecordsBatch(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var payload relayRecordsBatchRequest
	if err := decodeJSON(w, r, &payload, maxRecordsBodyBytes); err != nil {
		writeDecodeError(w, err)
		return
	}

	records := make([]store.RelayRecord, 0, len(payload.Records))
	for _, item := range payload.Records {
		records = append(records, store.RelayRecord{
			EventID:    item.EventID,
			RecordType: item.RecordType,
			Sender:     item.Sender,
			Body:       item.Body,
			SmsCode:    item.SmsCode,
			Package:    item.Package,
			MsgType:    item.MsgType,
			CallType:   item.CallType,
			OccurredAt: item.OccurredAt,
			Metadata:   parseJSONMap(item.Metadata),
		})
	}

	inserted, err := s.store.InsertRelayRecords(r.Context(), auth.Device.UserID, auth.Device.ID, records)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "insert records failed")
		return
	}

	// Best-effort retention: trim the user's records after ingest. A pruning
	// failure must not fail the upload, so it is logged and ignored.
	if _, err := s.store.PruneRelayRecords(r.Context(), auth.Device.UserID, s.recordsRetention(r.Context(), auth.Device.UserID)); err != nil {
		log.Printf("[records] prune for user %d failed: %v", auth.Device.UserID, err)
	}

	s.hub.Broadcast(auth.Device.UserID, "records.ingested", map[string]any{
		"deviceId": auth.Device.ID,
		"inserted": inserted,
	})
	writeJSON(w, http.StatusOK, relayRecordsBatchResponse{Inserted: inserted})
}

// recordsRetention builds the retention policy for a user from server config,
// optionally following the per-type history limits the device already syncs in
// its config snapshot.
func (s *Server) recordsRetention(ctx context.Context, userID int64) store.RecordsRetention {
	retention := store.RecordsRetention{
		MaxPerUser: s.cfg.RecordsMaxPerUser,
	}
	if s.cfg.RecordsRetentionDays > 0 {
		retention.MaxAge = time.Duration(s.cfg.RecordsRetentionDays) * 24 * time.Hour
	}
	if s.cfg.RecordsFollowDeviceLimits {
		retention.PerType = s.deviceHistoryLimits(ctx, userID)
	}
	return retention
}

// deviceHistoryLimits reads the per-type history limits from the user's synced
// config snapshot and maps them to relay_records.record_type values. A
// missing/unparseable/non-positive limit is treated as "unlimited" (matching
// the device's own semantics) and omitted from the result.
func (s *Server) deviceHistoryLimits(ctx context.Context, userID int64) map[string]int {
	snapshot, err := s.store.GetConfigSnapshot(ctx, userID)
	if err != nil || len(snapshot.Content) == 0 {
		return nil
	}

	var parsed struct {
		Records struct {
			CodeHistoryLimit       string `json:"codeHistoryLimit"`
			PlainSmsHistoryLimit   string `json:"plainSmsHistoryLimit"`
			AppNotifyHistoryLimit  string `json:"appNotifyHistoryLimit"`
			CallNotifyHistoryLimit string `json:"callNotifyHistoryLimit"`
		} `json:"records"`
	}
	if err := json.Unmarshal(snapshot.Content, &parsed); err != nil {
		return nil
	}

	limits := make(map[string]int, 4)
	addHistoryLimit(limits, "sms_code", parsed.Records.CodeHistoryLimit)
	addHistoryLimit(limits, "sms_plain", parsed.Records.PlainSmsHistoryLimit)
	addHistoryLimit(limits, "app_notify", parsed.Records.AppNotifyHistoryLimit)
	addHistoryLimit(limits, "call", parsed.Records.CallNotifyHistoryLimit)
	if len(limits) == 0 {
		return nil
	}
	return limits
}

func addHistoryLimit(limits map[string]int, recordType string, raw string) {
	value, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || value <= 0 {
		return
	}
	limits[recordType] = value
}

func (s *Server) handleRecords(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	limit := readLimitQuery(r, 50)
	offset := readOffsetQuery(r)
	deviceID, err := readOptionalInt64Query(r, "device_id")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid device_id")
		return
	}

	records, err := s.store.ListRelayRecords(r.Context(), auth.User.ID, limit, offset, deviceID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "list records failed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"records": records,
		"limit":   limit,
		"offset":  offset,
	})
}

func (s *Server) handleRecordByID(w http.ResponseWriter, r *http.Request, auth authContext) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	recordID, err := pathID(r.URL.Path, "/api/v1/records/", "")
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid record id")
		return
	}

	record, err := s.store.GetRelayRecord(r.Context(), auth.User.ID, recordID)
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "record not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "load record failed")
		return
	}
	writeJSON(w, http.StatusOK, record)
}

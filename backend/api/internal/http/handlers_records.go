package http

import (
	"net/http"

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

	s.hub.Broadcast(auth.Device.UserID, "records.ingested", map[string]any{
		"deviceId": auth.Device.ID,
		"inserted": inserted,
	})
	writeJSON(w, http.StatusOK, relayRecordsBatchResponse{Inserted: inserted})
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

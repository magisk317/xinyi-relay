package http

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type recordSyncCountingStore struct {
	panicStore
	calls int
}

func (s *recordSyncCountingStore) SyncRelayRecords(
	context.Context,
	int64,
	int64,
	[]store.RelayRecord,
	bool,
) (store.RelayRecordSyncResult, error) {
	s.calls++
	return store.RelayRecordSyncResult{}, nil
}

func TestAddHistoryLimit(t *testing.T) {
	cases := []struct {
		name string
		raw  string
		want int // 0 means "not added"
	}{
		{"positive", "50", 50},
		{"zero is unlimited", "0", 0},
		{"negative is unlimited", "-5", 0},
		{"blank", "", 0},
		{"whitespace", "  10 ", 10},
		{"unparseable", "abc", 0},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			limits := map[string]int{}
			addHistoryLimit(limits, "sms_code", tc.raw)
			got, ok := limits["sms_code"]
			if tc.want == 0 {
				if ok {
					t.Fatalf("expected no entry, got %d", got)
				}
				return
			}
			if !ok || got != tc.want {
				t.Fatalf("expected %d, got %d (ok=%v)", tc.want, got, ok)
			}
		})
	}
}

// recordsRetention maps server config into the retention policy. When
// follow-device limits are disabled, no snapshot lookup happens, so a nil store
// is safe and only the global caps are populated.
func TestRecordsRetentionGlobalCapsOnly(t *testing.T) {
	s := &Server{cfg: config.Config{
		RecordsFollowDeviceLimits: false,
		RecordsMaxPerUser:         2000,
		RecordsRetentionDays:      30,
	}}

	retention := s.recordsRetention(nil, 1, 1)

	if retention.MaxPerUser != 2000 {
		t.Fatalf("expected MaxPerUser 2000, got %d", retention.MaxPerUser)
	}
	if retention.MaxAge != 30*24*time.Hour {
		t.Fatalf("expected MaxAge 30d, got %s", retention.MaxAge)
	}
	if retention.PerType != nil {
		t.Fatalf("expected no per-type limits when follow-device disabled, got %v", retention.PerType)
	}
}

func TestRecordsRetentionDisabledDays(t *testing.T) {
	s := &Server{cfg: config.Config{RecordsRetentionDays: 0}}
	if got := s.recordsRetention(nil, 1, 1).MaxAge; got != 0 {
		t.Fatalf("expected MaxAge 0 when retention days disabled, got %s", got)
	}
}

func TestRecordsSnapshotOverLimitIsRejectedBeforeStoreMutation(t *testing.T) {
	fake := &recordSyncCountingStore{}
	s := &Server{store: fake}
	records := make([]map[string]any, maxRecordsPerBatch+1)
	for index := range records {
		records[index] = map[string]any{
			"eventId":    strconv.Itoa(index),
			"recordType": "sms_plain",
			"occurredAt": "2026-07-16T00:00:00Z",
		}
	}
	body, err := json.Marshal(map[string]any{"records": records, "replaceExisting": true})
	if err != nil {
		t.Fatalf("marshal request: %v", err)
	}
	req := httptest.NewRequest(http.MethodPost, "/api/v1/agent/records:batch", bytes.NewReader(body))
	rec := httptest.NewRecorder()

	s.handleAgentRecordsBatch(rec, req, authContext{Device: store.Device{ID: 2, UserID: 1}})

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d: %s", rec.Code, rec.Body.String())
	}
	if fake.calls != 0 {
		t.Fatalf("oversized replacement reached the store %d times", fake.calls)
	}
}

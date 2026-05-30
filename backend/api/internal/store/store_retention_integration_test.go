package store

import (
	"context"
	"fmt"
	"os"
	"testing"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	"github.com/magisk317/xinyi-relay/backend/api/internal/database"
)

// openTestStore connects to the integration database, runs migrations, and
// returns a Store. It skips the test when RELAY_TEST_DATABASE_URL is unset, so
// the default CI run (which has no database) is unaffected.
func openTestStore(t *testing.T) *Store {
	t.Helper()
	dsn := os.Getenv("RELAY_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("RELAY_TEST_DATABASE_URL not set; skipping postgres integration test")
	}
	ctx := context.Background()
	db, err := database.Open(ctx, config.Config{DatabaseURL: dsn})
	if err != nil {
		t.Fatalf("open database: %v", err)
	}
	t.Cleanup(db.Close)
	return New(db)
}

// seedRecords inserts count records of the given type for a fresh user/device,
// spacing occurred_at so the most recent record has the largest timestamp.
func seedRecords(t *testing.T, s *Store, recordType string, count int) (int64, int64) {
	t.Helper()
	ctx := context.Background()

	username := fmt.Sprintf("retention_%s_%d", recordType, time.Now().UnixNano())
	user, err := s.CreateUser(ctx, username, "hash")
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	device, err := s.CreateDevice(ctx, user.ID, "dev", "model", "android", "1.0", username+"_token")
	if err != nil {
		t.Fatalf("create device: %v", err)
	}

	base := time.Now().Add(-time.Duration(count) * time.Hour).Truncate(time.Second)
	records := make([]RelayRecord, 0, count)
	for i := 0; i < count; i++ {
		records = append(records, RelayRecord{
			RecordType: recordType,
			Sender:     "sender",
			Body:       fmt.Sprintf("body-%d", i),
			OccurredAt: base.Add(time.Duration(i) * time.Hour),
			Metadata:   []byte("{}"),
		})
	}
	if _, err := s.InsertRelayRecords(ctx, user.ID, device.ID, records); err != nil {
		t.Fatalf("insert records: %v", err)
	}
	return user.ID, device.ID
}

func countRecords(t *testing.T, s *Store, userID int64) int {
	t.Helper()
	var n int
	if err := s.db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM relay_records WHERE user_id = $1`, userID).Scan(&n); err != nil {
		t.Fatalf("count: %v", err)
	}
	return n
}

func TestPruneRelayRecordsPerType(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	userID, _ := seedRecords(t, s, "sms_code", 10)

	deleted, err := s.PruneRelayRecords(ctx, userID, RecordsRetention{
		PerType: map[string]int{"sms_code": 3},
	})
	if err != nil {
		t.Fatalf("prune: %v", err)
	}
	if deleted != 7 {
		t.Fatalf("expected 7 deleted, got %d", deleted)
	}
	if got := countRecords(t, s, userID); got != 3 {
		t.Fatalf("expected 3 remaining, got %d", got)
	}

	// The kept rows must be the most recent ones.
	rows, err := s.ListRelayRecords(ctx, userID, 100, 0, nil)
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(rows) != 3 {
		t.Fatalf("expected 3 listed, got %d", len(rows))
	}
	if rows[0].Body != "body-9" || rows[2].Body != "body-7" {
		t.Fatalf("expected newest body-9..body-7 retained, got %s..%s", rows[0].Body, rows[2].Body)
	}
}

func TestPruneRelayRecordsPerTypeIsScopedByType(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	// Two record types under one user; a limit on one must not touch the other.
	username := fmt.Sprintf("retention_mixed_%d", time.Now().UnixNano())
	user, err := s.CreateUser(ctx, username, "hash")
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	device, err := s.CreateDevice(ctx, user.ID, "dev", "model", "android", "1.0", username+"_token")
	if err != nil {
		t.Fatalf("create device: %v", err)
	}

	base := time.Now().Add(-100 * time.Hour).Truncate(time.Second)
	records := make([]RelayRecord, 0, 8)
	for i := 0; i < 5; i++ {
		records = append(records, RelayRecord{RecordType: "sms_code", Body: fmt.Sprintf("c-%d", i), OccurredAt: base.Add(time.Duration(i) * time.Hour), Metadata: []byte("{}")})
	}
	for i := 0; i < 3; i++ {
		records = append(records, RelayRecord{RecordType: "call", Body: fmt.Sprintf("k-%d", i), OccurredAt: base.Add(time.Duration(i) * time.Hour), Metadata: []byte("{}")})
	}
	if _, err := s.InsertRelayRecords(ctx, user.ID, device.ID, records); err != nil {
		t.Fatalf("insert: %v", err)
	}

	if _, err := s.PruneRelayRecords(ctx, user.ID, RecordsRetention{PerType: map[string]int{"sms_code": 2}}); err != nil {
		t.Fatalf("prune: %v", err)
	}
	// 2 sms_code + 3 call (untouched) = 5
	if got := countRecords(t, s, user.ID); got != 5 {
		t.Fatalf("expected 5 remaining, got %d", got)
	}
}

func TestPruneRelayRecordsMaxPerUser(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	userID, _ := seedRecords(t, s, "app_notify", 6)

	deleted, err := s.PruneRelayRecords(ctx, userID, RecordsRetention{MaxPerUser: 4})
	if err != nil {
		t.Fatalf("prune: %v", err)
	}
	if deleted != 2 {
		t.Fatalf("expected 2 deleted, got %d", deleted)
	}
	if got := countRecords(t, s, userID); got != 4 {
		t.Fatalf("expected 4 remaining, got %d", got)
	}
}

func TestPruneRelayRecordsMaxAge(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	// seedRecords spaces rows one hour apart ending near now; with 5 rows the
	// oldest is ~5h old. A 3h max-age must drop the oldest rows only.
	userID, _ := seedRecords(t, s, "sms_plain", 5)

	deleted, err := s.PruneRelayRecords(ctx, userID, RecordsRetention{MaxAge: 3 * time.Hour})
	if err != nil {
		t.Fatalf("prune: %v", err)
	}
	if deleted == 0 {
		t.Fatal("expected age-based pruning to delete the oldest rows")
	}
	remaining := countRecords(t, s, userID)
	if remaining == 0 || remaining == 5 {
		t.Fatalf("expected partial age-based pruning, got %d remaining", remaining)
	}
}

func TestPruneRelayRecordsNoopDoesNothing(t *testing.T) {
	s := openTestStore(t)
	ctx := context.Background()

	userID, _ := seedRecords(t, s, "sms_code", 4)

	deleted, err := s.PruneRelayRecords(ctx, userID, RecordsRetention{
		PerType:    map[string]int{"sms_code": 0},
		MaxPerUser: 0,
		MaxAge:     0,
	})
	if err != nil {
		t.Fatalf("prune: %v", err)
	}
	if deleted != 0 {
		t.Fatalf("expected no deletions, got %d", deleted)
	}
	if got := countRecords(t, s, userID); got != 4 {
		t.Fatalf("expected 4 remaining, got %d", got)
	}
}

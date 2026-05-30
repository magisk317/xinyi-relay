package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
)

type RelayRecord struct {
	ID         int64           `json:"id"`
	DeviceID   int64           `json:"deviceId"`
	EventID    string          `json:"eventId,omitempty"`
	RecordType string          `json:"recordType"`
	Sender     string          `json:"sender"`
	Body       string          `json:"body"`
	SmsCode    string          `json:"smsCode"`
	Package    string          `json:"packageName"`
	MsgType    int             `json:"msgType"`
	CallType   int             `json:"callType"`
	OccurredAt time.Time       `json:"occurredAt"`
	UploadedAt time.Time       `json:"uploadedAt"`
	Metadata   json.RawMessage `json:"metadata"`
}

func (s *Store) InsertRelayRecords(ctx context.Context, userID int64, deviceID int64, records []RelayRecord) (int64, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return 0, err
	}
	defer tx.Rollback(ctx)

	var inserted int64
	for _, record := range records {
		commandTag, err := tx.Exec(
			ctx,
			`INSERT INTO relay_records (
				user_id, device_id, event_id, record_type, sender, body, sms_code, package_name, msg_type, call_type, occurred_at, metadata
			 ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
			 ON CONFLICT (device_id, event_id) WHERE event_id IS NOT NULL DO UPDATE SET
			     record_type = EXCLUDED.record_type,
			     sender = EXCLUDED.sender,
			     body = EXCLUDED.body,
			     sms_code = EXCLUDED.sms_code,
			     package_name = EXCLUDED.package_name,
			     msg_type = EXCLUDED.msg_type,
			     call_type = EXCLUDED.call_type,
			     occurred_at = EXCLUDED.occurred_at,
			     metadata = EXCLUDED.metadata,
			     uploaded_at = NOW()`,
			userID,
			deviceID,
			nullableString(record.EventID),
			record.RecordType,
			record.Sender,
			record.Body,
			record.SmsCode,
			record.Package,
			record.MsgType,
			record.CallType,
			record.OccurredAt,
			record.Metadata,
		)
		if err != nil {
			return 0, err
		}
		inserted += commandTag.RowsAffected()
	}

	if err := tx.Commit(ctx); err != nil {
		return 0, err
	}
	return inserted, nil
}

func (s *Store) ListRelayRecords(ctx context.Context, userID int64, limit int32, offset int32, deviceID *int64) ([]RelayRecord, error) {
	query := `SELECT id, device_id, COALESCE(event_id, ''), record_type, sender, body, sms_code, package_name, msg_type, call_type, occurred_at, uploaded_at, metadata
	            FROM relay_records
	           WHERE user_id = $1`
	args := []any{userID}
	if deviceID != nil {
		query += ` AND device_id = $2`
		args = append(args, *deviceID)
	}
	query += ` ORDER BY occurred_at DESC LIMIT $` + fmt.Sprint(len(args)+1) + ` OFFSET $` + fmt.Sprint(len(args)+2)
	args = append(args, limit, offset)

	rows, err := s.db.Pool.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var result []RelayRecord
	for rows.Next() {
		var record RelayRecord
		if err := rows.Scan(
			&record.ID,
			&record.DeviceID,
			&record.EventID,
			&record.RecordType,
			&record.Sender,
			&record.Body,
			&record.SmsCode,
			&record.Package,
			&record.MsgType,
			&record.CallType,
			&record.OccurredAt,
			&record.UploadedAt,
			&record.Metadata,
		); err != nil {
			return nil, err
		}
		result = append(result, record)
	}
	return result, rows.Err()
}

func (s *Store) GetRelayRecord(ctx context.Context, userID int64, recordID int64) (RelayRecord, error) {
	var record RelayRecord
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT id, device_id, COALESCE(event_id, ''), record_type, sender, body, sms_code, package_name, msg_type, call_type, occurred_at, uploaded_at, metadata
		   FROM relay_records
		  WHERE user_id = $1 AND id = $2`,
		userID,
		recordID,
	).Scan(
		&record.ID,
		&record.DeviceID,
		&record.EventID,
		&record.RecordType,
		&record.Sender,
		&record.Body,
		&record.SmsCode,
		&record.Package,
		&record.MsgType,
		&record.CallType,
		&record.OccurredAt,
		&record.UploadedAt,
		&record.Metadata,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return RelayRecord{}, ErrNotFound
	}
	return record, err
}

// RecordsRetention describes how relay_records should be trimmed for a single
// user. All limits are independent and each is skipped when non-positive.
type RecordsRetention struct {
	// PerType maps a record_type ("sms_code", "sms_plain", "app_notify",
	// "call") to the maximum number of rows to keep for that type. A
	// non-positive value means "unlimited" for that type.
	PerType map[string]int
	// MaxPerUser caps the total number of rows kept for the user across all
	// record types. Non-positive means disabled.
	MaxPerUser int
	// MaxAge deletes rows whose occurred_at is older than this duration before
	// now. Non-positive means disabled.
	MaxAge time.Duration
}

func (r RecordsRetention) isNoop() bool {
	if r.MaxPerUser > 0 || r.MaxAge > 0 {
		return false
	}
	for _, limit := range r.PerType {
		if limit > 0 {
			return false
		}
	}
	return true
}

// PruneRelayRecords enforces the given retention policy for a single user,
// deleting the oldest rows that fall outside the configured limits. It returns
// the number of rows deleted. The per-type and global count rules keep the most
// recent rows ordered by (occurred_at DESC, id DESC), matching the read order.
func (s *Store) PruneRelayRecords(ctx context.Context, userID int64, retention RecordsRetention) (int64, error) {
	if retention.isNoop() {
		return 0, nil
	}

	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return 0, err
	}
	defer tx.Rollback(ctx)

	var deleted int64

	for recordType, limit := range retention.PerType {
		if limit <= 0 {
			continue
		}
		// Delete everything older than the Nth most recent row for this type.
		// The cutoff subquery seeks straight to that row via the
		// (user_id, record_type, occurred_at DESC, id DESC) index, and the
		// tuple comparison lets the outer DELETE range-scan instead of testing
		// every row against a large NOT IN set. When fewer than N rows exist the
		// subquery yields NULL, so nothing is deleted.
		tag, err := tx.Exec(
			ctx,
			`DELETE FROM relay_records
			  WHERE user_id = $1 AND record_type = $2
			    AND (occurred_at, id) < (
			        SELECT occurred_at, id FROM relay_records
			         WHERE user_id = $1 AND record_type = $2
			         ORDER BY occurred_at DESC, id DESC
			         OFFSET $3 LIMIT 1
			    )`,
			userID,
			recordType,
			limit-1,
		)
		if err != nil {
			return 0, err
		}
		deleted += tag.RowsAffected()
	}

	if retention.MaxPerUser > 0 {
		tag, err := tx.Exec(
			ctx,
			`DELETE FROM relay_records
			  WHERE user_id = $1
			    AND (occurred_at, id) < (
			        SELECT occurred_at, id FROM relay_records
			         WHERE user_id = $1
			         ORDER BY occurred_at DESC, id DESC
			         OFFSET $2 LIMIT 1
			    )`,
			userID,
			retention.MaxPerUser-1,
		)
		if err != nil {
			return 0, err
		}
		deleted += tag.RowsAffected()
	}

	if retention.MaxAge > 0 {
		// Evaluate the cutoff against the database clock (NOW()) rather than the
		// app's wall clock so age-based pruning is consistent across instances.
		tag, err := tx.Exec(
			ctx,
			`DELETE FROM relay_records WHERE user_id = $1 AND occurred_at < NOW() - make_interval(secs => $2)`,
			userID,
			retention.MaxAge.Seconds(),
		)
		if err != nil {
			return 0, err
		}
		deleted += tag.RowsAffected()
	}

	if err := tx.Commit(ctx); err != nil {
		return 0, err
	}
	return deleted, nil
}

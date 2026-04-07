package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/magisk317/xinyi-relay/backend/api/internal/database"
)

var ErrNotFound = errors.New("not found")
var ErrConflict = errors.New("conflict")

type Store struct {
	db *database.Database
}

func New(db *database.Database) *Store {
	return &Store{db: db}
}

type User struct {
	ID           int64
	Username     string
	PasswordHash string
	CreatedAt    time.Time
}

type Session struct {
	ID        int64
	UserID    int64
	Username  string
	TokenHash string
	CSRFToken string
	ExpiresAt time.Time
}

type BindCode struct {
	ID        int64
	UserID    int64
	CodeHash  string
	ExpiresAt time.Time
	UsedAt    *time.Time
}

type Device struct {
	ID             int64           `json:"id"`
	UserID         int64           `json:"userId"`
	DeviceName     string          `json:"deviceName"`
	DeviceModel    string          `json:"deviceModel"`
	Platform       string          `json:"platform"`
	AppVersion     string          `json:"appVersion"`
	DisplayName    string          `json:"displayName"`
	Enabled        bool            `json:"enabled"`
	RevokedAt      *time.Time      `json:"revokedAt,omitempty"`
	LastSeenAt     *time.Time      `json:"lastSeenAt,omitempty"`
	LocalAddresses json.RawMessage `json:"localAddresses"`
	Capabilities   json.RawMessage `json:"capabilities"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
}

type ConfigSnapshot struct {
	Revision int64           `json:"revision"`
	Content  json.RawMessage `json:"content"`
}

type ConfigAuditLog struct {
	ID        int64     `json:"id"`
	Revision  int64     `json:"revision"`
	ActorType string    `json:"actorType"`
	ActorID   int64     `json:"actorId"`
	Summary   string    `json:"summary"`
	CreatedAt time.Time `json:"createdAt"`
}

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

func (s *Store) UserCount(ctx context.Context) (int64, error) {
	var count int64
	if err := s.db.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM users`).Scan(&count); err != nil {
		return 0, err
	}
	return count, nil
}

func (s *Store) CreateUser(ctx context.Context, username string, passwordHash string) (User, error) {
	var user User
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO users (username, password_hash) VALUES ($1, $2) RETURNING id, username, password_hash, created_at`,
		username,
		passwordHash,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.CreatedAt)
	return user, err
}

func (s *Store) GetUserByUsername(ctx context.Context, username string) (User, error) {
	var user User
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT id, username, password_hash, created_at FROM users WHERE username = $1`,
		username,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return user, err
}

func (s *Store) UpdateUserPassword(ctx context.Context, userID int64, passwordHash string) error {
	commandTag, err := s.db.Pool.Exec(
		ctx,
		`UPDATE users SET password_hash = $2 WHERE id = $1`,
		userID,
		passwordHash,
	)
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) CreateSession(
	ctx context.Context,
	user User,
	tokenHash string,
	csrfToken string,
	expiresAt time.Time,
) (Session, error) {
	var session Session
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO web_sessions (user_id, token_hash, csrf_token, expires_at) VALUES ($1, $2, $3, $4)
		 RETURNING id, user_id, token_hash, csrf_token, expires_at`,
		user.ID,
		tokenHash,
		csrfToken,
		expiresAt,
	).Scan(&session.ID, &session.UserID, &session.TokenHash, &session.CSRFToken, &session.ExpiresAt)
	session.Username = user.Username
	return session, err
}

func (s *Store) GetSessionByTokenHash(ctx context.Context, tokenHash string) (Session, error) {
	var session Session
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT ws.id, ws.user_id, u.username, ws.token_hash, ws.csrf_token, ws.expires_at
		 FROM web_sessions ws
		 JOIN users u ON u.id = ws.user_id
		 WHERE ws.token_hash = $1 AND ws.expires_at > NOW()`,
		tokenHash,
	).Scan(
		&session.ID,
		&session.UserID,
		&session.Username,
		&session.TokenHash,
		&session.CSRFToken,
		&session.ExpiresAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Session{}, ErrNotFound
	}
	return session, err
}

func (s *Store) TouchSession(ctx context.Context, sessionID int64) error {
	_, err := s.db.Pool.Exec(ctx, `UPDATE web_sessions SET last_seen_at = NOW() WHERE id = $1`, sessionID)
	return err
}

func (s *Store) DeleteSessionByTokenHash(ctx context.Context, tokenHash string) error {
	_, err := s.db.Pool.Exec(ctx, `DELETE FROM web_sessions WHERE token_hash = $1`, tokenHash)
	return err
}

func (s *Store) CreateBindCode(ctx context.Context, userID int64, codeHash string, expiresAt time.Time) (BindCode, error) {
	var bindCode BindCode
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO device_bind_codes (user_id, code_hash, expires_at) VALUES ($1, $2, $3)
		 RETURNING id, user_id, code_hash, expires_at, used_at`,
		userID,
		codeHash,
		expiresAt,
	).Scan(&bindCode.ID, &bindCode.UserID, &bindCode.CodeHash, &bindCode.ExpiresAt, &bindCode.UsedAt)
	return bindCode, err
}

func (s *Store) ConsumeBindCode(ctx context.Context, codeHash string) (BindCode, error) {
	var bindCode BindCode
	err := s.db.Pool.QueryRow(
		ctx,
		`UPDATE device_bind_codes
		 SET used_at = NOW()
		 WHERE code_hash = $1 AND used_at IS NULL AND expires_at > NOW()
		 RETURNING id, user_id, code_hash, expires_at, used_at`,
		codeHash,
	).Scan(&bindCode.ID, &bindCode.UserID, &bindCode.CodeHash, &bindCode.ExpiresAt, &bindCode.UsedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return BindCode{}, ErrNotFound
	}
	return bindCode, err
}

func (s *Store) CreateDevice(
	ctx context.Context,
	userID int64,
	deviceName string,
	deviceModel string,
	platform string,
	appVersion string,
	tokenHash string,
) (Device, error) {
	var device Device
	err := s.db.Pool.QueryRow(
		ctx,
		`INSERT INTO devices (
			user_id, device_name, device_model, platform, app_version, display_name, token_hash
		) VALUES ($1, $2, $3, $4, $5, $2, $6)
		RETURNING id, user_id, device_name, device_model, platform, app_version, display_name, enabled,
		          revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at`,
		userID,
		deviceName,
		deviceModel,
		platform,
		appVersion,
		tokenHash,
	).Scan(
		&device.ID,
		&device.UserID,
		&device.DeviceName,
		&device.DeviceModel,
		&device.Platform,
		&device.AppVersion,
		&device.DisplayName,
		&device.Enabled,
		&device.RevokedAt,
		&device.LastSeenAt,
		&device.LocalAddresses,
		&device.Capabilities,
		&device.CreatedAt,
		&device.UpdatedAt,
	)
	return device, err
}

func (s *Store) GetDeviceByTokenHash(ctx context.Context, tokenHash string) (Device, error) {
	var device Device
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT id, user_id, device_name, device_model, platform, app_version, display_name, enabled,
		        revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at
		   FROM devices
		  WHERE token_hash = $1 AND revoked_at IS NULL AND enabled = TRUE`,
		tokenHash,
	).Scan(
		&device.ID,
		&device.UserID,
		&device.DeviceName,
		&device.DeviceModel,
		&device.Platform,
		&device.AppVersion,
		&device.DisplayName,
		&device.Enabled,
		&device.RevokedAt,
		&device.LastSeenAt,
		&device.LocalAddresses,
		&device.Capabilities,
		&device.CreatedAt,
		&device.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Device{}, ErrNotFound
	}
	return device, err
}

func (s *Store) ListDevicesByUser(ctx context.Context, userID int64) ([]Device, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT id, user_id, device_name, device_model, platform, app_version, display_name, enabled,
		        revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at
		   FROM devices
		  WHERE user_id = $1 AND revoked_at IS NULL
		  ORDER BY created_at DESC`,
		userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var devices []Device
	for rows.Next() {
		var device Device
		if err := rows.Scan(
			&device.ID,
			&device.UserID,
			&device.DeviceName,
			&device.DeviceModel,
			&device.Platform,
			&device.AppVersion,
			&device.DisplayName,
			&device.Enabled,
			&device.RevokedAt,
			&device.LastSeenAt,
			&device.LocalAddresses,
			&device.Capabilities,
			&device.CreatedAt,
			&device.UpdatedAt,
		); err != nil {
			return nil, err
		}
		devices = append(devices, device)
	}
	return devices, rows.Err()
}

func (s *Store) UpdateDeviceHeartbeat(
	ctx context.Context,
	deviceID int64,
	appVersion string,
	localAddresses json.RawMessage,
	capabilities json.RawMessage,
) error {
	_, err := s.db.Pool.Exec(
		ctx,
		`UPDATE devices
		    SET app_version = $2,
		        local_addresses = $3,
		        capabilities = $4,
		        last_seen_at = NOW(),
		        updated_at = NOW()
		  WHERE id = $1`,
		deviceID,
		appVersion,
		localAddresses,
		capabilities,
	)
	return err
}

func (s *Store) PatchDevice(ctx context.Context, userID, deviceID int64, displayName *string, enabled *bool) (Device, error) {
	var current Device
	rows, err := s.ListDevicesByUser(ctx, userID)
	if err != nil {
		return Device{}, err
	}
	for _, device := range rows {
		if device.ID == deviceID {
			current = device
			break
		}
	}
	if current.ID == 0 {
		return Device{}, ErrNotFound
	}

	if displayName == nil {
		displayName = &current.DisplayName
	}
	if enabled == nil {
		enabled = &current.Enabled
	}

	var updated Device
	err = s.db.Pool.QueryRow(
		ctx,
		`UPDATE devices
		    SET display_name = $3,
		        enabled = $4,
		        updated_at = NOW()
		  WHERE user_id = $1 AND id = $2
		  RETURNING id, user_id, device_name, device_model, platform, app_version, display_name, enabled,
		            revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at`,
		userID,
		deviceID,
		*displayName,
		*enabled,
	).Scan(
		&updated.ID,
		&updated.UserID,
		&updated.DeviceName,
		&updated.DeviceModel,
		&updated.Platform,
		&updated.AppVersion,
		&updated.DisplayName,
		&updated.Enabled,
		&updated.RevokedAt,
		&updated.LastSeenAt,
		&updated.LocalAddresses,
		&updated.Capabilities,
		&updated.CreatedAt,
		&updated.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return Device{}, ErrNotFound
	}
	return updated, err
}

func (s *Store) RevokeDevice(ctx context.Context, userID, deviceID int64) error {
	commandTag, err := s.db.Pool.Exec(
		ctx,
		`UPDATE devices
		    SET token_hash = NULL,
		        revoked_at = NOW(),
		        updated_at = NOW()
		  WHERE user_id = $1 AND id = $2`,
		userID,
		deviceID,
	)
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) GetConfigSnapshot(ctx context.Context, userID int64) (ConfigSnapshot, error) {
	var snapshot ConfigSnapshot
	err := s.db.Pool.QueryRow(
		ctx,
		`SELECT revision, content
		   FROM config_snapshots
		  WHERE user_id = $1
		  ORDER BY revision DESC
		  LIMIT 1`,
		userID,
	).Scan(&snapshot.Revision, &snapshot.Content)
	if errors.Is(err, pgx.ErrNoRows) {
		return ConfigSnapshot{Revision: 0, Content: json.RawMessage(`{}`)}, nil
	}
	return snapshot, err
}

func (s *Store) PutConfigSnapshot(
	ctx context.Context,
	userID int64,
	baseRevision int64,
	content json.RawMessage,
	actorType string,
	actorID int64,
) (ConfigSnapshot, error) {
	tx, err := s.db.Pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return ConfigSnapshot{}, err
	}
	defer tx.Rollback(ctx)

	var currentRevision int64
	err = tx.QueryRow(
		ctx,
		`SELECT COALESCE(MAX(revision), 0) FROM config_snapshots WHERE user_id = $1`,
		userID,
	).Scan(&currentRevision)
	if err != nil {
		return ConfigSnapshot{}, err
	}
	if currentRevision != baseRevision {
		return ConfigSnapshot{}, ErrConflict
	}

	nextRevision := currentRevision + 1
	if _, err := tx.Exec(
		ctx,
		`INSERT INTO config_snapshots (user_id, revision, content, updated_by_type, updated_by_id)
		 VALUES ($1, $2, $3, $4, $5)`,
		userID,
		nextRevision,
		content,
		actorType,
		actorID,
	); err != nil {
		return ConfigSnapshot{}, err
	}

	summary := fmt.Sprintf("config revision %d committed by %s", nextRevision, actorType)
	if _, err := tx.Exec(
		ctx,
		`INSERT INTO config_audit_logs (user_id, revision, actor_type, actor_id, summary)
		 VALUES ($1, $2, $3, $4, $5)`,
		userID,
		nextRevision,
		actorType,
		actorID,
		summary,
	); err != nil {
		return ConfigSnapshot{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return ConfigSnapshot{}, err
	}
	return ConfigSnapshot{Revision: nextRevision, Content: content}, nil
}

func (s *Store) ListConfigAuditLogs(
	ctx context.Context,
	userID int64,
	limit int32,
	offset int32,
) ([]ConfigAuditLog, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT id, revision, actor_type, COALESCE(actor_id, 0), summary, created_at
		   FROM config_audit_logs
		  WHERE user_id = $1
		  ORDER BY created_at DESC, id DESC
		  LIMIT $2 OFFSET $3`,
		userID,
		limit,
		offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []ConfigAuditLog
	for rows.Next() {
		var log ConfigAuditLog
		if err := rows.Scan(
			&log.ID,
			&log.Revision,
			&log.ActorType,
			&log.ActorID,
			&log.Summary,
			&log.CreatedAt,
		); err != nil {
			return nil, err
		}
		logs = append(logs, log)
	}
	return logs, rows.Err()
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

func nullableString(value string) any {
	if value == "" {
		return nil
	}
	return value
}

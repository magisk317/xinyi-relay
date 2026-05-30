package store

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
)

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

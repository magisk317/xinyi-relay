package store

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

const (
	DeviceActionStatusPending = "pending"
	DeviceActionStatusDone    = "done"
	DeviceActionStatusFailed  = "failed"
)

// First-version device action whitelist. Actions are one-shot and carry no
// revision semantics: the device executes once, then acks with done/failed.
const (
	DeviceActionSetRingerMode         = "SET_RINGER_MODE"
	DeviceActionSetInterruptionFilter = "SET_INTERRUPTION_FILTER"
)

// maxPendingDeviceActions bounds how many unacked actions one pull response
// carries so a device that never acks cannot grow the payload unbounded.
const maxPendingDeviceActions = 20

// deviceActionParamRule describes the single params key an action accepts and
// the values allowed for it. Unknown actions reject with
// ErrUnsupportedDeviceAction; unknown keys/values reject with
// ErrInvalidDeviceActionParams.
type deviceActionParamRule struct {
	key     string
	allowed map[string]struct{}
}

var deviceActionParamRules = map[string]deviceActionParamRule{
	DeviceActionSetRingerMode: {
		key:     "mode",
		allowed: map[string]struct{}{"normal": {}, "silent": {}, "vibrate": {}},
	},
	DeviceActionSetInterruptionFilter: {
		key:     "filter",
		allowed: map[string]struct{}{"all": {}, "priority": {}, "none": {}, "alarms": {}},
	},
}

// ValidateDeviceAction checks the action name against the whitelist and the
// params object against the per-action rule. It is the queue-time gate so
// unsupported actions and malformed params never reach a device.
func ValidateDeviceAction(action string, params json.RawMessage) error {
	rule, ok := deviceActionParamRules[strings.TrimSpace(action)]
	if !ok {
		return ErrUnsupportedDeviceAction
	}
	var payload map[string]json.RawMessage
	if err := json.Unmarshal(params, &payload); err != nil {
		return ErrInvalidDeviceActionParams
	}
	raw, ok := payload[rule.key]
	if !ok {
		return ErrInvalidDeviceActionParams
	}
	var value string
	if err := json.Unmarshal(raw, &value); err != nil {
		return ErrInvalidDeviceActionParams
	}
	if _, ok := rule.allowed[value]; !ok {
		return ErrInvalidDeviceActionParams
	}
	return nil
}

type DeviceAction struct {
	ID            int64           `json:"id"`
	UserID        int64           `json:"userId"`
	DeviceID      int64           `json:"deviceId"`
	Action        string          `json:"action"`
	Params        json.RawMessage `json:"params"`
	ActorType     string          `json:"actorType"`
	ActorID       int64           `json:"actorId"`
	Status        string          `json:"status"`
	FailureReason *string         `json:"failureReason"`
	CreatedAt     time.Time       `json:"createdAt"`
	UpdatedAt     time.Time       `json:"updatedAt"`
	AckedAt       *time.Time      `json:"ackedAt"`
}

// CreateDeviceAction validates and queues a one-shot action for the device.
// The INSERT ... SELECT FROM devices shape makes a foreign device resolve to
// zero inserted rows, which surfaces as ErrNotFound.
func (s *Store) CreateDeviceAction(
	ctx context.Context,
	userID, deviceID int64,
	action string,
	params json.RawMessage,
	actorType string,
	actorID int64,
) (DeviceAction, error) {
	if err := ValidateDeviceAction(action, params); err != nil {
		return DeviceAction{}, err
	}

	action1, err := scanDeviceAction(
		s.db.Pool.QueryRow(
			ctx,
			`INSERT INTO device_actions (
				user_id, device_id, action, params, actor_type, actor_id, status, created_at, updated_at
			)
			SELECT devices.user_id, devices.id, $3, $4, $5, $6, $7, NOW(), NOW()
			  FROM devices
			 WHERE devices.user_id = $1 AND devices.id = $2
			RETURNING
				id, user_id, device_id, action, params,
				actor_type, COALESCE(actor_id, 0), status, failure_reason,
				created_at, updated_at, acked_at`,
			userID,
			deviceID,
			strings.TrimSpace(action),
			normalizeDeviceConfigJSON(params),
			actorType,
			nullableActorID(actorID),
			DeviceActionStatusPending,
		),
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return DeviceAction{}, ErrNotFound
	}
	if err != nil {
		return DeviceAction{}, err
	}
	return action1, nil
}

// ListPendingDeviceActions returns the oldest unacked actions for the device,
// newest-last so the device executes them in queue order.
func (s *Store) ListPendingDeviceActions(ctx context.Context, userID, deviceID int64) ([]DeviceAction, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT
			id, user_id, device_id, action, params,
			actor_type, COALESCE(actor_id, 0), status, failure_reason,
			created_at, updated_at, acked_at
		   FROM device_actions
		  WHERE user_id = $1 AND device_id = $2 AND status = $3
		  ORDER BY created_at ASC, id ASC
		  LIMIT $4`,
		userID,
		deviceID,
		DeviceActionStatusPending,
		maxPendingDeviceActions,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var actions []DeviceAction
	for rows.Next() {
		item, scanErr := scanDeviceAction(rows)
		if scanErr != nil {
			return nil, scanErr
		}
		actions = append(actions, item)
	}
	return actions, rows.Err()
}

// ListDeviceActions returns the device's most recent actions, newest first,
// for the console history view.
func (s *Store) ListDeviceActions(
	ctx context.Context,
	userID, deviceID int64,
	limit, offset int32,
) ([]DeviceAction, error) {
	rows, err := s.db.Pool.Query(
		ctx,
		`SELECT
			id, user_id, device_id, action, params,
			actor_type, COALESCE(actor_id, 0), status, failure_reason,
			created_at, updated_at, acked_at
		   FROM device_actions
		  WHERE user_id = $1 AND device_id = $2
		  ORDER BY created_at DESC, id DESC
		  LIMIT $3 OFFSET $4`,
		userID,
		deviceID,
		limit,
		offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var actions []DeviceAction
	for rows.Next() {
		item, scanErr := scanDeviceAction(rows)
		if scanErr != nil {
			return nil, scanErr
		}
		actions = append(actions, item)
	}
	return actions, rows.Err()
}

// AckDeviceAction transitions a pending action to done/failed. The status
// guard in the UPDATE makes a double ack a conflict rather than a silent
// overwrite; a second SELECT classifies ErrNotFound vs ErrConflict.
func (s *Store) AckDeviceAction(
	ctx context.Context,
	userID, deviceID, actionID int64,
	status string,
	failureReason string,
) (DeviceAction, error) {
	var reason any
	switch strings.TrimSpace(status) {
	case DeviceActionStatusDone:
	case DeviceActionStatusFailed:
		trimmed := strings.TrimSpace(failureReason)
		if trimmed == "" {
			trimmed = "action_failed"
		}
		reason = trimmed
	default:
		return DeviceAction{}, ErrUnsupportedDeviceActionStatus
	}

	updated, err := scanDeviceAction(
		s.db.Pool.QueryRow(
			ctx,
			`UPDATE device_actions
			    SET status = $4,
			        failure_reason = $5,
			        updated_at = NOW(),
			        acked_at = NOW()
			  WHERE user_id = $1 AND device_id = $2 AND id = $3 AND status = $6
			RETURNING
				id, user_id, device_id, action, params,
				actor_type, COALESCE(actor_id, 0), status, failure_reason,
				created_at, updated_at, acked_at`,
			userID,
			deviceID,
			actionID,
			strings.TrimSpace(status),
			reason,
			DeviceActionStatusPending,
		),
	)
	if err == nil {
		return updated, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return DeviceAction{}, err
	}

	_, selErr := scanDeviceAction(
		s.db.Pool.QueryRow(
			ctx,
			`SELECT
				id, user_id, device_id, action, params,
				actor_type, COALESCE(actor_id, 0), status, failure_reason,
				created_at, updated_at, acked_at
			   FROM device_actions
			  WHERE user_id = $1 AND device_id = $2 AND id = $3`,
			userID,
			deviceID,
			actionID,
		),
	)
	if errors.Is(selErr, pgx.ErrNoRows) {
		return DeviceAction{}, ErrNotFound
	}
	if selErr != nil {
		return DeviceAction{}, selErr
	}
	return DeviceAction{}, ErrConflict
}

func scanDeviceAction(scanner rowScanner) (DeviceAction, error) {
	var item DeviceAction
	err := scanner.Scan(
		&item.ID,
		&item.UserID,
		&item.DeviceID,
		&item.Action,
		&item.Params,
		&item.ActorType,
		&item.ActorID,
		&item.Status,
		&item.FailureReason,
		&item.CreatedAt,
		&item.UpdatedAt,
		&item.AckedAt,
	)
	item.Params = normalizeDeviceConfigJSON(item.Params)
	return item, err
}

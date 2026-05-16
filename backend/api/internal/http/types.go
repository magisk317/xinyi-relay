package http

import (
	"encoding/json"
	"time"
)

type errorResponse struct {
	Error string `json:"error"`
}

type bootstrapAdminRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type loginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type desktopLoginPageRequest struct {
	Username    string `json:"username"`
	Password    string `json:"password"`
	RedirectURI string `json:"redirect_uri"`
	State       string `json:"state"`
	ClientName  string `json:"client_name"`
}

type desktopExchangeRequest struct {
	Code string `json:"code"`
}

type desktopRefreshRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type desktopLogoutRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type changePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}

type bindCodeResponse struct {
	Code      string    `json:"code"`
	ExpiresAt time.Time `json:"expiresAt"`
}

type agentRegisterRequest struct {
	BindCode    string `json:"bindCode"`
	DeviceName  string `json:"deviceName"`
	DeviceModel string `json:"deviceModel"`
	Platform    string `json:"platform"`
	AppVersion  string `json:"appVersion"`
}

type agentRegisterResponse struct {
	UserID      int64  `json:"userId"`
	DeviceID    int64  `json:"deviceId"`
	DeviceToken string `json:"deviceToken"`
}

type heartbeatRequest struct {
	AppVersion     string          `json:"appVersion"`
	LocalAddresses []string        `json:"localAddresses"`
	Capabilities   json.RawMessage `json:"capabilities"`
}

type patchDeviceRequest struct {
	DisplayName *string `json:"displayName"`
	Enabled     *bool   `json:"enabled"`
}

type configSnapshotResponse struct {
	Revision int64           `json:"revision"`
	Snapshot json.RawMessage `json:"snapshot"`
}

type configSnapshotRequest struct {
	BaseRevision int64           `json:"base_revision"`
	Snapshot     json.RawMessage `json:"snapshot"`
}

type configAuditLogsResponse struct {
	Logs   []configAuditLogItem `json:"logs"`
	Limit  int32                `json:"limit"`
	Offset int32                `json:"offset"`
}

type configAuditLogItem struct {
	ID        int64     `json:"id"`
	Revision  int64     `json:"revision"`
	ActorType string    `json:"actorType"`
	ActorID   int64     `json:"actorId"`
	Summary   string    `json:"summary"`
	CreatedAt time.Time `json:"createdAt"`
}

type relayRecordWire struct {
	EventID    string          `json:"eventId"`
	RecordType string          `json:"recordType"`
	Sender     string          `json:"sender"`
	Body       string          `json:"body"`
	SmsCode    string          `json:"smsCode"`
	Package    string          `json:"packageName"`
	MsgType    int             `json:"msgType"`
	CallType   int             `json:"callType"`
	OccurredAt time.Time       `json:"occurredAt"`
	Metadata   json.RawMessage `json:"metadata"`
}

type relayRecordsBatchRequest struct {
	Records []relayRecordWire `json:"records"`
}

type relayRecordsBatchResponse struct {
	Inserted int64 `json:"inserted"`
}

type meResponse struct {
	Authenticated bool   `json:"authenticated"`
	Username      string `json:"username,omitempty"`
	CSRFToken     string `json:"csrfToken,omitempty"`
	LanguageTag   string `json:"languageTag,omitempty"`
}

type loginResponse struct {
	Authenticated bool   `json:"authenticated"`
	Username      string `json:"username"`
	CSRFToken     string `json:"csrfToken"`
	LanguageTag   string `json:"languageTag,omitempty"`
}

type desktopSessionResponse struct {
	Authenticated    bool      `json:"authenticated"`
	Username         string    `json:"username"`
	AccessToken      string    `json:"accessToken"`
	RefreshToken     string    `json:"refreshToken"`
	ExpiresAt        time.Time `json:"expiresAt"`
	RefreshExpiresAt time.Time `json:"refreshExpiresAt"`
}

type simpleOKResponse struct {
	OK bool `json:"ok"`
}

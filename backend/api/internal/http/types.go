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

type deviceConfigStateResponse struct {
	DeviceID        int64                     `json:"deviceId"`
	Revision        int64                     `json:"revision"`
	MirrorContent   json.RawMessage           `json:"mirrorContent"`
	PendingCommands []deviceConfigCommandItem `json:"pendingCommands"`
	UpdatedAt       time.Time                 `json:"updatedAt"`
}

type deviceConfigCommandRequest struct {
	BaseRevision int64           `json:"baseRevision"`
	Summary      string          `json:"summary"`
	Mutation     json.RawMessage `json:"mutation"`
}

type deviceConfigCommandItem struct {
	ID             int64           `json:"id"`
	BaseRevision   int64           `json:"baseRevision"`
	TargetRevision int64           `json:"targetRevision"`
	Mutation       json.RawMessage `json:"mutation"`
	Summary        string          `json:"summary"`
	ActorType      string          `json:"actorType"`
	ActorID        int64           `json:"actorId"`
	Status         string          `json:"status"`
	FailureReason  *string         `json:"failureReason"`
	CreatedAt      time.Time       `json:"createdAt"`
	UpdatedAt      time.Time       `json:"updatedAt"`
	AppliedAt      *time.Time      `json:"appliedAt"`
}

type deviceConfigAuditLogsResponse struct {
	Logs   []deviceConfigAuditLogItem `json:"logs"`
	Limit  int32                      `json:"limit"`
	Offset int32                      `json:"offset"`
}

type deviceConfigAuditLogItem struct {
	ID        int64     `json:"id"`
	DeviceID  int64     `json:"deviceId"`
	CommandID *int64    `json:"commandId"`
	Revision  int64     `json:"revision"`
	EventType string    `json:"eventType"`
	ActorType string    `json:"actorType"`
	ActorID   int64     `json:"actorId"`
	Summary   string    `json:"summary"`
	CreatedAt time.Time `json:"createdAt"`
}

type agentConfigMirrorRequest struct {
	LocalRevision int64           `json:"localRevision"`
	MirrorContent json.RawMessage `json:"mirrorContent"`
	Summary       string          `json:"summary"`
}

type agentConfigCommandsPullRequest struct {
	LocalRevision int64 `json:"localRevision"`
}

type agentConfigCommandsPullResponse struct {
	DeviceID        int64                     `json:"deviceId"`
	Revision        int64                     `json:"revision"`
	MirrorContent   json.RawMessage           `json:"mirrorContent"`
	PendingCommands []deviceConfigCommandItem `json:"pendingCommands"`
	UpdatedAt       time.Time                 `json:"updatedAt"`
}

type agentConfigCommandsAckRequest struct {
	CommandID       int64           `json:"commandId"`
	Status          string          `json:"status"`
	AppliedRevision int64           `json:"appliedRevision"`
	FailureReason   string          `json:"failureReason"`
	MirrorContent   json.RawMessage `json:"mirrorContent"`
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
	Records         []relayRecordWire `json:"records"`
	ReplaceExisting bool              `json:"replaceExisting"`
}

type relayRecordsBatchResponse struct {
	Inserted int64 `json:"inserted"`
	Updated  int64 `json:"updated"`
	Deleted  int64 `json:"deleted"`
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

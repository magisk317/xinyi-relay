package http

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type openAPIDocument struct {
	OpenAPI    string                        `json:"openapi"`
	Paths      map[string]map[string]any     `json:"paths"`
	Components openAPIContractTestComponents `json:"components"`
}

type openAPIContractTestComponents struct {
	Schemas map[string]openAPIContractTestSchema `json:"schemas"`
}

type openAPIContractTestSchema struct {
	Properties map[string]any `json:"properties"`
}

func TestOpenAPIContractDeclaresBackendRoutes(t *testing.T) {
	doc := loadOpenAPIContract(t)
	expectedRoutes := map[string][]string{
		"/healthz":                      {"get"},
		"/api/v1/system/info":           {"get"},
		"/api/v1/bootstrap/admin":       {"post"},
		"/api/v1/auth/login":            {"post"},
		"/api/v1/auth/logout":           {"post"},
		"/api/v1/auth/password":         {"post"},
		"/api/v1/auth/me":               {"get"},
		"/api/v1/auth/desktop/start":    {"get", "post"},
		"/api/v1/auth/desktop/exchange": {"post"},
		"/api/v1/auth/desktop/refresh":  {"post"},
		"/api/v1/auth/desktop/logout":   {"post"},
		"/api/v1/devices/bind-codes":    {"post"},
		"/api/v1/devices":               {"get"},
		"/api/v1/devices/{id}":          {"patch"},
		"/api/v1/devices/{id}/revoke":   {"post"},
		"/api/v1/agent/register":        {"post"},
		"/api/v1/agent/heartbeat":       {"post"},
		"/api/v1/agent/records:batch":   {"post"},
		"/api/v1/config/snapshot":       {"get", "put"},
		"/api/v1/config/audit":          {"get"},
		"/api/v1/records":               {"get"},
		"/api/v1/records/{id}":          {"get"},
		"/api/v1/realtime/ws":           {"get"},
	}

	for path, methods := range expectedRoutes {
		pathItem, ok := doc.Paths[path]
		if !ok {
			t.Fatalf("OpenAPI contract is missing path %s", path)
		}
		for _, method := range methods {
			if _, ok := pathItem[method]; !ok {
				t.Fatalf("OpenAPI contract is missing %s %s", strings.ToUpper(method), path)
			}
		}
	}
}

func TestOpenAPIContractSchemaFieldsMatchBackendDTOs(t *testing.T) {
	doc := loadOpenAPIContract(t)
	cases := []struct {
		schemaName string
		dto        any
	}{
		{"ErrorResponse", errorResponse{}},
		{"SystemInfoResponse", systemInfoResponse{}},
		{"BootstrapAdminRequest", bootstrapAdminRequest{}},
		{"LoginRequest", loginRequest{}},
		{"LoginResponse", loginResponse{}},
		{"MeResponse", meResponse{}},
		{"ChangePasswordRequest", changePasswordRequest{}},
		{"DesktopLoginPageRequest", desktopLoginPageRequest{}},
		{"DesktopExchangeRequest", desktopExchangeRequest{}},
		{"DesktopRefreshRequest", desktopRefreshRequest{}},
		{"DesktopLogoutRequest", desktopLogoutRequest{}},
		{"DesktopSessionResponse", desktopSessionResponse{}},
		{"SimpleOKResponse", simpleOKResponse{}},
		{"BindCodeResponse", bindCodeResponse{}},
		{"AgentRegisterRequest", agentRegisterRequest{}},
		{"AgentRegisterResponse", agentRegisterResponse{}},
		{"HeartbeatRequest", heartbeatRequest{}},
		{"PatchDeviceRequest", patchDeviceRequest{}},
		{"DeviceItem", store.Device{}},
		{"ConfigSnapshotRequest", configSnapshotRequest{}},
		{"ConfigSnapshotResponse", configSnapshotResponse{}},
		{"ConfigAuditLogItem", configAuditLogItem{}},
		{"ConfigAuditLogsResponse", configAuditLogsResponse{}},
		{"RelayRecordWire", relayRecordWire{}},
		{"RelayRecordsBatchRequest", relayRecordsBatchRequest{}},
		{"RelayRecordsBatchResponse", relayRecordsBatchResponse{}},
		{"RelayRecord", store.RelayRecord{}},
	}

	for _, tc := range cases {
		t.Run(tc.schemaName, func(t *testing.T) {
			schema, ok := doc.Components.Schemas[tc.schemaName]
			if !ok {
				t.Fatalf("OpenAPI contract is missing schema %s", tc.schemaName)
			}
			got := sortedKeys(schema.Properties)
			want := jsonFieldNames(reflect.TypeOf(tc.dto))
			if !reflect.DeepEqual(got, want) {
				t.Fatalf("%s fields drifted\nschema: %v\nbackend: %v", tc.schemaName, got, want)
			}
		})
	}
}

func loadOpenAPIContract(t *testing.T) openAPIDocument {
	t.Helper()
	path := filepath.Clean("../../../../shared/contracts/openapi.json")
	body, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read OpenAPI contract %s: %v", path, err)
	}

	var doc openAPIDocument
	if err := json.Unmarshal(body, &doc); err != nil {
		t.Fatalf("parse OpenAPI contract: %v", err)
	}
	if doc.OpenAPI == "" {
		t.Fatalf("OpenAPI contract is missing openapi version")
	}
	if len(doc.Paths) == 0 {
		t.Fatalf("OpenAPI contract has no paths")
	}
	if len(doc.Components.Schemas) == 0 {
		t.Fatalf("OpenAPI contract has no schemas")
	}
	return doc
}

func jsonFieldNames(typ reflect.Type) []string {
	if typ.Kind() == reflect.Pointer {
		typ = typ.Elem()
	}
	var names []string
	for i := 0; i < typ.NumField(); i++ {
		field := typ.Field(i)
		if field.Anonymous {
			continue
		}
		tag := field.Tag.Get("json")
		name := strings.Split(tag, ",")[0]
		if name == "-" {
			continue
		}
		if name == "" {
			name = field.Name
		}
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func sortedKeys(values map[string]any) []string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}

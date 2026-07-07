package http

import (
	"bytes"
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
		"/healthz":                             {"get"},
		"/api/v1/system/info":                  {"get"},
		"/api/v1/bootstrap/admin":              {"post"},
		"/api/v1/auth/login":                   {"post"},
		"/api/v1/auth/logout":                  {"post"},
		"/api/v1/auth/password":                {"post"},
		"/api/v1/auth/me":                      {"get"},
		"/api/v1/auth/desktop/start":           {"get", "post"},
		"/api/v1/auth/desktop/exchange":        {"post"},
		"/api/v1/auth/desktop/refresh":         {"post"},
		"/api/v1/auth/desktop/logout":          {"post"},
		"/api/v1/devices/bind-codes":           {"post"},
		"/api/v1/devices":                      {"get"},
		"/api/v1/devices/{id}":                 {"patch"},
		"/api/v1/devices/{id}/revoke":          {"post"},
		"/api/v1/devices/{id}/config":          {"get"},
		"/api/v1/devices/{id}/config/commands": {"post"},
		"/api/v1/devices/{id}/config/audit":    {"get"},
		"/api/v1/agent/register":               {"post"},
		"/api/v1/agent/heartbeat":              {"post"},
		"/api/v1/agent/config/mirror":          {"post"},
		"/api/v1/agent/config/commands:pull":   {"post"},
		"/api/v1/agent/config/commands:ack":    {"post"},
		"/api/v1/agent/records:batch":          {"post"},
		"/api/v1/records":                      {"get"},
		"/api/v1/records/{id}":                 {"get"},
		"/api/v1/realtime/ws":                  {"get"},
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

func TestOpenAPIContractRoutesDeclareRequestResponseAndParameters(t *testing.T) {
	doc := loadOpenAPIContract(t)

	queueCommand := openAPIOperation(t, doc, "/api/v1/devices/{id}/config/commands", "post")
	assertOpenAPIParameter(t, queueCommand, "id", "path", true, "integer")
	assertOpenAPIRequestSchema(t, queueCommand, "application/json", "DeviceConfigCommandRequest", true)
	assertOpenAPIResponseSchema(t, queueCommand, "201", "application/json", "DeviceConfigCommandItem")

	agentMirror := openAPIOperation(t, doc, "/api/v1/agent/config/mirror", "post")
	assertOpenAPIRequestSchema(t, agentMirror, "application/json", "AgentConfigMirrorRequest", true)
	assertOpenAPIResponseSchema(t, agentMirror, "200", "application/json", "DeviceConfigStateResponse")

	records := openAPIOperation(t, doc, "/api/v1/records", "get")
	assertOpenAPIParameter(t, records, "limit", "query", false, "integer")
	assertOpenAPIParameter(t, records, "offset", "query", false, "integer")
	assertOpenAPIParameter(t, records, "device_id", "query", false, "integer")
	assertOpenAPIResponseSchema(t, records, "200", "application/json", "RecordsResponse")

	desktopLogout := openAPIOperation(t, doc, "/api/v1/auth/desktop/logout", "post")
	assertOpenAPIRequestSchema(t, desktopLogout, "application/json", "DesktopLogoutRequest", false)
	assertOpenAPIResponseSchema(t, desktopLogout, "200", "application/json", "SimpleOKResponse")

	pullCommands := openAPIOperation(t, doc, "/api/v1/agent/config/commands:pull", "post")
	assertOpenAPIRequestSchema(t, pullCommands, "application/json", "AgentConfigCommandsPullRequest", true)
	assertOpenAPIResponseSchema(t, pullCommands, "200", "application/json", "AgentConfigCommandsPullResponse")

	realtime := openAPIOperation(t, doc, "/api/v1/realtime/ws", "get")
	responses := asOpenAPIObject(t, realtime["responses"], "realtime responses")
	if _, ok := responses["101"]; !ok {
		t.Fatalf("realtime route should declare WebSocket 101 response")
	}
}

func TestOpenAPIContractAllOperationsDeclareResponses(t *testing.T) {
	doc := loadOpenAPIContract(t)
	for path, pathItem := range doc.Paths {
		for method, rawOperation := range pathItem {
			operation := asOpenAPIObject(t, rawOperation, path+" "+method)
			responses := asOpenAPIObject(t, operation["responses"], path+" "+method+" responses")
			if len(responses) == 0 {
				t.Fatalf("%s %s has no OpenAPI responses", strings.ToUpper(method), path)
			}
		}
	}
}

func TestOpenAPIContractRouteSchemaRefsResolve(t *testing.T) {
	doc := loadOpenAPIContract(t)
	for path, pathItem := range doc.Paths {
		for method, rawOperation := range pathItem {
			operation := asOpenAPIObject(t, rawOperation, path+" "+method)
			for _, ref := range collectOpenAPIRefs(operation) {
				schemaName := strings.TrimPrefix(ref, "#/components/schemas/")
				if schemaName == ref {
					continue
				}
				if _, ok := doc.Components.Schemas[schemaName]; !ok {
					t.Fatalf("%s %s references missing schema %s", strings.ToUpper(method), path, schemaName)
				}
			}
		}
	}
}

func TestGeneratedOpenAPIContractMatchesCheckedInFile(t *testing.T) {
	path := filepath.Clean("../../../../frontend/shared/contracts/openapi.json")
	body, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read OpenAPI contract %s: %v", path, err)
	}

	generated, err := json.MarshalIndent(BuildOpenAPIContractDocument(), "", "  ")
	if err != nil {
		t.Fatalf("marshal generated OpenAPI contract: %v", err)
	}
	generated = append(generated, '\n')

	if !bytes.Equal(body, generated) {
		t.Fatalf("checked-in OpenAPI contract is out of date; run scripts/codegen/generate_openapi_contract.sh")
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
		{"DeviceConfigStateResponse", deviceConfigStateResponse{}},
		{"DeviceConfigCommandRequest", deviceConfigCommandRequest{}},
		{"DeviceConfigCommandItem", deviceConfigCommandItem{}},
		{"DeviceConfigAuditLogItem", deviceConfigAuditLogItem{}},
		{"DeviceConfigAuditLogsResponse", deviceConfigAuditLogsResponse{}},
		{"AgentConfigMirrorRequest", agentConfigMirrorRequest{}},
		{"AgentConfigCommandsPullRequest", agentConfigCommandsPullRequest{}},
		{"AgentConfigCommandsPullResponse", agentConfigCommandsPullResponse{}},
		{"AgentConfigCommandsAckRequest", agentConfigCommandsAckRequest{}},
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
	path := filepath.Clean("../../../../frontend/shared/contracts/openapi.json")
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

func collectOpenAPIRefs(value any) []string {
	var refs []string
	var walk func(any)
	walk = func(next any) {
		switch typed := next.(type) {
		case map[string]any:
			if ref, ok := typed["$ref"].(string); ok {
				refs = append(refs, ref)
			}
			for _, child := range typed {
				walk(child)
			}
		case []any:
			for _, child := range typed {
				walk(child)
			}
		}
	}
	walk(value)
	return refs
}

func openAPIOperation(t *testing.T, doc openAPIDocument, path string, method string) map[string]any {
	t.Helper()
	pathItem, ok := doc.Paths[path]
	if !ok {
		t.Fatalf("OpenAPI contract is missing path %s", path)
	}
	rawOperation, ok := pathItem[method]
	if !ok {
		t.Fatalf("OpenAPI contract is missing %s %s", strings.ToUpper(method), path)
	}
	return asOpenAPIObject(t, rawOperation, path+" "+method)
}

func assertOpenAPIRequestSchema(
	t *testing.T,
	operation map[string]any,
	contentType string,
	schemaName string,
	required bool,
) {
	t.Helper()
	requestBody := asOpenAPIObject(t, operation["requestBody"], "requestBody")
	if got, _ := requestBody["required"].(bool); got != required {
		t.Fatalf("requestBody required=%v, want %v", got, required)
	}
	ref := openAPIContentSchemaRef(t, requestBody, contentType)
	if ref != "#/components/schemas/"+schemaName {
		t.Fatalf("requestBody schema = %s, want %s", ref, schemaName)
	}
}

func assertOpenAPIResponseSchema(
	t *testing.T,
	operation map[string]any,
	status string,
	contentType string,
	schemaName string,
) {
	t.Helper()
	responses := asOpenAPIObject(t, operation["responses"], "responses")
	response := asOpenAPIObject(t, responses[status], "response "+status)
	ref := openAPIContentSchemaRef(t, response, contentType)
	if ref != "#/components/schemas/"+schemaName {
		t.Fatalf("response %s schema = %s, want %s", status, ref, schemaName)
	}
}

func assertOpenAPIParameter(
	t *testing.T,
	operation map[string]any,
	name string,
	location string,
	required bool,
	schemaType string,
) {
	t.Helper()
	rawParameters, ok := operation["parameters"].([]any)
	if !ok {
		t.Fatalf("operation has no parameters")
	}
	for _, rawParameter := range rawParameters {
		parameter := asOpenAPIObject(t, rawParameter, "parameter")
		if parameter["name"] != name || parameter["in"] != location {
			continue
		}
		if got, _ := parameter["required"].(bool); got != required {
			t.Fatalf("parameter %s required=%v, want %v", name, got, required)
		}
		schema := asOpenAPIObject(t, parameter["schema"], "parameter schema")
		if schema["type"] != schemaType {
			t.Fatalf("parameter %s schema type=%v, want %s", name, schema["type"], schemaType)
		}
		return
	}
	t.Fatalf("parameter %s in %s not found", name, location)
}

func openAPIContentSchemaRef(t *testing.T, owner map[string]any, contentType string) string {
	t.Helper()
	content := asOpenAPIObject(t, owner["content"], "content")
	mediaType := asOpenAPIObject(t, content[contentType], contentType)
	schema := asOpenAPIObject(t, mediaType["schema"], "schema")
	ref, ok := schema["$ref"].(string)
	if !ok {
		t.Fatalf("schema has no $ref: %#v", schema)
	}
	return ref
}

func asOpenAPIObject(t *testing.T, value any, label string) map[string]any {
	t.Helper()
	object, ok := value.(map[string]any)
	if !ok {
		t.Fatalf("%s should be an object, got %T", label, value)
	}
	return object
}

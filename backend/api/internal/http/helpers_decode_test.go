package http

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type decodeTarget struct {
	Value string `json:"value"`
}

func TestDecodeJSONWithinLimit(t *testing.T) {
	body := `{"value":"hello"}`
	r := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(body))
	rr := httptest.NewRecorder()

	var target decodeTarget
	if err := decodeJSON(rr, r, &target, 1024); err != nil {
		t.Fatalf("decodeJSON returned error for valid small body: %v", err)
	}
	if target.Value != "hello" {
		t.Fatalf("decoded value = %q, want %q", target.Value, "hello")
	}
}

func TestDecodeJSONExactlyAtLimit(t *testing.T) {
	// http.MaxBytesReader permits up to n bytes and only errors when exceeded,
	// so a body whose length is exactly maxBytes must still decode successfully.
	const wrapper = `{"value":""}` // 12 bytes around the inner string
	const limit = 64
	body := `{"value":"` + strings.Repeat("a", limit-len(wrapper)) + `"}`
	if len(body) != limit {
		t.Fatalf("test setup: body length = %d, want exactly %d", len(body), limit)
	}

	r := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(body))
	rr := httptest.NewRecorder()

	var target decodeTarget
	if err := decodeJSON(rr, r, &target, limit); err != nil {
		t.Fatalf("decodeJSON returned error for body exactly at limit: %v", err)
	}
}

func TestDecodeJSONExceedsLimitYields413(t *testing.T) {
	// Body is well-formed JSON but larger than the configured limit.
	large := `{"value":"` + strings.Repeat("a", 4096) + `"}`
	r := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(large))
	rr := httptest.NewRecorder()

	var target decodeTarget
	err := decodeJSON(rr, r, &target, 64)
	if err == nil {
		t.Fatal("decodeJSON returned nil error for oversized body, want an error")
	}

	writeDecodeError(rr, err)
	if rr.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d for oversized body", rr.Code, http.StatusRequestEntityTooLarge)
	}
}

func TestDecodeJSONMalformedYields400(t *testing.T) {
	r := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"value":`))
	rr := httptest.NewRecorder()

	var target decodeTarget
	err := decodeJSON(rr, r, &target, 1024)
	if err == nil {
		t.Fatal("decodeJSON returned nil error for malformed body, want an error")
	}

	writeDecodeError(rr, err)
	if rr.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d for malformed body", rr.Code, http.StatusBadRequest)
	}
}

func TestDecodeJSONUnknownFieldYields400(t *testing.T) {
	r := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"unexpected":1}`))
	rr := httptest.NewRecorder()

	var target decodeTarget
	err := decodeJSON(rr, r, &target, 1024)
	if err == nil {
		t.Fatal("decodeJSON returned nil error for unknown field, want an error")
	}

	writeDecodeError(rr, err)
	if rr.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d for unknown field", rr.Code, http.StatusBadRequest)
	}
}

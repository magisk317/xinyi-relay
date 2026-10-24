package http

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"testing/fstest"
)

func TestServeWebUIIndexRoutesDoNotRedirect(t *testing.T) {
	fSys := fstest.MapFS{
		"index.html":    {Data: []byte("<title>信驿 Relay WebUI</title>")},
		"assets/app.js": {Data: []byte("console.log('ok')")},
	}

	for _, path := range []string{"/", "/login", "/console", "/devices", "/index.html"} {
		t.Run(path, func(t *testing.T) {
			rec := httptest.NewRecorder()
			serveWebUI(fSys, rec, httptest.NewRequest(http.MethodGet, path, nil))

			if rec.Code != http.StatusOK {
				t.Fatalf("expected 200, got %d", rec.Code)
			}
			if location := rec.Header().Get("Location"); location != "" {
				t.Fatalf("expected no redirect, got Location %q", location)
			}
			if !strings.Contains(rec.Body.String(), "信驿 Relay WebUI") {
				t.Fatalf("expected index.html body, got %q", rec.Body.String())
			}
		})
	}
}

func TestServeWebUIServesExistingAssets(t *testing.T) {
	fSys := fstest.MapFS{
		"index.html":    {Data: []byte("<title>信驿 Relay WebUI</title>")},
		"assets/app.js": {Data: []byte("console.log('ok')")},
	}

	rec := httptest.NewRecorder()
	serveWebUI(fSys, rec, httptest.NewRequest(http.MethodGet, "/assets/app.js", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	if got := rec.Body.String(); got != "console.log('ok')" {
		t.Fatalf("expected asset body, got %q", got)
	}
}

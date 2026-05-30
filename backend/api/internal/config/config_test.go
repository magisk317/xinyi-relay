package config

import "testing"

func TestValidateAllowsSecureConfig(t *testing.T) {
	cfg := Config{
		AppEnv:        "production",
		CORSOrigin:    "https://app.example.com",
		AdminPassword: "a-strong-unique-secret",
		DatabaseURL:   "postgres://relay:secret@db:5432/relay?sslmode=require",
	}
	issues, err := cfg.Validate()
	if err != nil {
		t.Fatalf("expected no error for secure production config, got %v (issues=%v)", err, issues)
	}
	if len(issues) != 0 {
		t.Fatalf("expected no issues, got %v", issues)
	}
}

func TestValidateProductionRejectsInsecureDefaults(t *testing.T) {
	cfg := Config{
		AppEnv:        "production",
		CORSOrigin:    "*",
		AdminPassword: "relay-pass",
		DatabaseURL:   "postgres://relay:relay@postgres:5432/relay?sslmode=disable",
	}
	issues, err := cfg.Validate()
	if err == nil {
		t.Fatal("expected production with insecure defaults to error")
	}
	if len(issues) != 3 {
		t.Fatalf("expected 3 issues (cors, password, sslmode), got %d: %v", len(issues), issues)
	}
}

func TestValidateProductionHonorsAllowInsecure(t *testing.T) {
	cfg := Config{
		AppEnv:        "production",
		CORSOrigin:    "*",
		AdminPassword: "relay-pass",
		DatabaseURL:   "postgres://relay:relay@postgres:5432/relay?sslmode=disable",
		AllowInsecure: true,
	}
	issues, err := cfg.Validate()
	if err != nil {
		t.Fatalf("expected RELAY_ALLOW_INSECURE to suppress the error, got %v", err)
	}
	if len(issues) != 3 {
		t.Fatalf("expected issues still reported when overridden, got %v", issues)
	}
}

func TestValidateNonProductionIsAdvisoryOnly(t *testing.T) {
	cfg := Config{
		AppEnv:        "development",
		CORSOrigin:    "*",
		AdminPassword: "relay-pass",
		DatabaseURL:   "postgres://relay:relay@postgres:5432/relay?sslmode=disable",
	}
	issues, err := cfg.Validate()
	if err != nil {
		t.Fatalf("non-production must not error, got %v", err)
	}
	if len(issues) == 0 {
		t.Fatal("expected advisory issues to be reported in development")
	}
}

func TestValidateWeakPasswordDetectionIsCaseInsensitive(t *testing.T) {
	cfg := Config{
		AppEnv:        "production",
		CORSOrigin:    "https://app.example.com",
		AdminPassword: "Relay-Pass",
		DatabaseURL:   "postgres://relay:secret@db:5432/relay?sslmode=require",
	}
	issues, err := cfg.Validate()
	if err == nil {
		t.Fatal("expected weak password (different case) to be rejected in production")
	}
	if len(issues) != 1 {
		t.Fatalf("expected exactly the weak-password issue, got %v", issues)
	}
}

func TestValidateEmptyAdminPasswordIsNotFlagged(t *testing.T) {
	// An empty admin password simply disables bootstrap; it is not an insecure
	// default and must not be reported.
	cfg := Config{
		AppEnv:        "production",
		CORSOrigin:    "https://app.example.com",
		AdminPassword: "",
		DatabaseURL:   "postgres://relay:secret@db:5432/relay?sslmode=require",
	}
	issues, err := cfg.Validate()
	if err != nil || len(issues) != 0 {
		t.Fatalf("expected empty admin password to be fine, got err=%v issues=%v", err, issues)
	}
}

func TestIsProductionCaseInsensitive(t *testing.T) {
	for _, env := range []string{"production", "Production", "PRODUCTION", " production "} {
		if !(Config{AppEnv: env}).IsProduction() {
			t.Errorf("expected %q to be production", env)
		}
	}
	for _, env := range []string{"development", "staging", ""} {
		if (Config{AppEnv: env}).IsProduction() {
			t.Errorf("expected %q to not be production", env)
		}
	}
}

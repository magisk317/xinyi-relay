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

func TestDSNHasInsecureSSL(t *testing.T) {
	cases := []struct {
		name string
		dsn  string
		want bool
	}{
		{"url disable", "postgres://relay:relay@db:5432/relay?sslmode=disable", true},
		{"url disable uppercase", "postgres://relay:relay@db:5432/relay?sslmode=DISABLE", true},
		{"url require", "postgres://relay:relay@db:5432/relay?sslmode=require", false},
		{"url no sslmode", "postgres://relay:relay@db:5432/relay", false},
		{"keyword disable", "host=db port=5432 user=relay sslmode=disable", true},
		{"keyword require", "host=db port=5432 user=relay sslmode=require", false},
		{"empty", "", false},
		// "sslmode=disable" embedded in a password must not be a false positive.
		{"false positive in password", "postgres://relay:sslmode=disable@db:5432/relay?sslmode=require", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := dsnHasInsecureSSL(tc.dsn); got != tc.want {
				t.Fatalf("dsnHasInsecureSSL(%q) = %v, want %v", tc.dsn, got, tc.want)
			}
		})
	}
}

func TestLoadRecordRetentionDefaults(t *testing.T) {
	for _, key := range []string{"RELAY_RECORDS_FOLLOW_DEVICE_LIMITS", "RELAY_RECORDS_MAX_PER_USER", "RELAY_RECORDS_RETENTION_DAYS"} {
		t.Setenv(key, "")
	}
	cfg := Load()
	if !cfg.RecordsFollowDeviceLimits {
		t.Error("expected RecordsFollowDeviceLimits to default to true")
	}
	if cfg.RecordsMaxPerUser != 0 {
		t.Errorf("expected RecordsMaxPerUser default 0, got %d", cfg.RecordsMaxPerUser)
	}
	if cfg.RecordsRetentionDays != 0 {
		t.Errorf("expected RecordsRetentionDays default 0, got %d", cfg.RecordsRetentionDays)
	}
}

func TestLoadRecordRetentionOverrides(t *testing.T) {
	t.Setenv("RELAY_RECORDS_FOLLOW_DEVICE_LIMITS", "false")
	t.Setenv("RELAY_RECORDS_MAX_PER_USER", "2000")
	t.Setenv("RELAY_RECORDS_RETENTION_DAYS", "30")
	cfg := Load()
	if cfg.RecordsFollowDeviceLimits {
		t.Error("expected RecordsFollowDeviceLimits=false from env")
	}
	if cfg.RecordsMaxPerUser != 2000 {
		t.Errorf("expected RecordsMaxPerUser 2000, got %d", cfg.RecordsMaxPerUser)
	}
	if cfg.RecordsRetentionDays != 30 {
		t.Errorf("expected RecordsRetentionDays 30, got %d", cfg.RecordsRetentionDays)
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

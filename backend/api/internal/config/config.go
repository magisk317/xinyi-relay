package config

import (
	"fmt"
	"net/url"
	"os"
	"strings"
)

type Config struct {
	AppEnv        string
	HTTPAddr      string
	LocalBaseURL  string
	PublicBaseURL string
	CORSOrigin    string
	DatabaseURL   string
	AdminUsername string
	AdminPassword string
	AllowInsecure bool
	LogFile       string
	LogLevel      string
}

func Load() Config {
	return Config{
		AppEnv:        getEnv("RELAY_APP_ENV", "development"),
		HTTPAddr:      getEnv("RELAY_HTTP_ADDR", ":8080"),
		LocalBaseURL:  getEnv("RELAY_LOCAL_BASE_URL", "http://localhost:8080"),
		PublicBaseURL: getEnv("RELAY_PUBLIC_BASE_URL", ""),
		CORSOrigin:    getEnv("RELAY_CORS_ORIGIN", "http://localhost:8080"),
		DatabaseURL:   getEnv("RELAY_DATABASE_URL", ""),
		AdminUsername: getEnv("RELAY_ADMIN_USERNAME", ""),
		AdminPassword: getEnv("RELAY_ADMIN_PASSWORD", ""),
		AllowInsecure: getEnvBool("RELAY_ALLOW_INSECURE", false),
		LogFile:       getEnv("RELAY_LOG_FILE", ""),
		LogLevel:      getEnv("RELAY_LOG_LEVEL", "info"),
	}
}

// IsProduction reports whether the backend is configured to run in a production
// environment, where insecure defaults must not be tolerated.
func (c Config) IsProduction() bool {
	return strings.EqualFold(strings.TrimSpace(c.AppEnv), "production")
}

// weakAdminPasswords are well-known development passwords (shipped in
// .env.example or commonly copy-pasted) that must never reach production.
var weakAdminPasswords = map[string]struct{}{
	"relay-pass": {},
	"relay":      {},
	"password":   {},
	"admin":      {},
	"changeme":   {},
	"postgres":   {},
}

// Validate inspects the configuration for insecure settings. It always returns
// the list of human-readable issues found. In production (and unless
// RELAY_ALLOW_INSECURE=true) any issue also yields a non-nil error so the
// process can refuse to start; in non-production environments issues are only
// advisory and err is nil.
func (c Config) Validate() (issues []string, err error) {
	if c.CORSOrigin == "*" {
		issues = append(issues, "RELAY_CORS_ORIGIN is \"*\": a wildcard origin cannot be combined with credentialed requests; set it to your exact frontend origin")
	}
	if pw := strings.TrimSpace(c.AdminPassword); pw != "" {
		if _, weak := weakAdminPasswords[strings.ToLower(pw)]; weak {
			issues = append(issues, "RELAY_ADMIN_PASSWORD is a well-known weak/default password; set a strong, unique value")
		}
	}
	if dsnHasInsecureSSL(c.DatabaseURL) {
		issues = append(issues, "RELAY_DATABASE_URL uses sslmode=disable: database traffic is unencrypted; use sslmode=require (or stronger) outside a trusted private network")
	}

	if len(issues) == 0 {
		return nil, nil
	}
	if c.IsProduction() && !c.AllowInsecure {
		return issues, fmt.Errorf("refusing to start in production with insecure configuration (%d issue(s)); fix them or set RELAY_ALLOW_INSECURE=true to override", len(issues))
	}
	return issues, nil
}

// dsnHasInsecureSSL reports whether a PostgreSQL DSN explicitly disables TLS.
// It parses the DSN rather than doing a raw substring match so that an
// "sslmode=disable" appearing outside of an actual parameter (e.g. inside a
// password or path) does not produce a false positive.
func dsnHasInsecureSSL(dsn string) bool {
	return strings.EqualFold(sslModeFromDSN(dsn), "disable")
}

// sslModeFromDSN extracts the sslmode parameter from a PostgreSQL DSN given in
// either URL form (postgres://user:pass@host/db?sslmode=...) or keyword/value
// form (host=... sslmode=...). It returns an empty string when sslmode is not
// specified.
func sslModeFromDSN(dsn string) string {
	dsn = strings.TrimSpace(dsn)
	if dsn == "" {
		return ""
	}
	if u, err := url.Parse(dsn); err == nil && u.Scheme != "" {
		return u.Query().Get("sslmode")
	}
	for _, field := range strings.Fields(dsn) {
		if k, v, ok := strings.Cut(field, "="); ok && strings.EqualFold(strings.TrimSpace(k), "sslmode") {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func getEnvBool(key string, fallback bool) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}

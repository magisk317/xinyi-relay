package config

import (
	"fmt"
	"log"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	AppEnv               string
	HTTPAddr             string
	LocalBaseURL         string
	PublicBaseURL        string
	CORSOrigin           string
	DatabaseURL          string
	AdminUsername        string
	AdminPassword        string
	AllowInsecure        bool
	LoginRateLimitMax    int
	LoginRateLimitWindow time.Duration
	TrustProxyHeaders    bool
	LogFile              string
	LogLevel             string

	// Relay record retention. Pruning runs after each ingest batch.
	// RecordsFollowDeviceLimits applies the per-type history limits the device
	// already syncs in its config snapshot. RecordsMaxPerUser and
	// RecordsRetentionDays are optional global caps (0 = disabled).
	RecordsFollowDeviceLimits bool
	RecordsMaxPerUser         int
	RecordsRetentionDays      int
}

func Load() Config {
	return Config{
		AppEnv:               getEnv("RELAY_APP_ENV", "development"),
		HTTPAddr:             getEnv("RELAY_HTTP_ADDR", ":8080"),
		LocalBaseURL:         getEnv("RELAY_LOCAL_BASE_URL", "http://localhost:8080"),
		PublicBaseURL:        getEnv("RELAY_PUBLIC_BASE_URL", ""),
		CORSOrigin:           getEnv("RELAY_CORS_ORIGIN", "http://localhost:8080"),
		DatabaseURL:          getEnv("RELAY_DATABASE_URL", ""),
		AdminUsername:        getEnv("RELAY_ADMIN_USERNAME", ""),
		AdminPassword:        getEnv("RELAY_ADMIN_PASSWORD", ""),
		AllowInsecure:        getEnvBool("RELAY_ALLOW_INSECURE", false),
		LoginRateLimitMax:    getEnvInt("RELAY_LOGIN_RATE_LIMIT", 10),
		LoginRateLimitWindow: getEnvDuration("RELAY_LOGIN_RATE_WINDOW", 5*time.Minute),
		TrustProxyHeaders:    getEnvBool("RELAY_TRUST_PROXY_HEADERS", true),
		LogFile:              getEnv("RELAY_LOG_FILE", ""),
		LogLevel:             getEnv("RELAY_LOG_LEVEL", "info"),

		RecordsFollowDeviceLimits: getEnvBool("RELAY_RECORDS_FOLLOW_DEVICE_LIMITS", true),
		RecordsMaxPerUser:         getEnvInt("RELAY_RECORDS_MAX_PER_USER", 0),
		RecordsRetentionDays:      getEnvInt("RELAY_RECORDS_RETENTION_DAYS", 0),
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

// getEnvInt parses an integer env var. A non-positive value is returned as-is
// (callers may treat e.g. 0 as "disabled"); only an unparseable value falls
// back, and that is logged so a typo in security-sensitive config is visible.
func getEnvInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		log.Printf("[config] invalid integer for %s=%q, using fallback %d: %v", key, value, fallback, err)
		return fallback
	}
	return parsed
}

// getEnvDuration parses a Go duration env var, logging and falling back when the
// value is unparseable or non-positive so misconfiguration is not silent.
func getEnvDuration(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		log.Printf("[config] invalid duration for %s=%q, using fallback %s: %v", key, value, fallback, err)
		return fallback
	}
	if parsed <= 0 {
		log.Printf("[config] non-positive duration for %s=%s, using fallback %s", key, parsed, fallback)
		return fallback
	}
	return parsed
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

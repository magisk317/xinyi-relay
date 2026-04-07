package config

import "os"

type Config struct {
	AppEnv        string
	HTTPAddr      string
	LocalBaseURL  string
	PublicBaseURL string
	CORSOrigin    string
	DatabaseURL   string
	AdminUsername string
	AdminPassword string
}

func Load() Config {
	return Config{
		AppEnv:        getEnv("RELAY_APP_ENV", "development"),
		HTTPAddr:      getEnv("RELAY_HTTP_ADDR", ":8080"),
		LocalBaseURL:  getEnv("RELAY_LOCAL_BASE_URL", "http://localhost:8080"),
		PublicBaseURL: getEnv("RELAY_PUBLIC_BASE_URL", ""),
		CORSOrigin:    getEnv("RELAY_CORS_ORIGIN", "*"),
		DatabaseURL:   getEnv("RELAY_DATABASE_URL", ""),
		AdminUsername: getEnv("RELAY_ADMIN_USERNAME", ""),
		AdminPassword: getEnv("RELAY_ADMIN_PASSWORD", ""),
	}
}

func getEnv(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

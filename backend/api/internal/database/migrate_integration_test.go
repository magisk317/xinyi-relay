package database

import (
	"context"
	"os"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
)

// TestMigrateAgainstPostgres exercises the full migration runner against a real
// PostgreSQL instance. It only runs when RELAY_TEST_DATABASE_URL is set, so it
// is skipped in the default CI run (which has no database).
func TestMigrateAgainstPostgres(t *testing.T) {
	dsn := os.Getenv("RELAY_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("RELAY_TEST_DATABASE_URL not set; skipping postgres integration test")
	}

	ctx := context.Background()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	defer pool.Close()

	resetSchema(ctx, t, pool)
	db := &Database{Pool: pool}

	// First run applies the baseline.
	if err := db.migrate(ctx); err != nil {
		t.Fatalf("first migrate: %v", err)
	}
	if got := appliedCount(ctx, t, pool); got == 0 {
		t.Fatal("expected schema_migrations to record applied migrations")
	}
	if !tableExists(ctx, t, pool, "users") {
		t.Fatal("expected users table to exist after migrate")
	}

	// Second run is a no-op and must not error (idempotency).
	before := appliedCount(ctx, t, pool)
	if err := db.migrate(ctx); err != nil {
		t.Fatalf("second migrate: %v", err)
	}
	if after := appliedCount(ctx, t, pool); after != before {
		t.Fatalf("re-running migrate changed applied count: before=%d after=%d", before, after)
	}

	// A tampered checksum for an applied migration must be rejected.
	if _, err := pool.Exec(ctx, `UPDATE schema_migrations SET checksum = 'tampered' WHERE version = 1`); err != nil {
		t.Fatalf("tamper checksum: %v", err)
	}
	if err := db.migrate(ctx); err == nil {
		t.Fatal("expected checksum mismatch error, got nil")
	}
}

func resetSchema(ctx context.Context, t *testing.T, pool *pgxpool.Pool) {
	t.Helper()
	if _, err := pool.Exec(ctx, `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`); err != nil {
		t.Fatalf("reset schema: %v", err)
	}
}

func appliedCount(ctx context.Context, t *testing.T, pool *pgxpool.Pool) int64 {
	t.Helper()
	var count int64
	if err := pool.QueryRow(ctx, `SELECT COUNT(*) FROM schema_migrations`).Scan(&count); err != nil {
		t.Fatalf("count migrations: %v", err)
	}
	return count
}

func tableExists(ctx context.Context, t *testing.T, pool *pgxpool.Pool, name string) bool {
	t.Helper()
	var exists bool
	err := pool.QueryRow(ctx, `SELECT to_regclass($1) IS NOT NULL`, name).Scan(&exists)
	if err != nil {
		t.Fatalf("check table %s: %v", name, err)
	}
	return exists
}

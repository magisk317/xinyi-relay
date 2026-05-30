package database

import (
	"context"
	"os"
	"sync"
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

// TestMigrateLegacySchemaAgainstPostgres covers the highest-risk upgrade path:
// a database whose tables were created by the old startup DDL (no
// schema_migrations table). The baseline must adopt it without recreating
// objects or losing data, and record version 1.
func TestMigrateLegacySchemaAgainstPostgres(t *testing.T) {
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

	// Recreate a legacy table the way the old migrate.go would have, then seed a
	// row that must survive the migration.
	if _, err := pool.Exec(ctx, `
		CREATE TABLE users (
			id BIGSERIAL PRIMARY KEY,
			username TEXT NOT NULL UNIQUE,
			password_hash TEXT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
		)
	`); err != nil {
		t.Fatalf("create legacy users table: %v", err)
	}
	if _, err := pool.Exec(ctx, `INSERT INTO users (username, password_hash) VALUES ('legacy', 'x')`); err != nil {
		t.Fatalf("seed legacy user: %v", err)
	}

	db := &Database{Pool: pool}
	if err := db.migrate(ctx); err != nil {
		t.Fatalf("migrate legacy schema: %v", err)
	}

	// Baseline is recorded as applied.
	if name := migrationName(ctx, t, pool, 1); name != "0001_init.sql" {
		t.Fatalf("expected version 1 recorded as 0001_init.sql, got %q", name)
	}

	// Legacy data is preserved and the table was not recreated.
	var count int64
	if err := pool.QueryRow(ctx, `SELECT COUNT(*) FROM users WHERE username = 'legacy'`).Scan(&count); err != nil {
		t.Fatalf("count legacy user: %v", err)
	}
	if count != 1 {
		t.Fatalf("expected legacy user preserved, got count=%d", count)
	}

	// Tables only present in the baseline were created during adoption.
	if !tableExists(ctx, t, pool, "devices") {
		t.Fatal("expected devices table to exist after adopting legacy schema")
	}
}

// TestMigrateConcurrencyAdvisoryLockSerialization runs migrate concurrently to
// exercise the advisory-lock serialization: every call must succeed and each
// version must be recorded exactly once.
func TestMigrateConcurrencyAdvisoryLockSerialization(t *testing.T) {
	dsn := os.Getenv("RELAY_TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("RELAY_TEST_DATABASE_URL not set; skipping postgres integration test")
	}

	ctx := context.Background()

	const concurrency = 6
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		t.Fatalf("parse config: %v", err)
	}
	cfg.MaxConns = concurrency + 2
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	defer pool.Close()

	resetSchema(ctx, t, pool)
	db := &Database{Pool: pool}

	var wg sync.WaitGroup
	errs := make([]error, concurrency)
	for i := 0; i < concurrency; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			errs[idx] = db.migrate(ctx)
		}(i)
	}
	wg.Wait()

	for i, err := range errs {
		if err != nil {
			t.Fatalf("concurrent migrate[%d] failed: %v", i, err)
		}
	}

	// No duplicate version rows: total count equals distinct count.
	var total, distinct int64
	if err := pool.QueryRow(ctx, `SELECT COUNT(*), COUNT(DISTINCT version) FROM schema_migrations`).Scan(&total, &distinct); err != nil {
		t.Fatalf("count migrations: %v", err)
	}
	if total != distinct {
		t.Fatalf("expected each version recorded once, got total=%d distinct=%d", total, distinct)
	}
	if total == 0 {
		t.Fatal("expected at least one applied migration")
	}
}

func migrationName(ctx context.Context, t *testing.T, pool *pgxpool.Pool, version int64) string {
	t.Helper()
	var name string
	if err := pool.QueryRow(ctx, `SELECT name FROM schema_migrations WHERE version = $1`, version).Scan(&name); err != nil {
		t.Fatalf("lookup migration %d: %v", version, err)
	}
	return name
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

package database

import (
	"context"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"fmt"
	"io/fs"
	"sort"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

const migrationsDir = "migrations"

// migrationAdvisoryLockKey serializes migrations across concurrent instances so
// only one process applies pending migrations at a time.
const migrationAdvisoryLockKey int64 = 472407101

// migrationLockTimeout bounds how long we wait to acquire the advisory lock so a
// crashed or stuck holder cannot block startup indefinitely. It only affects the
// lock-acquisition wait; it is reset before the migrations themselves run.
const migrationLockTimeout = "30s"

const schemaMigrationsDDL = `
CREATE TABLE IF NOT EXISTS schema_migrations (
	version BIGINT PRIMARY KEY,
	name TEXT NOT NULL,
	checksum TEXT NOT NULL,
	applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)
`

type migration struct {
	version  int64
	name     string
	checksum string
	sql      string
}

type appliedMigration struct {
	version  int64
	checksum string
}

// loadMigrations reads and validates the embedded migration files, returning
// them ordered by version. It fails fast on unparsable names or duplicate
// versions so problems surface in tests rather than at startup.
func loadMigrations() ([]migration, error) {
	entries, err := fs.ReadDir(migrationsFS, migrationsDir)
	if err != nil {
		return nil, fmt.Errorf("read migrations dir: %w", err)
	}

	migrations := make([]migration, 0, len(entries))
	seen := make(map[int64]string)
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sql") {
			continue
		}
		version, err := parseMigrationVersion(entry.Name())
		if err != nil {
			return nil, err
		}
		if existing, dup := seen[version]; dup {
			return nil, fmt.Errorf("duplicate migration version %d: %s and %s", version, existing, entry.Name())
		}
		seen[version] = entry.Name()

		content, err := fs.ReadFile(migrationsFS, migrationsDir+"/"+entry.Name())
		if err != nil {
			return nil, fmt.Errorf("read migration %s: %w", entry.Name(), err)
		}
		if strings.TrimSpace(string(content)) == "" {
			return nil, fmt.Errorf("migration %s is empty", entry.Name())
		}
		sum := sha256.Sum256(content)
		migrations = append(migrations, migration{
			version:  version,
			name:     entry.Name(),
			checksum: hex.EncodeToString(sum[:]),
			sql:      string(content),
		})
	}

	sort.Slice(migrations, func(i, j int) bool {
		return migrations[i].version < migrations[j].version
	})
	return migrations, nil
}

// parseMigrationVersion extracts the leading numeric version from a file name
// such as "0001_init.sql".
func parseMigrationVersion(name string) (int64, error) {
	base := strings.TrimSuffix(name, ".sql")
	prefix := base
	if idx := strings.Index(base, "_"); idx >= 0 {
		prefix = base[:idx]
	}
	version, err := strconv.ParseInt(prefix, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid migration filename %q: expected leading numeric version", name)
	}
	if version <= 0 {
		return 0, fmt.Errorf("invalid migration version in %q: must be positive", name)
	}
	return version, nil
}

func (db *Database) migrate(ctx context.Context) error {
	migrations, err := loadMigrations()
	if err != nil {
		return err
	}

	conn, err := db.Pool.Acquire(ctx)
	if err != nil {
		return fmt.Errorf("acquire migration connection: %w", err)
	}
	defer conn.Release()

	// Bound the wait for the advisory lock; lock_timeout also covers
	// pg_advisory_lock, so a stuck holder fails fast instead of hanging forever.
	if _, err := conn.Exec(ctx, fmt.Sprintf(`SET lock_timeout = '%s'`, migrationLockTimeout)); err != nil {
		return fmt.Errorf("set migration lock_timeout: %w", err)
	}
	if _, err := conn.Exec(ctx, `SELECT pg_advisory_lock($1)`, migrationAdvisoryLockKey); err != nil {
		return fmt.Errorf("acquire migration lock (waited up to %s): %w", migrationLockTimeout, err)
	}
	defer func() {
		_, _ = conn.Exec(ctx, `SELECT pg_advisory_unlock($1)`, migrationAdvisoryLockKey)
	}()
	// Restore the default so migrations themselves are not subject to the
	// short acquisition timeout.
	if _, err := conn.Exec(ctx, `SET lock_timeout = DEFAULT`); err != nil {
		return fmt.Errorf("reset lock_timeout: %w", err)
	}

	if _, err := conn.Exec(ctx, schemaMigrationsDDL); err != nil {
		return fmt.Errorf("ensure schema_migrations: %w", err)
	}

	applied, err := loadAppliedMigrations(ctx, conn)
	if err != nil {
		return err
	}

	for _, m := range migrations {
		if record, ok := applied[m.version]; ok {
			if record.checksum != m.checksum {
				return fmt.Errorf(
					"migration %d (%s) checksum mismatch: applied %s, current %s; migrations are immutable once applied",
					m.version, m.name, record.checksum, m.checksum,
				)
			}
			continue
		}
		if err := applyMigration(ctx, conn, m); err != nil {
			return err
		}
	}
	return nil
}

func loadAppliedMigrations(ctx context.Context, conn *pgxpool.Conn) (map[int64]appliedMigration, error) {
	rows, err := conn.Query(ctx, `SELECT version, checksum FROM schema_migrations`)
	if err != nil {
		return nil, fmt.Errorf("load applied migrations: %w", err)
	}
	defer rows.Close()

	applied := make(map[int64]appliedMigration)
	for rows.Next() {
		var record appliedMigration
		if err := rows.Scan(&record.version, &record.checksum); err != nil {
			return nil, fmt.Errorf("scan applied migration: %w", err)
		}
		applied[record.version] = record
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate applied migrations: %w", err)
	}
	return applied, nil
}

// applyMigration runs a single migration and records it atomically. The DDL and
// the bookkeeping insert share one transaction, so a failed migration leaves no
// partial state and is retried on the next startup.
func applyMigration(ctx context.Context, conn *pgxpool.Conn, m migration) error {
	tx, err := conn.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin migration %d (%s): %w", m.version, m.name, err)
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()

	if _, err := tx.Exec(ctx, m.sql); err != nil {
		return fmt.Errorf("apply migration %d (%s): %w", m.version, m.name, err)
	}
	if _, err := tx.Exec(ctx,
		`INSERT INTO schema_migrations (version, name, checksum) VALUES ($1, $2, $3)`,
		m.version, m.name, m.checksum,
	); err != nil {
		return fmt.Errorf("record migration %d (%s): %w", m.version, m.name, err)
	}
	return tx.Commit(ctx)
}

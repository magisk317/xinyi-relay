package database

import "testing"

func TestLoadMigrationsAreOrderedUniqueAndNonEmpty(t *testing.T) {
	migrations, err := loadMigrations()
	if err != nil {
		t.Fatalf("loadMigrations() error = %v", err)
	}
	if len(migrations) == 0 {
		t.Fatal("expected at least one embedded migration")
	}

	seen := make(map[int64]bool)
	var prev int64
	for i, m := range migrations {
		if m.version <= 0 {
			t.Errorf("migration %q has non-positive version %d", m.name, m.version)
		}
		if seen[m.version] {
			t.Errorf("duplicate migration version %d", m.version)
		}
		seen[m.version] = true
		if i > 0 && m.version <= prev {
			t.Errorf("migrations not strictly ordered: %d came after %d", m.version, prev)
		}
		prev = m.version
		if m.sql == "" {
			t.Errorf("migration %q has empty sql", m.name)
		}
		if len(m.checksum) != 64 {
			t.Errorf("migration %q checksum %q is not a sha256 hex digest", m.name, m.checksum)
		}
	}
}

func TestLoadMigrationsIncludesBaseline(t *testing.T) {
	migrations, err := loadMigrations()
	if err != nil {
		t.Fatalf("loadMigrations() error = %v", err)
	}
	if migrations[0].version != 1 {
		t.Fatalf("expected first migration version 1, got %d (%s)", migrations[0].version, migrations[0].name)
	}
	if migrations[0].name != "0001_init.sql" {
		t.Fatalf("expected first migration to be 0001_init.sql, got %s", migrations[0].name)
	}
}

func TestParseMigrationVersion(t *testing.T) {
	cases := []struct {
		name    string
		want    int64
		wantErr bool
	}{
		{name: "0001_init.sql", want: 1},
		{name: "0002_add_login_attempts.sql", want: 2},
		{name: "10_no_pad.sql", want: 10},
		{name: "0003.sql", want: 3},
		{name: "init.sql", wantErr: true},
		{name: "0000_zero.sql", wantErr: true},
		{name: "-1_negative.sql", wantErr: true},
	}
	for _, tc := range cases {
		got, err := parseMigrationVersion(tc.name)
		if tc.wantErr {
			if err == nil {
				t.Errorf("parseMigrationVersion(%q) expected error, got version %d", tc.name, got)
			}
			continue
		}
		if err != nil {
			t.Errorf("parseMigrationVersion(%q) unexpected error: %v", tc.name, err)
			continue
		}
		if got != tc.want {
			t.Errorf("parseMigrationVersion(%q) = %d, want %d", tc.name, got, tc.want)
		}
	}
}

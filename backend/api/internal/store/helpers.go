package store

// nullableString maps an empty string to a nil SQL value so optional text
// columns are stored as NULL rather than an empty string. Shared across the
// per-domain store files.
func nullableString(value string) any {
	if value == "" {
		return nil
	}
	return value
}

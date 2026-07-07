package store

import (
	"errors"

	"github.com/magisk317/xinyi-relay/backend/api/internal/database"
)

var ErrNotFound = errors.New("not found")
var ErrConflict = errors.New("conflict")
var ErrStaleBaseRevision = errors.New("stale base revision")
var ErrPendingCommandExists = errors.New("pending command exists")
var ErrOutdatedMirrorRevision = errors.New("outdated mirror revision")
var ErrUnsupportedCommandStatus = errors.New("unsupported command status")

type Store struct {
	db *database.Database
}

func New(db *database.Database) *Store {
	return &Store{db: db}
}

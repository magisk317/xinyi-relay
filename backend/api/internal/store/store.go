package store

import (
	"errors"

	"github.com/magisk317/xinyi-relay/backend/api/internal/database"
)

var ErrNotFound = errors.New("not found")
var ErrConflict = errors.New("conflict")

type Store struct {
	db *database.Database
}

func New(db *database.Database) *Store {
	return &Store{db: db}
}

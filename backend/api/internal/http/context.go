package http

import (
	"context"

	"github.com/magisk317/xinyi-relay/backend/api/internal/store"
)

type authKind string

const (
	authKindSession authKind = "session"
	authKindDesktop authKind = "desktop_session"
	authKindDevice  authKind = "device"
)

type authContext struct {
	Kind           authKind
	User           store.User
	Session        store.Session
	DesktopSession store.DesktopSession
	Device         store.Device
}

type contextKey string

const authContextKey contextKey = "auth_context"

func withAuthContext(ctx context.Context, value authContext) context.Context {
	return context.WithValue(ctx, authContextKey, value)
}

func readAuthContext(ctx context.Context) (authContext, bool) {
	value, ok := ctx.Value(authContextKey).(authContext)
	return value, ok
}

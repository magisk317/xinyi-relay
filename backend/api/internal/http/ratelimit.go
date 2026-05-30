package http

import (
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

// rateLimiter is a small in-process fixed-window limiter keyed by an arbitrary
// string (e.g. client IP or username). It is safe for concurrent use.
type rateLimiter struct {
	mu      sync.Mutex
	entries map[string]*rlEntry
	max     int
	window  time.Duration
	now     func() time.Time
}

type rlEntry struct {
	count       int
	windowStart time.Time
}

func newRateLimiter(max int, window time.Duration) *rateLimiter {
	return &rateLimiter{
		entries: make(map[string]*rlEntry),
		max:     max,
		window:  window,
		now:     time.Now,
	}
}

// Allow records an attempt for key and reports whether it is permitted. A
// nil limiter, a non-positive max, or a non-positive window all disable
// limiting (always allow).
func (l *rateLimiter) Allow(key string) bool {
	if l == nil || l.max <= 0 || l.window <= 0 {
		return true
	}
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()
	l.pruneLocked(now)

	e := l.entries[key]
	if e == nil || now.Sub(e.windowStart) >= l.window {
		l.entries[key] = &rlEntry{count: 1, windowStart: now}
		return true
	}
	if e.count >= l.max {
		return false
	}
	e.count++
	return true
}

// Reset clears the counter for key (e.g. after a successful login so a valid
// user is not penalised for earlier typos).
func (l *rateLimiter) Reset(key string) {
	if l == nil {
		return
	}
	l.mu.Lock()
	delete(l.entries, key)
	l.mu.Unlock()
}

// pruneLocked drops expired entries to bound memory growth. Callers must hold l.mu.
func (l *rateLimiter) pruneLocked(now time.Time) {
	for k, e := range l.entries {
		if now.Sub(e.windowStart) >= l.window {
			delete(l.entries, k)
		}
	}
}

// clientIP extracts the best-effort client IP from a request. The backend runs
// behind a trusted reverse proxy (Caddy) which sets X-Forwarded-For/X-Real-IP,
// so those are preferred; otherwise the transport remote address is used.
func clientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		if first := strings.TrimSpace(strings.Split(xff, ",")[0]); first != "" {
			return first
		}
	}
	if xrip := strings.TrimSpace(r.Header.Get("X-Real-IP")); xrip != "" {
		return xrip
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

package realtime

import (
	"encoding/json"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const (
	// writeWait bounds a single socket write; a peer that cannot accept a
	// frame within it is treated as dead.
	writeWait = 10 * time.Second

	// defaultPingPeriod is how often the server pushes a protocol-level
	// ping. Browsers and OkHttp answer pings with pongs automatically, so
	// this is what keeps NATs, proxies, and the handler read deadline from
	// reaping live connections. It must stay below the read deadline set
	// by the WebSocket handler.
	defaultPingPeriod = 25 * time.Second

	// sendBuffer is how many events may queue for one peer. A peer that
	// falls this far behind is dropped instead of stalling broadcasters.
	sendBuffer = 64
)

type Event struct {
	Type string `json:"type"`
	Time string `json:"time"`
	Data any    `json:"data"`
}

// client is one registered socket. Every write funnels through the send
// channel so no two goroutines ever touch the socket concurrently.
type client struct {
	conn         *websocket.Conn
	send         chan []byte
	pingInterval time.Duration
	closeOnce    sync.Once
}

func (c *client) closeSend() {
	c.closeOnce.Do(func() { close(c.send) })
}

// writePump owns all writes to the socket: queued events plus the periodic
// protocol ping. It returns when the hub closes the channel (Unregister) or
// a write fails, dropping the peer.
func (c *client) writePump() {
	ticker := time.NewTicker(c.pingInterval)
	defer ticker.Stop()
	defer c.conn.Close()
	for {
		select {
		case body, ok := <-c.send:
			if !ok {
				_ = c.conn.WriteControl(
					websocket.CloseMessage,
					websocket.FormatCloseMessage(websocket.CloseNormalClosure, "shutdown"),
					time.Now().Add(writeWait),
				)
				return
			}
			_ = c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.TextMessage, body); err != nil {
				return
			}
		case <-ticker.C:
			_ = c.conn.WriteControl(websocket.PingMessage, nil, time.Now().Add(writeWait))
		}
	}
}

type Hub struct {
	mu           sync.RWMutex
	clients      map[int64]map[*client]struct{}
	pingInterval time.Duration
}

func NewHub() *Hub {
	return &Hub{
		clients:      make(map[int64]map[*client]struct{}),
		pingInterval: defaultPingPeriod,
	}
}

// Register adds the socket to the user fan-out set and starts its writer
// goroutine. The returned client must be handed to Unregister exactly once.
func (h *Hub) Register(userID int64, conn *websocket.Conn) *client {
	interval := h.pingInterval
	if interval <= 0 {
		interval = defaultPingPeriod
	}
	c := &client{
		conn:         conn,
		send:         make(chan []byte, sendBuffer),
		pingInterval: interval,
	}
	h.mu.Lock()
	if h.clients[userID] == nil {
		h.clients[userID] = make(map[*client]struct{})
	}
	h.clients[userID][c] = struct{}{}
	h.mu.Unlock()
	go c.writePump()
	return c
}

// Unregister removes the socket and stops its writer. Safe to call twice.
func (h *Hub) Unregister(userID int64, c *client) {
	h.mu.Lock()
	if userClients := h.clients[userID]; userClients != nil {
		if _, ok := userClients[c]; ok {
			delete(userClients, c)
			c.closeSend()
			if len(userClients) == 0 {
				delete(h.clients, userID)
			}
		}
	}
	h.mu.Unlock()
}

// Broadcast fans an event out to every socket of one user. A peer whose
// buffer is full is dropped instead of blocking the caller.
func (h *Hub) Broadcast(userID int64, eventType string, payload any) {
	event := Event{
		Type: eventType,
		Time: time.Now().UTC().Format(time.RFC3339),
		Data: payload,
	}
	body, err := json.Marshal(event)
	if err != nil {
		return
	}

	h.mu.RLock()
	var stalled []*client
	for c := range h.clients[userID] {
		select {
		case c.send <- body:
		default:
			stalled = append(stalled, c)
		}
	}
	h.mu.RUnlock()

	for _, c := range stalled {
		h.Unregister(userID, c)
		c.conn.Close()
	}
}

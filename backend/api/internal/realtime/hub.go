package realtime

import (
	"encoding/json"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

type Event struct {
	Type string `json:"type"`
	Time string `json:"time"`
	Data any    `json:"data"`
}

type Hub struct {
	mu      sync.RWMutex
	clients map[int64]map[*websocket.Conn]struct{}
}

func NewHub() *Hub {
	return &Hub{
		clients: make(map[int64]map[*websocket.Conn]struct{}),
	}
}

func (h *Hub) Register(userID int64, conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.clients[userID] == nil {
		h.clients[userID] = make(map[*websocket.Conn]struct{})
	}
	h.clients[userID][conn] = struct{}{}
}

func (h *Hub) Unregister(userID int64, conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if userClients := h.clients[userID]; userClients != nil {
		delete(userClients, conn)
		if len(userClients) == 0 {
			delete(h.clients, userID)
		}
	}
}

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
	defer h.mu.RUnlock()
	for conn := range h.clients[userID] {
		_ = conn.WriteMessage(websocket.TextMessage, body)
	}
}

package realtime

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

// startHubTestServer serves one WebSocket endpoint that registers every
// connection with the hub, mirroring the real handler lifecycle.
func startHubTestServer(t *testing.T, hub *Hub) string {
	t.Helper()
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		c := hub.Register(1, conn)
		defer hub.Unregister(1, c)
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}))
	t.Cleanup(server.Close)
	return "ws" + strings.TrimPrefix(server.URL, "http")
}

func dialHub(t *testing.T, url string) *websocket.Conn {
	t.Helper()
	conn, _, err := websocket.DefaultDialer.Dial(url, nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	t.Cleanup(func() { conn.Close() })
	return conn
}

func hubClientCount(hub *Hub) int {
	hub.mu.RLock()
	defer hub.mu.RUnlock()
	total := 0
	for _, set := range hub.clients {
		total += len(set)
	}
	return total
}

func waitForClients(t *testing.T, hub *Hub, want int) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if hubClientCount(hub) >= want {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for %d registered clients", want)
}

func waitForClientsGone(t *testing.T, hub *Hub) {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		if hubClientCount(hub) == 0 {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("stalled client was not dropped")
}

func TestBroadcastDeliversEvent(t *testing.T) {
	hub := NewHub()
	url := startHubTestServer(t, hub)
	conn := dialHub(t, url)
	waitForClients(t, hub, 1)

	hub.Broadcast(1, "device.updated", map[string]any{"id": 7})

	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var event Event
	if err := conn.ReadJSON(&event); err != nil {
		t.Fatalf("read event: %v", err)
	}
	if event.Type != "device.updated" || event.Time == "" {
		t.Fatalf("unexpected event: %+v", event)
	}
}

// Concurrent broadcasts used to race on a shared socket write; they must now
// be serialized through the per-client writer.
func TestConcurrentBroadcastsStaySerialized(t *testing.T) {
	hub := NewHub()
	url := startHubTestServer(t, hub)
	conn := dialHub(t, url)
	waitForClients(t, hub, 1)

	// Keep the total event count within one client's send buffer so the
	// test stays deterministic even if every worker finishes before the
	// reader drains its first frame.
	const workers = 8
	each := sendBuffer / workers

	readerReady := make(chan struct{})
	done := make(chan struct{})
	go func() {
		defer close(done)
		close(readerReady)
		for i := 0; i < workers*each; i++ {
			conn.SetReadDeadline(time.Now().Add(10 * time.Second))
			var event Event
			if err := conn.ReadJSON(&event); err != nil {
				t.Errorf("read event %d: %v", i, err)
				return
			}
		}
	}()
	<-readerReady

	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			for j := 0; j < each; j++ {
				hub.Broadcast(1, "records.ingested", map[string]any{"n": i*each + j})
			}
		}(i)
	}
	wg.Wait()

	select {
	case <-done:
	case <-time.After(10 * time.Second):
		t.Fatal("timed out waiting for all events")
	}
}

// A peer that stops reading must be dropped once its buffer fills instead of
// blocking broadcasters or wedging the hub.
func TestStalledClientIsDropped(t *testing.T) {
	hub := NewHub()
	url := startHubTestServer(t, hub)
	conn := dialHub(t, url)
	waitForClients(t, hub, 1)

	// Payloads far larger than socket buffers so the writer blocks and the
	// send channel fills.
	payload := strings.Repeat("x", 512*1024)
	for i := 0; i < sendBuffer+32; i++ {
		hub.Broadcast(1, "records.ingested", map[string]any{"body": payload})
	}

	waitForClientsGone(t, hub)

	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			return // server dropped the socket as intended
		}
	}
}

// The server must push protocol-level pings; browser and OkHttp clients
// answer them with pongs, which is what keeps the read deadline fresh.
func TestServerPingsKeepConnectionAlive(t *testing.T) {
	hub := NewHub()
	hub.pingInterval = 50 * time.Millisecond
	url := startHubTestServer(t, hub)
	conn := dialHub(t, url)
	waitForClients(t, hub, 1)

	pings := make(chan struct{}, 8)
	conn.SetPingHandler(func(appData string) error {
		select {
		case pings <- struct{}{}:
		default:
		}
		return conn.WriteControl(websocket.PongMessage, []byte(appData), time.Now().Add(time.Second))
	})
	// Control frames are only processed while the socket is being read.
	go func() {
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}()

	for i := 0; i < 2; i++ {
		select {
		case <-pings:
		case <-time.After(2 * time.Second):
			t.Fatalf("server ping %d not observed", i)
		}
	}
}

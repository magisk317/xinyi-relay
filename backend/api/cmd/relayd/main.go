package main

import (
	"context"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	relayhttp "github.com/magisk317/xinyi-relay/backend/api/internal/http"
)

func main() {
	cfg := config.Load()
	server, err := relayhttp.NewServer(context.Background(), cfg)
	if err != nil {
		log.Fatalf("relay backend init failed: %v", err)
	}
	if err := server.BootstrapAdminIfNeeded(context.Background()); err != nil {
		log.Fatalf("relay backend bootstrap failed: %v", err)
	}

	log.Printf("relay backend starting on %s", cfg.HTTPAddr)

	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("relay backend listen failed: %v", err)
		}
	}()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	<-ctx.Done()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	log.Printf("relay backend shutting down")
	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("relay backend shutdown failed: %v", err)
	}
}

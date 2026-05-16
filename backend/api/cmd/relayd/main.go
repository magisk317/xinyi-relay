package main

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/magisk317/xinyi-relay/backend/api/internal/config"
	relayhttp "github.com/magisk317/xinyi-relay/backend/api/internal/http"
)

func setupLogFile(cfg config.Config) (*os.File, error) {
	if cfg.LogFile == "" {
		return nil, nil
	}

	// 确保日志目录存在
	logDir := filepath.Dir(cfg.LogFile)
	if err := os.MkdirAll(logDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create log directory: %w", err)
	}

	// 打开日志文件（追加模式）
	logFile, err := os.OpenFile(cfg.LogFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to open log file: %w", err)
	}

	// 同时输出到文件和标准输出
	multiWriter := io.MultiWriter(os.Stdout, logFile)
	log.SetOutput(multiWriter)

	// 设置日志格式包含日期时间
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds | log.Llongfile)

	return logFile, nil
}

func main() {
	cfg := config.Load()

	// 初始化日志文件
	logFile, err := setupLogFile(cfg)
	if err != nil {
		log.Fatalf("Failed to setup log file: %v", err)
	}
	if logFile != nil {
		defer logFile.Close()
		log.Printf("Logging to file: %s", cfg.LogFile)
	}

	log.Printf("relay backend connecting to database")
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

	go func() {
		time.Sleep(5 * time.Second)
		resp, err := http.Get("http://localhost" + cfg.HTTPAddr + "/healthz")
		if err != nil {
			log.Printf("[liveness] self-check failed: %v", err)
		} else {
			resp.Body.Close()
			log.Printf("[liveness] self-check ok, status=%d", resp.StatusCode)
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

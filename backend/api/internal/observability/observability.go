package observability

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	sdkresource "go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

const (
	defaultTracesEndpoint = "https://136325658.otel.gitlab-o11y.com:14318/v1/traces"
	defaultServiceName    = "xinyi-relay-backend"
	disableEnvName        = "RELAY_OTEL_DISABLED"
)

type settings struct {
	tracesEndpoint    string
	serviceName       string
	serviceVersion    string
	environmentName   string
	gitLabProjectID   string
	gitLabProjectName string
}

func Start(ctx context.Context) (func(context.Context) error, error) {
	if !Enabled() {
		return func(context.Context) error { return nil }, nil
	}
	settings := loadSettings()

	exporter, err := otlptracehttp.New(ctx, traceExporterOptions(settings.tracesEndpoint)...)
	if err != nil {
		return nil, fmt.Errorf("create otlp trace exporter: %w", err)
	}

	resourceAttributes := []attribute.KeyValue{
		attribute.String("service.name", settings.serviceName),
	}
	if settings.serviceVersion != "" {
		resourceAttributes = append(resourceAttributes, attribute.String("service.version", settings.serviceVersion))
	}
	if settings.environmentName != "" {
		resourceAttributes = append(resourceAttributes, attribute.String("deployment.environment.name", settings.environmentName))
	}
	if settings.gitLabProjectID != "" {
		resourceAttributes = append(resourceAttributes, attribute.String("gitlab.project.id", settings.gitLabProjectID))
	}
	if settings.gitLabProjectName != "" {
		resourceAttributes = append(resourceAttributes, attribute.String("gitlab.project.name", settings.gitLabProjectName))
	}

	resource, err := sdkresource.Merge(
		sdkresource.Default(),
		sdkresource.NewWithAttributes("", resourceAttributes...),
	)
	if err != nil {
		return nil, fmt.Errorf("build otel resource: %w", err)
	}

	provider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(resource),
	)
	otel.SetTracerProvider(provider)
	otel.SetTextMapPropagator(
		propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		),
	)

	log.Printf(
		"[observability] enabled service=%s endpoint=%s gitlab_project_id=%s env=%s",
		settings.serviceName,
		settings.tracesEndpoint,
		emptyFallback(settings.gitLabProjectID, "<unset>"),
		emptyFallback(settings.environmentName, "<unset>"),
	)

	return provider.Shutdown, nil
}

func WrapHTTPHandler(next http.Handler) http.Handler {
	if !Enabled() {
		return next
	}
	return otelhttp.NewHandler(
		next,
		"http.server",
		otelhttp.WithFilter(func(r *http.Request) bool {
			return r.URL.Path != "/healthz"
		}),
	)
}

func Enabled() bool {
	// Observability is on by default so deployed backends emit traces to GitLab.
	// Set RELAY_OTEL_DISABLED=true to disable exports for a specific environment.
	return !truthy(os.Getenv(disableEnvName))
}

func loadSettings() settings {
	return settings{
		tracesEndpoint:    defaultTracesEndpoint,
		serviceName:       defaultServiceName,
		serviceVersion:    os.Getenv("CI_COMMIT_SHA"),
		environmentName:   firstNonEmpty(os.Getenv("CI_ENVIRONMENT_NAME"), os.Getenv("RELAY_APP_ENV"), "development"),
		gitLabProjectID:   os.Getenv("CI_PROJECT_ID"),
		gitLabProjectName: os.Getenv("CI_PROJECT_NAME"),
	}
}

func traceExporterOptions(endpoint string) []otlptracehttp.Option {
	parsed, err := url.Parse(endpoint)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		log.Printf("[observability] invalid traces endpoint %q, using exporter defaults", endpoint)
		return nil
	}

	path := parsed.EscapedPath()
	if path == "" {
		path = "/v1/traces"
	}

	options := []otlptracehttp.Option{
		otlptracehttp.WithEndpoint(parsed.Host),
		otlptracehttp.WithURLPath(path),
	}
	if parsed.Scheme == "http" {
		options = append(options, otlptracehttp.WithInsecure())
	}
	return options
}

func emptyFallback(value, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}

func truthy(value string) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

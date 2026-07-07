package observability

import "testing"

func TestEnabledDefaultsToTrue(t *testing.T) {
	t.Setenv(disableEnvName, "")
	if !Enabled() {
		t.Fatal("expected observability to be enabled by default")
	}
}

func TestEnabledCanBeDisabledByEnv(t *testing.T) {
	t.Setenv(disableEnvName, "true")
	if Enabled() {
		t.Fatal("expected observability to be disabled when env is set")
	}
}

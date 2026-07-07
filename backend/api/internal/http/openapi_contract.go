package http

import (
	_ "embed"
	"encoding/json"
	"fmt"
)

//go:embed openapi_schemas.generated.json
var generatedOpenAPISchemas []byte

//go:embed openapi_routes.generated.json
var generatedOpenAPIRoutes []byte

func BuildOpenAPIContractDocument() map[string]any {
	schemas := map[string]any{}
	for name, schema := range mustLoadGeneratedOpenAPISchemas() {
		schemas[name] = schema
	}

	return map[string]any{
		"openapi": "3.1.0",
		"info": map[string]any{
			"title":       "Xinyi Relay Remote API",
			"version":     "0.1.0",
			"description": "Contract for the Xinyi Relay backend, Android agent, Web console, and Desktop console.",
		},
		"servers": []map[string]any{
			{
				"url":         "/",
				"description": "Same-origin backend",
			},
		},
		"paths": openAPIContractRoutes(),
		"components": map[string]any{
			"schemas": schemas,
		},
	}
}

func openAPIContractRoutes() map[string]map[string]map[string]any {
	return mustLoadGeneratedOpenAPIRoutes()
}

func mustLoadGeneratedOpenAPISchemas() map[string]any {
	var schemas map[string]any
	if err := json.Unmarshal(generatedOpenAPISchemas, &schemas); err != nil {
		panic(fmt.Sprintf("parse embedded OpenAPI schemas: %v", err))
	}
	return schemas
}

func mustLoadGeneratedOpenAPIRoutes() map[string]map[string]map[string]any {
	var routes map[string]map[string]map[string]any
	if err := json.Unmarshal(generatedOpenAPIRoutes, &routes); err != nil {
		panic(fmt.Sprintf("parse embedded OpenAPI routes: %v", err))
	}
	return routes
}

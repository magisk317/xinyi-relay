package main

import (
	"encoding/json"
	"log"
	"os"

	httppkg "github.com/magisk317/xinyi-relay/backend/api/internal/http"
)

func main() {
	document := httppkg.BuildOpenAPIContractDocument()
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetEscapeHTML(false)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(document); err != nil {
		log.Fatal(err)
	}
}

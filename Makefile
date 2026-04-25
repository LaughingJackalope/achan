SHELL := /bin/sh

BASE_URL ?= http://localhost:8080
OLLAMA_HOST ?= http://localhost:11434
COMPOSE_FILE ?= docker-compose.yml

.PHONY: help up down restart logs ps \
	quarkus-dev health ollama-health \
	test-search test-websocket test-all lifecycle

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make <target>\\n\\nTargets:\\n"} /^[a-zA-Z0-9_.-]+:.*##/ {printf "  %-18s %s\\n", $$1, $$2}' $(MAKEFILE_LIST)

up: ## Start infrastructure via docker compose
	docker compose -f $(COMPOSE_FILE) up -d

down: ## Stop infrastructure via docker compose
	docker compose -f $(COMPOSE_FILE) down

restart: down up ## Restart infrastructure via docker compose

logs: ## Tail docker compose logs
	docker compose -f $(COMPOSE_FILE) logs -f --tail=200

ps: ## Show docker compose service status
	docker compose -f $(COMPOSE_FILE) ps

quarkus-dev: ## Start Quarkus in dev mode
	./gradlew quarkusDev

health: ## Check Quarkus health endpoint
	@echo "Checking $(BASE_URL)/q/health/live"
	@curl -sS $(BASE_URL)/q/health/live | sed -e 's/\\r//g'

ollama-health: ## Check Ollama and list available models
	@echo "Checking $(OLLAMA_HOST)/api/tags"
	@curl -sS $(OLLAMA_HOST)/api/tags | sed -e 's/\\r//g'

test-search: ## Run semantic search API test script
	./test-search-api.sh

test-websocket: ## Run websocket cognitive pipeline test
	node test-websocket.js

test-all: test-search test-websocket ## Run all local integration tests

lifecycle: up ## End-to-end local validation (infra + health + tests)
	@echo "Running lifecycle checks against $(BASE_URL) with Ollama at $(OLLAMA_HOST)"
	@curl -sSf $(BASE_URL)/q/health/live > /dev/null || (echo "Quarkus not healthy at $(BASE_URL). Start it and retry."; exit 1)
	@curl -sSf $(OLLAMA_HOST)/api/tags > /dev/null || (echo "Ollama not reachable at $(OLLAMA_HOST). Start it and retry."; exit 1)
	@$(MAKE) --no-print-directory test-all

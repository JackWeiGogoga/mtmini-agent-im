SHELL := /bin/bash

ROOT_DIR := $(CURDIR)
LOCAL_ENV := $(ROOT_DIR)/.env.local
MAVEN_BIN := $(shell if [ -x "/Users/jackwei/data/apache-maven-3.9.14/bin/mvn" ]; then echo "/Users/jackwei/data/apache-maven-3.9.14/bin/mvn"; else echo "mvn"; fi)
define with_env
set -a; \
if [ -f "$(LOCAL_ENV)" ]; then source "$(LOCAL_ENV)"; fi; \
set +a; \
$(1)
endef

.PHONY: help env-copy env-check backend-dev admin-dev h5-dev backend-test admin-test h5-build test-all validate

help:
	@echo "Local development targets:"
	@echo "  make env-copy       Create .env.local from .env.local.example if missing"
	@echo "  make env-check      Print key local environment status"
	@echo "  make backend-dev    Start Spring Boot backend on :8080"
	@echo "  make h5-dev         Start uni-app H5 on :5173"
	@echo "  make admin-dev      Start React admin on :5174"
	@echo "  make backend-test   Run backend tests"
	@echo "  make admin-test     Run frontend lint + typecheck"
	@echo "  make h5-build       Build H5"
	@echo "  make test-all       Run backend/frontend/H5 verification"
	@echo "  make validate       Run OpenSpec strict validation"

env-copy:
	@if [ -f "$(LOCAL_ENV)" ]; then \
		echo ".env.local already exists"; \
	else \
		cp .env.local.example "$(LOCAL_ENV)"; \
		echo "Created .env.local. Fill API keys before testing live RAG."; \
	fi

env-check:
	@$(call with_env, \
		echo "SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-dev}"; \
		echo "SERVER_PORT=$${SERVER_PORT:-8080}"; \
		echo "SPRING_DATASOURCE_URL=$${SPRING_DATASOURCE_URL:-<backend dev default>}"; \
		echo "SPRING_DATASOURCE_USERNAME=$${SPRING_DATASOURCE_USERNAME:-root}"; \
		echo "SPRING_DATA_REDIS_HOST=$${SPRING_DATA_REDIS_HOST:-localhost}"; \
		echo "SPRING_DATA_REDIS_PORT=$${SPRING_DATA_REDIS_PORT:-6379}"; \
		echo "COS_ENABLED=$${COS_ENABLED:-false}"; \
		echo "SCHEDULED_ENABLED=$${SCHEDULED_ENABLED:-false}"; \
		if [ -n "$${MT_IM_SECRET:-}" ]; then echo "IM callback secret: configured"; else echo "IM callback secret: missing"; fi; \
		if [ -n "$${MT_KNOWLEDGE_EMBEDDING_API_KEY:-$${DASHSCOPE_API_KEY:-}}" ]; then echo "Embedding key: configured"; else echo "Embedding key: missing"; fi; \
		if [ -n "$${MT_KNOWLEDGE_MODEL_API_KEY:-}" ]; then echo "Chat model key: configured"; else echo "Chat model key: missing"; fi \
	)

backend-dev:
	@$(call with_env, cd backend && "$(MAVEN_BIN)" spring-boot:run)

admin-dev:
	@cd frontend && pnpm dev

h5-dev:
	@cd h5 && pnpm dev:h5

backend-test:
	@"$(MAVEN_BIN)" -f backend/pom.xml test

admin-test:
	@cd frontend && pnpm lint && pnpm typecheck

h5-build:
	@cd h5 && pnpm build:h5

test-all: backend-test admin-test h5-build validate

validate:
	@openspec validate replace-comment-reply-with-im-auto-reply-foundation --strict

SHELL := /bin/bash

.PHONY: render-config
render-config: ## Render dex/config/config.yaml from dex/.env.local
	@test -f .env.local || (echo "Missing dex/.env.local (copy from dex/.env.example)" && exit 1)
	@python3 scripts/render_config.py
	@echo "Rendered dex/config/config.yaml"

.PHONY: up-local
up-local: render-config ## Render config and start local Dex
	@docker compose up -d

.PHONY: down-local
down-local: ## Stop local Dex
	@docker compose down

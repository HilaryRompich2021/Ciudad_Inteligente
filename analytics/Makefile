up:
	docker compose --env-file platform/.env -f platform/docker-compose.yml up -d
down:
	docker compose --env-file platform/.env -f platform/docker-compose.yml down
ps:
	docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
logs-kafka:
	docker compose --env-file platform/.env -f platform/docker-compose.yml logs -f kafka
logs-postgres:
	docker compose --env-file platform/.env -f platform/docker-compose.yml logs -f postgres

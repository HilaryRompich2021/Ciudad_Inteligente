# Catálogo de REdis

### Iniciar Docker:
docker run --name mi-redis -p 6379:6379 -d redis

### Cargar el script:
docker exec -i mi-redis redis-cli < redis-setup.redis

### Verificar Redis:
docker exec -it mi-reids redis-cli

### Verificar Blacklist de placas:
SMEMBERS blacklist:plates

### Políticas de velocidad por zona:
HGETALL policy:speed:zona1

### contadores de eventos:
GET events:zona1:overspeed

### Alertas activas
LRANGE alerts:active 0 -1
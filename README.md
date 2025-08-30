# Proyecto Ciudad Inteligente — Base de Infra (Fase 0)

Este repo contiene el **esqueleto de infraestructura** para que el equipo trabaje en paralelo y tú puedas integrar rápidamente.

## Estructura
```
platform/                # Docker Compose + .env
scripts/                 # Scripts de Kafka, redes, etc. (los agregarán tus compañeros)
db/                      # SQL de usuarios y tablas (los agregará el owner de DB)
redis/                   # Seeds / catálogos para Redis
mops/                    # Documentación de arranque y validación
```

## Paso 1: Configurar entorno
Copia el archivo de ejemplo y ajusta valores:
```bash
cp platform/.env.sample platform/.env
# Modo local
# (En Windows usa notepad o edítalo a mano)
```
Edita `platform/.env` y deja:
```
MODE=local
HOST_IP=127.0.0.1
TEAM_ID=t01
POSTGRES_PASSWORD=admin123
```

Si vas a apuntar al host del profesor el día de la clase, cambia a:
```
MODE=profe
HOST_IP=192.168.1.50   # ejemplo
```

## Paso 2: Levantar servicios
```bash
make -f platform/Makefile up
make -f platform/Makefile ps
# Kafka-UI en http://localhost:8081
```

## Paso 3: Validar conectividad
```bash
# Kafka bootstrap
nc -vz localhost 29092 || true
# Redis
nc -vz localhost 6379 || true
# Postgres
nc -vz localhost 5432 || true
```

## Equipo: ¿qué sube cada quien?
- **Kafka (topics):** `scripts/create-topics.sh`, `scripts/verify-topics.sh`
- **DB:** `db/users_and_schema.sql`, `db/tables_phase0.sql`
- **Redis:** `redis/redis-setup.redis`
- **Redes/Docs:** `mops/MOPS_Fase0.md`, `scripts/connectivity_checks.sh`

## Notas
- Este compose está pensado para laboratorio (sin seguridad). No usar en producción.
- Kafka anuncia `PLAINTEXT_HOST=${HOST_IP}:29092` para que otros clientes en LAN puedan conectarse.

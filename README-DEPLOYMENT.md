# 🏙️ Ciudad Inteligente - Guía de Despliegue Completo

## 📋 Tabla de Contenidos

1. [Descripción del Proyecto](#descripción-del-proyecto)
2. [Arquitectura del Sistema](#arquitectura-del-sistema)
3. [Requisitos Previos](#requisitos-previos)
4. [Despliegue desde Cero](#despliegue-desde-cero)
5. [Verificación del Sistema](#verificación-del-sistema)
6. [Pruebas End-to-End](#pruebas-end-to-end)
7. [Monitoreo con Grafana](#monitoreo-con-grafana)
8. [Troubleshooting](#troubleshooting)

---

## 📖 Descripción del Proyecto

Sistema distribuido de supervisión perimetral para ciudades inteligentes que procesa eventos en tiempo real, correlaciona patrones de eventos y genera alertas complejas.

### Componentes Principales

- **Event Ingestor**: Punto de entrada REST para eventos
- **Correlator**: Motor de correlación de eventos y generación de alertas
- **Kafka**: Backbone de mensajería
- **Redis**: Ventanas de correlación temporal
- **PostgreSQL**: Persistencia de eventos y alertas
- **Grafana**: Visualización de dashboards

---

## 🏗️ Arquitectura del Sistema

```
┌─────────────┐
│  Postman/   │
│  Artillery  │
└──────┬──────┘
       │ HTTP POST /events
       ↓
┌─────────────────────┐
│  Event Ingestor     │ (Puerto 8000)
│  - Validación       │
│  - Enriquecimiento  │
└──────┬──────────────┘
       │ Kafka: events.standardized
       ↓
┌─────────────────────┐
│  Correlator         │ (Puerto 8080)
│  - Redis Windowing  │
│  - Pattern Matching │
└──────┬──────────────┘
       │ Kafka: correlated.alerts
       ↓
┌─────────────────────┐
│  PostgreSQL         │ (Puerto 5432)
│  - events table     │
│  - alerts table     │
└──────┬──────────────┘
       │
       ↓
┌─────────────────────┐
│  Grafana            │ (Puerto 3000)
│  - Dashboards       │
│  - Alertas en vivo  │
└─────────────────────┘
```

---

## ✅ Requisitos Previos

### Software Necesario

- **Docker Desktop** (Windows/Mac) o **Docker CE** (Linux/WSL)
- **Java 17** o superior
- **Maven** 3.8+
- **Git**
- **Postman** (opcional, para pruebas)

### Verificar Instalación

```bash
# Docker
docker --version
docker-compose --version

# Java
java -version

# Maven
mvn -version
```

---

## 🚀 Despliegue desde Cero

### Paso 1: Clonar el Repositorio

```bash
git clone https://github.com/HilaryRompich2021/Ciudad_Inteligente.git
cd Ciudad_Inteligente
```

---

### Paso 2: Levantar la Infraestructura Base

#### Opción A: Docker Desktop (Windows/Mac)

```powershell
cd platform
docker-compose up -d
```

#### Opción B: WSL + Docker CE (Linux)

```bash
cd platform

# 1. Crear red externa (primera vez)
docker network create ciudad-inteligente-net

# 2. Levantar servicios
docker-compose -f docker-compose.pruebas.yml up -d
```

**Servicios iniciados:**
- ✅ Zookeeper (puerto 2181)
- ✅ Kafka (puertos 9092, 29092)
- ✅ Kafka UI (puerto 8081)
- ✅ Redis (puerto 6379)
- ✅ PostgreSQL (puerto 5432)
- ✅ Grafana (puerto 3000)

**Tiempo estimado:** 30-60 segundos

---

### Paso 3: Verificar Infraestructura

#### Verificar contenedores corriendo:

```bash
docker ps
```

Deberías ver 6 contenedores:
- `platform_zookeeper_1`
- `platform_kafka_1`
- `platform_kafka-ui_1`
- `platform_redis_1`
- `platform_postgres_1`
- `platform_grafana_1`

#### Verificar Topics de Kafka:

Accede a: **http://localhost:8081**

Deberías ver:
- `events.standardized` (3 particiones)
- `correlated.alerts` (3 particiones)

#### Verificar Base de Datos:

```bash
# Docker Desktop
docker exec -it postgres psql -U postgres -d ciudades -c "\dt"

# WSL
docker exec -it platform-postgres-1 psql -U postgres -d ciudades -c "\dt"
```

Deberías ver las tablas: `events` y `alerts`

---

### Paso 4: Compilar y Desplegar el Ingestor

```bash
cd src/ingestor

# Compilar
./mvnw clean package -DskipTests

# Construir imagen Docker
docker build -t ingestor:latest .

# Levantar contenedor
docker-compose up -d
```

**Verificar:**

```bash
# Ver logs
docker logs -f ingestor-ingestor-1

# Verificar health
curl http://localhost:8000/events/health
```

**Respuesta esperada:**
```json
{
  "status": "UP"
}
```

---

### Paso 5: Compilar y Desplegar el Correlator

```bash
cd ../correlator

# Compilar
./mvnw clean package -DskipTests

# Construir imagen Docker
docker build -t correlator:latest .

# Levantar contenedor
docker-compose up -d
```

**Verificar:**

```bash
# Ver logs
docker logs -f correlator-correlator-1

# Buscar línea de inicio exitoso
docker logs correlator-correlator-1 | grep "Started CorrelatorApplication"
```

**Deberías ver:**
```
Started CorrelatorApplication in X.XXX seconds
```

---

### Paso 6: Verificar Grafana

Accede a: **http://localhost:3000**

**Credenciales:**
- Usuario: `admin`
- Contraseña: `admin`

**Verificar Datasource:**
1. Ve a **Configuration** (⚙️) → **Data Sources**
2. Click en **PostgreSQL-Ciudades**
3. Scroll al final y click **"Save & Test"**
4. Debe mostrar: ✅ **"Database Connection OK"**

**Verificar Dashboards:**
1. Ve a **Dashboards** → **Browse**
2. Deberías ver:
   - ✅ Ciudad Inteligente - Dashboard Principal
   - ✅ Ciudad Inteligente - Mapa de Calor por Zona

---

## 🧪 Verificación del Sistema

### Test 1: Ingestor - Health Check

```bash
curl http://localhost:8000/events/health
```

**Esperado:** JSON con `status: "UP"`, `kafka: "available"`, `validator: "ready"`

---

### Test 2: Ingestor - Endpoint de Eventos

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "f1e2d3c4-0001-4001-8001-000000000001",
    "producer": "health-check",
    "source": "simulated",
    "correlation_id": "f2e3d4c5-0001-4001-8001-100000000001",
    "trace_id": "f3e4d5c6-0001-4001-8001-200000000001",
    "timestamp": "2025-10-01T12:00:00Z",
    "partition_key": "zone_test",
    "geo": {
      "zone": "zone_test",
      "lat": 14.62,
      "lon": -90.52
    },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "test"
    }
  }'
```

**Esperado:** Status `202 Accepted` con respuesta JSON detallada:

```json
{
  "status": "success",
  "message": "Event processed and published successfully",
  "event_id": "f1e2d3c4-0001-4001-8001-000000000001",
  "event_type": "panic.button",
  "partition_key": "zone_test",
  "timestamp": "2025-10-05T14:30:15.123Z"
}
```

---

### 🔄 Enriquecimiento Automático de Eventos

El **Event Ingestor** enriquece automáticamente los eventos con campos opcionales que falten:

#### Campos Enriquecidos Automáticamente:

| Campo | Si falta | Se genera automáticamente |
|-------|----------|---------------------------|
| `timestamp` | ❌ | ✅ Timestamp actual (Instant.now()) |
| `trace_id` | ❌ | ✅ UUID v4 aleatorio |
| `correlation_id` | ❌ | ✅ UUID v4 aleatorio |
| `partition_key` | ❌ | ✅ Auto-extraído de geo.zone o payload.placa_vehicular |

**Nota:** Aunque el esquema JSON marca estos campos como `required`, el **EventEnricher** los genera automáticamente si faltan, facilitando las pruebas y garantizando la completitud de los datos.

#### 📝 Ejemplo: Evento Mínimo

Puedes enviar un evento **sin** `timestamp`, `trace_id`, `correlation_id` o `partition_key` (serán auto-generados):

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "f1e2d3c4-0099-4099-8099-000000000099",
    "producer": "test-minimal",
    "source": "simulated",
    "geo": {
      "zone": "zone_test",
      "lat": 14.62,
      "lon": -90.52
    },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "test"
    }
  }'
```

**El Ingestor lo enriquecerá automáticamente a:**

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "f1e2d3c4-0099-4099-8099-000000000099",
  "producer": "test-minimal",
  "source": "simulated",
  "timestamp": "2025-10-05T14:30:15.123Z",            // ← Auto-generado (Instant.now())
  "trace_id": "a1b2c3d4-5678-4abc-8def-123456789012", // ← Auto-generado
  "correlation_id": "b2c3d4e5-6789-4bcd-8ef0-234567890123", // ← Auto-generado
  "partition_key": "zone_test",                       // ← Auto-extraído de geo.zone
  "geo": {
    "zone": "zone_test",
    "lat": 14.62,
    "lon": -90.52
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "test"
  }
}
```

#### ✅ Ventajas del Enriquecimiento Automático

1. **Simplifica testing**: No necesitas generar UUIDs manualmente para trace_id/correlation_id
2. **Garantiza trazabilidad**: Todos los eventos tienen trace_id y correlation_id
3. **Auto-particionado**: Extrae partition_key de geo.zone o payload.placa_vehicular
4. **Compatibilidad**: Puedes enviar campos completos si lo prefieres

#### ⚠️ Notas Importantes

- Si **envías** `timestamp`, `trace_id`, `correlation_id` o `partition_key`, el Ingestor **respetará** tus valores
- El campo **`event_id` DEBE ser un UUID v4 válido** - El sistema lo convierte a tipo UUID para PostgreSQL (`UUID.fromString()`)
- Los campos **estrictamente obligatorios** (no enriquecibles) son: `event_id` (UUID válido), `event_version`, `event_type`, `producer`, `source`, `geo`, `severity` y `payload`
- Los UUIDs auto-generados para `trace_id` y `correlation_id` cumplen con el formato UUID v4
- El timestamp auto-generado usa el formato ISO 8601 (ej: `2025-10-01T12:00:00Z`)
- El `partition_key` es NOT NULL en la BD, por lo que el enriquecedor lo extrae de `geo.zone` si no lo envías

---

### Test 3: Verificar Evento en Kafka

1. Ve a **Kafka UI**: http://localhost:8081
2. Click en **Topics** → **events.standardized**
3. Tab **Messages**
4. Deberías ver el evento con `event_id: "f1e2d3c4-0001-4001-8001-000000000001"`

---

### Test 4: Verificar Evento en PostgreSQL

```bash
# Docker Desktop
docker exec -it postgres psql -U postgres -d ciudades -c "SELECT event_id, event_type, zone FROM events ORDER BY ts_utc DESC LIMIT 5;"

# WSL
docker exec -it platform-postgres-1 psql -U postgres -d ciudades -c "SELECT event_id, event_type, zone FROM events ORDER BY ts_utc DESC LIMIT 5;"
```

**Esperado:** Ver el evento `test-health-001`

---

## 🎯 Pruebas End-to-End

### Escenario 1: Generar Alerta de Robo

**Objetivo:** Simular un botón de pánico + vehículo a alta velocidad = Alerta `possible_robbery`

#### Evento 1: Botón de Pánico

```json
POST http://localhost:8000/events
Content-Type: application/json

{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "e1e2e3e4-0001-4001-8001-000000000001",
  "producer": "postman",
  "source": "simulated",
  "correlation_id": "e2e3e4e5-0001-4001-8001-100000000001",
  "trace_id": "e3e4e5e6-0001-4001-8001-200000000001",
  "timestamp": "2025-10-01T15:00:00Z",
  "partition_key": "panic.button",
  "geo": {
    "zone": "zone_1",
    "lat": 14.62,
    "lon": -90.52
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "panico",
    "identificador_dispositivo": "BTN-001"
  }
}
```

**Respuesta esperada:** `202 Accepted`

#### Evento 2: Sensor LPR (1 minuto después)

```json
POST http://localhost:8000/events
Content-Type: application/json

{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "e1e2e3e4-0002-4002-8002-000000000002",
  "producer": "postman",
  "source": "simulated",
  "correlation_id": "e2e3e4e5-0001-4001-8001-100000000001",
  "trace_id": "e3e4e5e6-0002-4002-8002-200000000002",
  "timestamp": "2025-10-01T15:01:00Z",
  "partition_key": "sensor.lpr",
  "geo": {
    "zone": "zone_1",
    "lat": 14.62,
    "lon": -90.52
  },
  "severity": "warning",
  "payload": {
    "placa_vehicular": "ABC123",
    "velocidad_estimada": 95.0,
    "modelo_vehiculo": "sedan",
    "color_vehiculo": "negro"
  }
}
```

**Respuesta esperada:** `202 Accepted`

---

### Verificar Alerta Generada

#### Opción 1: Kafka UI

1. Ve a: http://localhost:8081
2. Topics → **correlated.alerts**
3. Messages
4. Deberías ver una alerta de tipo `possible_robbery`

#### Opción 2: PostgreSQL

```bash
docker exec -it platform-postgres-1 psql -U postgres -d ciudades -c "SELECT alert_id, type, zone, score, created_at FROM alerts ORDER BY created_at DESC LIMIT 5;"
```

**Esperado:**
```
               alert_id               |       type       | zone  | score |          created_at
--------------------------------------+------------------+-------+-------+-------------------------------
 <uuid>                               | possible_robbery | zone_1| 0.85  | 2025-10-01 15:01:05.123456+00
```

#### Opción 3: Grafana

1. Ve a: http://localhost:3000
2. Dashboard Principal
3. Deberías ver:
   - **Total Alertas (24h)**: Incrementado
   - **Tabla de Alertas Recientes**: Nueva fila con `possible_robbery`
   - **Gráfico de Alertas por Tipo**: Barra de `possible_robbery`

---

### Escenario 2: Generar Alerta de Accidente

**Objetivo:** Simular reporte ciudadano + sensor acústico = Alerta `accident`

#### Evento 1: Reporte Ciudadano

```json
POST http://localhost:8000/events
Content-Type: application/json

{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "e1e2e3e4-0003-4003-8003-000000000003",
  "producer": "postman",
  "source": "simulated",
  "correlation_id": "e2e3e4e5-0002-4002-8002-100000000002",
  "trace_id": "e3e4e5e6-0003-4003-8003-200000000003",
  "timestamp": "2025-10-01T15:10:00Z",
  "partition_key": "citizen.report",
  "geo": {
    "zone": "zone_2",
    "lat": 14.63,
    "lon": -90.53
  },
  "severity": "warning",
  "payload": {
    "tipo_evento": "accidente",
    "mensaje_descriptivo": "Vehiculo volcado en autopista",
    "ubicacion_aproximada": "zone_2",
    "origen": "app"
  }
}
```

#### Evento 2: Sensor Acústico (2 minutos después)

```json
POST http://localhost:8000/events
Content-Type: application/json

{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "e1e2e3e4-0004-4004-8004-000000000004",
  "producer": "postman",
  "source": "simulated",
  "correlation_id": "e2e3e4e5-0002-4002-8002-100000000002",
  "trace_id": "e3e4e5e6-0004-4004-8004-200000000004",
  "timestamp": "2025-10-01T15:12:00Z",
  "partition_key": "sensor.acoustic",
  "geo": {
    "zone": "zone_2",
    "lat": 14.63,
    "lon": -90.53
  },
  "severity": "critical",
  "payload": {
    "tipo_sonido_detectado": "explosion",
    "nivel_decibeles": 115,
    "probabilidad_evento_critico": 0.90
  }
}
```

**Verificar:** Alerta de tipo `accident` generada en Kafka, PostgreSQL y Grafana.

---

## 📊 Monitoreo con Grafana

### Dashboard Principal

**URL:** http://localhost:3000/dashboards

**Paneles disponibles:**

1. **Total Alertas (24h)**: Contador de alertas generadas
2. **Total Eventos (24h)**: Contador de eventos procesados
3. **Eventos por Tipo (Última Hora)**: Gráfico temporal por tipo de evento
4. **Alertas por Tipo (Última Hora)**: Gráfico temporal por tipo de alerta
5. **Alertas Recientes (últimas 50)**: Tabla detallada con evidencia
6. **Distribución por Zona**: Pie chart de alertas por zona
7. **Distribución por Tipo**: Donut chart de alertas por tipo

**Auto-refresh:** 10 segundos

---

### Dashboard Mapa de Calor

**Paneles disponibles:**

1. **Mapa de Eventos por Ubicación Geográfica**: Visualización geoespacial
2. **Eventos por Zona y Tipo**: Barchart comparativo
3. **Mapa de Calor: Eventos por Zona en el Tiempo**: Heatmap temporal

**Auto-refresh:** 30 segundos

---

### Exportar Dashboard (Entregable A6)

1. Abre un dashboard
2. Click en **Share** (arriba a la derecha)
3. Tab **Export**
4. Click **"Save to file"**
5. Se descarga un archivo JSON

---

## 🔧 Configuración Avanzada

### Ajustar Ventana de Correlación

Edita `src/correlator/src/main/java/com/ciudadesinteligentes/correlator/service/CorrelatorService.java`:

```java
// Línea 57: Cambiar ventana de 2 minutos a 5 minutos
if ("panic.button".equals(e.event_type) && diffSec <= 300) panicEvents.add(e);
                                                    // ↑ 300 segundos = 5 minutos
```

Luego recompila y redespliega:

```bash
./mvnw clean package -DskipTests
docker build -t correlator:latest .
docker-compose restart
```

---

### Cambiar Umbral de Velocidad

Edita `src/correlator/src/main/java/com/ciudadesinteligentes/correlator/service/CorrelatorService.java`:

```java
// Línea 60: Cambiar umbral de 80 km/h a 100 km/h
if (v > 100 && diffSec <= 120) lprEvents.add(e);
    // ↑ Ahora solo alertará con velocidad > 100 km/h
```

---

## 🐛 Troubleshooting

### Problema: Ingestor no inicia

**Error:** `Connection refused: kafka`

**Solución:**
```bash
# Verificar que Kafka esté corriendo
docker ps | grep kafka

# Reiniciar Kafka
docker restart platform_kafka_1

# Esperar 30 segundos y reiniciar ingestor
docker restart ingestor-ingestor-1
```

---

### Problema: Correlator no genera alertas

**Causa 1:** Eventos duplicados (mismo `event_id`)

El correlator tiene lógica de idempotencia que rechaza eventos duplicados por 10 minutos.

**Solución:** Usa `event_id` únicos para cada evento de prueba.

**⚠️ IMPORTANTE: Los Event IDs DEBEN ser UUIDs v4 válidos**

El ingestor convierte `event_id`, `correlation_id` y `trace_id` a tipo UUID para persistirlos en PostgreSQL. Si envías strings que no sean UUIDs válidos, obtendrás error 400:

```java
// EventService.java
entity.setEventId(UUID.fromString(event.getEventId())); // ← Lanza excepción si no es UUID válido
```

**Generar UUIDs v4 válidos:**
```bash
# PowerShell
New-Guid

# Linux/Mac
uuidgen

# Python
python -c "import uuid; print(uuid.uuid4())"
```

**Causa 2:** Eventos fuera de la ventana temporal

La ventana de correlación es de **±2 minutos** para `possible_robbery` y **5 minutos** para `accident`.

**Solución:** Envía los eventos relacionados con menos tiempo de diferencia.

**Causa 3:** Payload incorrecto

Verifica que:
- `sensor.lpr` tenga `payload.velocidad_estimada > 80`
- `citizen.report` tenga `payload.tipo_evento = "accidente"`
- `sensor.acoustic` tenga `payload.tipo_sonido_detectado = "explosion"` o `"vidrio_roto"`

---

### Problema: Grafana muestra "No data"

**Causa 1:** Rango de tiempo incorrecto

Las queries del dashboard filtran por las últimas 24 horas. Si tus eventos tienen timestamps antiguos, no aparecerán.

**Solución:** Cambia el rango de tiempo en Grafana:
- Click en "Last 24 hours" (arriba a la derecha)
- Selecciona "Last 30 days" o "Last 7 days"
- Click "Apply"

**Causa 2:** Datasource desconectado

**Solución:**
1. Ve a Configuration → Data Sources → PostgreSQL-Ciudades
2. Click "Save & Test"
3. Debe mostrar "Database Connection OK"
4. Si falla, reinicia Grafana: `docker restart platform_grafana_1`

---

### Problema: Redis pierde datos

**Causa:** TTL expirado (10 minutos por defecto)

Las ventanas de correlación en Redis tienen TTL de 10 minutos. Datos más antiguos se eliminan automáticamente.

**Solución:** Esto es esperado y forma parte del diseño. No es un bug.

---

## 📈 Métricas y Monitoreo

### Endpoints de Health y Monitoreo

**Ingestor:**
```bash
# Health Check (incluye estado de Kafka y validador)
curl http://localhost:8000/events/health

# Respuesta esperada:
# {
#   "status": "UP",
#   "kafka": "available",
#   "validator": "ready",
#   "timestamp": "2025-10-05T12:00:00.123Z",
#   "service": "ingestor",
#   "version": "1.0",
#   "details": {
#     "topic": "events.standardized",
#     "schema_version": "1.0",
#     "kafka_template_configured": true,
#     "note": "Health check uses basic availability verification"
#   }
# }

# Obtener Schema Canónico
curl http://localhost:8000/events/schema
```

**Correlator:**
```bash
# Health Check
curl http://localhost:8080/health
# Respuesta: "OK"

# Métricas básicas
curl http://localhost:8080/metrics
# Respuesta: {"alerts":0,"events":0}

# Consultar alertas activas por zona
curl "http://localhost:8080/alerts/active?zone=zone_1"
# Respuesta: Array de alertas correlacionadas activas
```

---

### Logs en Tiempo Real

```bash
# Ver todos los contenedores
docker-compose logs -f

# Solo ingestor
docker logs -f ingestor-ingestor-1

# Solo correlator
docker logs -f correlator-correlator-1

# Filtrar por palabra clave
docker logs correlator-correlator-1 | grep "Alert generated"
```

---

## 🧹 Limpieza del Sistema

### Detener todos los servicios

```bash
# Detener microservicios
cd src/ingestor
docker-compose down

cd ../correlator
docker-compose down

# Detener infraestructura
cd ../../platform
docker-compose down  # Docker Desktop
# o
docker-compose -f docker-compose.pruebas.yml down  # WSL
```

### Limpiar datos (CUIDADO: elimina todo)

```bash
# Eliminar volúmenes de PostgreSQL y Grafana
docker volume rm platform_pgdata platform_grafana-data

# Eliminar red (WSL)
docker network rm ciudad-inteligente-net
```

---

## 📚 Recursos Adicionales

### URLs de Servicios

| Servicio | URL | Credenciales |
|----------|-----|--------------|
| Kafka UI | http://localhost:8081 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Ingestor | http://localhost:8000 | - |
| Correlator | http://localhost:8080 | - |
| PostgreSQL | localhost:5432 | postgres / postgres |
| Redis | localhost:6379 | - |



**Última actualización:** 5 de octubre de 2025  
**Versión del sistema:** 1.0  
**Curso:** Arquitectura de Computadoras II

# Microservicio Ingestor - Guía de Uso y Pruebas con Docker Desktop
---

## 📋 Tabla de Contenidos

1. Requisitos Previos
2. Configuración de Variables de Entorno
3. Levantar el Microservicio con Docker
4. Endpoints y Ejemplos
5. Enriquecimiento Automático
6. Notas Importantes

---

## 1. Requisitos Previos

- Docker Desktop instalado
- Acceso a un broker Kafka externo (host, puerto, topic)
- Variables de entorno necesarias para Redis y Postgres si aplica

---

## 2. Configuración de Variables de Entorno


El microservicio utiliza variables de entorno definidas en el archivo `.env` (ver plantilla `.env.example`). Ejemplo seguro:

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=<KAFKA_HOST>:<KAFKA_PORT>
SPRING_REDIS_HOST=<REDIS_HOST>
SPRING_REDIS_PORT=<REDIS_PORT>
SPRING_DATASOURCE_URL=<POSTGRES_URL>
SPRING_DATASOURCE_USERNAME=<POSTGRES_USER>
SPRING_DATASOURCE_PASSWORD=<POSTGRES_PASSWORD>
KAFKA_TOPIC_EVENTS_STANDARDIZED=<TOPIC_NAME>
SERVER_PORT=8000
```

**Importante:**
- No subas el archivo `.env` con credenciales reales al repositorio. Usa `.env.example` como plantilla.
- Docker Compose lee automáticamente el archivo `.env` si está en el mismo directorio que `docker-compose.ingestor.yml`.
- Las variables se inyectan al contenedor y son leídas por Spring Boot.

---

## 3. Levantar el Microservicio con Docker

Desde la carpeta `src/ingestor`:

```bash
docker-compose -f docker-compose.ingestor.yml up --build
```

**Salida esperada:**
```
ingestor_1  | Started IngestorApplication in 4.567 seconds
ingestor_1  | Kafka bootstrap servers: kafka:9092
ingestor_1  | Publishing to topic: events.standardized
```

---

## 4. Endpoints Disponibles y Ejemplos

### 4.1. Ingesta de Evento Individual

**Endpoint:** `POST /events`

**Body ejemplo (evento completo):**
```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0001-4001-8001-100000000001",
  "trace_id": "c1d2e3f4-0001-4001-8001-200000000001",
  "timestamp": "2025-10-01T15:00:00Z",
  "partition_key": "panic.button",
  "geo": {
    "zone": "zone_1",
    "lat": -12.0464,
    "lon": -77.0428
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "panico",
    "identificador_dispositivo": "BTN-001"
  }
}
```

**Respuesta esperada (202 Accepted):**
```json
{
  "status": "success",
  "message": "Event processed and published successfully",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "event_type": "panic.button",
  "partition_key": "zone_1",
  "timestamp": "2025-10-01T12:34:56.789Z"
}
```

### 4.2. Ingesta Masiva

**Endpoint:** `POST /events/bulk`

**Body ejemplo:**
```json
[
  {
    "event_version": "1.0",
    "event_type": "sensor.lpr",
    "event_id": "a1b2c3d4-0002-4002-8002-000000000002",
    "producer": "test-suite",
    "source": "simulated",
    "timestamp": "2025-10-01T15:00:00Z",
    "partition_key": "sensor.lpr",
    "geo": { 
      "zone": "zone_1", 
      "lat": -12.0464, 
      "lon": -77.0428 
    },
    "severity": "warning",
    "payload": { 
      "placa_vehicular": "ABC123",
      "velocidad_estimada": 95.0 
    }
  },
  {
    "event_version": "1.0",
    "event_type": "citizen.report",
    "event_id": "a1b2c3d4-0003-4003-8003-000000000003",
    "producer": "test-suite",
    "source": "simulated",
    "timestamp": "2025-10-01T15:05:00Z",
    "partition_key": "citizen.report",
    "geo": { 
      "zone": "zone_2", 
      "lat": -12.0500, 
      "lon": -77.0500 
    },
    "severity": "warning",
    "payload": { 
      "tipo_evento": "accidente",
      "mensaje_descriptivo": "Colisión en intersección"
    }
  }
]
```

**Respuesta esperada:**
```json
{
  "status": "completed",
  "message": "Bulk processing completed",
  "total": 2,
  "successful": 2,
  "failed": 0,
  "successful_events": [
    "a1b2c3d4-0002-4002-8002-000000000002",
    "a1b2c3d4-0003-4003-8003-000000000003"
  ],
  "failed_events": [],
  "timestamp": "2025-10-01T12:35:00.123Z"
}
```

### 4.3. Health Check

**Endpoint:** `GET /events/health`

**Respuesta:**
```json
{
  "status": "UP",
  "kafka": "CONNECTED",
  "database": "CONNECTED",
  "timestamp": "2025-10-01T12:36:00.456Z"
}
```

### 4.4. Obtener Esquema Canónico

**Endpoint:** `GET /events/schema`

**Respuesta:** Retorna el JSON Schema v1.0 completo

---

## 5. 🔄 Enriquecimiento Automático de Eventos

El **Ingestor** implementa un enriquecedor automático (`EventEnricher`) que completa campos opcionales que falten en el evento.

### 5.1. Campos Enriquecidos Automáticamente

| Campo | Obligatorio | Si falta | Acción del Ingestor |
|-------|-------------|----------|---------------------|
| `event_id` | ✅ Sí | ❌ Error | Rechaza evento (400 Bad Request) |
| `event_version` | ✅ Sí | ❌ Error | Rechaza evento |
| `event_type` | ✅ Sí | ❌ Error | Rechaza evento |
| `producer` | ✅ Sí | ❌ Error | Rechaza evento |
| `source` | ✅ Sí | ❌ Error | Rechaza evento |
| `partition_key` | ⚠️ Opcional | ✅ Auto-genera | Usa el valor de `event_type` |
| `geo` | ✅ Sí | ❌ Error | Rechaza evento |
| `severity` | ✅ Sí | ❌ Error | Rechaza evento |
| `payload` | ✅ Sí | ❌ Error | Rechaza evento |
| **`timestamp`** | ⚠️ Opcional | ✅ Auto-genera | Timestamp UTC actual (ISO-8601) |
| **`trace_id`** | ⚠️ Opcional | ✅ Auto-genera | UUID v4 aleatorio |
| **`correlation_id`** | ⚠️ Opcional | ✅ Auto-genera | UUID v4 aleatorio |

### 5.2. Ejemplo: Evento Mínimo (Sin Enriquecimiento Manual)

Puedes enviar un evento **sin** `timestamp`, `trace_id` ni `correlation_id`:

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "f1e2d3c4-9999-4999-8999-000000009999",
  "producer": "test-minimal",
  "source": "simulated",
  "partition_key": "panic.button",
  "geo": {
    "zone": "zone_test",
    "lat": -12.0464,
    "lon": -77.0428
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "test_minimo"
  }
}
```

**El Ingestor lo enriquecerá automáticamente:**

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "f1e2d3c4-9999-4999-8999-000000009999",
  "producer": "test-minimal",
  "source": "simulated",
  "timestamp": "2025-10-01T12:40:15.678Z",        // ← Auto-generado
  "trace_id": "d4e5f6a7-1234-4567-89ab-123456789abc", // ← Auto-generado
  "correlation_id": "e5f6a7b8-2345-5678-9abc-234567890bcd", // ← Auto-generado
  "partition_key": "panic.button",
  "geo": {
    "zone": "zone_test",
    "lat": -12.0464,
    "lon": -77.0428
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "test_minimo"
  }
}
```

### 5.3. ✅ Ventajas del Enriquecimiento Automático

1. **Simplifica testing**: No necesitas generar UUIDs manualmente para cada evento
2. **Garantiza trazabilidad**: Todos los eventos tienen `trace_id` único
3. **Timestamps precisos**: Se usa el momento exacto de ingesta
4. **Flexibilidad**: Puedes enviar campos completos si necesitas valores específicos
5. **Compatibilidad**: Funciona con herramientas de testing (Artillery, Postman, curl)

### 5.4. ⚠️ Comportamiento Importante

- Si **envías** `timestamp`, `trace_id` o `correlation_id`, el Ingestor **respetará** tus valores
- Si **omites** estos campos, se generarán automáticamente
- Los UUIDs auto-generados cumplen con el formato UUID v4
- El `timestamp` auto-generado está en formato ISO-8601 UTC

### 5.5. Ejemplo: Evento con Enriquecimiento Parcial

```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0010-4010-8010-000000000010",
  "producer": "cam-001",
  "source": "simulated",
  "correlation_id": "e5f6a7b8-2345-5678-9abc-234567890bcd",  // ← Proporcionado manualmente (UUID v4 válido)
  // trace_id y timestamp se auto-generarán
  "partition_key": "sensor.lpr", // ← Auto-generado igual a event_type si falta
  "geo": {
    "zone": "zone_autopista",
    "lat": -12.0600,
    "lon": -77.0600
  },
  "severity": "warning",
  "payload": {
    "placa_vehicular": "XYZ789",
    "velocidad_estimada": 105.0
  }
}
```

**Resultado:**
- ✅ `correlation_id`: Usa tu valor `"mi-correlacion-personalizada-123"`
- ✅ `trace_id`: Auto-generado (UUID v4)
- ✅ `timestamp`: Auto-generado (UTC actual)

---

## 6. Notas Importantes

- Usa `.env.example` como plantilla para variables de entorno.
- Consulta los logs del contenedor para debugging: `docker logs -f ingestor`
- Verifica eventos en Kafka con Kafka CLI o Kafka UI.

---

**Para cualquier duda, revisa este README o contacta al responsable del microservicio.**

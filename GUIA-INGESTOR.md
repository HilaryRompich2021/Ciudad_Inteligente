# Guía de Implementación: Ingestor

## Tabla de Contenidos
1. [Introducción](#introducción)
2. [Arquitectura del Ingestor](#arquitectura-del-ingestor)
3. [Flujo de Procesamiento](#flujo-de-procesamiento)
4. [Componentes del Ingestor](#componentes-del-ingestor)
5. [Endpoints REST API](#endpoints-rest-api)
6. [Enriquecimiento Automático](#enriquecimiento-automático)
7. [Validación de Eventos](#validación-de-eventos)
8. [Persistencia de Datos](#persistencia-de-datos)
9. [Troubleshooting](#troubleshooting)

---

## Introducción

Este documento explica cómo funciona el **Ingestor**, el microservicio encargado de recibir eventos desde múltiples fuentes, validarlos, enriquecerlos y publicarlos en Kafka para su procesamiento posterior.

### ¿Qué hace el Ingestor?

El Ingestor es la "puerta de entrada" del sistema de ciudad inteligente. Su trabajo es:
- **Recibir eventos** vía REST API (HTTP POST)
- **Validar formato** contra esquema JSON canónico
- **Enriquecer automáticamente** campos faltantes (timestamp, IDs de correlación)
- **Persistir eventos** en PostgreSQL para auditoría
- **Publicar en Kafka** al topic `t01.events.standardized`
- **Procesar eventos individuales y en lote** (bulk processing)

### Tecnologías Clave

| Componente | Tecnología | Versión | Propósito |
|------------|------------|---------|-----------|
| Framework | Spring Boot | 3.5.5 | Base del microservicio |
| Web | Spring Web | - | REST API |
| Mensajería | Spring Kafka | - | Publicación a Kafka |
| Base de Datos | Spring Data JPA | - | Persistencia en PostgreSQL |
| Validación | JSON Schema Validator | 1.0.87 | Validación contra esquema |
| Serialización | Jackson | - | JSON a objetos Java |

---

## Arquitectura del Ingestor

### Diagrama de Flujo Completo

```
┌─────────────────────────────────────────────────────────────────┐
│                    FUENTES EXTERNAS                              │
│  - Aplicaciones Web/Mobile                                       │
│  - Sensores IoT                                                  │
│  - Sistemas de Terceros                                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTP POST
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    EventController.java                          │
│  Endpoints REST:                                                 │
│  - POST /events           → Evento individual                   │
│  - POST /events/bulk      → Múltiples eventos                   │
│  - GET  /events/health    → Estado del servicio                 │
│  - GET  /events/schema    → Esquema canónico                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Delega procesamiento
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EventService.java                           │
│  1. Enriquecimiento (EventEnricher)                             │
│  2. Validación (CanonicalEventValidator)                        │
│  3. Persistencia (EventRepository)                              │
│  4. Publicación (KafkaTemplate)                                 │
└──────────┬──────────────────────┬───────────────────────────────┘
           │                      │
           │ Enriquece            │ Valida
           ▼                      ▼
┌────────────────────┐  ┌────────────────────────────────────────┐
│ EventEnricher.java │  │ CanonicalEventValidator.java           │
│ - timestamp        │  │ - Compara contra schema JSON           │
│ - trace_id         │  │ - Verifica campos requeridos           │
│ - correlation_id   │  │ - Valida tipos y formatos              │
└────────────────────┘  └────────────────────────────────────────┘
           │
           │ Guarda en BD
           ▼
┌─────────────────────────────────────────────────────────────────┐
│               PostgreSQL Database                                │
│  Tabla: t01_event_ingestor                                      │
│  - event_id, event_type, zone, timestamp, payload, etc.         │
└─────────────────────────────────────────────────────────────────┘
           │
           │ Publica
           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    KAFKA TOPIC                                   │
│              t01.events.standardized                             │
│  Consumidores:                                                   │
│  - Correlator (detección de patrones)                           │
│  - Otros microservicios de análisis                             │
└─────────────────────────────────────────────────────────────────┘
```

### Responsabilidades por Capa

| Capa | Componente | Responsabilidad |
|------|------------|-----------------|
| **Presentación** | EventController | Exponer endpoints REST, manejar requests HTTP |
| **Lógica de Negocio** | EventService | Orquestar procesamiento (enriquecer, validar, persistir, publicar) |
| **Enriquecimiento** | EventEnricher | Agregar campos automáticos faltantes |
| **Validación** | CanonicalEventValidator | Validar contra esquema JSON |
| **Persistencia** | EventRepository | Guardar en PostgreSQL vía JPA |
| **Mensajería** | KafkaTemplate | Publicar eventos a Kafka |
| **Monitoreo** | HealthService | Estado del servicio y dependencias |

---

## Flujo de Procesamiento

### Paso 1: Recepción del Evento (EventController)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/controller/EventController.java`

```java
@PostMapping
public ResponseEntity<?> ingestEvent(@Valid @RequestBody CanonicalEvent event) {
    // 1. Recibe evento desde cliente HTTP
    // 2. Spring Boot deserializa JSON → CanonicalEvent
    // 3. Delega a EventService para procesamiento
    // 4. Retorna respuesta con detalles
}
```

**Request Example:**
```bash
POST http://localhost:8000/events
Content-Type: application/json

{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "evt-123",
  "producer": "mobile-app",
  "source": "simulated",
  "partition_key": "zone-norte",
  "geo": {
    "zone": "Norte",
    "lat": -12.0464,
    "lon": -77.0428
  },
  "severity": "critical",
  "payload": {
    "user_id": "user-456",
    "description": "Emergency"
  }
}
```

**Response Example:**
```json
{
  "status": "success",
  "message": "Event processed and published successfully",
  "event_id": "evt-123",
  "event_type": "panic.button",
  "partition_key": "zone-norte",
  "timestamp": "2025-10-01T10:30:00Z"
}
```

**¿Qué buscar en caso de error?**
- Si retorna 400: Verificar formato JSON, campos requeridos
- Si retorna 500: Revisar logs de conexión a Kafka/PostgreSQL
- Logs: `[EventController]` en consola

---

### Paso 2: Enriquecimiento Automático (EventEnricher)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/service/EventEnricher.java`

El Ingestor puede **generar automáticamente** campos que faltan:

```java
public void enrichEventIfNeeded(CanonicalEvent event) {
    // Si timestamp no viene, generarlo
    if (event.getTimestamp() == null || event.getTimestamp().isEmpty()) {
        event.setTimestamp(Instant.now().toString());
    }
    
    // Si trace_id no viene, generar UUID
    if (event.getTraceId() == null || event.getTraceId().isEmpty()) {
        event.setTraceId(UUID.randomUUID().toString());
    }
    
    // Si correlation_id no viene, generar UUID
    if (event.getCorrelationId() == null || event.getCorrelationId().isEmpty()) {
        event.setCorrelationId(UUID.randomUUID().toString());
    }
}
```

#### Campos Enriquecidos Automáticamente

| Campo | Tipo | Generación Automática | Ejemplo |
|-------|------|----------------------|---------|
| `timestamp` | ISO 8601 | `Instant.now()` | `2025-10-01T10:30:00Z` |
| `trace_id` | UUID v4 | `UUID.randomUUID()` | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |
| `correlation_id` | UUID v4 | `UUID.randomUUID()` | `6ba7b810-9dad-11d1-80b4-00c04fd430c8` |

**Ejemplo de Enriquecimiento:**

**Request (campos faltantes):**
```json
{
  "event_version": "1.0",
  "event_type": "sensor.speed",
  "event_id": "sensor-001",
  "producer": "speed-sensor",
  "source": "simulated",
  "partition_key": "zone-centro",
  "geo": {"zone": "Centro"},
  "severity": "warning",
  "payload": {"speed": 95}
}
```

**Después del Enriquecimiento:**
```json
{
  "event_version": "1.0",
  "event_type": "sensor.speed",
  "event_id": "sensor-001",
  "producer": "speed-sensor",
  "source": "simulated",
  "timestamp": "2025-10-01T10:30:00.123Z",          ← GENERADO
  "trace_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",  ← GENERADO
  "correlation_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8", ← GENERADO
  "partition_key": "zone-centro",
  "geo": {"zone": "Centro"},
  "severity": "warning",
  "payload": {"speed": 95}
}
```

**¿Qué buscar en caso de error?**
- Si enriquecimiento falla: Verificar que `EventEnricher` está inyectado en `EventService`
- Logs: `[ENRICHMENT]` en consola

---

### Paso 3: Validación del Evento (CanonicalEventValidator)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/util/CanonicalEventValidator.java`

Valida el evento contra el **esquema JSON canónico**:

```java
public void validate(String eventJson) throws Exception {
    JsonNode eventNode = objectMapper.readTree(eventJson);
    Set<ValidationMessage> errors = schema.validate(eventNode);
    
    if (!errors.isEmpty()) {
        throw new RuntimeException("Validation errors: " + errors.toString());
    }
}
```

#### Esquema JSON Canónico

**Ubicación:** `src/main/resources/canonical-event-schema.json`

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "CanonicalEventV1",
  "type": "object",
  "required": [
    "event_version", "event_type", "event_id", "producer", "source",
    "timestamp", "partition_key", "geo", "severity", "payload"
  ],
  "properties": {
    "event_version": {
      "type": "string",
      "const": "1.0"
    },
    "event_type": {
      "type": "string",
      "enum": ["panic.button", "sensor.lpr", "sensor.speed", 
               "sensor.acoustic", "citizen.report"]
    },
    "event_id": {"type": "string"},
    "producer": {"type": "string"},
    "source": {
      "type": "string",
      "enum": ["simulated"]
    },
    "correlation_id": {"type": "string"},
    "trace_id": {"type": "string"},
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "partition_key": {"type": "string"},
    "geo": {
      "type": "object",
      "required": ["zone"],
      "properties": {
        "zone": {"type": "string"},
        "lat": {"type": "number"},
        "lon": {"type": "number"}
      }
    },
    "severity": {
      "type": "string",
      "enum": ["info", "warning", "critical"]
    },
    "payload": {"type": "object"}
  },
  "additionalProperties": false
}
```

#### Reglas de Validación

| Regla | Descripción | Ejemplo Válido | Ejemplo Inválido |
|-------|-------------|----------------|------------------|
| **Campos requeridos** | 10 campos obligatorios | Todos presentes | Falta `event_type` |
| **event_version** | Debe ser "1.0" | `"1.0"` | `"2.0"` |
| **event_type** | Solo 5 tipos permitidos | `"panic.button"` | `"custom.event"` |
| **source** | Solo "simulated" | `"simulated"` | `"real"` |
| **severity** | Solo info/warning/critical | `"critical"` | `"high"` |
| **timestamp** | Formato ISO 8601 | `"2025-10-01T10:30:00Z"` | `"01/10/2025"` |
| **geo.zone** | Campo requerido en geo | `{"zone": "Norte"}` | `{"lat": 10}` |
| **payload** | Debe ser objeto JSON | `{"key": "value"}` | `"string"` |
| **additionalProperties** | No campos extra | Campos del schema | `{"custom": "field"}` |

**Ejemplo de Error de Validación:**

**Request Inválido:**
```json
{
  "event_version": "2.0",           ← ERROR: debe ser "1.0"
  "event_type": "custom.type",      ← ERROR: tipo no permitido
  "event_id": "evt-001",
  "producer": "test",
  "source": "simulated",
  "timestamp": "2025-10-01T10:30:00Z",
  "partition_key": "key",
  "geo": {"lat": 10.5},             ← ERROR: falta campo "zone"
  "severity": "high",               ← ERROR: debe ser info/warning/critical
  "payload": {}
}
```

**Response de Error:**
```json
{
  "status": "error",
  "message": "Failed to process event",
  "error_details": "Validation errors: [$.event_version: does not have a value in the enumeration [1.0], $.event_type: does not have a value in the enumeration [...], $.geo: required property 'zone' not found, $.severity: does not have a value in the enumeration [info, warning, critical]]",
  "event_id": "evt-001",
  "timestamp": "2025-10-01T10:30:15Z"
}
```

**¿Qué buscar en caso de error?**
- Si validación falla: Revisar mensaje de error, comparar con esquema JSON
- Si schema no carga: Verificar que `canonical-event-schema.json` existe en `src/main/resources/`
- Logs: `Validation errors:` en consola

---

### Paso 4: Persistencia en PostgreSQL (EventRepository)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/repository/EventRepository.java`

```java
@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    // JPA genera automáticamente métodos CRUD
    // save(), findById(), findAll(), delete(), etc.
}
```

#### Modelo de Datos: EventEntity

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/model/EventEntity.java`

Mapea eventos a la tabla PostgreSQL:

```java
@Entity
@Table(name = "t01_event_ingestor")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID eventId;
    
    private String eventType;
    private String eventVersion;
    private String producer;
    private String source;
    private UUID correlationId;
    private UUID traceId;
    private String partitionKey;
    private OffsetDateTime tsUtc;        // timestamp
    private String zone;                 // geo.zone
    private Double geoLat;               // geo.lat
    private Double geoLon;               // geo.lon
    private String severity;
    
    @Type(JsonNodeBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;            // Almacenado como JSONB
}
```

#### Estructura de la Tabla PostgreSQL

```sql
CREATE TABLE t01_event_ingestor (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100),
    event_version VARCHAR(10),
    producer VARCHAR(255),
    source VARCHAR(50),
    correlation_id UUID,
    trace_id UUID,
    partition_key VARCHAR(100),
    ts_utc TIMESTAMP WITH TIME ZONE,
    zone VARCHAR(100),
    geo_lat DOUBLE PRECISION,
    geo_lon DOUBLE PRECISION,
    severity VARCHAR(20),
    payload JSONB
);
```

#### Mapeo: CanonicalEvent → EventEntity

El método `mapToEntity()` en `EventService` realiza la conversión:

| Campo CanonicalEvent | Campo EventEntity | Transformación |
|---------------------|-------------------|----------------|
| `event_id` (String) | `eventId` (UUID) | `UUID.fromString()` |
| `event_type` | `eventType` | Directo |
| `timestamp` (String) | `tsUtc` (OffsetDateTime) | `OffsetDateTime.parse()` |
| `geo.zone` | `zone` | Extracción anidada |
| `geo.lat` | `geoLat` | Extracción anidada |
| `geo.lon` | `geoLon` | Extracción anidada |
| `payload` (Map) | `payload` (JsonNode) | `objectMapper.valueToTree()` |

**¿Qué buscar en caso de error?**
- Si no persiste: Verificar conexión a PostgreSQL (`application.properties`)
- Si error de tipo: Verificar conversiones UUID/OffsetDateTime
- Consultar eventos guardados:
  ```sql
  SELECT * FROM t01_event_ingestor ORDER BY ts_utc DESC LIMIT 10;
  ```
- Logs: `[EventRepository]` en consola

---

### Paso 5: Publicación a Kafka (KafkaTemplate)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/service/EventService.java`

```java
public void processAndPublish(CanonicalEvent event) throws Exception {
    // ... enriquecimiento, validación, persistencia ...
    
    // Usar eventId como clave si partitionKey es null
    String key = event.getPartitionKey() != null 
        ? event.getPartitionKey() 
        : event.getEventId();
    
    kafkaTemplate.send(TOPIC, key, event)
        .whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Successfully published event to Kafka: " 
                    + result.getRecordMetadata());
            } else {
                System.err.println("Failed to publish event to Kafka: " 
                    + ex.getMessage());
            }
        });
}
```

#### Configuración de Kafka

**Ubicación:** `src/main/resources/application.properties`

```properties
# Kafka Producer
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties.spring.json.add.type.headers=false
spring.kafka.admin.auto-create=true

# Topic
app.kafka.topic.events-standardized=t01.events.standardized
```

#### Particionamiento en Kafka

Kafka distribuye eventos en **particiones** basándose en la **clave (key)**:

```
Key = partition_key (si existe) O event_id (fallback)

Ejemplo:
- Evento con partition_key="zone-norte" → Siempre va a la misma partición
- Todos los eventos de "zona-norte" se procesan en orden
- Permite paralelización por zona
```

**Ventajas del Particionamiento:**
- ✅ Eventos de la misma zona se procesan en orden
- ✅ Permite múltiples consumers en paralelo
- ✅ Mejor distribución de carga

**¿Qué buscar en caso de error?**
- Si no publica: Verificar que Kafka esté corriendo (`docker ps`)
- Si error de serialización: Verificar `JsonSerializer` configurado
- Verificar topic existe:
  ```bash
  docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
  ```
- Logs: `Successfully published event to Kafka` en consola

---

## Componentes del Ingestor

### EventController (Capa de Presentación)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/controller/EventController.java`

Este controlador expone 4 endpoints REST:

#### 1. Ingesta de Evento Individual

```
POST http://localhost:8000/events
Content-Type: application/json
Body: CanonicalEvent (JSON)
```

**Casos de Uso:**
- Aplicaciones móviles enviando botón de pánico
- Sensores IoT enviando lecturas en tiempo real
- Sistemas de terceros integrándose vía API

**Respuesta Exitosa (202 Accepted):**
```json
{
  "status": "success",
  "message": "Event processed and published successfully",
  "event_id": "evt-123",
  "event_type": "panic.button",
  "partition_key": "zone-norte",
  "timestamp": "2025-10-01T10:30:00Z"
}
```

**Respuesta de Error (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Failed to process event",
  "error_details": "Validation errors: ...",
  "event_id": "evt-123",
  "timestamp": "2025-10-01T10:30:00Z"
}
```

---

#### 2. Ingesta en Lote (Bulk Processing)

```
POST http://localhost:8000/events/bulk
Content-Type: application/json
Body: Array de CanonicalEvent
```

**Casos de Uso:**
- Importación masiva de datos históricos
- Carga de eventos simulados para testing
- Sistemas batch enviando eventos acumulados

**Request Example:**
```json
[
  {
    "event_version": "1.0",
    "event_type": "sensor.speed",
    "event_id": "evt-001",
    "producer": "sensor-1",
    "source": "simulated",
    "partition_key": "zone-norte",
    "geo": {"zone": "Norte"},
    "severity": "warning",
    "payload": {"speed": 90}
  },
  {
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "evt-002",
    "producer": "app-user-123",
    "source": "simulated",
    "partition_key": "zone-centro",
    "geo": {"zone": "Centro"},
    "severity": "critical",
    "payload": {"location": "Plaza de Armas"}
  }
]
```

**Respuesta (202 Accepted):**
```json
{
  "status": "completed",
  "message": "Bulk processing completed",
  "total": 2,
  "successful": 2,
  "failed": 0,
  "successful_events": ["evt-001", "evt-002"],
  "failed_events": [],
  "timestamp": "2025-10-01T10:30:00Z"
}
```

**Respuesta con Errores Parciales:**
```json
{
  "status": "completed",
  "message": "Bulk processing completed",
  "total": 3,
  "successful": 2,
  "failed": 1,
  "successful_events": ["evt-001", "evt-002"],
  "failed_events": [
    {
      "index": 2,
      "event_id": "evt-003",
      "error": "Validation errors: $.severity: does not have a value in the enumeration [info, warning, critical]"
    }
  ],
  "timestamp": "2025-10-01T10:30:00Z"
}
```

**Lógica de Procesamiento Bulk:**
```java
public BulkProcessResult processAndPublishBulk(List<CanonicalEvent> events) {
    List<String> successfulEvents = new ArrayList<>();
    List<ProcessingError> failedEvents = new ArrayList<>();
    
    for (int i = 0; i < events.size(); i++) {
        try {
            CanonicalEvent event = events.get(i);
            processAndPublish(event);  // Usa mismo flujo que endpoint individual
            successfulEvents.add(event.getEventId());
        } catch (Exception e) {
            String eventId = events.get(i).getEventId() != null 
                ? events.get(i).getEventId() 
                : "unknown";
            failedEvents.add(new ProcessingError(i, eventId, e.getMessage()));
        }
    }
    
    return new BulkProcessResult(events.size(), successfulEvents, failedEvents);
}
```

**Características del Bulk Processing:**
- ✅ **Procesamiento independiente:** Un error en un evento NO detiene los demás
- ✅ **Respuesta detallada:** Indica exactamente qué eventos fallaron y por qué
- ✅ **Transaccionalidad por evento:** Cada evento se persiste y publica independientemente
- ✅ **Auditoría completa:** Reporte de éxitos y fallos con índices

**¿Qué buscar en caso de error?**
- Si todos fallan: Verificar conexiones a Kafka/PostgreSQL
- Si algunos fallan: Revisar `failed_events` en respuesta
- Logs: `Bulk processing completed: X/Y successful`

---

#### 3. Health Check

```
GET http://localhost:8000/events/health
```

**Ubicación del servicio:** `src/main/java/com/ciudadesinteligentes/ingestor/service/HealthService.java`

**Respuesta (200 OK):**
```json
{
  "status": "UP",
  "kafka": "available",
  "validator": "ready",
  "timestamp": "2025-10-01T10:30:00Z",
  "service": "ingestor",
  "version": "1.0",
  "details": {
    "topic": "t01.events.standardized",
    "schema_version": "1.0",
    "kafka_template_configured": true,
    "note": "Health check uses basic availability verification"
  }
}
```

**Respuesta Degradada:**
```json
{
  "status": "DEGRADED",
  "kafka": "unavailable",
  "validator": "ready",
  "timestamp": "2025-10-01T10:30:00Z",
  "service": "ingestor",
  "version": "1.0",
  "details": { ... }
}
```

**Verificaciones Realizadas:**
- ✅ **Kafka:** Verifica que `KafkaTemplate` esté configurado
- ✅ **Validator:** Verifica que el validador JSON esté cargado
- ❌ **NO verifica PostgreSQL** (solo Kafka y validador)

**¿Qué buscar en caso de error?**
- Si retorna 503: El servicio no está operativo
- Si kafka="unavailable": Verificar conexión a Kafka
- Si validator="error": Revisar que `canonical-event-schema.json` exista

---

#### 4. Obtener Esquema Canónico

```
GET http://localhost:8000/events/schema
```

**Respuesta (200 OK):**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "CanonicalEventV1",
  "type": "object",
  "required": [...],
  "properties": {...}
}
```

**Uso:**
- 📄 Documentación para desarrolladores externos
- ✅ Validación del lado del cliente antes de enviar
- 🔍 Referencia rápida de campos disponibles

---

### EventService (Lógica de Negocio)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/service/EventService.java`

Este servicio **orquesta** todo el flujo de procesamiento:

```java
public void processAndPublish(CanonicalEvent event) throws Exception {
    // 1. ENRIQUECIMIENTO
    eventEnricher.enrichEventIfNeeded(event);
    
    // 2. VALIDACIÓN
    String eventJson = objectMapper.writeValueAsString(event);
    validator.validate(eventJson);
    
    // 3. PERSISTENCIA
    EventEntity entity = mapToEntity(event);
    eventRepository.save(entity);
    
    // 4. PUBLICACIÓN A KAFKA
    String key = event.getPartitionKey() != null 
        ? event.getPartitionKey() 
        : event.getEventId();
    kafkaTemplate.send(TOPIC, key, event);
}
```

**Flujo Secuencial:**
1. ✅ Enriquece campos faltantes (timestamp, IDs)
2. ✅ Valida contra esquema JSON (lanza excepción si falla)
3. ✅ Guarda en PostgreSQL para auditoría
4. ✅ Publica a Kafka para procesamiento downstream

**Manejo de Errores:**
- Si **validación falla** → Evento rechazado, NO se guarda ni publica
- Si **persistencia falla** → Evento rechazado, NO se publica (integridad de datos)
- Si **publicación falla** → Evento YA está guardado en BD (puede reintentarse)

**¿Qué buscar en caso de error?**
- Si falla enriquecimiento: Logs `[ENRICHMENT]`
- Si falla validación: Logs `Validation errors:`
- Si falla persistencia: Logs de JPA/Hibernate
- Si falla publicación: Logs `Failed to publish event to Kafka:`

---

### Modelos de Datos

#### CanonicalEvent (Modelo de Dominio)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/model/CanonicalEvent.java`

Representa el evento en memoria:

```java
public class CanonicalEvent {
    private String eventVersion;
    private String eventType;
    private String eventId;
    private String producer;
    private String source;
    private String correlationId;
    private String traceId;
    private String timestamp;
    private String partitionKey;
    private Geo geo;              // Clase anidada
    private String severity;
    private Map<String, Object> payload;  // Flexibilidad para payloads dinámicos
    
    // Getters y Setters
}
```

**Clase Anidada Geo:**
```java
public static class Geo {
    private String zone;   // Requerido
    private Double lat;    // Opcional
    private Double lon;    // Opcional
    
    // Getters y Setters
}
```

---

#### BulkProcessResult (Resultado de Bulk)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/ingestor/model/BulkProcessResult.java`

Encapsula el resultado del procesamiento bulk:

```java
public class BulkProcessResult {
    private int total;                          // Total de eventos enviados
    private List<String> successfulEvents;      // IDs de eventos exitosos
    private List<ProcessingError> failedEvents; // Detalles de fallos
    
    public int getSuccessfulCount() {
        return successfulEvents.size();
    }
    
    public int getFailedCount() {
        return failedEvents.size();
    }
    
    // Clase interna para errores
    public static class ProcessingError {
        private int index;        // Índice en el array original
        private String eventId;   // ID del evento fallido
        private String error;     // Mensaje de error
        
        // Constructor, Getters
    }
}
```

---

## Endpoints REST API

### Resumen de Endpoints

| Método | Endpoint | Propósito | Body | Response |
|--------|----------|-----------|------|----------|
| POST | `/events` | Ingestar evento individual | CanonicalEvent | 202 Accepted |
| POST | `/events/bulk` | Ingestar múltiples eventos | Array[CanonicalEvent] | 202 Accepted |
| GET | `/events/health` | Estado del servicio | - | 200 OK / 503 Unavailable |
| GET | `/events/schema` | Esquema JSON canónico | - | 200 OK |

### Códigos de Estado HTTP

| Código | Significado | Cuándo Ocurre |
|--------|-------------|---------------|
| 200 OK | Éxito (GET) | Health/Schema exitosos |
| 202 Accepted | Evento aceptado para procesamiento | POST exitoso |
| 400 Bad Request | Error de validación | Evento no cumple esquema |
| 500 Internal Server Error | Error del servidor | Fallo en Kafka/PostgreSQL |
| 503 Service Unavailable | Servicio no disponible | Health check falla |

### Ejemplos de Uso con cURL

#### Enviar Evento Individual
```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "evt-123",
    "producer": "mobile-app",
    "source": "simulated",
    "partition_key": "zone-norte",
    "geo": {"zone": "Norte", "lat": -12.0464, "lon": -77.0428},
    "severity": "critical",
    "payload": {"user_id": "user-456"}
  }'
```

#### Enviar Eventos en Lote
```bash
curl -X POST http://localhost:8000/events/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {
      "event_version": "1.0",
      "event_type": "sensor.speed",
      "event_id": "evt-001",
      "producer": "sensor-1",
      "source": "simulated",
      "partition_key": "zone-norte",
      "geo": {"zone": "Norte"},
      "severity": "warning",
      "payload": {"speed": 90}
    },
    {
      "event_version": "1.0",
      "event_type": "sensor.lpr",
      "event_id": "evt-002",
      "producer": "lpr-camera-5",
      "source": "simulated",
      "partition_key": "zone-centro",
      "geo": {"zone": "Centro"},
      "severity": "info",
      "payload": {"plate": "ABC-123"}
    }
  ]'
```

#### Verificar Estado del Servicio
```bash
curl http://localhost:8000/events/health
```

#### Obtener Esquema JSON
```bash
curl http://localhost:8000/events/schema
```

---

## Enriquecimiento Automático

### Ventajas del Enriquecimiento

| Ventaja | Descripción |
|---------|-------------|
| **Simplifica clientes** | No necesitan generar timestamps ni IDs |
| **Consistencia** | Formato uniforme de timestamps (ISO 8601) |
| **Trazabilidad** | UUIDs únicos para seguimiento distribuido |
| **Retrocompatibilidad** | Clientes legacy sin esos campos siguen funcionando |

### Casos de Uso

#### Caso 1: Sensor IoT Simple
**Problema:** Sensor con recursos limitados no puede generar UUIDs

**Solución:**
```json
// Request del sensor (mínimo)
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "sensor-acustico-01",
  "producer": "acoustic-sensor",
  "source": "simulated",
  "partition_key": "zone-este",
  "geo": {"zone": "Este"},
  "severity": "info",
  "payload": {"decibels": 75}
}

// Después del enriquecimiento
{
  ... (campos originales) ...,
  "timestamp": "2025-10-01T10:30:00.123Z",         ← AGREGADO
  "trace_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",  ← AGREGADO
  "correlation_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8"  ← AGREGADO
}
```

#### Caso 2: Sistema Legacy sin Soporte de Timestamp
**Problema:** Sistema antiguo envía eventos sin timestamp

**Solución:** Ingestor agrega timestamp del momento de recepción

---

## Validación de Eventos

### Errores Comunes de Validación

#### Error 1: Campo Requerido Faltante
```json
// Request
{
  "event_version": "1.0",
  "event_type": "panic.button",
  // ❌ Falta: event_id
  "producer": "app",
  "source": "simulated",
  "partition_key": "key",
  "geo": {"zone": "Norte"},
  "severity": "critical",
  "payload": {}
}

// Response
{
  "status": "error",
  "error_details": "Validation errors: [$.event_id: is missing but it is required]"
}
```

#### Error 2: Tipo de Evento Inválido
```json
// Request
{
  "event_type": "custom.alert",  // ❌ No está en enum
  // ... otros campos ...
}

// Response
{
  "error_details": "Validation errors: [$.event_type: does not have a value in the enumeration [panic.button, sensor.lpr, sensor.speed, sensor.acoustic, citizen.report]]"
}
```

#### Error 3: Severity Inválido
```json
// Request
{
  "severity": "high",  // ❌ Debe ser info/warning/critical
  // ... otros campos ...
}

// Response
{
  "error_details": "Validation errors: [$.severity: does not have a value in the enumeration [info, warning, critical]]"
}
```

#### Error 4: Geo Sin Zona
```json
// Request
{
  "geo": {"lat": -12.0, "lon": -77.0},  // ❌ Falta campo "zone"
  // ... otros campos ...
}

// Response
{
  "error_details": "Validation errors: [$.geo: required property 'zone' not found]"
}
```

### Modificar el Esquema JSON

Para agregar nuevos tipos de eventos o cambiar validaciones:

**1. Editar:** `src/main/resources/canonical-event-schema.json`

**Ejemplo: Agregar nuevo tipo de evento**
```json
{
  "event_type": {
    "type": "string",
    "enum": [
      "panic.button", 
      "sensor.lpr", 
      "sensor.speed", 
      "sensor.acoustic", 
      "citizen.report",
      "fire.alarm"  ← NUEVO
    ]
  }
}
```

**2. Reiniciar:** El esquema se carga al arrancar la aplicación

**3. Probar:** Enviar evento con el nuevo tipo

---

## Persistencia de Datos

### Consultas Útiles en PostgreSQL

#### Ver últimos eventos ingestados
```sql
SELECT 
  event_id, 
  event_type, 
  zone, 
  severity, 
  ts_utc
FROM t01_event_ingestor
ORDER BY ts_utc DESC
LIMIT 20;
```

#### Contar eventos por tipo
```sql
SELECT 
  event_type, 
  COUNT(*) as total
FROM t01_event_ingestor
GROUP BY event_type
ORDER BY total DESC;
```

#### Ver eventos de una zona específica
```sql
SELECT 
  event_id, 
  event_type, 
  severity, 
  ts_utc, 
  payload
FROM t01_event_ingestor
WHERE zone = 'Norte'
ORDER BY ts_utc DESC
LIMIT 10;
```

#### Ver eventos críticos
```sql
SELECT 
  event_id, 
  event_type, 
  zone, 
  ts_utc, 
  payload
FROM t01_event_ingestor
WHERE severity = 'critical'
ORDER BY ts_utc DESC;
```

#### Buscar por trace_id (debugging distribuido)
```sql
SELECT 
  event_id, 
  event_type, 
  correlation_id, 
  trace_id
FROM t01_event_ingestor
WHERE trace_id = 'f47ac10b-58cc-4372-a567-0e02b2c3d479';
```

#### Consultar payload JSONB
```sql
-- Buscar eventos con clave específica en payload
SELECT 
  event_id, 
  event_type, 
  payload->'speed' as speed
FROM t01_event_ingestor
WHERE payload ? 'speed'  -- Verifica que existe la clave
AND (payload->>'speed')::int > 80;
```

---

## Troubleshooting

### Problema 1: Evento es rechazado (400 Bad Request)

**Síntomas:**
- Response: `"status": "error"`
- Mensaje: `"Validation errors: ..."`

**Solución:**
1. **Revisar mensaje de error:** Indica exactamente qué campo falla
2. **Comparar con esquema:**
   ```bash
   curl http://localhost:8000/events/schema
   ```
3. **Verificar campos requeridos:**
   - event_version, event_type, event_id, producer, source
   - timestamp (o dejar vacío para auto-generación)
   - partition_key, geo (con zone), severity, payload

4. **Verificar enums:**
   - `event_type`: solo 5 valores permitidos
   - `source`: solo "simulated"
   - `severity`: solo info/warning/critical

5. **Probar con ejemplo mínimo:**
   ```json
   {
     "event_version": "1.0",
     "event_type": "citizen.report",
     "event_id": "test-001",
     "producer": "test-producer",
     "source": "simulated",
     "partition_key": "test-key",
     "geo": {"zone": "Test"},
     "severity": "info",
     "payload": {}
   }
   ```

---

### Problema 2: Ingestor no arranca

**Síntomas:**
- Error al ejecutar `docker-compose up`
- Logs: `Failed to start bean`

**Solución:**
1. **Verificar dependencias:**
   ```bash
   docker ps
   ```
   Debe listar: kafka, zookeeper, postgres

2. **Verificar orden de inicio:**
   - Agregar `depends_on` en `docker-compose.ingestor.yml`:
   ```yaml
   depends_on:
     - kafka
     - postgres
   ```

3. **Verificar configuración:**
   - `application.properties` debe tener URLs correctas
   - Para Docker Desktop: `host.docker.internal:29092`
   - Para WSL: `kafka:9092`

4. **Revisar logs completos:**
   ```bash
   docker logs ingestor
   ```

---

### Problema 3: Eventos no se publican a Kafka

**Síntomas:**
- Response 202 exitoso pero Correlator no recibe eventos
- Logs: `Failed to publish event to Kafka`

**Solución:**
1. **Verificar Kafka:**
   ```bash
   docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
   ```
   Debe listar: `t01.events.standardized`

2. **Verificar configuración de bootstrap-servers:**
   ```bash
   docker logs ingestor | grep "bootstrap.servers"
   ```

3. **Probar publicación manual:**
   ```bash
   docker exec -it kafka kafka-console-producer \
     --bootstrap-server localhost:9092 \
     --topic t01.events.standardized
   ```

4. **Ver mensajes en topic:**
   ```bash
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic t01.events.standardized \
     --from-beginning
   ```

5. **Revisar serializer:** Debe ser `JsonSerializer` en `application.properties`

---

### Problema 4: Eventos no se persisten en PostgreSQL

**Síntomas:**
- Response 202 exitoso pero no hay datos en BD
- Logs de Hibernate con errores

**Solución:**
1. **Verificar conexión a PostgreSQL:**
   ```bash
   docker exec -it postgres psql -U postgres -d ciudades
   ```

2. **Verificar que tabla exista:**
   ```sql
   \dt
   ```
   Debe listar: `t01_event_ingestor`

3. **Si tabla no existe:**
   ```sql
   -- JPA debería crearla automáticamente, pero si no:
   CREATE TABLE t01_event_ingestor (
     event_id UUID PRIMARY KEY,
     event_type VARCHAR(100),
     event_version VARCHAR(10),
     producer VARCHAR(255),
     source VARCHAR(50),
     correlation_id UUID,
     trace_id UUID,
     partition_key VARCHAR(100),
     ts_utc TIMESTAMP WITH TIME ZONE,
     zone VARCHAR(100),
     geo_lat DOUBLE PRECISION,
     geo_lon DOUBLE PRECISION,
     severity VARCHAR(20),
     payload JSONB
   );
   ```

4. **Verificar credenciales en `application.properties`:**
   ```properties
   spring.datasource.url=jdbc:postgresql://postgres:5432/ciudades
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   ```

5. **Revisar logs de JPA:**
   ```bash
   docker logs ingestor | grep "Hibernate"
   ```

---

### Problema 5: Health Check devuelve DEGRADED

**Síntomas:**
- GET `/events/health` → `"status": "DEGRADED"`
- `"kafka": "unavailable"`

**Solución:**
1. **Verificar que Kafka esté corriendo:**
   ```bash
   docker ps | grep kafka
   ```

2. **Verificar conectividad desde Ingestor:**
   ```bash
   docker exec -it ingestor ping kafka
   ```

3. **Revisar bootstrap-servers:**
   - Docker Desktop: `host.docker.internal:29092`
   - WSL: `kafka:9092`

4. **Reiniciar Ingestor:**
   ```bash
   docker-compose restart ingestor
   ```

5. **Si persiste, revisar logs de Kafka:**
   ```bash
   docker logs kafka | tail -50
   ```

---

### Problema 6: Bulk Processing falla completamente

**Síntomas:**
- POST `/events/bulk` → 500 Internal Server Error
- Todos los eventos fallan

**Solución:**
1. **Verificar formato del body:**
   - Debe ser **array** de eventos: `[{...}, {...}]`
   - NO objeto con array: `{"events": [...]}`

2. **Probar con un solo evento:**
   ```json
   [{
     "event_version": "1.0",
     "event_type": "citizen.report",
     "event_id": "test-001",
     "producer": "test",
     "source": "simulated",
     "partition_key": "key",
     "geo": {"zone": "Test"},
     "severity": "info",
     "payload": {}
   }]
   ```

3. **Revisar logs:**
   ```bash
   docker logs ingestor | grep "Bulk processing"
   ```

4. **Verificar límites de tamaño:**
   - Spring Boot tiene límite de tamaño de request (default 2MB)
   - Para cambiar: agregar a `application.properties`
   ```properties
   spring.servlet.multipart.max-file-size=10MB
   spring.servlet.multipart.max-request-size=10MB
   ```

---

## Resumen de Componentes Clave

### Archivos más importantes

| Archivo | Responsabilidad | ¿Qué buscar en caso de error? |
|---------|----------------|-------------------------------|
| `EventController.java` | Endpoints REST | Logs de requests HTTP, códigos de respuesta |
| `EventService.java` | Orquestación de procesamiento | Logs de cada paso (enriquecer, validar, persistir, publicar) |
| `EventEnricher.java` | Enriquecimiento automático | Campos timestamp, trace_id, correlation_id |
| `CanonicalEventValidator.java` | Validación | Mensajes "Validation errors:" |
| `EventRepository.java` | Persistencia JPA | Logs de Hibernate, conexión a PostgreSQL |
| `HealthService.java` | Monitoreo | Estado de Kafka y validador |
| `application.properties` | Configuración | URLs de Kafka, PostgreSQL, puerto |
| `canonical-event-schema.json` | Definición del esquema | Campos requeridos, enums, tipos |

---

## Comandos Útiles para Debugging

```bash
# Ver logs del Ingestor
docker logs -f ingestor

# Verificar que Kafka esté corriendo
docker ps | grep kafka

# Ver mensajes en topic Kafka
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic t01.events.standardized \
  --from-beginning

# Conectar a PostgreSQL
docker exec -it postgres psql -U postgres -d ciudades

# Ver últimos eventos en BD
docker exec -it postgres psql -U postgres -d ciudades \
  -c "SELECT event_id, event_type, zone, ts_utc FROM t01_event_ingestor ORDER BY ts_utc DESC LIMIT 10;"

# Verificar health del Ingestor
curl http://localhost:8000/events/health

# Obtener esquema JSON
curl http://localhost:8000/events/schema

# Enviar evento de prueba
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "citizen.report",
    "event_id": "test-001",
    "producer": "test",
    "source": "simulated",
    "partition_key": "test",
    "geo": {"zone": "Test"},
    "severity": "info",
    "payload": {"message": "test"}
  }'
```

---

## Flujo Completo: Cliente → Kafka

```
1. CLIENTE: Envía HTTP POST a /events
   ↓
2. CONTROLLER: Recibe y deserializa JSON → CanonicalEvent
   ↓
3. SERVICE: Orquesta procesamiento
   ├── ENRICHER: Agrega timestamp, trace_id, correlation_id (si faltan)
   ├── VALIDATOR: Valida contra canonical-event-schema.json
   ├── REPOSITORY: Guarda en PostgreSQL (tabla t01_event_ingestor)
   └── KAFKA: Publica a topic t01.events.standardized
   ↓
4. RESPONSE: Retorna 202 Accepted con detalles
   ↓
5. KAFKA: Evento disponible para consumidores (Correlator, etc.)
```

---

## Conclusión

El **Ingestor** es el punto de entrada crítico del sistema. Para cualquier error:

1. **Revisa los logs** del Ingestor (`docker logs ingestor`)
2. **Verifica conexiones** a Kafka y PostgreSQL
3. **Consulta la sección de Troubleshooting** correspondiente
4. **Prueba con eventos mínimos** para aislar el problema
5. **Usa el endpoint /health** para verificar estado del servicio

¿Necesitas agregar nuevos tipos de eventos? → Edita `canonical-event-schema.json`  
¿Quieres cambiar el topic de Kafka? → Modifica `application.properties`  
¿Necesitas auditoría adicional? → Agrega logs en `EventService.processAndPublish()`

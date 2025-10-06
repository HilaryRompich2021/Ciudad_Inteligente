# Guía de Implementación: Correlator y Grafana

## Tabla de Contenidos
1. [Introducción](#introducción)
2. [Arquitectura del Correlator](#arquitectura-del-correlator)
3. [Flujo de Procesamiento](#flujo-de-procesamiento)
4. [Componentes del Correlator](#componentes-del-correlator)
5. [Integración con Grafana](#integración-con-grafana)
6. [Troubleshooting](#troubleshooting)

---

## Introducción

Este documento explica cómo funciona el **Correlator**, el microservicio encargado de analizar eventos en tiempo real para detectar patrones sospechosos y generar alertas. También detalla cómo **Grafana** visualiza estas alertas y métricas del sistema.

### ¿Qué hace el Correlator?

El Correlator es el "cerebro" del sistema de ciudad inteligente. Su trabajo es:
- **Consumir eventos** desde Kafka (tráfico, cámaras, sensores)
- **Buscar patrones sospechosos** en ventanas de tiempo (últimos 5 minutos)
- **Generar alertas** cuando detecta anomalías
- **Persistir alertas** en PostgreSQL para análisis histórico
- **Cachear datos** en Redis para consultas rápidas

### ¿Qué hace Grafana?

Grafana es la **interfaz de visualización** que muestra:
- Mapa de calor de alertas por zona
- Gráficos de tendencias de eventos
- Métricas del sistema en tiempo real

---

## Arquitectura del Correlator

### Diagrama de Flujo

```
┌─────────────────────────────────────────────────────────────────┐
│                        KAFKA TOPIC                               │
│                   events.standardized                            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Consume
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EventConsumer.java                          │
│  - @KafkaListener topic: events.standardized                    │
│  - Valida formato del evento canónico                           │
│  - Delega procesamiento a CorrelatorService                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ processEvent()
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CorrelatorService.java                        │
│  - Idempotencia: evita duplicados por event_id (Redis)         │
│  - Guarda EventSummary por zona y placa en Redis (TTL 10 min)  │
│  - Aplica reglas de correlación en ventanas de tiempo           │
│  - Genera CorrelatedAlert cuando cumple condiciones             │
└────────────────────┬───────────────────────┬────────────────────┘
                     │                       │
                     │ Guarda + Publica      │ Cachea
                     ▼                       ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│     AlertService.java        │  │      Redis Cache             │
│  - Persiste en PostgreSQL    │  │  - Alertas activas (TTL 10m) │
│  - Mapea a AlertEntity       │  │  - EventSummary por zona     │
│  - Publica a Kafka           │  │  - Idempotencia por event_id │
└──────────────────────────────┘  └──────────────────────────────┘
                     │
                     │ Almacena
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PostgreSQL Database                         │
│  Tabla: alerts                                                   │
│  - alert_id (UUID), correlation_id (UUID), type, score, zone    │
│  - window_start, window_end, evidence (JSONB), created_at       │
└─────────────────────────────────────────────────────────────────┘
                     │
                     │ Publica a Kafka
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      KAFKA TOPIC                                 │
│                   correlated.alerts                              │
│  - Key: zona                                                     │
│  - Value: CorrelatedAlert (JSON)                                │
└─────────────────────────────────────────────────────────────────┘
```

### Tecnologías Utilizadas

| Componente | Tecnología | Propósito |
|------------|------------|-----------|
| Framework | Spring Boot 3.5.5 | Base del microservicio |
| Mensajería | Apache Kafka | Consumo de eventos |
| Base de Datos | PostgreSQL | Persistencia de alertas |
| Cache | Redis | Almacenamiento temporal de alertas activas |
| Validación | JSON Schema | Validación de eventos canónicos |
| Visualización | Grafana | Dashboards y gráficos |

---

## Flujo de Procesamiento

### Paso 1: Consumo de Eventos (EventConsumer)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/consumer/EventConsumer.java`

```java
@Component
public class EventConsumer {
    private CanonicalEventValidator validator;
    
    @Autowired
    private CorrelatorService correlatorService;
    
    @PostConstruct
    public void init() {
        // Carga el esquema JSON desde resources al iniciar
        InputStream is = getClass().getClassLoader()
            .getResourceAsStream("canonical-event-schema.json");
        String schemaJson = new String(is.readAllBytes());
        validator = new CanonicalEventValidator(schemaJson);
    }
    
    @KafkaListener(topics = "events.standardized", groupId = "correlator-group")
    public void consume(CanonicalEvent event) {
        try {
            // 1. Valida el evento contra el esquema canónico
            validator.validate(event);
            
            // 2. Envía a CorrelatorService para procesamiento
            correlatorService.processEvent(event);
        } catch (Exception e) {
            System.err.println("Evento inválido o error: " + e.getMessage());
        }
    }
}
```

> ⚠️ **IMPORTANTE:** El topic correcto es `events.standardized` (no `canonical-events`).

**Características clave:**
- **Deserialización automática:** Kafka deserializa directamente a `CanonicalEvent` (configurado en `application.properties`)
- **Validación en tiempo de consumo:** Cada evento se valida antes de procesarse
- **Manejo de errores:** Eventos inválidos se loguean pero no detienen el consumer

**¿Qué buscar en caso de error?**
- Si no recibe eventos: Verificar `spring.kafka.bootstrap-servers` y que el topic `events.standardized` exista
- Si falla validación: Revisar que `canonical-event-schema.json` esté en `src/main/resources/`
- Si error de deserialización: Verificar que el evento cumpla con la estructura de `CanonicalEvent.java`
- Logs: `"Evento inválido o error: ..."` en consola

---

### Paso 2: Correlación de Eventos (CorrelatorService)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/service/CorrelatorService.java`

Este es el componente más importante. Realiza **cuatro operaciones clave:**

#### A. Idempotencia (Evitar Duplicados)

```java
public void processEvent(CanonicalEvent event) {
    // 1. Verificar si el evento ya fue procesado (por event_id)
    String seenKey = "corr:seen:" + event.event_id;
    Boolean alreadySeen = redisTemplate.hasKey(seenKey);
    if (Boolean.TRUE.equals(alreadySeen)) {
        // Evento ya procesado, lo ignoramos
        return;
    }
    
    // 2. Marcar como visto con TTL de 10 minutos
    redisTemplate.opsForValue().set(seenKey, "1", Duration.ofMinutes(10));
    
    // ... continúa procesamiento
}
```

**¿Por qué idempotencia?**
- Kafka puede entregar el mismo mensaje múltiples veces (at-least-once delivery)
- Evita generar alertas duplicadas por el mismo evento
- TTL de 10 minutos es suficiente para ventanas de correlación

#### B. Almacenamiento Temporal en Redis

```java
// Extraer zona del evento
String zone = event.geo.get("zone").toString();
String zoneKey = "corr:zone:" + zone;

// Crear resumen del evento (más liviano que el evento completo)
EventSummary summary = new EventSummary(
    event.getEvent_type(),
    event.getTimestamp(),
    event.getPayload(),
    event.getEvent_id()
);

// Guardar en Redis lista por zona (TTL 10 min)
redisTemplate.opsForList().rightPush(zoneKey, summary);
redisTemplate.expire(zoneKey, Duration.ofMinutes(10));

// Guardar también por placa si es evento LPR (rastreo multi-zona)
if (event.payload != null && event.payload.containsKey("placa_vehicular")) {
    String plate = event.payload.get("placa_vehicular").toString();
    String plateKey = "corr:plate:" + plate;
    redisTemplate.opsForList().rightPush(plateKey, summary);
    redisTemplate.expire(plateKey, Duration.ofMinutes(10));
}
```

**Ventajas de usar EventSummary:**
- Ocupa menos memoria en Redis (solo campos necesarios)
- Permite correlacionar eventos por zona y por placa vehicular
- TTL automático limpia datos antiguos

#### C. Detección de Patrones Sospechosos

El servicio aplica **2 reglas de correlación** específicas:

| Tipo de Alerta | Condición | Ventana de Tiempo | Score |
|----------------|-----------|-------------------|-------|
| `possible_robbery` | ≥1 `panic.button` + ≥1 `sensor.lpr` (velocidad > 80 km/h) | ±2 minutos | 0.85 |
| `accident` | ≥1 `citizen.report` (tipo: accidente) + ≥1 `sensor.acoustic` (explosion/vidrio_roto) | ±5 minutos | 0.85 |

**Código real de detección:**
```java
// Obtener eventos recientes de la zona desde Redis
List<Object> recentEvents = redisTemplate.opsForList().range(zoneKey, 0, -1);

// Clasificar eventos por tipo y ventana temporal
List<EventSummary> panicEvents = new ArrayList<>();
List<EventSummary> lprEvents = new ArrayList<>();
List<EventSummary> citizenEvents = new ArrayList<>();
List<EventSummary> acousticEvents = new ArrayList<>();
Instant now = Instant.parse(event.timestamp);

for (Object obj : recentEvents) {
    if (obj instanceof EventSummary) {
        EventSummary e = (EventSummary) obj;
        Instant ts = Instant.parse(e.getTimestamp());
        long diffSec = Math.abs(Duration.between(ts, now).getSeconds());
        
        // Regla 1: Posible robo (ventana ±2 min = 120 seg)
        if ("panic.button".equals(e.getEvent_type()) && diffSec <= 120) {
            panicEvents.add(e);
        }
        if ("sensor.lpr".equals(e.getEvent_type()) && 
            e.getPayload().containsKey("velocidad_estimada")) {
            double v = Double.parseDouble(
                e.getPayload().get("velocidad_estimada").toString());
            if (v > 80 && diffSec <= 120) {
                lprEvents.add(e);
            }
        }
        
        // Regla 2: Accidente (ventana ±5 min = 300 seg)
        if ("citizen.report".equals(e.getEvent_type()) && 
            "accidente".equals(e.getPayload().get("tipo_evento")) && 
            diffSec <= 300) {
            citizenEvents.add(e);
        }
        if ("sensor.acoustic".equals(e.getEvent_type())) {
            String tipoSonido = e.getPayload().get("tipo_sonido_detectado").toString();
            if (("explosion".equals(tipoSonido) || "vidrio_roto".equals(tipoSonido)) 
                && diffSec <= 300) {
                acousticEvents.add(e);
            }
        }
    }
}
```

#### D. Generación de Alertas

```java
// Regla 1: Posible robo
if (!panicEvents.isEmpty() && !lprEvents.isEmpty()) {
    CorrelatedAlert alert = new CorrelatedAlert();
    alert.alert_id = UUID.randomUUID().toString();
    alert.correlation_id = event.correlation_id != null 
        ? event.correlation_id 
        : UUID.randomUUID().toString();
    alert.type = "possible_robbery";
    alert.score = 0.85;
    alert.zone = zone;
    alert.window = Map.of(
        "start", panicEvents.get(0).getTimestamp(),
        "end", event.getTimestamp()
    );
    
    // Agregar IDs de eventos como evidencia
    List<String> evidence = new ArrayList<>();
    for (EventSummary e : panicEvents) evidence.add(e.getEvent_id());
    for (EventSummary e : lprEvents) evidence.add(e.getEvent_id());
    alert.evidence = evidence;
    alert.created_at = Instant.now().toString();
    
    // 1. Persistir en PostgreSQL
    alertService.saveAlert(alert);
    
    // 2. Publicar a Kafka (topic: correlated.alerts)
    kafkaTemplate.send("correlated.alerts", alert.zone, alert);
    
    // 3. Cachear en Redis para endpoint /alerts/active (TTL 10 min)
    String alertActiveKey = "alerts:active:" + zone;
    redisTemplate.opsForList().rightPush(alertActiveKey, alert);
    redisTemplate.expire(alertActiveKey, Duration.ofMinutes(10));
}

// Regla 2: Accidente (mismo flujo)
if (!citizenEvents.isEmpty() && !acousticEvents.isEmpty()) {
    // ... código similar para tipo "accident"
}
```

**Flujo de la alerta:**
1. **Persistencia primero:** Se guarda en PostgreSQL para auditoría permanente
2. **Publicación a Kafka:** Permite que otros microservicios reaccionen a la alerta
3. **Cache en Redis:** Para consultas rápidas en `/alerts/active` (TTL 10 min)

**¿Qué buscar en caso de error?**
- Si no genera alertas: Revisar logs `[ANALYZING EVENTS]`, verificar que eventos cumplan condiciones
- Si falla guardado: Verificar conexión a PostgreSQL/Redis en `application.properties`
- Logs: `[ALERT GENERATED]` indica éxito

---

### Paso 3: Persistencia de Alertas (AlertService)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/service/AlertService.java`

```java
@Service
public class AlertService {
    @Autowired
    private AlertRepository alertRepository;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    public AlertEntity saveAlert(CorrelatedAlert alert) {
        // 1. Mapear CorrelatedAlert → AlertEntity
        AlertEntity entity = mapToEntity(alert);
        
        // 2. Guardar en PostgreSQL
        return alertRepository.save(entity);
    }
    
    private AlertEntity mapToEntity(CorrelatedAlert alert) {
        AlertEntity entity = new AlertEntity();
        
        // Convertir String UUID a tipo UUID
        entity.setAlertId(UUID.fromString(alert.alert_id));
        entity.setCorrelationId(UUID.fromString(alert.correlation_id));
        
        entity.setType(alert.type);
        entity.setScore(alert.score);
        entity.setZone(alert.zone);
        
        // Parsear ventana temporal
        entity.setWindowStart(OffsetDateTime.parse(alert.window.get("start")));
        entity.setWindowEnd(OffsetDateTime.parse(alert.window.get("end")));
        
        // Serializar evidencia como JSON
        entity.setEvidence(objectMapper.writeValueAsString(alert.evidence));
        
        entity.setCreatedAt(OffsetDateTime.parse(alert.created_at));
        
        return entity;
    }
}
```

**Estructura REAL de la tabla `alerts`:**

```sql
CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY,                    -- UUID, no BIGSERIAL
    correlation_id UUID,                          -- UUID para trazabilidad
    type VARCHAR(255) NOT NULL,                   -- "possible_robbery", "accident"
    score DOUBLE PRECISION,                       -- Confianza de la correlación (0.0-1.0)
    zone VARCHAR(255),                            -- "Norte", "Centro", etc.
    window_start TIMESTAMP WITH TIME ZONE,        -- Inicio de ventana temporal
    window_end TIMESTAMP WITH TIME ZONE,          -- Fin de ventana temporal
    evidence JSONB,                               -- Array de event_ids como JSON
    created_at TIMESTAMP WITH TIME ZONE NOT NULL  -- Momento de creación
);

CREATE INDEX idx_alerts_zone ON alerts(zone);
CREATE INDEX idx_alerts_type ON alerts(type);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
```

**Diferencias clave con la documentación anterior:**
- **alert_id es UUID:** No es autoincremental, se genera en Java con `UUID.randomUUID()`
- **correlation_id:** Permite rastrear alertas relacionadas (mismo `correlation_id` del evento)
- **score:** Nivel de confianza de la correlación (0.85 = 85%)
- **window_start/end:** Rango temporal de los eventos correlacionados
- **evidence JSONB:** Lista de `event_id` que generaron la alerta
- **Sin severity ni event_count:** Se calcula dinámicamente basado en `type` y `evidence`

**¿Qué buscar en caso de error?**
- Si no persiste: Verificar que PostgreSQL esté corriendo (`docker ps`)
- Si error de UUID: Verificar que `alert_id` y `correlation_id` sean UUIDs válidos
- Si error de JSONB: Verificar que PostgreSQL soporte tipo JSONB (versión 9.4+)
- Logs: `Caused by: org.postgresql.util.PSQLException` indica error de BD

---

### Paso 4: Exposición de Endpoints (ManagementController)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/controller/ManagementController.java`

Este controlador expone **4 endpoints REST**:

#### 1. Health Check
```
GET http://localhost:8080/health
```
**Respuesta:**
```
"OK"
```
**Uso:** Verificar que el microservicio está corriendo (respuesta simple en texto plano).

#### 2. Métricas del Sistema
```
GET http://localhost:8080/metrics
```
**Respuesta:**
```json
{
  "alerts": 0,
  "events": 0
}
```
**Uso:** Endpoint básico para monitoreo. Actualmente devuelve valores estáticos (puede extenderse con Spring Boot Actuator).

> **Nota:** Para métricas reales de producción, considerar agregar contadores manuales o habilitar Spring Boot Actuator.

#### 3. Alertas Activas por Zona (desde Redis)
```
GET http://localhost:8080/alerts/active?zone=Norte
```
**Respuesta:**
```json
[
  {
    "alert_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "correlation_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "type": "possible_robbery",
    "score": 0.85,
    "zone": "Norte",
    "window": {
      "start": "2025-10-01T10:23:00Z",
      "end": "2025-10-01T10:25:00Z"
    },
    "evidence": [
      "a1b2c3d4-0001-4001-8001-111111111111",
      "b2c3d4e5-0002-4002-8002-222222222222"
    ],
    "created_at": "2025-10-01T10:25:15Z"
  }
]
```
**Características:**
- **Fuente:** Redis (lista `alerts:active:{zone}`)
- **TTL:** 10 minutos (alertas recientes)
- **Uso:** Dashboards en tiempo real, notificaciones urgentes
- **Parámetro obligatorio:** `zone` (ejemplo: `Norte`, `Centro`, `Sur`)

#### 4. Alertas Históricas por Zona (desde PostgreSQL)
```
GET http://localhost:8080/alerts/db?zone=Norte
```
**Respuesta:**
```json
[
  {
    "alertId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "correlationId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "type": "possible_robbery",
    "score": 0.85,
    "zone": "Norte",
    "windowStart": "2025-10-01T10:23:00Z",
    "windowEnd": "2025-10-01T10:25:00Z",
    "evidence": "[\"a1b2c3d4-0001-4001-8001-111111111111\",\"b2c3d4e5-0002-4002-8002-222222222222\"]",
    "createdAt": "2025-10-01T10:25:15Z"
  }
]
```
**Características:**
- **Fuente:** PostgreSQL (tabla `alerts`)
- **Persistencia:** Permanente (auditoría completa)
- **Uso:** Análisis histórico, reportes, Grafana
- **Parámetro opcional:** `zone` (si se omite, devuelve todas las alertas)

**Ejemplo sin filtro:**
```bash
curl http://localhost:8080/alerts/db
# Devuelve TODAS las alertas de todas las zonas
```

**¿Qué buscar en caso de error?**
- Si no responde: Verificar puerto 8080 (`docker ps | grep correlator`)
- Si `/alerts/active` devuelve vacío: Verificar Redis (`docker exec -it redis redis-cli KEYS "alerts:active:*"`)
- Si `/alerts/db` devuelve vacío: Verificar PostgreSQL (`docker exec -it postgres psql -U ciudad_user -d ciudad_inteligente -c "SELECT * FROM alerts;"`)
- Si error 500: Revisar logs del correlator (`docker logs correlator`)

---

## Componentes del Correlator

### Modelos de Datos

#### 1. CanonicalEvent.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/CanonicalEvent.java`

Representa un evento canónico validado:
```java
public class CanonicalEvent {
    private String eventId;           // UUID único
    private String eventType;         // "traffic_jam", "suspicious_behavior", etc.
    private String source;            // "camera", "sensor", etc.
    private Location location;        // {latitude, longitude, zone}
    private Instant timestamp;        // Momento del evento
    private Map<String, Object> data; // Datos adicionales
    // ... getters/setters
}
```

#### 2. CorrelatedAlert.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/CorrelatedAlert.java`

Representa una alerta generada:
```java
public class CorrelatedAlert {
    private String alertId;        // UUID de la alerta
    private String zone;           // "Norte", "Centro", etc.
    private String alertType;      // "possible_robbery", "accident", etc.
    private String severity;       // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private Integer eventCount;    // Cantidad de eventos que generaron la alerta
    private String description;    // Descripción legible
    private Instant timestamp;     // Momento de generación
    // ... getters/setters
}
```

#### 3. EventSummary.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/EventSummary.java`

Resumen ligero de evento para Redis:
```java
public class EventSummary {
    private String event_type;           // Tipo de evento
    private String timestamp;            // Timestamp ISO 8601
    private Map<String, Object> payload; // Payload completo
    private String event_id;             // ID del evento
    
    public EventSummary(String event_type, String timestamp, 
                        Map<String, Object> payload, String event_id) {
        this.event_type = event_type;
        this.timestamp = timestamp;
        this.payload = payload;
        this.event_id = event_id;
    }
    
    // Getters y setters
}
```

**¿Por qué EventSummary y no CanonicalEvent?**
- **Optimización de memoria:** Solo guarda campos necesarios para correlación
- **Menor latencia:** Serialización/deserialización más rápida en Redis
- **Flexibilidad:** Puede incluir campos calculados o derivados

#### 4. CorrelatedAlert.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/CorrelatedAlert.java`

Representa una alerta generada por correlación:
```java
public class CorrelatedAlert {
    public String alert_id;                 // UUID generado (UUID.randomUUID())
    public String correlation_id;           // Del evento o nuevo UUID
    public String type;                     // "possible_robbery", "accident"
    public double score;                    // Confianza (0.0-1.0), típicamente 0.85
    public String zone;                     // Zona geográfica
    public Map<String, String> window;      // {"start": "...", "end": "..."}
    public List<String> evidence;           // Lista de event_ids
    public String created_at;               // Timestamp ISO 8601
}
```

**Ejemplo completo:**
```json
{
  "alert_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "correlation_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "type": "possible_robbery",
  "score": 0.85,
  "zone": "Norte",
  "window": {
    "start": "2025-10-01T10:23:00Z",
    "end": "2025-10-01T10:25:00Z"
  },
  "evidence": [
    "a1b2c3d4-0001-4001-8001-111111111111",
    "b2c3d4e5-0002-4002-8002-222222222222"
  ],
  "created_at": "2025-10-01T10:25:15.123Z"
}
```

#### 5. AlertEntity.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/AlertEntity.java`

Entidad JPA para persistencia en PostgreSQL:
```java
@Entity
@Table(name = "alerts")
@Data
public class AlertEntity {
    @Id
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;                    // UUID como PK (no autoincremental)
    
    @Column(name = "correlation_id")
    private UUID correlationId;              // UUID para rastreo
    
    @Column(name = "type", nullable = false)
    private String type;                     // Tipo de alerta
    
    @Column(name = "score")
    private Double score;                    // Nivel de confianza
    
    @Column(name = "zone")
    private String zone;                     // Zona geográfica
    
    @Column(name = "window_start")
    private OffsetDateTime windowStart;      // Inicio ventana temporal
    
    @Column(name = "window_end")
    private OffsetDateTime windowEnd;        // Fin ventana temporal
    
    @Column(name = "evidence", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;                 // JSON array de event_ids
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;        // Timestamp de creación
}
```

**Mapeo: CorrelatedAlert → AlertEntity**

| Campo CorrelatedAlert | Campo AlertEntity | Transformación |
|----------------------|-------------------|----------------|
| `alert_id` (String) | `alertId` (UUID) | `UUID.fromString()` |
| `correlation_id` (String) | `correlationId` (UUID) | `UUID.fromString()` |
| `type` | `type` | Directo |
| `score` | `score` | Directo (double → Double) |
| `zone` | `zone` | Directo |
| `window["start"]` | `windowStart` | `OffsetDateTime.parse()` |
| `window["end"]` | `windowEnd` | `OffsetDateTime.parse()` |
| `evidence` (List) | `evidence` (String) | `ObjectMapper.writeValueAsString()` |
| `created_at` | `createdAt` | `OffsetDateTime.parse()` |

---

### Configuración

#### application.properties
**Ubicación:** `src/main/resources/application.properties`

```properties
# Puerto del servicio
server.port=8080

# Kafka Consumer
spring.kafka.bootstrap-servers=host.docker.internal:29092
spring.kafka.consumer.group-id=correlator-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer

# PostgreSQL
spring.datasource.url=jdbc:postgresql://host.docker.internal:5432/ciudad_inteligente
spring.datasource.username=ciudad_user
spring.datasource.password=ciudad_pass
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=host.docker.internal
spring.data.redis.port=6379
```

**¿Qué buscar en caso de error?**
- **Error de conexión a Kafka:** Verificar `bootstrap-servers` (debe ser `host.docker.internal:29092` en Docker Desktop)
- **Error de PostgreSQL:** Verificar que la base de datos `ciudad_inteligente` exista
- **Error de Redis:** Ejecutar `redis-cli ping` para verificar conectividad

---

### Validación de Eventos

#### canonical-event-schema.json
**Ubicación:** `src/main/resources/canonical-event-schema.json`

Define el formato válido de eventos:
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["event_id", "event_type", "source", "location", "timestamp"],
  "properties": {
    "event_id": { "type": "string" },
    "event_type": { "type": "string" },
    "source": { "type": "string" },
    "location": {
      "type": "object",
      "required": ["latitude", "longitude", "zone"],
      "properties": {
        "latitude": { "type": "number" },
        "longitude": { "type": "number" },
        "zone": { "type": "string" }
      }
    },
    "timestamp": { "type": "string", "format": "date-time" }
  }
}
```

**¿Qué buscar en caso de error?**
- Si eventos son rechazados: Verificar que cumplan con `required` fields
- Logs: `[VALIDATION ERROR]` indica campos faltantes

---

## Integración con Grafana

### ¿Cómo se conecta Grafana con el Correlator?

Grafana **NO** consume directamente desde Kafka ni llama al REST API del Correlator. En su lugar:

1. **Lee datos desde PostgreSQL** (fuente de datos configurada)
2. **Ejecuta queries SQL** para obtener alertas y métricas
3. **Renderiza dashboards** con gráficos interactivos

### Arquitectura de Conexión

```
┌─────────────────────┐
│   Correlator        │
│   (Puerto 8080)     │
└──────────┬──────────┘
           │ Guarda alertas
           ▼
┌─────────────────────────────┐
│   PostgreSQL                │
│   (Puerto 5432)             │
│   DB: ciudad_inteligente    │
│   Tabla: alerts             │
└──────────┬──────────────────┘
           │ Consulta SQL
           ▼
┌─────────────────────────────┐
│   Grafana                   │
│   (Puerto 3000)             │
│   - Dashboard Heatmap       │
│   - Dashboard Main          │
└─────────────────────────────┘
```

---

### Estructura de Archivos de Grafana

**Ubicación:** `platform/grafana/`

```
grafana/
├── provisioning/                          # Configuración para Docker Desktop
│   ├── datasources/
│   │   └── postgres.yml                  # Conexión a PostgreSQL
│   └── dashboards/
│       ├── dashboard-provider.yml        # Configuración de carga automática
│       ├── ciudad-inteligente-main.json  # Dashboard principal
│       └── ciudad-inteligente-heatmap.json # Dashboard de mapa de calor
```

#### 1. Configuración de Datasource (postgres.yml)

**Ubicación:** `platform/grafana/provisioning/datasources/postgres.yml`

```yaml
apiVersion: 1
datasources:
  - name: PostgreSQL-CiudadInteligente
    type: postgres
    url: host.docker.internal:5432
    database: ciudad_inteligente
    user: ciudad_user
    secureJsonData:
      password: ciudad_pass
    jsonData:
      sslmode: disable
      postgresVersion: 1500
```

**Explicación:**
- **name:** Nombre del datasource que aparece en Grafana
- **url:** `host.docker.internal:5432` permite que Grafana (corriendo en Docker) se conecte a PostgreSQL en el host
- **database:** Nombre de la base de datos donde están las alertas
- **sslmode: disable:** Desactiva SSL para desarrollo local

**¿Qué buscar en caso de error?**
- Si Grafana no muestra datos: Ir a **Configuration → Data Sources** en Grafana y hacer "Test" de la conexión
- Si falla conexión: Verificar que PostgreSQL esté corriendo (`docker ps`)

---

#### 2. Dashboard Principal (ciudad-inteligente-main.json)

**Ubicación:** `platform/grafana/provisioning/dashboards/ciudad-inteligente-main.json`

Este dashboard contiene **paneles (panels)** que muestran:

##### Panel 1: Total de Alertas Generadas
**Query SQL:**
```sql
SELECT COUNT(*) as total
FROM alerts
WHERE timestamp >= NOW() - INTERVAL '24 hours'
```
**Tipo:** Stat (número grande)
**Uso:** Muestra cuántas alertas se generaron en las últimas 24 horas

##### Panel 2: Alertas por Severidad
**Query SQL:**
```sql
SELECT 
  severity,
  COUNT(*) as count
FROM alerts
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY severity
ORDER BY 
  CASE severity
    WHEN 'CRITICAL' THEN 1
    WHEN 'HIGH' THEN 2
    WHEN 'MEDIUM' THEN 3
    WHEN 'LOW' THEN 4
  END
```
**Tipo:** Bar Chart (gráfico de barras)
**Uso:** Compara cantidad de alertas por severidad (CRITICAL, HIGH, MEDIUM, LOW)

##### Panel 3: Evolución de Alertas en el Tiempo
**Query SQL:**
```sql
SELECT 
  timestamp as time,
  COUNT(*) as count
FROM alerts
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY timestamp
ORDER BY timestamp
```
**Tipo:** Time Series (línea de tiempo)
**Uso:** Muestra tendencia de generación de alertas a lo largo del día

##### Panel 4: Alertas por Zona
**Query SQL:**
```sql
SELECT 
  zone,
  COUNT(*) as count
FROM alerts
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY zone
ORDER BY count DESC
```
**Tipo:** Pie Chart (gráfico circular)
**Uso:** Identifica qué zonas tienen más alertas

**¿Qué buscar en caso de error?**
- Si panel está vacío: Verificar que existan datos en la tabla `alerts` (`SELECT * FROM alerts;`)
- Si query falla: Revisar sintaxis SQL en **Panel → Edit → Query Inspector**
- Si no carga dashboard: Verificar que el archivo `.json` esté en `provisioning/dashboards/`

---

#### 3. Dashboard Heatmap (ciudad-inteligente-heatmap.json)

**Ubicación:** `platform/grafana/provisioning/dashboards/ciudad-inteligente-heatmap.json`

Este dashboard muestra un **mapa de calor** de alertas por zona y tipo.

##### Panel: Heatmap de Alertas
**Query SQL:**
```sql
SELECT 
  zone,
  alert_type,
  COUNT(*) as count
FROM alerts
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY zone, alert_type
```
**Tipo:** Heatmap
**Uso:** Visualiza qué tipos de alertas son más comunes en cada zona (colores más intensos = más alertas)

**Interpretación del Heatmap:**
- **Eje X:** Tipos de alerta (possible_robbery, accident, fire_risk)
- **Eje Y:** Zonas geográficas (Norte, Centro, Sur, Este, Oeste)
- **Color:** Intensidad (más oscuro = más alertas)

**¿Qué buscar en caso de error?**
- Si no muestra colores: Verificar que haya variedad de `alert_type` en la base de datos
- Si zona no aparece: Asegurarse de que el campo `zone` no sea NULL en eventos

---

#### 4. Configuración de Carga Automática (dashboard-provider.yml)

**Ubicación:** `platform/grafana/provisioning/dashboards/dashboard-provider.yml`

```yaml
apiVersion: 1
providers:
  - name: 'Ciudad Inteligente'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

**Explicación:**
- **path:** Ruta donde Grafana busca archivos `.json` de dashboards
- **updateIntervalSeconds:** Cada 10 segundos revisa si hay cambios en los archivos
- **allowUiUpdates:** Permite editar dashboards desde la UI de Grafana

**¿Qué buscar en caso de error?**
- Si dashboards no aparecen: Verificar que los archivos `.json` estén en la ruta correcta
- Si no se cargan automáticamente: Reiniciar Grafana (`docker restart grafana`)

---

### Acceso a Grafana

1. **URL:** http://localhost:3000
2. **Usuario:** `admin`
3. **Contraseña:** `admin` (se pedirá cambiarla en primer login)

#### Navegación en Grafana:
- **Dashboards → Browse:** Ver todos los dashboards disponibles
- **Configuration → Data Sources:** Verificar conexión a PostgreSQL
- **Explore:** Ejecutar queries SQL manuales para debugging

---

## Troubleshooting

### Problema 1: Correlator no consume eventos de Kafka

**Síntomas:**
- No aparecen logs `[CONSUMER]` en consola
- Contador de eventos procesados en `/metrics` no aumenta

**Solución:**
1. Verificar que Kafka esté corriendo:
   ```bash
   docker ps | grep kafka
   ```
2. Verificar que el topic `canonical-events` exista:
   ```bash
   docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
   ```
3. Revisar `application.properties` → `spring.kafka.bootstrap-servers`
4. Verificar logs de Kafka:
   ```bash
   docker logs kafka
   ```

---

### Problema 2: No se generan alertas

**Síntomas:**
- Eventos se consumen (aparecen logs `[CONSUMER]`) pero no se generan alertas
- Endpoint `/alerts/active` devuelve lista vacía

**Solución:**
1. Verificar que eventos cumplan condiciones de correlación:
   - ¿Hay al menos 2 eventos `suspicious_behavior` en la misma zona?
   - ¿Los eventos están en una ventana de 5 minutos?
2. Revisar logs `[ANALYZING EVENTS]` para ver conteos:
   ```
   [ANALYZING EVENTS] Zona Norte: 2 suspicious_behavior
   ```
3. Verificar que `zone` en eventos sea correcto (debe coincidir)
4. Revisar código en `CorrelatorService.analyzeZoneEvents()`

---

### Problema 3: Alertas no se persisten en PostgreSQL

**Síntomas:**
- Aparece log `[ALERT GENERATED]` pero no se guarda en base de datos
- Error: `Connection refused` o `Could not connect to database`

**Solución:**
1. Verificar que PostgreSQL esté corriendo:
   ```bash
   docker ps | grep postgres
   ```
2. Probar conexión manual:
   ```bash
   docker exec -it postgres psql -U ciudad_user -d ciudad_inteligente
   ```
3. Verificar que tabla `alerts` exista:
   ```sql
   \dt
   SELECT * FROM alerts;
   ```
4. Revisar `application.properties` → `spring.datasource.url`

---

### Problema 4: Grafana no muestra datos

**Síntomas:**
- Dashboard carga pero paneles están vacíos
- Mensaje: "No data"

**Solución:**
1. Verificar que existan alertas en PostgreSQL:
   ```sql
   SELECT COUNT(*) FROM alerts;
   ```
2. Probar conexión de datasource:
   - Ir a Grafana → Configuration → Data Sources
   - Click en "PostgreSQL-CiudadInteligente"
   - Click en "Save & Test" (debe decir "Database Connection OK")
3. Revisar query del panel:
   - Abrir dashboard → Click en título del panel → Edit
   - Verificar sintaxis SQL en pestaña "Query"
4. Verificar rango de tiempo:
   - Selector de tiempo (arriba derecha) debe incluir el periodo con datos
   - Ejemplo: "Last 24 hours"

---

### Problema 5: Correlator no arranca

**Síntomas:**
- Error al ejecutar `docker-compose up`
- Logs muestran: `Failed to start bean 'correlatorApplication'`

**Solución:**
1. Verificar que todas las dependencias estén corriendo:
   ```bash
   docker ps
   ```
   Debe listar: kafka, zookeeper, postgres, redis
2. Verificar orden de inicio:
   - Kafka debe iniciar DESPUÉS de Zookeeper
   - Correlator debe iniciar DESPUÉS de Kafka, PostgreSQL y Redis
3. Agregar `depends_on` en `docker-compose.correlator.yml`:
   ```yaml
   depends_on:
     - kafka
     - postgres
     - redis
   ```
4. Revisar logs completos:
   ```bash
   docker logs correlator
   ```

---

### Problema 6: Redis no cachea alertas

**Síntomas:**
- Consultas a `/alerts/active` son lentas
- Logs no muestran `[REDIS]`

**Solución:**
1. Verificar que Redis esté corriendo:
   ```bash
   docker exec -it redis redis-cli ping
   ```
   Debe responder: `PONG`
2. Verificar configuración en `RedisTemplateConfig.java`
3. Revisar que TTL esté configurado (24 horas = 86400 segundos)
4. Probar manualmente:
   ```bash
   docker exec -it redis redis-cli
   > KEYS *
   > GET alerts:active
   ```

---

## Resumen de Componentes Clave

### Archivos más importantes para entender el Correlator:

| Archivo | Responsabilidad | ¿Qué buscar en caso de error? |
|---------|----------------|-------------------------------|
| `EventConsumer.java` | Consumir eventos de Kafka | Logs `[CONSUMER]`, conexión a Kafka |
| `CorrelatorService.java` | Lógica de correlación | Logs `[ANALYZING EVENTS]`, reglas de detección |
| `AlertService.java` | Persistencia de alertas | Logs `[ALERT SAVED]`, conexión a PostgreSQL/Redis |
| `ManagementController.java` | Endpoints REST | Respuesta de `/health`, `/metrics`, `/alerts/active` |
| `application.properties` | Configuración | URLs de Kafka, PostgreSQL, Redis |
| `canonical-event-schema.json` | Validación | Logs `[VALIDATION ERROR]`, campos requeridos |

### Archivos más importantes para entender Grafana:

| Archivo | Responsabilidad | ¿Qué buscar en caso de error? |
|---------|----------------|-------------------------------|
| `postgres.yml` | Conexión a base de datos | Test de datasource en UI |
| `ciudad-inteligente-main.json` | Dashboard principal | Queries SQL, datos en PostgreSQL |
| `ciudad-inteligente-heatmap.json` | Mapa de calor | Existencia de datos por zona/tipo |
| `dashboard-provider.yml` | Carga automática | Ruta de archivos, logs de Grafana |

---

## Flujo Completo: Evento → Alerta → Visualización

```
1. KAFKA: Ingestor publica evento en topic "canonical-events"
   ↓
2. CONSUMER: EventConsumer recibe y valida evento
   ↓
3. CORRELATOR: CorrelatorService analiza eventos por zona
   ↓
4. DETECCIÓN: Si cumple condiciones → genera CorrelatedAlert
   ↓
5. PERSISTENCIA: AlertService guarda en PostgreSQL + Redis
   ↓
6. GRAFANA: Dashboard ejecuta query SQL sobre tabla "alerts"
   ↓
7. USUARIO: Ve alerta en tiempo real en dashboard
```

---

## Comandos Útiles para Debugging

```bash
# Ver logs del Correlator
docker logs -f correlator

# Verificar eventos en Kafka
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic canonical-events \
  --from-beginning

# Consultar alertas en PostgreSQL
docker exec -it postgres psql -U ciudad_user -d ciudad_inteligente \
  -c "SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 10;"

# Ver alertas en Redis
docker exec -it redis redis-cli GET alerts:active

# Verificar health del Correlator
curl http://localhost:8080/health

# Ver métricas del Correlator
curl http://localhost:8080/metrics

# Obtener alertas activas
curl "http://localhost:8080/alerts/active?zone=Norte"
```

---

## Mejoras Futuras Recomendadas

### 🛡️ Hardening de Serialización JSON

**Contexto:** Actualmente la serialización JSON funciona correctamente porque todos los campos obligatorios (`alert_id`, `correlation_id`, `type`) siempre tienen valores asignados. Sin embargo, para fortalecer el código contra modificaciones futuras, se recomienda:

**1. Agregar `@JsonInclude(NON_NULL)` a `CorrelatedAlert`**
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CorrelatedAlert {
    // ...campos
}
```
**Beneficio:** Evita que campos opcionales con valor `null` se incluyan en JSON al publicar a Kafka.

**2. Configurar `RedisTemplateConfig` con ObjectMapper personalizado**
```java
@Bean
public GenericJackson2JsonRedisSerializer jsonSerializer() {
    ObjectMapper mapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return new GenericJackson2JsonRedisSerializer(mapper);
}
```
**Beneficio:** Evita guardar campos `null` en Redis, manteniendo datos más limpios.

**Impacto:** Ninguno en el comportamiento actual (defensa en profundidad para cambios futuros).

---

## Conclusión

Este documento proporciona una visión completa del **Correlator** y **Grafana**. Para cualquier error:

1. **Revisa los logs** del componente específico
2. **Verifica conexiones** entre servicios (Kafka, PostgreSQL, Redis)
3. **Consulta la sección de Troubleshooting** correspondiente
4. **Prueba manualmente** con los comandos de debugging

¿Necesitas agregar más reglas de correlación? → Edita `CorrelatorService.analyzeZoneEvents()`  
¿Quieres nuevos dashboards? → Crea archivos `.json` en `provisioning/dashboards/`  
¿Necesitas más métricas? → Agrega queries SQL en los paneles de Grafana

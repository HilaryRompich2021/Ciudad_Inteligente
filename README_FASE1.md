# Fase 1: Microservicio Ingestor y Kafka - Ciudad Inteligente ✅ COMPLETADO

## 🎯 ¿Qué se implementó?

Se desarrolló un sistema distribuido para la **ingesta de eventos en tiempo real** para una ciudad inteligente, utilizando **microservicios**, **Apache Kafka** y **Docker**. El sistema permite recibir eventos canónicos vía API REST, validarlos contra un esquema oficial estricto y publicarlos en Kafka para su procesamiento posterior.

### ✅ **Estado actual: FUNCIONANDO**
- 🟢 **Microservicio Ingestor**: Operativo en puerto 8080
- 🟢 **Kafka**: Topics auto-creados y mensajes publicándose correctamente
- 🟢 **Validación JSON Schema**: Esquema canónico v1.0 implementado según guía oficial
- 🟢 **Docker Compose**: Infraestructura completa orquestada
- 🟢 **Kafka UI**: Interfaz web disponible en puerto 8081 para monitoreo

---

## 🏗️ Arquitectura Implementada

```
[Cliente HTTP/Postman/Insomnia] 
           ↓ POST /events (JSON canónico)
    [Microservicio Ingestor:8080]
           ↓ Validación + Enriquecimiento
           ↓ Kafka Producer
         [Apache Kafka]
           ↓ Topic: t01.events.standardized
        [Kafka UI:8081] (Monitoreo)
```

### 🛠️ **Stack tecnológico:**
- **Backend**: Java 17 + Spring Boot 3.5.5
- **Broker**: Apache Kafka 3.7 + Zookeeper 3.8
- **Validación**: JSON Schema 2020-12 con networknt/json-schema-validator
- **Contenedores**: Docker + Docker Compose
- **Base de datos**: PostgreSQL 16 (preparado para siguientes fases)
- **Cache**: Redis 7 (preparado para correlación de eventos)
- **Monitoreo**: Kafka UI

---

## 📋 Proceso Paso a Paso

### 1. **Arranque de la infraestructura**
```bash
# Desde el directorio platform/
docker-compose up --build -d
```
- ✅ Kafka + Zookeeper: Broker de mensajes
- ✅ PostgreSQL: Base de datos (para futuras fases)
- ✅ Redis: Cache (para correlación de eventos)
- ✅ Kafka UI: Interfaz web de monitoreo
- ✅ **Microservicio Ingestor**: Punto de entrada de eventos

### 2. **Creación automática de topics**
Los topics se crean **automáticamente** al iniciar el microservicio:
- 📨 `t01.events.standardized`: Eventos canónicos validados
- 🚨 `t01.correlated.alerts`: Alertas correlacionadas (para Fase 2)

### 3. **Recepción y validación de eventos**

El microservicio ingestor expone **4 endpoints principales**:

#### **📋 Endpoints disponibles:**
- 🟢 `POST /events` - Procesar un evento individual
- 🟢 `POST /events/bulk` - Procesar múltiples eventos en lote
- 🟢 `GET /health` - Estado del servicio y conectividad
- 🟢 `GET /schema` - Obtener el esquema canónico JSON

---

## 📝 **Campos del Evento Canónico**

### **✅ Campos OBLIGATORIOS** (según esquema JSON):
- `event_version` *(string)*: Siempre "1.0"
- `event_type` *(enum)*: panic.button | sensor.lpr | sensor.speed | sensor.acoustic | citizen.report
- `event_id` *(string)*: Identificador único del evento
- `producer` *(string)*: Sistema que genera el evento
- `source` *(enum)*: Solo "simulated" por ahora
- `timestamp` *(datetime)*: ISO 8601 (se genera automáticamente si no se envía)
- `partition_key` *(string)*: Clave para particionado de Kafka
- `geo` *(object)*: **Todos los campos son obligatorios** (`zone`, `lat`, `lon`)
  - `zone` *(string)*: Identificador de zona (ej: "downtown", "north", "highway-101")
  - `lat` *(number)*: Latitud (-90 a 90)
  - `lon` *(number)*: Longitud (-180 a 180)
- `severity` *(enum)*: info | warning | critical
- `payload` *(object)*: Datos específicos del evento (estructura libre)
  - **Nota**: Actualmente acepta cualquier estructura JSON válida
  - **Próxima mejora**: Validaciones específicas por tipo de evento (ver sección "Mejoras futuras")

### **⚪ Campos OPCIONALES** (se generan automáticamente si no se envían):
- `correlation_id` *(string)*: UUID para correlación de eventos
- `trace_id` *(string)*: UUID para trazabilidad

---

## 🔮 **Mejoras futuras planeadas**

### **Validaciones específicas de payload por tipo de evento:**

Estas validaciones están planificadas para implementarse después de completar el correlador:

#### **🚨 panic.button**
```json
"payload": {
  "tipo_de_alerta": "panico | emergencia | incendio",  // enum obligatorio
  "user_context": "movil | quiosco | web",             // enum obligatorio
  "device_id": "string",                               // opcional
  "battery_level": "number (0-100)"                    // opcional
}
```

#### **🚗 sensor.lpr**
```json
"payload": {
  "placa_vehicular": "string (formato específico)",    // obligatorio
  "ubicacion_sensor": "string",                        // obligatorio
  "confidence": "number (0-1)",                        // opcional
  "vehicle_type": "sedan | truck | motorcycle | ..."  // opcional
}
```

#### **🏃 sensor.speed**
```json
"payload": {
  "velocidad_detectada": "number (> 0)",               // obligatorio
  "direccion": "NORTE | SUR | ESTE | OESTE",          // enum obligatorio
  "speed_limit": "number",                             // opcional
  "vehicle_type": "string"                             // opcional
}
```

#### **🔊 sensor.acoustic**
```json
"payload": {
  "tipo_sonido_detectado": "disparo | explosion | vidrio_roto | normal", // enum obligatorio
  "probabilidad_evento_critico": "number (0-1)",      // obligatorio
  "decibel_level": "number",                           // opcional
  "duration_ms": "number"                              // opcional
}
```

#### **👤 citizen.report**
```json
"payload": {
  "tipo_evento": "accidente | incendio | altercado | vandalismo", // enum obligatorio
  "origen": "usuario | app | punto_fisico",           // enum obligatorio
  "description": "string",                             // opcional
  "citizen_id": "string",                              // opcional
  "attachments": "array"                               // opcional
}
```

### **Beneficios de implementar estas validaciones:**
- ✅ **Datos más consistentes**: Garantiza que cada tipo de evento tenga la estructura esperada
- ✅ **Mejor correlación**: El correlador puede confiar en la estructura específica de cada payload
- ✅ **Detección temprana de errores**: Errores de estructura se detectan en el ingestor, no en el correlador
- ✅ **API más robusta**: Clientes reciben retroalimentación específica sobre errores en el payload

---

## 🧪 **Ejemplos de uso de endpoints**

### **1. POST /events - Evento individual con TODOS los campos**

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "evt-2025-09-10-001",
    "producer": "mobile-app",
    "source": "simulated",
    "correlation_id": "corr-2025-09-10-001",
    "trace_id": "trace-2025-09-10-001",
    "timestamp": "2025-09-10T15:30:00Z",
    "partition_key": "zone-downtown",
    "geo": {
      "zone": "downtown",
      "lat": 19.4326,
      "lon": -99.1332
    },
    "severity": "critical",
    "payload": {
      "device_id": "panic-btn-001",
      "user_id": "citizen-1234",
      "battery_level": 85
    }
  }
```

### **2. POST /events - Evento con AUTOCOMPLETADO (campos mínimos)**

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "sensor.lpr",
    "event_id": "evt-2025-09-10-002",
    "producer": "camera-system",
    "source": "simulated",
    "partition_key": "zone-north",
    "geo": {
      "zone": "north",
      "lat": 19.4500,
      "lon": -99.1300
    },
    "severity": "info",
    "payload": {
      "plate_number": "ABC123",
      "confidence": 0.98
    }
  }'
```
> **⚡ El microservicio automáticamente genera:** `timestamp`, `correlation_id`, `trace_id`

## 📍 **Coordenadas de referencia por zona:**

Para facilitar las pruebas, aquí tienes coordenadas de ejemplo para diferentes zonas:

- **downtown**: `lat: 19.4326, lon: -99.1332` (Centro histórico)
- **north**: `lat: 19.4500, lon: -99.1300` (Zona Norte)
- **south**: `lat: 19.4100, lon: -99.1400` (Zona Sur)
- **east**: `lat: 19.4300, lon: -99.1200` (Zona Este)
- **west**: `lat: 19.4300, lon: -99.1500` (Zona Oeste)
- **highway-101**: `lat: 19.3852, lon: -99.1781` (Carretera principal)
- **zone-test**: `lat: 19.4200, lon: -99.1350` (Zona de pruebas)

### **3. POST /events/bulk - Múltiples eventos con AUTOCOMPLETADO**

```bash
curl -X POST http://localhost:8080/events/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {
      "event_version": "1.0",
      "event_type": "sensor.speed",
      "event_id": "evt-2025-09-10-003",
      "producer": "speed-sensor",
      "source": "simulated",
      "partition_key": "highway-101",
      "geo": {
        "zone": "highway-101",
        "lat": 19.3852,
        "lon": -99.1781
      },
      "severity": "warning",
      "payload": {
        "speed": 135.5,
        "speed_limit": 80,
        "vehicle_type": "motorcycle"
      }
    },
    {
      "event_version": "1.0",
      "event_type": "citizen.report",
      "event_id": "evt-2025-09-10-004",
      "producer": "citizen-app",
      "source": "simulated",
      "partition_key": "zone-south",
      "geo": {
        "zone": "south",
        "lat": 19.4100,
        "lon": -99.1400
      },
      "severity": "info",
      "payload": {
        "report_type": "graffiti",
        "description": "Vandalism on public wall"
      }
    }
  ]'
```

**💡 Respuesta de /events/bulk:**
```json
{
  "total": 2,
  "successful": 2,
  "failed": 0,
  "successful_events": ["evt-2025-09-10-003", "evt-2025-09-10-004"],
  "failed_events": [],
  "timestamp": "2025-09-10T15:35:00Z"
}
```

### **4. GET /health - Estado del servicio**

```bash
curl -X GET http://localhost:8080/health
```

**Respuesta esperada:**
```json
{
  "status": "UP",
  "kafka": "connected",
  "validator": "ready",
  "timestamp": "2025-09-10T15:30:45Z",
  "service": "ingestor",
  "version": "1.0",
  "details": {
    "topic": "t01.events.standardized",
    "schema_version": "1.0"
  }
}
```

### **5. GET /schema - Esquema canónico**

```bash
curl -X GET http://localhost:8080/schema
```

Devuelve el esquema JSON completo usado para validación.

---

## 🚨 **Casos de error comunes**

### **❌ Campo obligatorio faltante:**
```json
{
  "event_version": "1.0",
  "event_type": "panic.button"
  // Faltan: event_id, producer, source, partition_key, geo, severity, payload
}
```
**Respuesta:** `400 Bad Request` con detalles de validación

### **❌ Tipo de evento inválido:**
```json
{
  "event_type": "invalid.type"  // Solo se permiten los 5 tipos definidos
}
```

### **❌ Geo sin zone (campo obligatorio):**
```json
{
  "geo": {
    "lat": 19.4326,
    "lon": -99.1332
    // Falta: "zone" (obligatorio)
  }
}
```

### **❌ JSON mal formado:**
```json
{
  "event_id": "test"
  "event_type": "panic.button"  // Falta coma
}
```

### 4. **Validación estricta y enriquecimiento**
- ✅ **JSON Schema v1.0**: Validación contra esquema oficial del proyecto
- ✅ **Campos obligatorios**: `event_version`, `event_type`, `event_id`, `producer`, `source`, `timestamp`, `partition_key`, `geo` (con `zone`, `lat`, `lon`), `severity`, `payload`
- ✅ **Enriquecimiento automático**: Si faltan `timestamp`, `trace_id`, o `correlation_id`, se generan automáticamente con UUID
- ✅ **Snake_case mapping**: Mapeo automático entre JSON (snake_case) y Java (camelCase)
- ✅ **Procesamiento en lote**: El endpoint `/events/bulk` procesa eventos independientemente (si uno falla, los demás continúan)

#### **Tipos de eventos soportados:**
- `panic.button`: Botones de pánico ciudadano
- `sensor.lpr`: Cámaras de reconocimiento de placas
- `sensor.speed`: Sensores de velocidad/movimiento  
- `sensor.acoustic`: Sensores acústicos/ambientales
- `citizen.report`: Reportes ciudadanos vía app móvil

### 5. **Publicación exitosa en Kafka**
- 📨 **Topic**: `t01.events.standardized`
- 🔑 **Key**: `partition_key` (ej: "zone_4") para afinidad de partición
- ✅ **Confirmación**: Logs de publicación exitosa
- 👁️ **Monitoreo**: Mensajes visibles en Kafka UI (http://localhost:8081)

---

## 🧪 ¿Cómo probar el sistema?

### **Paso 1: Arrancar la infraestructura**
```bash
cd platform/
docker-compose up --build -d
docker ps  # Verificar que todos los contenedores estén UP
```

### **Paso 2: Probar los endpoints**

#### **Opción A: Evento individual básico**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "test-001",
    "producer": "test-client",
    "source": "simulated",
    "partition_key": "zone-test",
    "geo": { 
      "zone": "downtown",
      "lat": 19.4326,
      "lon": -99.1332
    },
    "severity": "critical",
    "payload": { "test": true }
  }'
```

#### **Opción B: Lote de eventos**
```bash
curl -X POST http://localhost:8080/events/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {
      "event_version": "1.0",
      "event_type": "panic.button",
      "event_id": "bulk-001",
      "producer": "test",
      "source": "simulated",
      "partition_key": "zone-1",
      "geo": { 
        "zone": "north",
        "lat": 19.4500,
        "lon": -99.1300
      },
      "severity": "critical",
      "payload": { "device": "btn-001" }
    },
    {
      "event_version": "1.0",
      "event_type": "sensor.lpr",
      "event_id": "bulk-002",
      "producer": "test",
      "source": "simulated",
      "partition_key": "zone-2",
      "geo": { 
        "zone": "south",
        "lat": 19.4100,
        "lon": -99.1400
      },
      "severity": "info",
      "payload": { "plate": "ABC123" }
    }
  ]'
```

#### **Opción C: Verificar estado del servicio**
```bash
curl -X GET http://localhost:8080/health
curl -X GET http://localhost:8080/schema
```

### **Paso 3: Verificar en Kafka UI**
- Abrir: http://localhost:8081
- Ir a Topics → `t01.events.standardized`
- Verificar que el mensaje aparezca con la key `zone_4`

### **Respuestas esperadas:**
- ✅ `202 Accepted`: Evento(s) procesado(s) y publicado(s) exitosamente
- ✅ `200 OK`: Para endpoints GET (/health, /schema)
- ❌ `400 Bad Request`: Error de validación (JSON mal formado, campos faltantes o inválidos)
- ❌ `500 Internal Server Error`: Error interno del microservicio

---

## 🔧 Detalles técnicos de implementación

### **Estructura del proyecto:**
```
src/ingestor/
├── src/main/java/com/ciudadesinteligentes/ingestor/
│   ├── IngestorApplication.java           # Main Spring Boot
│   ├── controller/EventController.java    # REST API endpoints
│   ├── service/EventService.java          # Lógica de negocio
│   ├── model/CanonicalEvent.java         # Modelo con @JsonProperty
│   ├── config/KafkaConfig.java           # Auto-creación de topics
│   └── util/CanonicalEventValidator.java # Validador JSON Schema
└── src/main/resources/
    ├── application.properties             # Configuración Spring
    └── canonical-event-schema.json        # Esquema oficial v1.0
```

### **Configuraciones clave:**

**application.properties:**
```properties
spring.application.name=ingestor
spring.kafka.bootstrap-servers=kafka:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.admin.auto-create=true
app.kafka.topic.events-standardized=t01.events.standardized
```

**Auto-creación de topics (KafkaConfig.java):**
```java
@Bean
public NewTopic eventsStandardizedTopic() {
    return TopicBuilder.name("t01.events.standardized")
            .partitions(3)
            .replicas(1)
            .build();
}
```

**Mapeo snake_case ↔ camelCase:**
```java
@JsonProperty("event_id")
private String eventId;

@JsonProperty("event_type") 
private String eventType;
// ... etc
```

---

## 🚀 Siguientes pasos (Roadmap)

### **Fase 2: Correlator + Redis (Siguiente prioridad)**
- **Objetivo**: Consumir eventos de Kafka y generar alertas correlacionadas
- **Funcionalidad**: 
  - Stream processor que correlaciona eventos dentro de ventanas de tiempo
  - Reglas inteligentes (ej: panic.button + velocidad alta = posible robo)
  - Cache en Redis para estado transitorio
  - Publicación de alertas en `t01.correlated.alerts`

### **Fase 2.5: Validaciones específicas de payload (Mejora incremental)**
- **Objetivo**: Agregar validación granular para cada tipo de evento
- **Funcionalidad**:
  - Esquemas JSON específicos para cada `event_type`
  - Validación de enums y formatos específicos (placas, direcciones, etc.)
  - Mensajes de error más específicos para desarrolladores
  - Retrocompatibilidad con eventos existentes

### **Fase 3: Persistencia y ETL**
- Guardar eventos y alertas en PostgreSQL para análisis histórico
- ETL batch/streaming desde Kafka a base de datos
- Índices optimizados para consultas geoespaciales y temporales

### **Fase 4: Observabilidad y Dashboards**
- Integración con Prometheus + Grafana
- Métricas de rendimiento de microservicios y Kafka
- Dashboards en tiempo real con heatmaps y timelines geoespaciales

### **Fase 5: Simuladores y carga**
- Artillery/JMeter para simulación de eventos masivos
- Generadores Python para diferentes tipos de sensores
- Pruebas de stress y benchmarking

### **Fase 6: Escalabilidad**
- Schema Registry para evolución de schemas
- Particionado inteligente por zona geográfica
- Auto-scaling de microservicios según carga

---

## 🔍 Troubleshooting y comandos útiles

### **Ver logs del microservicio:**
```bash
docker logs platform_ingestor_1 --tail 50 -f
```

### **Verificar topics creados:**
```bash
docker exec -it platform_kafka_1 kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### **Consumir mensajes directamente:**
```bash
docker exec -it platform_kafka_1 kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic t01.events.standardized \
  --from-beginning
```

### **Estado de contenedores:**
```bash
docker ps
docker-compose logs ingestor  # Solo logs del ingestor
```

### **Reiniciar solo el ingestor:**
```bash
docker-compose restart ingestor
# O con rebuild para cambios de código:
docker-compose up --build -d ingestor
```

---

## 💡 Lecciones aprendidas y mejores prácticas

1. **Validación estricta**: El JSON Schema 2020-12 es fundamental para garantizar consistencia
2. **Auto-creación de topics**: Evita pasos manuales y mejora la experiencia de desarrollo
3. **Mapeo explícito**: `@JsonProperty` es más confiable que configuración global de Jackson
4. **Enriquecimiento automático**: Genera automáticamente campos como `trace_id` si faltan
5. **Monitoreo visual**: Kafka UI es esencial para debugging y verificación
6. **Dockerización completa**: La infraestructura dockerizada facilita desarrollo y despliegue
7. **Logs estructurados**: Importantes para debugging en entornos distribuidos

---

## 📚 Referencias técnicas
- **JSON Schema oficial**: `src/ingestor/src/main/resources/canonical-event-schema.json`
- **Docker Compose**: `platform/docker-compose.yml`
- **Spring Kafka**: https://spring.io/projects/spring-kafka
---

**✅ Estado**: Fase 1 completamente implementada y funcional  
**🎯 Próximo objetivo**: Implementar Correlator para generar alertas inteligentes  
**👥 Equipo**: Listo para demo y continuación del desarrollo

**¡Listo para avanzar a la siguiente fase!**

---

## 🛡️ Validación de eventos: Individual vs Bulk

### 1. Endpoint individual (`POST /events`)
- **Validación automática:** Se usa `@Valid` en el controlador para verificar los campos mínimos obligatorios (por anotaciones como `@NotNull`).
- **Validación de esquema:** Además, el evento se valida contra el esquema JSON canónico en el servicio antes de publicarse en Kafka.
- **Errores personalizados:** Si la validación automática falla, un manejador global (`@ControllerAdvice`) devuelve una respuesta JSON detallada con los errores de validación.
- **Enriquecimiento:** Si faltan campos opcionales (`timestamp`, `trace_id`, `correlation_id`), se generan automáticamente.

### 2. Endpoint bulk (`POST /events/bulk`)
- **Validación manual:** No se usa `@Valid` en el controlador. La validación se realiza manualmente en el servicio, evento por evento, usando el esquema JSON canónico.
- **Procesamiento parcial:** Los eventos válidos se publican en Kafka; los inválidos se reportan en la respuesta con detalles (índice, eventId, mensaje de error).
- **Respuesta estructurada:** La respuesta incluye el total de eventos, los exitosos, los fallidos y los errores específicos de cada evento.

**Ventajas de este enfoque:**
- Máxima robustez y calidad de datos.
- Respuestas claras y personalizadas para el cliente.
- Permite procesamiento parcial en lote y validación estricta en ambos casos.

---

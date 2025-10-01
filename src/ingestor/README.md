
# Guía de Pruebas Locales: Microservicio Ingestor

> **IMPORTANTE:**
> Si desea probar el microservicio ingestor usando Docker y conectándolo a un Kafka externo, consulte la guía específica en [`README_PRUEBAS_INGESTOR.md`](README_PRUEBAS_INGESTOR.md).

Esta guía es para levantar y probar el microservicio **ingestor** de forma local (sin Docker), usando un broker Kafka externo (por ejemplo, el de su laboratorio o entorno personal).

---

## 📋 Tabla de Contenidos

1. [Requisitos Previos](#1-requisitos-previos)
2. [Configuración de Kafka](#2-configuración-de-conexión-a-kafka-externo)
3. [Compilar y Ejecutar](#3-compilar-y-ejecutar-el-microservicio)
4. [Endpoints y Ejemplos](#4-endpoints-disponibles-y-ejemplos)
5. [Enriquecimiento Automático](#5-enriquecimiento-automático-de-eventos)
6. [Notas Importantes](#6-notas-importantes)

---

## 1. Requisitos Previos

- Java 17+
- Maven 3.8+
- Acceso a un broker Kafka externo (host, puerto, topic)

---

## 2. Configuración de Conexión a Kafka Externo

Edite el archivo `src/main/resources/application.properties` y configure las siguientes variables según su entorno Kafka:

```properties
spring.kafka.bootstrap-servers=<HOST>:<PUERTO>
ingestor.kafka.topic=<TOPIC_DESTINO>
```

**Ejemplo:**
```properties
spring.kafka.bootstrap-servers=localhost:9092
ingestor.kafka.topic=t01.events.standardized
```

También puede usar variables de entorno al ejecutar el JAR:

```bash
# Linux/Mac
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export INGESTOR_KAFKA_TOPIC=t01.events.standardized

# Windows (PowerShell)
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
$env:INGESTOR_KAFKA_TOPIC="t01.events.standardized"
```

---

## 3. Compilar y Ejecutar el Microservicio

Desde la carpeta `src/ingestor`:

```bash
# Compilar el proyecto
mvn clean package

# Ejecutar la aplicación
java -jar target/ingestor-0.0.1-SNAPSHOT.jar
```

**Salida esperada:**
```
Started IngestorApplication in 3.456 seconds
Kafka bootstrap servers: localhost:9092
Publishing to topic: t01.events.standardized
```

---

## 4. Endpoints Disponibles y Ejemplos

### 4.1. Ingesta de Evento Individual

**Endpoint:** `POST /events`

**Body ejemplo (completo):**
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
  "partition_key": "zone_1",
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

---

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
    "partition_key": "zone_1",
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
    "partition_key": "zone_2",
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

---

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

---

### 4.4. Obtener Esquema Canónico

**Endpoint:** `GET /events/schema`

**Respuesta:** Retorna el JSON Schema v1.0 completo

---

## 5. 🔄 Enriquecimiento Automático de Eventos

El **Ingestor** implementa un enriquecedor automático (`EventEnricher`) que completa campos opcionales que falten en el evento.

### 5.1. Campos Enriquecidos Automáticamente

| Campo | Obligatorio | Si falta | Acción del Ingestor |
|-------|-------------|----------|---------------------|
| `event_id` | ✅ SÍ | ❌ Error | Rechaza evento (400 Bad Request) |
| `event_version` | ✅ SÍ | ❌ Error | Rechaza evento |
| `event_type` | ✅ SÍ | ❌ Error | Rechaza evento |
| `producer` | ✅ SÍ | ❌ Error | Rechaza evento |
| `source` | ✅ SÍ | ❌ Error | Rechaza evento |
| `partition_key` | ✅ SÍ | ❌ Error | Rechaza evento |
| `geo` | ✅ SÍ | ❌ Error | Rechaza evento |
| `severity` | ✅ SÍ | ❌ Error | Rechaza evento |
| `payload` | ✅ SÍ | ❌ Error | Rechaza evento |
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
  "partition_key": "zone_test",
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
  "partition_key": "zone_test",
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
  "correlation_id": "mi-correlacion-personalizada-123",  // ← Proporcionado manualmente
  // trace_id y timestamp se auto-generarán
  "partition_key": "zone_autopista",
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

### ✅ Hacer

- Usar UUIDs v4 válidos para `event_id` (obligatorio)
- Enviar todos los campos obligatorios del esquema canónico
- Usar timestamps en formato ISO-8601 si los proporcionas manualmente
- Verificar la conexión a Kafka antes de enviar eventos
- Consultar los logs para debugging: `mvn spring-boot:run`

### ❌ Evitar

- No enviar `event_id` con formato inválido (debe ser UUID v4)
- No omitir campos obligatorios del esquema
- No usar timestamps en formato no-ISO (e.g., epoch, custom format)
- No reutilizar `event_id` (causa problemas de idempotencia en Correlator)

### 🔍 Verificación de Eventos

Después de enviar eventos, puedes verificar en Kafka:

```bash
# Consumir mensajes del topic (si tienes acceso a Kafka CLI)
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic t01.events.standardized \
  --from-beginning
```

O usar Kafka UI si está disponible: http://localhost:8081

---

## 📚 Recursos Adicionales

- **Esquema Canónico:** `src/main/resources/canonical-event-schema.json`
- **EventEnricher:** `src/main/java/.../service/EventEnricher.java`
- **Guía Docker:** [`README_PRUEBAS_INGESTOR.md`](README_PRUEBAS_INGESTOR.md)
- **Documentación Principal:** `../../README-DEPLOYMENT.md`

---

## 💡 Tips para Testing Rápido

### Comando curl completo (evento mínimo):

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "test-'$(uuidgen)'",
    "producer": "curl-test",
    "source": "simulated",
    "partition_key": "zone_test",
    "geo": {"zone": "zone_test", "lat": -12.0464, "lon": -77.0428},
    "severity": "critical",
    "payload": {"tipo_de_alerta": "test"}
  }'
```

### Generar UUIDs en diferentes plataformas:

```bash
# Linux/Mac
uuidgen

# PowerShell
New-Guid

# Python
python -c "import uuid; print(uuid.uuid4())"

# Online
https://www.uuidgenerator.net/
```

---

**Para cualquier duda, revise este README o contacte al responsable del microservicio.**

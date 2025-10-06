
# Guía de Pruebas con Docker y Kafka Externo: Microservicio Ingestor

Esta guía explica cómo levantar el microservicio **ingestor** usando Docker y conectarlo a un broker Kafka externo (por ejemplo, el de su laboratorio o entorno personal).

---

## 📋 Tabla de Contenidos

1. [Requisitos Previos](#1-requisitos-previos)
2. [Configuración del Docker Compose](#2-revisar-y-configurar-el-docker-compose)
3. [Levantar el Microservicio](#3-levantar-el-microservicio-con-docker)
4. [Endpoints y Ejemplos](#4-endpoints-disponibles-y-ejemplos)
5. [Enriquecimiento Automático](#5-enriquecimiento-automático-de-eventos)
6. [Notas Importantes](#6-notas)

---

## 1. Requisitos Previos

- Docker instalado
- Acceso a un broker Kafka externo (host, puerto, topic)
- Variables de entorno necesarias para Redis y Postgres si aplica

---

## 2. Revisar y Configurar el docker-compose

El archivo `docker-compose.ingestor.yml` contiene la definición del servicio ingestor. Debe ajustar las variables de entorno para que apunten a su Kafka externo y, si corresponde, a Redis y Postgres. Ejemplo:

```yaml
environment:
  - SPRING_KAFKA_BOOTSTRAP_SERVERS=<HOST>:<PUERTO>
  - INGESTOR_KAFKA_TOPIC=<TOPIC_DESTINO>
  - SPRING_REDIS_HOST=<REDIS_HOST> # (opcional)
  - SPRING_DATASOURCE_URL=jdbc:postgresql://<POSTGRES_HOST>:5432/ciudades # (opcional)
  - SPRING_DATASOURCE_USERNAME=postgres # (opcional)
  - SPRING_DATASOURCE_PASSWORD=postgres # (opcional)
```

**Notas importantes:**
- No es necesario modificar el archivo `application.properties` si define correctamente las variables de entorno en el docker-compose.
- El microservicio tomará primero las variables de entorno y solo usará el properties si alguna falta.
- Si solo desea probar la ingesta a Kafka, puede omitir o comentar las variables de Redis y Postgres.

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
ingestor_1  | Publishing to topic: t01.events.standardized
```

---

## 4. Endpoints Disponibles y Ejemplos

### 4.1. Ingesta de Evento Individual

**Endpoint:** `POST /events`

**Body ejemplo (evento completo con todos los campos):**
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0001-4001-8001-100000000001",
  "trace_id": "c1d2e3f4-0001-4001-8001-200000000001",
  "timestamp": "2025-09-28T12:00:00Z",
  "partition_key": "zone-1",
  "geo": {
    "zone": "zone-1",
    "lat": 19.4326,
    "lon": -99.1332
  },
  "severity": "info",
  "payload": {
    "placa_vehicular": "ABC123",
    "velocidad_estimada": 45.2
  }
}
```

**Respuesta esperada (202 Accepted):**
```json
{
  "status": "success",
  "message": "Event processed and published successfully",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "event_type": "sensor.lpr",
  "partition_key": "zone-1",
  "timestamp": "2025-09-28T12:34:56.789Z"
}
```

---

### 4.2. Ingesta Masiva

**Endpoint:** `POST /events/bulk`

**Body ejemplo (arreglo de eventos válidos):**
```json
[
  {
    "event_version": "1.0",
    "event_type": "sensor.speed",
    "event_id": "a1b2c3d4-0002-4002-8002-000000000002",
    "producer": "simulator",
    "source": "simulated",
    "timestamp": "2025-09-28T12:01:00Z",
    "partition_key": "zone-2",
    "geo": { 
      "zone": "zone-2", 
      "lat": 19.4, 
      "lon": -99.1 
    },
    "severity": "warning",
    "payload": { 
      "velocidad_detectada": 80.5 
    }
  },
  {
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "a1b2c3d4-0003-4003-8003-000000000003",
    "producer": "simulator",
    "source": "simulated",
    "timestamp": "2025-09-28T12:02:00Z",
    "partition_key": "zone-3",
    "geo": { 
      "zone": "zone-3", 
      "lat": 19.5, 
      "lon": -99.2 
    },
    "severity": "critical",
    "payload": { 
      "tipo_de_alerta": "panico",
      "identificador_dispositivo": "btn-01" 
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
  "failed_events": []
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
  "timestamp": "2025-09-28T12:05:00.123Z"
}
```

---

### 4.4. Obtener Esquema

**Endpoint:** `GET /events/schema`

**Respuesta:** Retorna el JSON Schema canónico v1.0 completo

---

## 5. 🔄 Enriquecimiento Automático de Eventos

El **Ingestor** implementa un enriquecedor automático que completa campos opcionales que falten en el evento.

### 5.1. Campos Enriquecidos Automáticamente

| Campo | Si falta | Acción del Ingestor |
|-------|----------|---------------------|
| `timestamp` | ❌ | ✅ Se genera timestamp UTC actual (ISO-8601) |
| `trace_id` | ❌ | ✅ Se genera UUID v4 aleatorio |
| `correlation_id` | ❌ | ✅ Se genera UUID v4 aleatorio |

**Todos los demás campos son OBLIGATORIOS** y deben ser proporcionados.

### 5.2. Ejemplo: Evento Mínimo (Sin Campos Opcionales)

Puedes enviar un evento **sin** `timestamp`, `event_id`  `trace_id` ni `correlation_id`:

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "producer": "test-minimal",
  "source": "simulated",
  "partition_key": "zone_test",
  "geo": {
    "zone": "zone_test",
    "lat": 19.4326,
    "lon": -99.1332
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "test"
  }
}
```

**El Ingestor lo enriquecerá automáticamente a:**

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "f1e2d3c4-9999-4999-8999-000000009999",  // ← Auto-generado
  "producer": "test-minimal",
  "source": "simulated",
  "timestamp": "2025-09-28T12:40:15.678Z",        // ← Auto-generado
  "trace_id": "d4e5f6a7-1234-4567-89ab-123456789abc", // ← Auto-generado
  "correlation_id": "e5f6a7b8-2345-5678-9abc-234567890bcd", // ← Auto-generado
  "partition_key": "zone_test",
  "geo": {
    "zone": "zone_test",
    "lat": 19.4326,
    "lon": -99.1332
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "test"
  }
}
```

### 5.3. ✅ Ventajas del Enriquecimiento Automático

1. **Simplifica testing**: No necesitas generar UUIDs manualmente
2. **Garantiza trazabilidad**: Todos los eventos tienen trace_id
3. **Timestamps precisos**: Se usa el momento exacto de ingesta
4. **Compatibilidad**: Funciona con Artillery, Postman, curl, etc.

### 5.4. ⚠️ Comportamiento Importante

- Si **envías** `timestamp`, `event_id`, `trace_id` o `correlation_id`, el Ingestor **respetará** tus valores
- Si **omites** estos campos, se generarán automáticamente
- Los UUIDs auto-generados cumplen con el formato UUID v4

---

## 6. Notas

### ✅ Recomendaciones

- El microservicio puede levantarse solo con Docker, sin necesidad de levantar el resto de la infraestructura.
- Solo necesita acceso a un broker Kafka funcional.
- Consulte los logs del contenedor para verificar la publicación de eventos:
  ```bash
  docker logs -f ingestor
  ```
- Use UUIDs v4 válidos para `event_id` (se autogenera si falta)
- Los demás campos opcionales (`timestamp`, `trace_id`, `correlation_id`) se auto-generan si faltan

### 🔍 Verificar Eventos Publicados

Puedes verificar que los eventos llegaron a Kafka:

```bash
# Si tienes acceso a Kafka CLI
docker exec -it <kafka-container> kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic t01.events.standardized \
  --from-beginning
```

O usar Kafka UI si está disponible: http://localhost:8081

---

## 📚 Recursos Adicionales

- **Guía Local:** [`README.md`](README.md) - Ejecución sin Docker
- **Esquema Canónico:** `src/main/resources/canonical-event-schema.json`
- **EventEnricher:** `src/main/java/.../service/EventEnricher.java`

---

**Para cualquier duda, revise este archivo o contacte al responsable del microservicio.**

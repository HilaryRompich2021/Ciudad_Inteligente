
# Guía de Pruebas con Docker y Kafka Externo: Microservicio Ingestor

Esta guía explica cómo levantar el microservicio **ingestor** usando Docker y conectarlo a un broker Kafka externo (por ejemplo, el de su laboratorio o entorno personal).

---

## 1. Requisitos Previos
- Docker instalado
- Acceso a un broker Kafka externo (host, puerto, topic)
- Variables de entorno necesarias para Redis y Postgres si aplica


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

## 3. Levantar el Microservicio con Docker

Desde la carpeta `src/ingestor`:

```
docker-compose -f docker-compose.ingestor.yml up --build
```

## 4. Endpoints Disponibles y Ejemplos


### 4.1. Ingesta de Evento Individual
- **POST** `/api/events`
- **Body ejemplo (válido según canonical-event-schema.json):**
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "evt-001",
  "producer": "simulator",
  "source": "simulated",
  "timestamp": "2025-09-28T12:00:00Z",
  "partition_key": "zone-1",
  "geo": {
    "zone": "zone-1",
    "lat": 19.4326,
    "lon": -99.1332
  },
  "severity": "info",
  "payload": {
    "plate": "ABC123",
    "speed": 45.2
  }
}
```

### 4.2. Ingesta Masiva
- **POST** `/api/events/bulk`
- **Body ejemplo (arreglo de eventos válidos):**
```json
[
  {
    "event_version": "1.0",
    "event_type": "sensor.speed",
    "event_id": "evt-002",
    "producer": "simulator",
    "source": "simulated",
    "timestamp": "2025-09-28T12:01:00Z",
    "partition_key": "zone-2",
    "geo": { "zone": "zone-2", "lat": 19.4, "lon": -99.1 },
    "severity": "warning",
    "payload": { "speed": 80.5 }
  },
  {
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "evt-003",
    "producer": "simulator",
    "source": "simulated",
    "timestamp": "2025-09-28T12:02:00Z",
    "partition_key": "zone-3",
    "geo": { "zone": "zone-3", "lat": 19.5, "lon": -99.2 },
    "severity": "critical",
    "payload": { "button_id": "btn-01", "status": "pressed" }
  }
]
```

### 4.3. Health Check
- **GET** `/api/health`

### 4.4. Obtener Esquema
- **GET** `/api/schema`

## 5. Notas
- El microservicio puede levantarse solo con Docker, sin necesidad de levantar el resto de la infraestructura.
- Solo necesita acceso a un broker Kafka funcional.
- Consulte los logs del contenedor para verificar la publicación de eventos.

---

**Para cualquier duda, revise este archivo o contacte al responsable del microservicio.**

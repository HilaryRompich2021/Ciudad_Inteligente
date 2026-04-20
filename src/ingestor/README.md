
# Guía de Pruebas Locales: Microservicio Ingestor

> **IMPORTANTE:**
> Si desea probar el microservicio ingestor usando Docker y conectándolo a un Kafka externo, consulte la guía específica en [`README_PRUEBAS_INGESTOR.md`](README_PRUEBAS_INGESTOR.md).

Esta guía es para levantar y probar el microservicio **ingestor** de forma local (sin Docker), usando un broker Kafka externo (por ejemplo, el de su laboratorio o entorno personal).

## 1. Requisitos Previos
- Java 17+
- Maven 3.8+
- Acceso a un broker Kafka externo (host, puerto, topic)

## 2. Configuración de Conexión a Kafka Externo

Edite el archivo `src/main/resources/application.properties` y configure las siguientes variables según su entorno Kafka:

```
spring.kafka.bootstrap-servers=<HOST>:<PUERTO>
ingestor.kafka.topic=<TOPIC_DESTINO>
```

**Ejemplo:**
```
spring.kafka.bootstrap-servers=localhost:9092
ingestor.kafka.topic=ciudad.canonical.events
```

También puede usar variables de entorno al ejecutar el JAR:
```
set SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set INGESTOR_KAFKA_TOPIC=ciudad.canonical.events
```

## 3. Compilar y Ejecutar el Microservicio

Desde la carpeta `src/ingestor`:

```
mvn clean package
java -jar target/ingestor-0.0.1-SNAPSHOT.jar
```

## 4. Endpoints Disponibles y Ejemplos

### 4.1. Ingesta de Evento Individual
- **POST** `/api/events`
- **Body ejemplo:**
```json
{
  "id": "evt-001",
  "timestamp": "2025-09-28T12:00:00Z",
  "type": "sensor.temperature",
  "payload": { "value": 23.5, "unit": "C" }
}
```

### 4.2. Ingesta Masiva
- **POST** `/api/events/bulk`
- **Body ejemplo:**
```json
[
  { "id": "evt-002", "timestamp": "2025-09-28T12:01:00Z", "type": "sensor.humidity", "payload": { "value": 60 } },
  { "id": "evt-003", "timestamp": "2025-09-28T12:02:00Z", "type": "sensor.temperature", "payload": { "value": 22.1, "unit": "C" } }
]
```

### 4.3. Health Check
- **GET** `/api/health`

### 4.4. Obtener Esquema
- **GET** `/api/schema`


## 5. Notas
- Este README es para pruebas locales (Java/Maven).
- Para pruebas usando Docker, consulte [`README_PRUEBAS_INGESTOR.md`](README_PRUEBAS_INGESTOR.md).
- El microservicio no requiere levantar el `docker-compose` general del repositorio para pruebas locales.
- Solo necesita acceso a un broker Kafka funcional.
- Consulte los logs para verificar la publicación de eventos.

---

**Para cualquier duda, revise este README o contacte al responsable del microservicio.**

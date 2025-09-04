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
**Endpoint principal**: `POST http://localhost:8080/events`

#### **Ejemplo de evento canónico válido:**
```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "123e4567-e89b-12d3-a456-426614174000",
  "producer": "artillery",
  "source": "simulated",
  "correlation_id": "123e4567-e89b-12d3-a456-426614174001",
  "trace_id": "123e4567-e89b-12d3-a456-426614174002",
  "timestamp": "2025-09-04T21:00:00Z",
  "partition_key": "zone_4",
  "geo": { "zone": "zone_4", "lat": 14.62, "lon": -90.52 },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "panico",
    "identificador_dispositivo": "BTN-001",
    "user_context": "movil"
  }
}
```

#### **Tipos de eventos soportados:**
- `panic.button`: Botones de pánico
- `sensor.lpr`: Cámaras de reconocimiento de placas
- `sensor.speed`: Sensores de velocidad/movimiento  
- `sensor.acoustic`: Sensores acústicos/ambientales
- `citizen.report`: Reportes ciudadanos

### 4. **Validación estricta y enriquecimiento**
- ✅ **JSON Schema v1.0**: Validación contra esquema oficial del proyecto
- ✅ **Campos obligatorios**: `event_version`, `event_type`, `event_id`, `producer`, `source`, `timestamp`, `partition_key`, `geo`, `severity`, `payload`
- ✅ **Enriquecimiento automático**: Si faltan `timestamp`, `trace_id`, o `correlation_id`, se generan automáticamente
- ✅ **Snake_case mapping**: Mapeo automático entre JSON (snake_case) y Java (camelCase)

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

### **Paso 2: Enviar evento de prueba**
**Método 1: Con Postman/Insomnia**
- URL: `POST http://localhost:8080/events`
- Headers: `Content-Type: application/json`
- Body: JSON canónico (ver ejemplo arriba)

**Método 2: Con curl**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "123e4567-e89b-12d3-a456-426614174000",
    "producer": "artillery",
    "source": "simulated",
    "timestamp": "2025-09-04T21:00:00Z",
    "partition_key": "zone_4",
    "geo": { "zone": "zone_4", "lat": 14.62, "lon": -90.52 },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "panico",
      "identificador_dispositivo": "BTN-001",
      "user_context": "movil"
    }
  }'
```

### **Paso 3: Verificar en Kafka UI**
- Abrir: http://localhost:8081
- Ir a Topics → `t01.events.standardized`
- Verificar que el mensaje aparezca con la key `zone_4`

### **Respuestas esperadas:**
- ✅ `202 Accepted`: Evento procesado y publicado exitosamente
- ❌ `400 Bad Request`: Error de validación (JSON mal formado o campos faltantes)
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

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
│                    canonical-events                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Consume
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EventConsumer.java                          │
│  - Lee eventos del topic Kafka                                   │
│  - Valida formato del evento canónico                           │
│  - Delega procesamiento a CorrelatorService                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Procesa
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CorrelatorService.java                        │
│  - Agrupa eventos por zona y ventana de tiempo                  │
│  - Aplica reglas de correlación (detecta patrones)              │
│  - Genera alertas cuando se cumplen condiciones                 │
└────────────────────┬───────────────────────┬────────────────────┘
                     │                       │
                     │ Guarda Alerta         │ Cachea
                     ▼                       ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│     AlertService.java        │  │      Redis Cache             │
│  - Persiste en PostgreSQL    │  │  - Alertas activas en memoria│
│  - Consulta alertas activas  │  │  - TTL de 24 horas           │
└──────────────────────────────┘  └──────────────────────────────┘
                     │
                     │ Almacena
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PostgreSQL Database                         │
│  Tabla: alerts                                                   │
│  - id, zone, alert_type, severity, event_count, etc.            │
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
@KafkaListener(topics = "canonical-events", groupId = "correlator-group")
public void consume(String eventJson) {
    // 1. Recibe evento desde Kafka
    // 2. Valida que cumpla con el esquema canónico
    // 3. Deserializa JSON a objeto CanonicalEvent
    // 4. Envía a CorrelatorService para procesamiento
}
```

**¿Qué buscar en caso de error?**
- Si no recibe eventos: Verificar conexión a Kafka (`application.properties`)
- Si falla validación: Revisar `canonical-event-schema.json`
- Logs: `[CONSUMER]` en consola

---

### Paso 2: Correlación de Eventos (CorrelatorService)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/service/CorrelatorService.java`

Este es el componente más importante. Realiza tres operaciones clave:

#### A. Agrupación por Zona y Ventana de Tiempo

```java
private Map<String, List<CanonicalEvent>> groupEventsByZone(List<CanonicalEvent> events) {
    // Agrupa eventos de los últimos 5 minutos por zona geográfica
    // Ejemplo: {"Norte": [evento1, evento2], "Centro": [evento3]}
}
```

**¿Por qué 5 minutos?**
- Es una ventana suficiente para detectar patrones (ejemplo: varios robos en la misma zona)
- Evita falsos positivos (eventos aislados no generan alerta)

#### B. Detección de Patrones Sospechosos

El servicio aplica **reglas de correlación** específicas:

| Tipo de Alerta | Condición | Severidad |
|----------------|-----------|-----------|
| `possible_robbery` | ≥2 eventos "suspicious_behavior" en misma zona | MEDIUM |
| `accident` | ≥3 eventos "traffic_jam" en misma zona | HIGH |
| `fire_risk` | ≥1 evento "fire" | CRITICAL |

**Código relevante:**
```java
private void analyzeZoneEvents(String zone, List<CanonicalEvent> events) {
    // Cuenta tipos de eventos por zona
    long suspiciousBehaviorCount = events.stream()
        .filter(e -> "suspicious_behavior".equals(e.getEvent_type()))
        .count();
    
    // Si hay 2+ comportamientos sospechosos → ALERTA
    if (suspiciousBehaviorCount >= 2) {
        createAlert(zone, "possible_robbery", "MEDIUM", ...);
    }
}
```

#### C. Generación de Alertas

```java
private void createAlert(String zone, String alertType, String severity, ...) {
    // 1. Crea objeto CorrelatedAlert
    // 2. Guarda en PostgreSQL vía AlertService
    // 3. Cachea en Redis para consultas rápidas
    // 4. Loguea: [ALERT GENERATED]
}
```

**¿Qué buscar en caso de error?**
- Si no genera alertas: Revisar logs `[ANALYZING EVENTS]`, verificar que eventos cumplan condiciones
- Si falla guardado: Verificar conexión a PostgreSQL/Redis en `application.properties`
- Logs: `[ALERT GENERATED]` indica éxito

---

### Paso 3: Persistencia de Alertas (AlertService)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/service/AlertService.java`

```java
public void saveAlert(CorrelatedAlert alert) {
    // 1. Convierte CorrelatedAlert → AlertEntity (JPA)
    // 2. Guarda en PostgreSQL usando AlertRepository
    // 3. Cachea en Redis con TTL de 24 horas
}

public List<CorrelatedAlert> getActiveAlerts(String zone) {
    // 1. Intenta obtener desde Redis (rápido)
    // 2. Si no existe, consulta PostgreSQL
    // 3. Filtra por zona si se proporciona
}
```

**Estructura de la tabla `alerts`:**

```sql
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(255) UNIQUE,
    zone VARCHAR(255),
    alert_type VARCHAR(255),
    severity VARCHAR(50),
    event_count INTEGER,
    description TEXT,
    timestamp TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**¿Qué buscar en caso de error?**
- Si no persiste: Verificar que PostgreSQL esté corriendo (`docker ps`)
- Si no cachea: Verificar Redis (`redis-cli ping`)
- Logs: `[ALERT SAVED]` en consola

---

### Paso 4: Exposición de Endpoints (ManagementController)

**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/controller/ManagementController.java`

Este controlador expone tres endpoints REST:

#### 1. Health Check
```
GET http://localhost:8080/health
```
**Respuesta:**
```json
{
  "status": "UP",
  "service": "Correlator",
  "timestamp": "2025-10-01T10:30:00Z"
}
```
**Uso:** Verificar que el microservicio está corriendo.

#### 2. Métricas del Sistema
```
GET http://localhost:8080/metrics
```
**Respuesta:**
```json
{
  "eventsProcessed": 1247,
  "alertsGenerated": 8,
  "uptime": "2h 15m",
  "status": "HEALTHY"
}
```
**Uso:** Monitoreo de rendimiento.

#### 3. Alertas Activas por Zona
```
GET http://localhost:8080/alerts/active?zone=Norte
```
**Respuesta:**
```json
[
  {
    "alertId": "alert-uuid-1234",
    "zone": "Norte",
    "alertType": "possible_robbery",
    "severity": "MEDIUM",
    "eventCount": 3,
    "description": "Patrón sospechoso detectado...",
    "timestamp": "2025-10-01T10:25:00Z"
  }
]
```
**Uso:** Grafana consulta este endpoint para mostrar alertas en dashboards.

**¿Qué buscar en caso de error?**
- Si no responde: Verificar puerto 8080 (`docker ps`)
- Si devuelve vacío: Revisar que existan alertas en PostgreSQL (`SELECT * FROM alerts;`)
- Logs: `[REST API]` en consola

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

#### 3. AlertEntity.java
**Ubicación:** `src/main/java/com/ciudadesinteligentes/correlator/model/AlertEntity.java`

Entidad JPA para persistencia en PostgreSQL:
```java
@Entity
@Table(name = "alerts")
public class AlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String alertId;
    
    private String zone;
    private String alertType;
    // ... otros campos mapeados a columnas de la tabla
}
```

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

## Conclusión

Este documento proporciona una visión completa del **Correlator** y **Grafana**. Para cualquier error:

1. **Revisa los logs** del componente específico
2. **Verifica conexiones** entre servicios (Kafka, PostgreSQL, Redis)
3. **Consulta la sección de Troubleshooting** correspondiente
4. **Prueba manualmente** con los comandos de debugging

¿Necesitas agregar más reglas de correlación? → Edita `CorrelatorService.analyzeZoneEvents()`  
¿Quieres nuevos dashboards? → Crea archivos `.json` en `provisioning/dashboards/`  
¿Necesitas más métricas? → Agrega queries SQL en los paneles de Grafana

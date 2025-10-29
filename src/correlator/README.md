# Correlator - Microservicio de Correlación de Eventos

## 📋 Descripción

El **Correlator** es un microservicio Spring Boot que consume eventos estandarizados desde Kafka, correlaciona eventos relacionados usando Redis como ventana temporal, y genera alertas complejas basadas en patrones de eventos.

### Funcionalidades Principales

- **Consumo de eventos** desde el topic `events.standardized`
- **Correlación temporal** usando Redis (ventana de 5 minutos)
- **Detección de patrones**:
  - `possible_robbery`: Combina eventos `panic.button` + `sensor.lpr`
  - `accident`: Combina eventos `traffic.camera` + `ambulance.request`
- **Publicación de alertas** al topic `correlated.alerts`
- **Persistencia** de alertas en PostgreSQL

---

## 🏗️ Arquitectura

```
Kafka (events.standardized)
        ↓
    Correlator
        ↓ (correlación temporal con Redis)
        ↓
Kafka (correlated.alerts) + PostgreSQL
```

### Tecnologías

- **Spring Boot**: 3.5.5
- **Java**: 17
- **Kafka**: Consumer/Producer
- **Redis**: Almacenamiento temporal para correlación
- **PostgreSQL**: Persistencia de alertas
- **Maven**: Gestión de dependencias

---

## 🚀 Requisitos Previos

Antes de desplegar el Correlator, asegúrate de tener los siguientes servicios corriendo:

### 1. Infraestructura Base

Primero debes levantar la plataforma completa desde `platform/`:

```powershell
cd ..\..\platform
docker-compose up -d
```

Esto iniciará:
- ✅ Zookeeper
- ✅ Kafka (puerto 29092)
- ✅ Kafka UI (puerto 8081)
- ✅ Redis (puerto 6379)
- ✅ PostgreSQL (puerto 5432)
- ✅ Inicialización automática de topics Kafka

### 2. Verificar que los Topics Existen

Accede a Kafka UI: [http://localhost:8081](http://localhost:8081)

Verifica que existan los topics:
- `events.standardized` (3 particiones)
- `correlated.alerts` (3 particiones)

---

### 2. Probar Correlación de Robo (possible_robbery)

**Paso 1**: Envía un evento `panic.button`

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "e1e2e3e4-0001-4001-8001-000000000001",
    "producer": "test-correlator",
    "source": "simulated",
    "partition_key": "panic.button",
    "geo": {
      "zone": "zone_centro",
      "lat": -12.0464,
      "lon": -77.0428
    },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "panico",
      "identificador_dispositivo": "BTN-001"
    }
  }'
```

**Paso 2**: Envía un evento `sensor.lpr` dentro de 2 minutos (misma zona, velocidad > 80)

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "sensor.lpr",
    "event_id": "e1e2e3e4-0002-4002-8002-000000000002",
    "producer": "test-correlator",
    "source": "simulated",
    "partition_key": "sensor.lpr",
    "geo": {
      "zone": "zone_centro",
      "lat": -12.0465,
      "lon": -77.0429
    },
    "severity": "warning",
    "payload": {
      "placa_vehicular": "ABC123",
      "velocidad_estimada": 95.0,
      "modelo_vehiculo": "sedan",
      "color_vehiculo": "negro"
    }
  }'
```

**Resultado Esperado**: El Correlator detectará el patrón y generará una alerta:

```json
{
  "alert_id": "uuid-generado",
  "correlation_id": "uuid-generado",
  "type": "possible_robbery",
  "score": 0.85,
  "zone": "zone_centro",
  "window": {
    "start": "2025-10-01T10:00:00Z",
    "end": "2025-10-01T10:01:30Z"
  },
  "evidence": [
    "e1e2e3e4-0001-4001-8001-000000000001",
    "e1e2e3e4-0002-4002-8002-000000000002"
  ],
  "created_at": "2025-10-01T10:01:30.456Z"
}
```

---

### 3. Probar Correlación de Accidente (accident)

**Paso 1**: Envía un evento `citizen.report` (tipo_evento = "accidente")

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "citizen.report",
    "event_id": "e1e2e3e4-0003-4003-8003-000000000003",
    "producer": "test-correlator",
    "source": "simulated",
    "partition_key": "citizen.report",
    "geo": {
      "zone": "zone_autopista",
      "lat": -12.0600,
      "lon": -77.0600
    },
    "severity": "warning",
    "payload": {
      "tipo_evento": "accidente",
      "mensaje_descriptivo": "Colisión múltiple con vehículo volcado",
      "ubicacion_aproximada": "KM 15 autopista sur",
      "origen": "app"
    }
  }'
```

**Paso 2**: Envía un evento `sensor.acoustic` dentro de 5 minutos (misma zona, explosión o vidrio roto)

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "sensor.acoustic",
    "event_id": "e1e2e3e4-0004-4004-8004-000000000004",
    "producer": "test-correlator",
    "source": "simulated",
    "partition_key": "sensor.acoustic",
    "geo": {
      "zone": "zone_autopista",
      "lat": -12.0601,
      "lon": -77.0601
    },
    "severity": "critical",
    "payload": {
      "tipo_sonido_detectado": "explosion",
      "nivel_decibeles": 125,
      "probabilidad_evento_critico": 0.95
    }
  }'
```

**Resultado Esperado**: Alerta de tipo `accident` generada.

---

### 4. Verificar Alertas Generadas

Conéctate a PostgreSQL y ejecuta el script de inicialización:

```powershell
# Copiar el script SQL al contenedor
docker cp ..\..\db\tables_phase0.sql postgres:/tmp/

# Ejecutar el script
docker exec -it postgres psql -U postgres -d ciudades -f /tmp/tables_phase0.sql
```

Verifica que existan las tablas `events` y `alerts`.

---

## 📦 Instalación y Despliegue

### Opción 1: Despliegue con Docker (Recomendado)

#### Paso 1: Compilar el Proyecto

```powershell
# Desde el directorio del correlator
./mvnw clean package -DskipTests
```

Esto generará el archivo `target/correlator-0.0.1-SNAPSHOT.jar`.

#### Paso 2: Construir la Imagen Docker

```powershell
docker build -t correlator:latest .
```

#### Paso 3: Levantar el Correlator

```powershell
docker-compose -f docker-compose.correlator.yml up -d
```

#### Paso 4: Verificar el Estado

```powershell
# Ver logs del contenedor
docker logs -f correlator

# Verificar que el servicio esté corriendo
docker ps | findstr correlator
```

Deberías ver logs como:
```
Started CorrelatorApplication in X seconds
Subscribed to topic(s): events.standardized
```

### Opción 2: Ejecución Local (Desarrollo)

Si prefieres ejecutar localmente sin Docker:

```powershell
# Configurar variables de entorno
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:29092"
$env:SPRING_REDIS_HOST="localhost"
$env:SPRING_REDIS_PORT="6379"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/ciudades"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"

# Ejecutar la aplicación
./mvnw spring-boot:run
```

---

## 🧪 Pruebas End-to-End

### Prerrequisitos

El **Event Ingestor** debe estar corriendo para enviar eventos. El Ingestor implementa **enriquecimiento automático** de eventos:

#### 🔄 Campos Auto-Generados por el Ingestor



#### 🔄 Tipos de Alertas Generadas

El correlator puede generar las siguientes alertas:


| Tipo de alerta              | Eventos requeridos                                                                                                    | Ventana temporal | Condición clave                      |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------|------------------|--------------------------------------|
| possible_robbery            | `panic.button` + `sensor.lpr` (velocidad_estimada > 80) en la misma zona                                             | ±2 min           | Ambos eventos en zona y tiempo       |
| accident                    | `citizen.report` (`tipo_evento = accidente`) + `sensor.acoustic` (`explosion` o `vidrio_roto`) + caída repentina de velocidad en LPR en la misma zona | 5 min            | Ambos eventos y caída de velocidad en zona y tiempo |
| traffic_speed_violation     | 3 o más eventos `sensor.lpr` (velocidad_estimada > 80) en la misma zona                                              | 2 min            | Mínimo 3 eventos en zona y tiempo    |
| fire                        | `citizen.report` (`tipo_evento = incendio`) + `sensor.acoustic` (`explosion` o `nivel_decibeles` > 100) en la zona   | 5 min            | Ambos eventos en zona y tiempo       |

---

### Ejemplos de eventos para cada alerta


**possible_robbery**
```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0001-4001-8001-100000000001",
  "trace_id": "c1d2e3f4-0001-4001-8001-200000000001",
  "timestamp": "2025-10-27T10:00:00Z",
  "partition_key": "panic.button",
  "geo": { "zone": "zone_1", "lat": 14.62, "lon": -90.52 },
  "severity": "critical",
  "payload": { "tipo_de_alerta": "panico", "identificador_dispositivo": "BTN-001" }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0002-4002-8002-000000000002",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0002-4002-8002-100000000002",
  "trace_id": "c1d2e3f4-0002-4002-8002-200000000002",
  "timestamp": "2025-10-27T10:01:30Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_1", "lat": 14.62, "lon": -90.52 },
  "severity": "warning",
  "payload": { "placa_vehicular": "XYZ123", "velocidad_estimada": 95 }
}
```



**accident**
```json
{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "a1b2c3d4-0003-4003-8003-000000000003",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0003-4003-8003-100000000003",
  "trace_id": "c1d2e3f4-0003-4003-8003-200000000003",
  "timestamp": "2025-10-27T11:00:00Z",
  "partition_key": "citizen.report",
  "geo": { "zone": "zone_2", "lat": 14.63, "lon": -90.53 },
  "severity": "warning",
  "payload": { "tipo_evento": "accidente", "mensaje_descriptivo": "colisión múltiple" }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "a1b2c3d4-0004-4004-8004-000000000004",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0004-4004-8004-100000000004",
  "trace_id": "c1d2e3f4-0004-4004-8004-200000000004",
  "timestamp": "2025-10-27T11:04:00Z",
  "partition_key": "sensor.acoustic",
  "geo": { "zone": "zone_2", "lat": 14.63, "lon": -90.53 },
  "severity": "critical",
  "payload": { "tipo_sonido_detectado": "explosion", "nivel_decibeles": 120 }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0010-4010-8010-000000000010",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0010-4010-8010-100000000010",
  "trace_id": "c1d2e3f4-0010-4010-8010-200000000010",
  "timestamp": "2025-10-27T11:02:00Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_2", "lat": 14.63, "lon": -90.53 },
  "severity": "warning",
  "payload": { "placa_vehicular": "XYZ123", "velocidad_estimada": 100 }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0011-4011-8011-000000000011",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0011-4011-8011-100000000011",
  "trace_id": "c1d2e3f4-0011-4011-8011-200000000011",
  "timestamp": "2025-10-27T11:03:00Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_2", "lat": 14.63, "lon": -90.53 },
  "severity": "warning",
  "payload": { "placa_vehicular": "XYZ123", "velocidad_estimada": 30 }
}
```


**traffic_speed_violation**
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0005-4005-8005-000000000005",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0005-4005-8005-100000000005",
  "trace_id": "c1d2e3f4-0005-4005-8005-200000000005",
  "timestamp": "2025-10-27T12:00:00Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_3", "lat": 14.64, "lon": -90.54 },
  "severity": "warning",
  "payload": { "placa_vehicular": "ABC123", "velocidad_estimada": 100 }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0006-4006-8006-000000000006",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0006-4006-8006-100000000006",
  "trace_id": "c1d2e3f4-0006-4006-8006-200000000006",
  "timestamp": "2025-10-27T12:01:00Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_3", "lat": 14.64, "lon": -90.54 },
  "severity": "warning",
  "payload": { "placa_vehicular": "DEF456", "velocidad_estimada": 105 }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0007-4007-8007-000000000007",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0007-4007-8007-100000000007",
  "trace_id": "c1d2e3f4-0007-4007-8007-200000000007",
  "timestamp": "2025-10-27T12:01:30Z",
  "partition_key": "sensor.lpr",
  "geo": { "zone": "zone_3", "lat": 14.64, "lon": -90.54 },
  "severity": "warning",
  "payload": { "placa_vehicular": "GHI789", "velocidad_estimada": 110 }
}
```


**fire**
```json
{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "a1b2c3d4-0008-4008-8008-000000000008",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0008-4008-8008-100000000008",
  "trace_id": "c1d2e3f4-0008-4008-8008-200000000008",
  "timestamp": "2025-10-27T13:00:00Z",
  "partition_key": "citizen.report",
  "geo": { "zone": "zone_4", "lat": 14.65, "lon": -90.55 },
  "severity": "critical",
  "payload": { "tipo_evento": "incendio", "mensaje_descriptivo": "fuego en edificio" }
}
```
```json
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "a1b2c3d4-0009-4009-8009-000000000009",
  "producer": "simulator",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0009-4009-8009-100000000009",
  "trace_id": "c1d2e3f4-0009-4009-8009-200000000009",
  "timestamp": "2025-10-27T13:04:00Z",
  "partition_key": "sensor.acoustic",
  "geo": { "zone": "zone_4", "lat": 14.65, "lon": -90.55 },
  "severity": "critical",
  "payload": { "tipo_sonido_detectado": "explosion", "nivel_decibeles": 130 }
}
```

> ⚠️ **Importante:** El correlator espera eventos ya enriquecidos por el ingestor. Los campos `timestamp`, `trace_id`, `correlation_id` y `partition_key` deben estar presentes y ser válidos.

**UUIDs:** Los campos `event_id`, `correlation_id` y `trace_id` deben ser UUID v4 válidos. Si no lo son, el evento será rechazado.

**partition_key:** Debe estar presente y normalmente igual al `event_type`.

**Idempotencia:** El correlator rechaza eventos duplicados (`event_id` repetido) por 10 minutos.

---

### 1. Preparar Eventos de Prueba

Puedes enviar eventos al Ingestor de dos formas:

#### Opción A: Evento Completo (Con todos los campos)
```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
    "producer": "test-suite",
    "source": "simulated",
    "correlation_id": "b1c2d3e4-0001-4001-8001-100000000001",
    "trace_id": "c1d2e3f4-0001-4001-8001-200000000001",
    "timestamp": "2025-10-01T10:00:00Z",
    "partition_key": "panic.button",
    "geo": {
      "zone": "zone_centro",
      "lat": -12.0464,
      "lon": -77.0428
    },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "panico",
      "identificador_dispositivo": "BTN-001"
    }
  }'
```

#### Opción B: Evento Mínimo (Enriquecimiento Automático)
```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_version": "1.0",
    "event_type": "panic.button",
    "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
    "producer": "test-suite",
    "source": "simulated",
    "partition_key": "panic.button",
    "geo": {
      "zone": "zone_centro",
      "lat": -12.0464,
      "lon": -77.0428
    },
    "severity": "critical",
    "payload": {
      "tipo_de_alerta": "panico",
      "identificador_dispositivo": "BTN-001"
    }
  }'
```

**Nota:** En la Opción B, el Ingestor generará automáticamente `timestamp`, `trace_id` y `correlation_id`.

---

**Opción A: Kafka UI**
1. Accede a [http://localhost:8081](http://localhost:8081)
2. Navega a `Topics` → `correlated.alerts`
3. Ve a la pestaña `Messages`
4. Deberías ver la alerta generada

**Opción B: PostgreSQL**
```powershell
docker exec -it postgres psql -U postgres -d ciudades -c "SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 5;"
```

**Opción C: Logs del Correlator**
```powershell
docker logs correlator | Select-String "Alert generated"
```

---

## 📊 Endpoints de Monitoreo

## 📊 Endpoints REST Disponibles

### 1. Health Check
`GET /health`
**Respuesta:**
```
OK
```

### 2. Métricas de la Aplicación
`GET /metrics`
**Respuesta:**
```json
{
  "alerts": 0,
  "events": 0
}
```

### 3. Consultar Alertas Activas (Redis)
`GET /alerts/active?zone=<nombre_zona>`
**Respuesta:** Array de alertas correlacionadas activas en esa zona (TTL 10 min)

### 4. Consultar Alertas Persistidas (PostgreSQL)
`GET /alerts/db?zone=<nombre_zona>`
**Respuesta:** Array de alertas persistidas en la base de datos. Si no se especifica zona, retorna todas.

## 🔧 Configuración


### Variables de Entorno

> ⚠️ **Seguridad:** Nunca subas el archivo `.env` con credenciales reales al repositorio. Usa `.env.example` como plantilla segura.

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Servidor Kafka | `host.docker.internal:29092` |
| `SPRING_REDIS_HOST` | Host de Redis | `host.docker.internal` |
| `SPRING_REDIS_PORT` | Puerto de Redis | `6379` |
| `SPRING_DATASOURCE_URL` | URL de PostgreSQL | `jdbc:postgresql://host.docker.internal:5432/ciudades` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de BD | `postgres` |
| `KAFKA_TOPIC_CORRELATED_ALERTS` | Topic de alertas correlacionadas | `correlated.alerts` |

### Configuración de Correlación

Edita `src/main/resources/application.properties` para ajustar:

```properties
# Ventana de correlación (en segundos)
correlation.window.seconds=300

# Score mínimo para generar alerta
correlation.min.score=0.8
```

---

## 🐛 Troubleshooting

### El Correlator no inicia

**Error**: `Unable to connect to Redis`

**Solución**:
1. Verifica que Redis esté corriendo: `docker ps | findstr redis`
2. Verifica la conectividad: `docker exec correlator ping host.docker.internal`
3. Revisa las variables de entorno en `docker-compose.correlator.yml`

---

### No se generan alertas

**Posibles causas**:

1. **Los eventos no están en el topic correcto**
   ```powershell
   # Verificar mensajes en Kafka UI
   http://localhost:8081
   ```

2. **Los eventos no están dentro de la ventana temporal**
   - La ventana por defecto es de 5 minutos
   - Envía los eventos relacionados con menos de 5 minutos de diferencia

3. **Falta información de ubicación**
   - Los eventos deben tener `location.latitude` y `location.longitude`

---

### Error de conexión a PostgreSQL

**Error**: `Connection refused: postgres`

**Solución**:
1. Verifica que PostgreSQL esté corriendo: `docker ps | findstr postgres`
2. Verifica que la BD `ciudades` exista:
   ```powershell
   docker exec -it postgres psql -U postgres -l
   ```
3. Inicializa las tablas si no existen (ver sección "Requisitos Previos")

---

## 📝 Estructura del Proyecto

```
correlator/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ciudadesinteligentes/correlator/
│   │   │       ├── CorrelatorApplication.java      # Clase principal
│   │   │       ├── config/                         # Configuraciones
│   │   │       ├── consumer/
│   │   │       │   └── EventConsumer.java          # Consumer de Kafka
│   │   │       ├── producer/
│   │   │       │   └── AlertProducer.java          # Producer de Kafka
│   │   │       ├── service/
│   │   │       │   └── CorrelatorService.java      # Lógica de correlación
│   │   │       ├── model/                          # Entidades y DTOs
│   │   │       └── repository/                     # Repositorios JPA
│   │   └── resources/
│   │       └── application.properties              # Configuración
├── docker-compose.correlator.yml                   # Docker Compose
├── Dockerfile                                       # Imagen Docker
├── pom.xml                                          # Dependencias Maven
└── README.md                                        # Este archivo
```

---

## 🔄 Flujo de Trabajo Recomendado

### 1. Desarrollo Local

```powershell
# Levantar infraestructura
cd ..\..\platform
docker-compose up -d

# Volver al correlator
cd ..\src\correlator

# Ejecutar localmente
./mvnw spring-boot:run
```

### 2. Testing en Docker

```powershell
# Compilar y construir imagen
./mvnw clean package -DskipTests
docker build -t correlator:latest .

# Desplegar
docker-compose -f docker-compose.correlator.yml up -d

# Ver logs en tiempo real
docker logs -f correlator
```

### 3. Detener Servicios

```powershell
# Detener el correlator
docker-compose -f docker-compose.correlator.yml down

# Detener toda la infraestructura
cd ..\..\platform
docker-compose down
```

---

## 📚 Recursos Adicionales

- **Kafka UI**: http://localhost:8081
- **PostgreSQL**: `localhost:5432` (usuario: `postgres`, password: `postgres`)
- **Redis**: `localhost:6379`

---

## 👥 Soporte

Para problemas o preguntas:
1. Revisa la sección de **Troubleshooting**
2. Verifica los logs: `docker logs -f correlator`
3. Consulta la documentación del proyecto principal

---

## 📄 Licencia
---

## 🚀 Próximos Pasos / Integraciones Futuras

- Integración con Elastic para indexar alertas y búsquedas avanzadas.
- Mejorar métricas expuestas en `/metrics`.
- Documentar nuevos endpoints si se agregan.

Este proyecto es parte del curso de Arquitectura de Computadoras II.

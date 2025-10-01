# Correlator - Microservicio de Correlación de Eventos

## 📋 Descripción

El **Correlator** es un microservicio Spring Boot que consume eventos estandarizados desde Kafka, correlaciona eventos relacionados usando Redis como ventana temporal, y genera alertas complejas basadas en patrones de eventos.

### Funcionalidades Principales

- **Consumo de eventos** desde el topic `t01.events.standardized`
- **Correlación temporal** usando Redis (ventana de 5 minutos)
- **Detección de patrones**:
  - `possible_robbery`: Combina eventos `panic.button` + `sensor.lpr`
  - `accident`: Combina eventos `traffic.camera` + `ambulance.request`
- **Publicación de alertas** al topic `t01.correlated.alerts`
- **Persistencia** de alertas en PostgreSQL

---

## 🏗️ Arquitectura

```
Kafka (t01.events.standardized)
        ↓
    Correlator
        ↓ (correlación temporal con Redis)
        ↓
Kafka (t01.correlated.alerts) + PostgreSQL
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
- `t01.events.standardized` (3 particiones)
- `t01.correlated.alerts` (3 particiones)

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
    "partition_key": "zone_centro",
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
    "partition_key": "zone_centro",
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
    "partition_key": "zone_autopista",
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
    "partition_key": "zone_autopista",
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
Subscribed to topic(s): t01.events.standardized
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

| Campo | Si falta | Acción del Ingestor |
|-------|----------|---------------------|
| `timestamp` | ❌ | ✅ Se genera timestamp UTC actual |
| `trace_id` | ❌ | ✅ Se genera UUID v4 aleatorio |
| `correlation_id` | ❌ | ✅ Se genera UUID v4 aleatorio |

**Esto significa que puedes enviar eventos mínimos sin estos campos opcionales.**

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
    "partition_key": "zone_centro",
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
    "partition_key": "zone_centro",
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
2. Navega a `Topics` → `t01.correlated.alerts`
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

### Health Check

```bash
curl http://localhost:8080/health
```

**Respuesta esperada:**
```
OK
```

### Métricas de la Aplicación

```bash
curl http://localhost:8080/metrics
```

**Respuesta esperada:**
```json
{
  "alerts": 0,
  "events": 0
}
```

### Consultar Alertas Activas

```bash
# Por zona específica
curl "http://localhost:8080/alerts/active?zone=zone_1"
```

**Respuesta:** Array de alertas correlacionadas activas en esa zona

---

## 🔧 Configuración

### Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Servidor Kafka | `host.docker.internal:29092` |
| `SPRING_REDIS_HOST` | Host de Redis | `host.docker.internal` |
| `SPRING_REDIS_PORT` | Puerto de Redis | `6379` |
| `SPRING_DATASOURCE_URL` | URL de PostgreSQL | `jdbc:postgresql://host.docker.internal:5432/ciudades` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de BD | `postgres` |

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

Este proyecto es parte del curso de Arquitectura de Computadoras II.

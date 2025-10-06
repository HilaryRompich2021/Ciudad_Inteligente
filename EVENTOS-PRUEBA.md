# 🧪 Guía de Eventos de Prueba - Correlator

## 📋 Tabla de Contenidos

1. [Introducción](#introducción)
2. [Notas Importantes sobre Event IDs](#notas-importantes-sobre-event-ids)
3. [Reglas de Correlación](#reglas-de-correlación)
4. [Escenarios de Prueba](#escenarios-de-prueba)
5. [Verificación de Resultados](#verificación-de-resultados)
6. [Tips para Pruebas Exitosas](#tips-para-pruebas-exitosas)

---

## 📖 Introducción

Esta guía proporciona **6 pares de eventos** diseñados específicamente para activar las reglas de correlación del microservicio Correlator y generar alertas.

### Requisitos Previ## 📊 Resumen de Escenarios

| # | Tipo Alerta | Zona | Eventos | Tiempo | Event IDs (últimos 4 dígitos) |
|---|-------------|------|---------|--------|--------------------------------|
| 1 | possible_robbery | zone_centro_comercial | panic + lpr | 90s | ...0001, ...0002 |
| 2 | possible_robbery | zone_residencial_norte | panic + lpr | 60s | ...0003, ...0004 |
| 3 | accident | zone_autopista_sur | citizen + acoustic | 120s | ...0005, ...0006 |
| 4 | accident | zone_centro_historico | citizen + acoustic | 180s | ...0007, ...0008 |
| 5 | possible_robbery | zone_industrial_este | panic + lpr | 45s | ...0009, ...0010 |
| 6 | accident | zone_universitaria | citizen + acoustic | 240s | ...0011, ...0012 |

**Nota:** Todos los Event IDs son UUIDs v4 válidos. La tabla muestra solo los últimos 4 dígitos para referencia.stema completo desplegado (consulta `README-DEPLOYMENT.md`)
- Postman o curl para enviar eventos
- Ingestor corriendo en puerto 8000
- Correlator corriendo en puerto 8080

---

## ⚠️ Notas Importantes sobre Event IDs

### ¿Por qué necesito Event IDs únicos?

El correlator implementa **lógica de idempotencia** para evitar procesar el mismo evento dos veces:

```java
// Código del correlator (CorrelatorService.java, línea 26-29)
String seenKey = "corr:seen:" + event.event_id;
Boolean alreadySeen = redisTemplate.hasKey(seenKey);
if (Boolean.TRUE.equals(alreadySeen)) return;  // ← Rechaza duplicados
redisTemplate.opsForValue().set(seenKey, "1", Duration.ofMinutes(10));
```

### Comportamiento de Duplicados

- **Si reutilizas un `event_id`**: El correlator lo detecta y **ignora el evento** durante **10 minutos**
- **Si usas `event_id` único**: El evento se procesa normalmente

### 🔄 Enriquecimiento Automático del Ingestor

El **Event Ingestor** enriquece automáticamente los eventos con campos opcionales:

| Campo | Obligatorio en Request | Si falta | Acción del Ingestor |
|-------|------------------------|----------|---------------------|
| `event_id` | ✅ SÍ (UUID válido) | ❌ Error | Rechaza evento (400 Bad Request) |
| `timestamp` | ❌ No | ✅ Auto-genera | Timestamp UTC actual (ISO-8601) |
| `trace_id` | ❌ No | ✅ Auto-genera | UUID v4 aleatorio |
| `correlation_id` | ❌ No | ✅ Auto-genera | UUID v4 aleatorio |
| `partition_key` | ❌ No | ✅ Auto-extrae | De geo.zone o payload.placa_vehicular |

**⚠️ Importante sobre `partition_key`:**
- La BD requiere `partition_key NOT NULL`, por lo que el enriquecedor **DEBE** extraerlo si no viene
- Prioridad de extracción: `geo.zone` > `payload.placa_vehicular` > **error si ninguno existe**
- Si envías `partition_key` explícitamente, el ingestor lo respetará

**Esto significa que puedes:**
1. **Enviar eventos SIN** `timestamp`, `trace_id`, `correlation_id` o `partition_key` - El Ingestor los generará/extraerá automáticamente
2. **Enviar eventos CON** estos campos - El Ingestor respetará tus valores
3. **SIEMPRE enviar** `event_id` como **UUID v4 válido** - No negociable

### Cómo Generar Event IDs Únicos

**⚠️ OBLIGATORIO: Los Event IDs DEBEN ser UUIDs v4 válidos**

El ingestor convierte el `event_id` a UUID para persistirlo en PostgreSQL (columna tipo `uuid`). Si envías un string que no sea UUID válido, obtendrás error:

```java
// EventService.java, línea 83
entity.setEventId(UUID.fromString(event.getEventId())); // ← Lanza excepción si no es UUID
```

**Ejemplo de error si usas string no-UUID:**
```json
{
  "status": "error",
  "message": "Failed to process event",
  "error_details": "Invalid UUID string: test-001"
}
```

**Generar UUIDs v4 válidos:**
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

### 💡 Opciones para Enviar Eventos

#### Opción 1: Evento Completo (Todos los campos)
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
  "partition_key": "zone_centro",
  "geo": {...},
  "severity": "critical",
  "payload": {...}
}
```

#### Opción 2: Evento Mínimo (Enriquecimiento Automático)
```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "a1b2c3d4-0001-4001-8001-000000000001",
  "producer": "test-suite",
  "source": "simulated",
  "geo": {...},
  "severity": "critical",
  "payload": {...}
  // timestamp, trace_id, correlation_id y partition_key se auto-generan/extraen
}
```

**Nota:** En el ejemplo anterior, `partition_key` se extraerá automáticamente de `geo.zone`. Puedes incluirlo explícitamente si prefieres.

**⚠️ IMPORTANTE:** Si pruebas un escenario y falla, **genera nuevos UUIDs** antes de reintentarlo. De lo contrario, el correlator ignorará los eventos duplicados por 10 minutos.

---

## 📐 Reglas de Correlación

### Regla 1: Possible Robbery (Posible Robo)

**Condiciones:**
- ✅ Evento tipo `panic.button` en una zona
- ✅ Evento tipo `sensor.lpr` en la **misma zona**
- ✅ `velocidad_estimada` > **80 km/h**
- ✅ Diferencia de tiempo: **≤ 2 minutos (120 segundos)**

**Alerta generada:**
```json
{
  "type": "possible_robbery",
  "score": 0.85,
  "zone": "<zona>",
  "evidence": ["<event_id_1>", "<event_id_2>"]
}
```

**Código fuente:** `CorrelatorService.java`, líneas 51-78

---

### Regla 2: Accident (Accidente)

**Condiciones:**
- ✅ Evento tipo `citizen.report` con `payload.tipo_evento = "accidente"`
- ✅ Evento tipo `sensor.acoustic` en la **misma zona**
- ✅ `tipo_sonido_detectado` = **"explosion"** o **"vidrio_roto"**
- ✅ Diferencia de tiempo: **≤ 5 minutos (300 segundos)**

**Alerta generada:**
```json
{
  "type": "accident",
  "score": 0.85,
  "zone": "<zona>",
  "evidence": ["<event_id_1>", "<event_id_2>"]
}
```

**Código fuente:** `CorrelatorService.java`, líneas 80-107

---

## 🎯 Escenarios de Prueba

### 📍 Escenario 1: Robo en Zona Comercial

**Contexto:** Botón de pánico activado en centro comercial + vehículo escapando a alta velocidad

#### Evento 1: Panic Button

**Timestamp:** 15:00:00  
**URL:** `POST http://localhost:8000/events`  
**Body:**

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
  "partition_key": "zone_centro_comercial",
  "geo": {
    "zone": "zone_centro_comercial",
    "lat": -12.0464,
    "lon": -77.0428
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "panico",
    "identificador_dispositivo": "BTN-CC-001",
    "user_context": "quiosco"
  }
}
```

#### Evento 2: Sensor LPR (90 segundos después)

**Timestamp:** 15:01:30  
**URL:** `POST http://localhost:8000/events`  
**Body:**

```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0002-4002-8002-000000000002",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0001-4001-8001-100000000001",
  "trace_id": "c1d2e3f4-0002-4002-8002-200000000002",
  "timestamp": "2025-10-01T15:01:30Z",
  "partition_key": "zone_centro_comercial",
  "geo": {
    "zone": "zone_centro_comercial",
    "lat": -12.0465,
    "lon": -77.0429
  },
  "severity": "warning",
  "payload": {
    "placa_vehicular": "XYZ789",
    "velocidad_estimada": 110.5,
    "modelo_vehiculo": "pickup",
    "color_vehiculo": "blanco",
    "ubicacion_sensor": "cam_salida_estacionamiento"
  }
}
```

**✅ Resultado Esperado:** Alerta `possible_robbery` en zona `zone_centro_comercial`

---

### 📍 Escenario 2: Robo en Zona Residencial

**Contexto:** Botón de pánico en residencia + vehículo sospechoso a alta velocidad

#### Evento 1: Panic Button

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "a1b2c3d4-0003-4003-8003-000000000003",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0002-4002-8002-100000000002",
  "trace_id": "c1d2e3f4-0003-4003-8003-200000000003",
  "timestamp": "2025-10-01T16:00:00Z",
  "partition_key": "zone_residencial_norte",
  "geo": {
    "zone": "zone_residencial_norte",
    "lat": -12.0500,
    "lon": -77.0500
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "emergencia",
    "identificador_dispositivo": "BTN-RES-042",
    "user_context": "movil"
  }
}
```

#### Evento 2: Sensor LPR (60 segundos después)

```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0004-4004-8004-000000000004",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0002-4002-8002-100000000002",
  "trace_id": "c1d2e3f4-0004-4004-8004-200000000004",
  "timestamp": "2025-10-01T16:01:00Z",
  "partition_key": "zone_residencial_norte",
  "geo": {
    "zone": "zone_residencial_norte",
    "lat": -12.0501,
    "lon": -77.0501
  },
  "severity": "warning",
  "payload": {
    "placa_vehicular": "ABC456",
    "velocidad_estimada": 95.0,
    "modelo_vehiculo": "sedan",
    "color_vehiculo": "negro",
    "ubicacion_sensor": "cam_calle_principal"
  }
}
```

**✅ Resultado Esperado:** Alerta `possible_robbery` en zona `zone_residencial_norte`

---

### 📍 Escenario 3: Accidente de Tránsito con Explosión

**Contexto:** Reporte ciudadano de accidente + sensor acústico detecta explosión

#### Evento 1: Citizen Report

```json
{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "a1b2c3d4-0005-4005-8005-000000000005",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0003-4003-8003-100000000003",
  "trace_id": "c1d2e3f4-0005-4005-8005-200000000005",
  "timestamp": "2025-10-01T17:00:00Z",
  "partition_key": "zone_autopista_sur",
  "geo": {
    "zone": "zone_autopista_sur",
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
}
```

#### Evento 2: Sensor Acoustic (120 segundos después)

```json
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "a1b2c3d4-0006-4006-8006-000000000006",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0003-4003-8003-100000000003",
  "trace_id": "c1d2e3f4-0006-4006-8006-200000000006",
  "timestamp": "2025-10-01T17:02:00Z",
  "partition_key": "zone_autopista_sur",
  "geo": {
    "zone": "zone_autopista_sur",
    "lat": -12.0601,
    "lon": -77.0601
  },
  "severity": "critical",
  "payload": {
    "tipo_sonido_detectado": "explosion",
    "nivel_decibeles": 125,
    "probabilidad_evento_critico": 0.95
  }
}
```

**✅ Resultado Esperado:** Alerta `accident` en zona `zone_autopista_sur`

---

### 📍 Escenario 4: Accidente con Vidrio Roto

**Contexto:** Reporte ciudadano + sensor acústico detecta vidrio roto

#### Evento 1: Citizen Report

```json
{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "a1b2c3d4-0007-4007-8007-000000000007",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0004-4004-8004-100000000004",
  "trace_id": "c1d2e3f4-0007-4007-8007-200000000007",
  "timestamp": "2025-10-01T18:00:00Z",
  "partition_key": "zone_centro_historico",
  "geo": {
    "zone": "zone_centro_historico",
    "lat": -12.0700,
    "lon": -77.0700
  },
  "severity": "warning",
  "payload": {
    "tipo_evento": "accidente",
    "mensaje_descriptivo": "Vehículo impactó contra vitrina",
    "ubicacion_aproximada": "Plaza de armas",
    "origen": "punto_fisico"
  }
}
```

#### Evento 2: Sensor Acoustic (180 segundos después)

```json
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "a1b2c3d4-0008-4008-8008-000000000008",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0004-4004-8004-100000000004",
  "trace_id": "c1d2e3f4-0008-4008-8008-200000000008",
  "timestamp": "2025-10-01T18:03:00Z",
  "partition_key": "zone_centro_historico",
  "geo": {
    "zone": "zone_centro_historico",
    "lat": -12.0701,
    "lon": -77.0701
  },
  "severity": "critical",
  "payload": {
    "tipo_sonido_detectado": "vidrio_roto",
    "nivel_decibeles": 105,
    "probabilidad_evento_critico": 0.88
  }
}
```

**✅ Resultado Esperado:** Alerta `accident` en zona `zone_centro_historico`

---

### 📍 Escenario 5: Robo en Zona Industrial

**Contexto:** Botón de pánico en fábrica + vehículo de carga a alta velocidad

#### Evento 1: Panic Button

```json
{
  "event_version": "1.0",
  "event_type": "panic.button",
  "event_id": "a1b2c3d4-0009-4009-8009-000000000009",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0005-4005-8005-100000000005",
  "trace_id": "c1d2e3f4-0009-4009-8009-200000000009",
  "timestamp": "2025-10-01T19:00:00Z",
  "partition_key": "zone_industrial_este",
  "geo": {
    "zone": "zone_industrial_este",
    "lat": -12.0800,
    "lon": -77.0800
  },
  "severity": "critical",
  "payload": {
    "tipo_de_alerta": "panico",
    "identificador_dispositivo": "BTN-FAB-101",
    "user_context": "quiosco"
  }
}
```

#### Evento 2: Sensor LPR (45 segundos después)

```json
{
  "event_version": "1.0",
  "event_type": "sensor.lpr",
  "event_id": "a1b2c3d4-0010-4010-8010-000000000010",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0005-4005-8005-100000000005",
  "trace_id": "c1d2e3f4-0010-4010-8010-200000000010",
  "timestamp": "2025-10-01T19:00:45Z",
  "partition_key": "zone_industrial_este",
  "geo": {
    "zone": "zone_industrial_este",
    "lat": -12.0801,
    "lon": -77.0801
  },
  "severity": "warning",
  "payload": {
    "placa_vehicular": "TRK999",
    "velocidad_estimada": 88.0,
    "modelo_vehiculo": "camion",
    "color_vehiculo": "gris",
    "ubicacion_sensor": "cam_acceso_industrial"
  }
}
```

**✅ Resultado Esperado:** Alerta `possible_robbery` en zona `zone_industrial_este`

---

### 📍 Escenario 6: Accidente en Zona Universitaria

**Contexto:** Reporte de accidente + sensor acústico detecta explosión (posible fuga de gas)

#### Evento 1: Citizen Report

```json
{
  "event_version": "1.0",
  "event_type": "citizen.report",
  "event_id": "a1b2c3d4-0011-4011-8011-000000000011",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0006-4006-8006-100000000006",
  "trace_id": "c1d2e3f4-0011-4011-8011-200000000011",
  "timestamp": "2025-10-01T20:00:00Z",
  "partition_key": "zone_universitaria",
  "geo": {
    "zone": "zone_universitaria",
    "lat": -12.0900,
    "lon": -77.0900
  },
  "severity": "warning",
  "payload": {
    "tipo_evento": "accidente",
    "mensaje_descriptivo": "Bus impactó poste de luz",
    "ubicacion_aproximada": "Av. Universitaria cruce con Angamos",
    "origen": "usuario"
  }
}
```

#### Evento 2: Sensor Acoustic (240 segundos después)

```json
{
  "event_version": "1.0",
  "event_type": "sensor.acoustic",
  "event_id": "a1b2c3d4-0012-4012-8012-000000000012",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "b1c2d3e4-0006-4006-8006-100000000006",
  "trace_id": "c1d2e3f4-0012-4012-8012-200000000012",
  "timestamp": "2025-10-01T20:04:00Z",
  "partition_key": "zone_universitaria",
  "geo": {
    "zone": "zone_universitaria",
    "lat": -12.0901,
    "lon": -77.0901
  },
  "severity": "critical",
  "payload": {
    "tipo_sonido_detectado": "explosion",
    "nivel_decibeles": 130,
    "probabilidad_evento_critico": 0.92
  }
}
```

**✅ Resultado Esperado:** Alerta `accident` en zona `zone_universitaria`

---

## ✅ Verificación de Resultados

### Método 1: Kafka UI

1. Accede a: http://localhost:8081
2. Click en **Topics** → **t01.correlated.alerts**
3. Tab **Messages**
4. Deberías ver las alertas generadas con sus respectivos `alert_id`, `type`, `zone`, `score` y `evidence`

---

### Método 2: PostgreSQL

```bash
# Docker Desktop
docker exec -it postgres psql -U postgres -d ciudades -c "SELECT alert_id, type, zone, score, created_at, evidence FROM alerts ORDER BY created_at DESC LIMIT 10;"

# WSL
docker exec -it platform_postgres_1 psql -U postgres -d ciudades -c "SELECT alert_id, type, zone, score, created_at, evidence FROM alerts ORDER BY created_at DESC LIMIT 10;"
```

**Salida esperada:**

```
               alert_id               |       type       |          zone           | score |          created_at           |                    evidence
--------------------------------------+------------------+-------------------------+-------+-------------------------------+--------------------------------------------
 <uuid>                               | possible_robbery | zone_industrial_este    | 0.85  | 2025-10-01 19:00:50.123+00    | ["a1b2c3d4-0009-...", "a1b2c3d4-0010-..."]
 <uuid>                               | accident         | zone_universitaria      | 0.85  | 2025-10-01 20:04:05.456+00    | ["a1b2c3d4-0011-...", "a1b2c3d4-0012-..."]
 ...
```

**Nota:** Los Event IDs en `evidence` son los UUIDs v4 completos que usaste al enviar los eventos.

---

### Método 3: Grafana

1. Accede a: http://localhost:3000
2. Ve a **Dashboard Principal**
3. Verifica:
   - **Total Alertas (24h)**: Debería ser 6
   - **Tabla de Alertas Recientes**: Deberías ver las 6 alertas
   - **Gráfico de Alertas por Tipo**: 
     - `possible_robbery`: 3
     - `accident`: 3

---

### Método 4: Logs del Correlator

```bash
docker logs correlator-correlator-1 | grep -i "alert"
```

Busca líneas como:
```
Alert generated: possible_robbery in zone zone_centro_comercial
Alert saved to database: <alert_id>
```

---

## 💡 Tips para Pruebas Exitosas

### ✅ Hacer

1. **Usar Event IDs únicos** cada vez que repitas un escenario
2. **Respetar las ventanas de tiempo**:
   - Robo: ≤ 2 minutos entre eventos
   - Accidente: ≤ 5 minutos entre eventos
3. **Enviar eventos en orden** (primero el trigger, luego el complementario)
4. **Usar la misma zona** en ambos eventos de un par
5. **Verificar payloads**:
   - LPR: `velocidad_estimada > 80`
   - Citizen: `tipo_evento = "accidente"`
   - Acoustic: `tipo_sonido_detectado = "explosion"` o `"vidrio_roto"`

---

### ❌ Evitar

1. **NO reutilizar Event IDs** (se rechazarán por idempotencia)
2. **NO enviar eventos muy separados en el tiempo** (fuera de ventana)
3. **NO cambiar la zona** entre eventos de un mismo par
4. **NO usar velocidad ≤ 80** para LPR (no activará la regla)
5. **NO usar `tipo_evento` diferente a "accidente"** en citizen reports
6. **NO usar timestamps muy antiguos** (Grafana podría no mostrarlos)

---

## 🔄 Cómo Repetir un Escenario

Si necesitas repetir un escenario (por ejemplo, para demo):

1. **Generar nuevos UUIDs v4**:
   ```bash
   # PowerShell
   New-Guid
   # Output: d4e5f6a7-1234-4567-89ab-123456789012
   
   # Linux/Mac
   uuidgen
   # Output: e5f6a7b8-2345-5678-9abc-234567890123
   ```

2. **Reemplazar Event IDs**:
   ```json
   // Antes
   "event_id": "a1b2c3d4-0001-4001-8001-000000000001"
   
   // Después (con nuevo UUID generado)
   "event_id": "d4e5f6a7-1234-4567-89ab-123456789012"
   ```

3. **Actualizar Timestamps** (opcional):
   ```bash
   # Obtener timestamp actual UTC
   date -u +%Y-%m-%dT%H:%M:%SZ
   ```

4. **Enviar eventos nuevamente**

---

## 📊 Resumen de Escenarios

| # | Tipo Alerta | Zona | Eventos | Tiempo | Event IDs |
|---|-------------|------|---------|--------|-----------|
| 1 | possible_robbery | zone_centro_comercial | panic + lpr | 90s | 001, 002 |
| 2 | possible_robbery | zone_residencial_norte | panic + lpr | 60s | 003, 004 |
| 3 | accident | zone_autopista_sur | citizen + acoustic | 120s | 005, 006 |
| 4 | accident | zone_centro_historico | citizen + acoustic | 180s | 007, 008 |
| 5 | possible_robbery | zone_industrial_este | panic + lpr | 45s | 009, 010 |
| 6 | accident | zone_universitaria | citizen + acoustic | 240s | 011, 012 |

---

## 🎯 Colección de Postman

Para facilitar las pruebas, puedes importar estos eventos en Postman:

1. Crea una **Collection** llamada "Ciudad Inteligente - Tests"
2. Crea **6 folders** (uno por escenario)
3. En cada folder, crea **2 requests** (Evento 1 y Evento 2)
4. Configura:
   - Method: `POST`
   - URL: `http://localhost:8000/events`
   - Headers: `Content-Type: application/json`
   - Body: Copiar el JSON de cada evento

---

## 📝 Plantilla para Crear Nuevos Escenarios

```json
{
  "event_version": "1.0",
  "event_type": "<panic.button | sensor.lpr | citizen.report | sensor.acoustic>",
  "event_id": "<UNIQUE-ID>",
  "producer": "test-suite",
  "source": "simulated",
  "correlation_id": "<CORRELATION-ID>",
  "trace_id": "<TRACE-ID>",
  "timestamp": "<YYYY-MM-DDTHH:MM:SSZ>",
  "partition_key": "<zone_name>",
  "geo": {
    "zone": "<zone_name>",
    "lat": <latitude>,
    "lon": <longitude>
  },
  "severity": "<info | warning | critical>",
  "payload": {
    // Específico por tipo de evento
  }
}
```

---

## 🎉 ¡Listo para Probar!

Ahora tienes 6 escenarios completos y probados. Si sigues las instrucciones correctamente, cada par de eventos generará una alerta visible en Kafka, PostgreSQL y Grafana.

**Siguiente paso:** Ejecuta los escenarios en orden y verifica los resultados en Grafana.

---

**Última actualización:** 1 de octubre de 2025  
**Versión:** 1.0  
**Compatible con:** Correlator v1.0

# Grafana - Visualización de Datos

## 📊 Descripción

Grafana proporciona dashboards interactivos para visualizar eventos, alertas y métricas del sistema de Ciudad Inteligente en tiempo real.

---

## 🚀 Acceso

### Credenciales por Defecto

- **URL**: http://localhost:3000
- **Usuario**: `admin`
- **Contraseña**: `admin`

> ⚠️ **Nota**: Al primer inicio, Grafana te pedirá cambiar la contraseña. Para desarrollo, puedes mantener `admin/admin`.

---

## 📈 Dashboards Disponibles

### 1. **Dashboard Principal - Ciudad Inteligente**

**Ruta**: Home → Dashboards → Ciudad Inteligente - Dashboard Principal

**Paneles incluidos:**
- 📊 **Total Alertas (24h)**: Contador de alertas generadas en las últimas 24 horas
- 📊 **Total Eventos (24h)**: Contador de eventos procesados en las últimas 24 horas
- 📈 **Alertas por Tipo**: Gráfico temporal de alertas categorizadas (possible_robbery, accident, etc.)
- 📈 **Eventos por Tipo**: Distribución de eventos por categoría (panic.button, sensor.lpr, etc.)
- 📋 **Tabla de Alertas Recientes**: Las últimas 50 alertas con detalles completos
- 🥧 **Distribución por Zona**: Pie chart de alertas agrupadas por zona geográfica
- 🍩 **Alertas por Tipo (24h)**: Donut chart de tipos de alerta

**Refresh**: Cada 10 segundos

---

### 2. **Mapa de Calor por Zona**

**Ruta**: Home → Dashboards → Ciudad Inteligente - Mapa de Calor por Zona

**Paneles incluidos:**
- 🗺️ **Mapa Geográfico**: Visualización de eventos en mapa con marcadores por ubicación
- 📊 **Eventos por Zona y Tipo**: Barchart comparativo de eventos
- 🔥 **Heatmap Temporal**: Mapa de calor mostrando actividad por zona en el tiempo

**Refresh**: Cada 30 segundos

---

## 🔧 Configuración

### Datasource

El datasource de PostgreSQL está pre-configurado automáticamente:

- **Nombre**: PostgreSQL-Ciudades
- **Host**: host.docker.internal:5432
- **Database**: ciudades
- **Usuario**: postgres
- **Contraseña**: postgres

### Verificar Conexión

1. Ve a **Configuration** (⚙️) → **Data Sources**
2. Click en **PostgreSQL-Ciudades**
3. Scroll hasta abajo y click en **Save & Test**
4. Deberías ver: ✅ "Database Connection OK"

---

## 📝 Queries SQL Disponibles

### Consulta de Alertas Recientes
```sql
SELECT 
  alert_id,
  type,
  zone,
  score,
  TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at,
  evidence
FROM alerts
WHERE created_at > NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC
LIMIT 50;
```

### Consulta de Eventos por Zona
```sql
SELECT 
  zone,
  event_type,
  COUNT(*) as count
FROM events
WHERE ts_utc > NOW() - INTERVAL '1 hour'
GROUP BY zone, event_type
ORDER BY zone, count DESC;
```

### Consulta de Mapa de Calor
```sql
SELECT 
  DATE_TRUNC('minute', ts_utc) AS time,
  zone,
  COUNT(*) as value
FROM events
WHERE ts_utc > NOW() - INTERVAL '2 hours'
GROUP BY time, zone
ORDER BY time;
```

---

## 🎨 Personalización

### Crear un Nuevo Panel

1. En cualquier dashboard, click en **Add panel** (arriba a la derecha)
2. Selecciona el datasource: **PostgreSQL-Ciudades**
3. Escribe tu query SQL en el editor
4. Selecciona el tipo de visualización (Graph, Table, Stat, etc.)
5. Configura opciones de display
6. Click en **Apply**

### Exportar Dashboard

1. Abre el dashboard que quieres exportar
2. Click en el ícono de **Share** (arriba a la derecha)
3. Ve a la pestaña **Export**
4. Click en **Save to file**
5. Se descargará un archivo JSON

> 📦 **Entregable A6**: Exporta el dashboard JSON y toma screenshots para tu documentación

---

## 🔄 Troubleshooting

### Error: "Database Connection Failed"

**Problema**: Grafana no puede conectarse a PostgreSQL

**Solución**:
1. Verifica que PostgreSQL esté corriendo:
   ```powershell
   docker ps | findstr postgres
   ```
2. Verifica que la base de datos `ciudades` exista:
   ```powershell
   docker exec -it postgres psql -U postgres -l
   ```
3. Reinicia Grafana:
   ```powershell
   docker-compose restart grafana
   ```

---

### Dashboards no cargan datos

**Problema**: Los paneles están vacíos

**Solución**:
1. Verifica que haya datos en las tablas:
   ```powershell
   docker exec -it postgres psql -U postgres -d ciudades -c "SELECT COUNT(*) FROM events;"
   docker exec -it postgres psql -U postgres -d ciudades -c "SELECT COUNT(*) FROM alerts;"
   ```
2. Genera algunos eventos de prueba con el Ingestor
3. Refresca el dashboard (botón de refresh arriba a la derecha)

---

### Mapa geográfico no muestra puntos

**Problema**: El panel de mapa no tiene marcadores

**Solución**:
1. Verifica que los eventos tengan coordenadas geográficas:
   ```powershell
   docker exec -it postgres psql -U postgres -d ciudades -c "SELECT COUNT(*) FROM events WHERE geo_lat IS NOT NULL AND geo_lon IS NOT NULL;"
   ```
2. Asegúrate de enviar eventos con campos `geo.lat` y `geo.lon` poblados

---

## 📚 Queries de Ejemplo para Testing

### Insertar Evento de Prueba
```sql
INSERT INTO events (event_id, event_type, event_version, producer, source, 
                    partition_key, ts_utc, zone, geo_lat, geo_lon, 
                    severity, payload)
VALUES (
  gen_random_uuid(),
  'panic.button',
  '1.0',
  'manual',
  'simulated',
  'zone_4',
  NOW(),
  'zone_4',
  -12.0464,
  -77.0428,
  'critical',
  '{"tipo_de_alerta": "panico"}'::jsonb
);
```

### Insertar Alerta de Prueba
```sql
INSERT INTO alerts (alert_id, type, zone, score, created_at, evidence)
VALUES (
  gen_random_uuid(),
  'possible_robbery',
  'zone_4',
  0.85,
  NOW(),
  '["event-1", "event-2"]'::jsonb
);
```

---

## 🎯 Tips de Uso

1. **Usa variables de tiempo**: Los dashboards tienen controles de tiempo arriba a la derecha
2. **Auto-refresh**: Configura refresh automático para monitoreo en tiempo real
3. **Filtros**: Usa los dropdowns en la parte superior para filtrar por zona o tipo
4. **Zoom en gráficos**: Click y arrastra en cualquier gráfico para hacer zoom
5. **Compartir**: Usa el botón "Share" para obtener URLs o embeddings

---

## 📖 Recursos Adicionales

- **Documentación oficial**: https://grafana.com/docs/
- **Panel plugins**: https://grafana.com/grafana/plugins/
- **Query examples**: https://grafana.com/docs/grafana/latest/datasources/postgres/

---

## 🎉 ¡Listo!

Tu Grafana está configurado y listo para visualizar los datos de tu Ciudad Inteligente. Accede a http://localhost:3000 y explora los dashboards.

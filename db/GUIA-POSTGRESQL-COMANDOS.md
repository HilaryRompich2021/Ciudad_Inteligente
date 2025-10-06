# Guía rápida: Comandos básicos para gestionar PostgreSQL en Docker

## 1. Acceder al contenedor de PostgreSQL

```bash
# Verifica el nombre del contenedor (ejemplo: platform_postgres_1)
docker ps | grep postgres

# Accede al contenedor con usuario y base de datos correctos
docker exec -it platform-postgres-1 -U postgres -d ciudades
```

---

## 2. Comandos básicos dentro de psql

### Listar todas las tablas
```sql
\dt
```

### Ver estructura de una tabla
```sql
\d events
\d alerts
```

### Ver los primeros registros de una tabla
```sql
SELECT * FROM events LIMIT 10;
SELECT * FROM alerts LIMIT 10;
```

### Consultar columnas específicas
```sql
SELECT event_id, event_type, zone, severity, ts_utc FROM events ORDER BY ts_utc DESC LIMIT 10;
SELECT alert_id, zone, type, score, created_at FROM alerts ORDER BY created_at DESC LIMIT 10;
```

### Buscar por zona o tipo
```sql
SELECT * FROM events WHERE zone = 'Norte' LIMIT 5;
SELECT * FROM alerts WHERE type = 'accident' LIMIT 5;
```

---

## 3. Usar los índices

### Ver los índices de una tabla
```sql
\di events*
\di alerts*
```

### Consultar usando un índice (ejemplo: por zona)
```sql
EXPLAIN SELECT * FROM events WHERE zone = 'Norte';
```

### Consultar por rango de tiempo (usa idx_events_ts)
```sql
SELECT * FROM events WHERE ts_utc > now() - interval '1 hour';
```

---

## 4. Consultas útiles para análisis

### Contar eventos por tipo
```sql
SELECT event_type, COUNT(*) FROM events GROUP BY event_type ORDER BY COUNT(*) DESC;
```

### Contar alertas por zona
```sql
SELECT zone, COUNT(*) FROM alerts GROUP BY zone ORDER BY COUNT(*) DESC;
```

### Ver alertas recientes
```sql
SELECT * FROM alerts WHERE created_at > now() - interval '15 minutes';
```

---

## 5. Exportar datos a CSV

```sql
\copy (SELECT * FROM events) TO '/tmp/events.csv' CSV HEADER;
\copy (SELECT * FROM alerts) TO '/tmp/alerts.csv' CSV HEADER;
```

---

## 6. Salir de psql
```sql
\q
```

---

## 7. Comandos avanzados

### Ver uso de índices en una consulta
```sql
EXPLAIN ANALYZE SELECT * FROM events WHERE partition_key = 'zone_4';
```

### Ver estadísticas de la base de datos
```sql
SELECT relname AS table, n_live_tup AS rows FROM pg_stat_user_tables ORDER BY rows DESC;
```

---

## 8. Limpieza y mantenimiento

### Eliminar registros antiguos
```sql
DELETE FROM events WHERE ts_utc < now() - interval '30 days';
```

### Vaciar una tabla (¡Cuidado!)
```sql
TRUNCATE TABLE events;
```

### Optimizar la base de datos
```sql
VACUUM;
ANALYZE;
```

---

## 9. Ayuda rápida en psql
```sql
\?
```

---

## 10. Resumen de comandos útiles
| Acción                  | Comando                  |
|------------------------|--------------------------|
| Listar tablas          | \dt                      |
| Ver estructura         | \d <tabla>               |
| Ver índices            | \di <tabla>*             |
| Consultar registros    | SELECT ... FROM <tabla>  |
| Exportar a CSV         | \copy ... TO ...         |
| Salir                  | \q                       |
| Ayuda                  | \?                       |

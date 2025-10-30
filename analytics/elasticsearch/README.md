# Guía rápida para probar Elasticsearch en el proyecto

Esta carpeta contiene los templates de índices para Elasticsearch (`events_template.json` y `alerts_template.json`). Aquí tienes los pasos básicos para probar que Elasticsearch está funcionando correctamente y que los datos se indexan según lo esperado.

---

## 1. Verifica que Elasticsearch está corriendo

Accede a:
```
http://localhost:9200
```
Deberías ver una respuesta JSON con información de la instancia.

---


## 2. Aplica los templates de índices (¡Obligatorio antes de indexar datos!)

Antes de insertar o sincronizar datos en Elasticsearch, asegúrate de aplicar los templates de índices. Esto garantiza que los campos y tipos estén correctamente definidos y evita problemas de mapeo.

Puedes aplicar los templates manualmente usando curl, PowerShell o Postman:


```powershell
# Events
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/events_template" -Method Put -Headers @{"Content-Type"="application/json"} -InFile "elasticsearch/templates/events_template.json"

# Alerts
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/alerts_template" -Method Put -Headers @{"Content-Type"="application/json"} -InFile "elasticsearch/templates/alerts_template.json"
```

---

## 3. Verifica los templates

```bash
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/events_template"
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/alerts_template"
```

---

## 4. Consulta los datos indexados


### Consulta rápida en PowerShell

Para ver los eventos y alertas indexados desde PowerShell, usa:

```powershell
# Eventos
(Invoke-WebRequest -Uri "http://localhost:9200/events-*/_search?pretty&size=5" -Method Get).Content

# Alertas
(Invoke-WebRequest -Uri "http://localhost:9200/alerts-*/_search?pretty&size=5" -Method Get).Content
```

Esto mostrará los documentos indexados directamente en la consola.

---



## 5. Prueba y visualización en Kibana

1. Accede a Kibana: `http://localhost:5601`
2. Crea los data views `events-*` y `alerts-*` como se indica arriba.
3. Ve a **Discover** y selecciona el data view que quieras explorar.
4. Ajusta el rango de tiempo en la parte superior derecha para ver los datos recientes.
5. Usa el buscador para filtrar por campos, por ejemplo:
	- `event_type: "sensor.lpr"` (solo eventos de tipo sensor.lpr)
	- `zone: "zone_4"` (solo eventos/alertas de la zona 4)
	- `score > 0.8` (solo alertas con score alto)
6. Haz clic en los campos de la izquierda para agregarlos a la tabla de resultados.
7. Puedes exportar los resultados o tomar capturas de pantalla para tu evidencia.


**Ejemplo de búsqueda en Discover para eventos:**

```


# Eventos de tipo panic.button y alerta de pánico en el payload
event_type: "panic.button" AND payload.tipo_de_alerta: "panico"

# Eventos críticos de tipo sensor.lpr
event_type: "sensor.lpr" AND severity: "critical"

# Eventos con latitud y longitud específicas
geo.lat: 14.63 AND geo.lon: -90.53
`
```

**Ejemplos de búsqueda en Discover para alertas:**
```
# 1. Todas las alertas de la zona 1
zone: "zone_1"

# 2. Alertas de tipo posible robo con score alto
type: "possible_robbery" AND score > 0.8

# 3. Alertas generadas en el último minuto
created_at > now-1m
```

**Visualización básica:**
- El histograma superior muestra la cantidad de documentos por intervalo de tiempo.
- La tabla inferior muestra los documentos y sus campos.

**Opcional:**
- Crea dashboards y visualizaciones (mapas, tablas, gráficos) usando los data views para mostrar métricas, mapas de calor, cronologías, etc.

---

## 5b. Prueba desde Grafana

- Accede a Grafana: `http://localhost:3000`
- Realiza búsquedas o visualizaciones usando los índices `events-*` y `alerts-*`

---

## 6. Notas

- Los templates definen los campos y tipos para los índices, asegúrate de aplicarlos antes de insertar datos.
- Si usas Airflow para ETL, los datos de eventos y alertas se sincronizan automáticamente desde PostgreSQL a Elasticsearch cada hora mediante el DAG `pg_to_es_etl_dag.py`.
- No necesitas insertar datos manualmente en Elasticsearch: solo asegúrate de que el DAG esté activo y los datos en PostgreSQL se indexarán en ES.
- Puedes modificar los templates según tus necesidades y volver a aplicarlos.
- Si modificas la estructura de los datos en PostgreSQL, actualiza también los templates y el DAG para reflejar los cambios.

---

¿Problemas? Verifica los logs de Elasticsearch y asegúrate de que los servicios estén corriendo en Docker.

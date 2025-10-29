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

## 2. Aplica los templates de índices

Puedes aplicar los templates manualmente usando curl o Postman:

```bash
# Events
curl -X PUT "http://localhost:9200/_index_template/events_template" -H 'Content-Type: application/json' --data-binary @templates/events_template.json

# Alerts
curl -X PUT "http://localhost:9200/_index_template/alerts_template" -H 'Content-Type: application/json' --data-binary @templates/alerts_template.json
```

---

## 3. Verifica los templates

```bash
curl -X GET "http://localhost:9200/_index_template/events_template?pretty"
curl -X GET "http://localhost:9200/_index_template/alerts_template?pretty"
```

---

## 4. Consulta los datos indexados

Para ver los eventos y alertas indexados:

```bash
# Eventos
curl -X GET "http://localhost:9200/events-*/_search?pretty&size=10"

# Alertas
curl -X GET "http://localhost:9200/alerts-*/_search?pretty&size=10"
```

---

## 5. Prueba desde Kibana o Grafana

- Accede a Kibana: `http://localhost:5601` (si está disponible)
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

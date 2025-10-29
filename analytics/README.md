
# 🌆 Ciudad Inteligente — Analytics Stack (Airflow + Elasticsearch + Grafana + Kibana)


## 🚀 Descripción
Este módulo contiene la integración de **Airflow** para ETL, **Elasticsearch** para indexación, **Grafana** para visualización y **Kibana** como interfaz de usuario para consultar y explorar los datos indexados en Elasticsearch.  
La base de datos de eventos y alertas (PostgreSQL/Supabase) está en línea y no se gestiona en esta carpeta.  
Si necesitas conectarte a la base de datos, configura correctamente las variables de entorno en `.env`.

---

## 🧱 Componentes principales


| Servicio              | Puerto | Descripción                                      |
|-----------------------|--------|--------------------------------------------------|
| 🌀 Airflow Webserver   | 8082   | Interfaz de administración y ejecución de DAGs   |
| ⚙️ Airflow Scheduler   | interno| Ejecuta las tareas de ETL                        |
| 📊 Grafana            | 3000   | Visualización de métricas ETL                    |
| 🔎 Elasticsearch      | 9200   | Indexación y consulta de eventos/alertas         |
| 🖥️ Kibana             | 5601   | Interfaz web para explorar y visualizar datos de Elasticsearch |

---


## 🧩 DAGs principales

- `pg_to_es_etl_dag.py`: ETL desde Supabase/PostgreSQL a Elasticsearch.
- `etl_analytics_dag.py`: Procesos de analítica sobre eventos indexados.
- `healthcheck_dag.py`: Monitoreo de la infraestructura.

---

## 🛠️ Configuración

- La base de datos (Supabase/PostgreSQL) está en línea.  
  Configura las variables de entorno en `.env` para conectar Airflow a la BD remota.
- Kafka y Redis no se gestionan en este módulo; su configuración está en el stack de microservicios.

---


## 🧠 Flujo de datos general

```mermaid
graph LR
A[Supabase/PostgreSQL (online)] --> B[Airflow ETL]
B --> C[Elasticsearch]
C --> D[Grafana Dashboard]
C --> E[Kibana UI]
```

---

## 🛠️ Solución a errores de base de datos en Airflow

Si el contenedor de Airflow no inicia y ves en los logs el error:

```
ERROR: You need to upgrade the database. Please run `airflow db upgrade`. Make sure the command is run using Airflow version X.Y.Z.
```

Esto significa que la base de datos interna (por defecto SQLite) necesita migraciones para la versión actual de Airflow.

### Pasos para solucionar
1. **Detén los servicios de Airflow:**
   ```powershell
   docker-compose down
   ```
2. **Elimina el archivo PID si existe (opcional):**
   ```powershell
   del .\airflow_data\airflow-webserver.pid
   ```
3. **Ejecuta la migración de la base de datos:**
   ```powershell
   docker-compose run --rm airflow-webserver airflow db upgrade
   ```
4. **Vuelve a levantar los servicios:**
   ```powershell
   docker-compose up -d
   ```

### Notas
- Configura la variable `AIRFLOW__DATABASE__SQL_ALCHEMY_CONN` en el `docker-compose.yml` para conectar a la BD remota si es necesario.
- Para crear un usuario admin si no existe:
   ```powershell
   docker-compose run --rm airflow-webserver airflow users create --username admin --firstname Admin --lastname User --role Admin --email admin@example.com --password admin
   ```

---

## 🗂️ Elasticsearch: Carga de plantillas

Para cargar las plantillas de índices en Elasticsearch, ejecuta:

```powershell
Invoke-WebRequest -Uri "http://localhost:9200/_index_template/alerts_template" `
  -Method Put `
  -Headers @{ "Content-Type" = "application/json" } `
  -InFile ".\elasticsearch\templates\alerts_template.json" `
  -ErrorAction Stop

Invoke-WebRequest -Uri "http://localhost:9200/_index_template/events_template" `
  -Method Put `
  -Headers @{ "Content-Type" = "application/json" } `
  -InFile ".\elasticsearch\templates\events_template.json" `
  -ErrorAction Stop
```

---


## 🚦 Cómo levantar el stack Analytics

1. Asegúrate de tener Docker y Docker Compose instalados.
2. Configura las variables de entorno necesarias en el archivo `.env` (por ejemplo, credenciales de Supabase/PostgreSQL si usas la BD remota).
3. Desde la carpeta `Analytics`, ejecuta:

   ```powershell
   docker-compose up -d
   ```

Esto levantará los servicios de Airflow, Elasticsearch, Grafana y Kibana en segundo plano.

Accede a las interfaces web:
- Airflow: [http://localhost:8082](http://localhost:8082)
- Grafana: [http://localhost:3000](http://localhost:3000)
- Kibana: [http://localhost:5601](http://localhost:5601)

Para detener el stack:

   ```powershell
   docker-compose down
   ```

---

## 📄 Notas finales

- Este módulo no gestiona la base de datos ni los microservicios de eventos (Kafka, Redis).
- Revisa la documentación de cada componente para configuración avanzada.
# 🌆 Ciudad Inteligente — Integración Airflow + PostgreSQL + Grafana

## 🚀 Descripción
Esta rama (`airflow`) contiene la integración completa del **DAG de analítica (ETL)** con **Airflow**, **PostgreSQL** y **Grafana**.  
Permite ejecutar flujos automáticos de datos desde Kafka, agregarlos y visualizarlos en dashboards interactivos.

---

## 🧱 Componentes principales

| Servicio | Puerto | Descripción |
|-----------|--------|-------------|
| 🐘 **PostgreSQL** | 5432 | Base de datos `ciudades` con tablas `alerts` y `analytics_results` |
| 🌀 **Airflow Webserver** | 8081 | Interfaz de administración y ejecución de DAGs |
| ⚙️ **Airflow Scheduler** | interno | Ejecuta las tareas del DAG `etl_analytics_dag` |
| 📊 **Grafana** | 3000 | Visualización de métricas ETL |
| 📦 **Kafka / Zookeeper** | 9092 / 2181 | Flujo de eventos IoT |

---

## 🧩 DAG `etl_analytics_dag.py`

**Objetivo:**  
Agrupar eventos almacenados en `alerts` y generar métricas en `analytics_results` cada 10 minutos.

**Tareas:**
1. `clear_old_data` → limpia métricas antiguas (>1 día)  
2. `aggregate_alerts` → agrega nuevas métricas agrupadas por tipo y zona

---

## 🧠 Flujo de datos general

```mermaid
graph LR
A[Postman] --> B[Ingestor API]
B --> C[Kafka Topic: t01.events.standardized]
C --> D[Airflow ETL]
D --> E[(PostgreSQL - analytics_results)]
E --> F[Grafana Dashboard]

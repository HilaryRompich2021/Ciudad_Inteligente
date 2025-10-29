# Guía General del Proyecto Ciudad Inteligente

Esta guía explica de manera clara y estructurada el propósito, arquitectura y funcionamiento de todos los componentes principales del proyecto, sin entrar en detalles de código.

---

## Descripción General

El proyecto Ciudad Inteligente integra una infraestructura moderna para la gestión y análisis de eventos urbanos, permitiendo la recolección, procesamiento, almacenamiento, visualización y análisis de datos en tiempo real y diferido.

---

## Componentes Principales

### 1. Ingestor
- Microservicio encargado de recibir eventos desde fuentes externas (sensores, aplicaciones, etc.).
- Valida y transforma los datos al formato canónico del proyecto.
- Publica los eventos en Kafka para su procesamiento posterior.
- Clases principales: `EventController`, `EventService`, `EventValidator`.

### 2. Correlator
- Microservicio que consume eventos desde Kafka.
- Aplica reglas de correlación para detectar situaciones relevantes (por ejemplo, alertas).
- Genera nuevas alertas y las persiste en la base de datos PostgreSQL.
- Clases principales: `CorrelatorService`, `AlertRule`, `AlertRepository`.

### 3. PostgreSQL
- Base de datos relacional donde se almacenan todos los eventos y alertas generados por los microservicios.
- Sirve como fuente principal para análisis y visualización.
- Entidades principales: `Event`, `Alert`.

### 4. Airflow
- Orquestador de procesos ETL (Extract, Transform, Load).
- Sincroniza periódicamente los datos desde PostgreSQL hacia Elasticsearch para indexación y búsquedas rápidas.
- DAG principal: `pg_to_es_etl_dag.py`.

### 5. Elasticsearch
- Motor de búsqueda y análisis de datos.
- Permite realizar consultas avanzadas, búsquedas geoespaciales y análisis de grandes volúmenes de eventos y alertas.
- Índices principales: `events-*`, `alerts-*`.

### 6. Grafana
- Plataforma de visualización de datos.
- Ofrece dashboards listos para usar, conectados tanto a PostgreSQL como a Elasticsearch.
- Permite monitorear el estado de la ciudad, analizar tendencias y visualizar mapas de calor, cronologías y evidencias.
- Dashboards principales: `main.json`, `heatmap.json`, `evidence.json`.

---

## Flujo de Datos

1. Los eventos llegan al microservicio Ingestor.
2. Ingestor valida y publica los eventos en Kafka.
3. Correlator consume los eventos, aplica reglas y genera alertas.
4. Todos los datos se almacenan en PostgreSQL.
5. Airflow ejecuta procesos ETL para sincronizar los datos hacia Elasticsearch.
6. Grafana y Kibana permiten visualizar y analizar los datos desde ambas fuentes.

---

## Seguridad y Configuración

- Todas las credenciales y parámetros sensibles se gestionan mediante archivos `.env`, nunca se suben al repositorio.
- La infraestructura se levanta con Docker Compose, facilitando la reproducción y despliegue en cualquier entorno local.
- Los archivos `.env.example` sirven como plantilla para la configuración inicial.

---

## Visualización y Análisis

- Grafana ofrece paneles analíticos, mapas de calor y cronologías para el monitoreo de eventos y alertas.
- Elasticsearch permite búsquedas rápidas y análisis avanzado de datos.
- Kibana puede usarse como alternativa para visualización sobre Elasticsearch.

---

## Objetivo

El proyecto busca proveer una base robusta y flexible para la gestión inteligente de ciudades, facilitando la integración de nuevos microservicios, el análisis de datos en tiempo real y la toma de decisiones basada en evidencia.

---

## Resumen de Clases y Archivos Relevantes

- Ingestor: `EventController`, `EventService`, `EventValidator`
- Correlator: `CorrelatorService`, `AlertRule`, `AlertRepository`
- PostgreSQL: Entidades `Event`, `Alert`
- Airflow: DAG `pg_to_es_etl_dag.py`
- Elasticsearch: Índices `events-*`, `alerts-*`
- Grafana: Dashboards `main.json`, `heatmap.json`, `evidence.json`

---

Para más detalles técnicos, consulta los README específicos de cada microservicio y la documentación de los dashboards en la carpeta `platform/grafana/provisioning/dashboards`.

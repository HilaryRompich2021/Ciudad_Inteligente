

# Ciudad Inteligente — Infraestructura Base

Este repositorio contiene la infraestructura y configuración principal para el proyecto **Ciudad Inteligente**, una plataforma distribuida para la gestión y análisis de eventos urbanos en tiempo real.

## 1. Resumen del Proyecto
Ciudad Inteligente integra microservicios, procesamiento de eventos, almacenamiento y visualización para monitorear sensores, generar alertas y analizar datos urbanos.

## 2. Arquitectura General
**Flujo principal:**
```
Ingestor → Kafka → Correlator → PostgreSQL → (ETL/Job con Airflow) → Elasticsearch → Grafana/Kibana
```
Cada componente está desacoplado y se comunica por eventos, permitiendo escalabilidad y resiliencia.

## 3. Estructura de Carpetas
- `analytics/`: Infraestructura de análisis y visualización (Airflow, Elasticsearch, Grafana, Kibana).
- `platform/`: Microservicios base y orquestación (Kafka, Zookeeper, Redis, Kafka UI).
- `db/`: Scripts y documentación para bases de datos (PostgreSQL).
- `src/`: Código fuente de microservicios:
  - `correlator/`: Servicio de correlación de eventos y generación de alertas.
  - `ingestor/`: Servicio de ingestión de eventos desde sensores.
- `scripts/`: Scripts para simulación y pruebas de eventos.
- `docs/`: Documentación técnica, ejemplos de eventos y guías de uso.
- `mops/`: Documentación y guías de operación y monitoreo.


## 4. Guía de Onboarding
1. Copia los archivos `.env.example` y completa tus credenciales:
	```bash
	cp platform/.env.example platform/.env
	cp analytics/grafana/.env.example analytics/grafana/.env
	cp analytics/airflow/.env.example analytics/airflow/.env
	# Edita cada archivo .env con tus credenciales
	```
2. **Crea la red Docker externa antes de levantar los servicios (solo la primera vez):**
	```bash
	cd scripts
	sh create-platform-network.sh
	```
3. Levanta los servicios:
	```bash
	make -f platform/Makefile up
	```
4. Accede a los servicios principales:
	- **Kafka-UI:** [http://localhost:8081](http://localhost:8081)
	- **Grafana:** [http://localhost:3000](http://localhost:3000)
	- **Airflow:** [http://localhost:8082](http://localhost:8082)
	- **Kibana (Elasticsearch):** [http://localhost:5601](http://localhost:5601)

## 5. Servicios Principales
- **Kafka:** Broker de eventos para comunicación entre microservicios.
- **Airflow:** Orquestación de ETLs y jobs de análisis.
- **Grafana:** Visualización de métricas y dashboards.
- **Kibana/Elasticsearch:** Almacenamiento y búsqueda de eventos y alertas.
- **Redis:** Almacenamiento en memoria para datos temporales.
- **Correlator:** Microservicio para correlación y generación de alertas.
- **Ingestor:** Microservicio para ingestión de eventos desde sensores.
- **PostgreSQL:** Base de datos relacional para persistencia de eventos.

## 6. Simulación y Pruebas
Para simular eventos y probar reglas, consulta `scripts/README.md`. Incluye scripts para enviar eventos, forzar alertas y verificar tópicos en Kafka.

## 7. Variables de Entorno
Todos los servicios usan archivos `.env` para credenciales y configuración. Ejemplos y plantillas disponibles en cada carpeta.

## 8. Referencias y Enlaces Útiles
- **Guía oficial del proyecto:** `Final-Project-Guide.md`
- **Esquema de eventos:** `docs/EVENTOS-PRUEBA.md`
- **Dashboards Grafana:** `analytics/grafana/provisioning/dashboards/`
- **Templates Elasticsearch:** `analytics/elasticsearch/templates/`

---
Para dudas o colaboración, revisa la documentación en la carpeta `docs/` y los archivos guía en cada servicio.


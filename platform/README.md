# 🌆 Ciudad Inteligente — Plataforma de Mensajería y Cache (Kafka + Redis)

## 🚀 Descripción
Este módulo contiene la infraestructura de mensajería y cache para microservicios, usando **Kafka** (con Zookeeper), **Redis** y la interfaz web **Kafka UI** para monitoreo.

---

## 🧱 Componentes principales

| Servicio      | Puerto         | Descripción                                 |
|---------------|---------------|---------------------------------------------|
| 🌀 Zookeeper   | 2181          | Coordinador para Kafka                      |
| 📦 Kafka      | 9092 / 29092  | Broker de eventos IoT                       |
| �️ Redis      | 6379          | Almacenamiento en memoria para caché/colas  |
| �️ Kafka UI   | 8081          | Interfaz web para monitoreo de Kafka        |

---

## 🚦 Cómo levantar el stack

1. Asegúrate de tener Docker y Docker Compose instalados.
2. Desde la carpeta `platform`, ejecuta:

   ```powershell
   docker-compose up -d
   ```

Esto levantará los servicios de Kafka, Zookeeper, Redis y Kafka UI en segundo plano.

Accede a la interfaz de Kafka UI en: [http://localhost:8081](http://localhost:8081)

Para detener el stack:

   ```powershell
   docker-compose down
   ```

---

## 🔗 Red compartida

Todos los servicios están conectados a la red externa `platform_default`, permitiendo la integración con otros stacks del proyecto.

---


## 📄 Notas finales

- La configuración y documentación de Redis se encuentra en la carpeta `redis/`.
- La infraestructura de analítica y visualización (Airflow, Elasticsearch, Grafana, Kibana) está en el módulo `analytics/

```

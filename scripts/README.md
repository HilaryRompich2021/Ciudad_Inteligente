

# Scripts de simulación de eventos

En esta carpeta tienes cuatro scripts para pruebas y simulación de eventos en el sistema:

1. **send_events_individual.py** — Envía eventos individuales al endpoint `/events`.
2. **send_events_bulk.py** — Envía lotes de eventos (bulk) al endpoint `/events/bulk`.
3. **send_events_forced_alerts.py** — Envía secuencias de eventos especialmente diseñadas para activar las 4 alertas principales del correlator (fire, possible_robbery, accident, traffic_speed_violation).
4. **send_events_forced_alerts_individual.py** — Envía los eventos necesarios para forzar alertas, pero los envía uno por uno al endpoint `/events` para observar el procesamiento individual de cada evento y alerta.

### Ejemplos de uso
Ejecuta los scripts desde la carpeta `scripts/` usando bash o terminal estándar:
```bash
# Simular eventos individuales
python send_events_individual.py

# Simular eventos en lote
python send_events_bulk.py

# Forzar alertas (lote)
python send_events_forced_alerts.py

# Forzar alertas enviando eventos individualmente
python send_events_forced_alerts_individual.py
```

## Requisitos

  ```powershell
  pip install requests
  ```


# Scripts de Simulación y Pruebas

Esta carpeta contiene utilidades para simular eventos, probar reglas de correlación y gestionar tópicos en Kafka.

## Índice de scripts



### 0. Creación de la red Docker externa
- **create-platform-network.sh**: Crea la red Docker externa `platform_default` necesaria para que todos los servicios puedan comunicarse entre sí. Debe ejecutarse antes de levantar los servicios por primera vez:
    ```bash
    cd scripts
    sh create-platform-network.sh
    ```

### 1. Creación y verificación de tópicos Kafka
- **create-topics.sh**: Espera a que Kafka esté disponible y crea los tópicos principales (`events.standardized`, `correlated.alerts`) con configuración de retención y particiones.
  - **Nota:** Este script normalmente es ejecutado automáticamente por Docker Compose. Solo es necesario usarlo manualmente si los tópicos no se han creado correctamente.
- **verify-topics.sh**: Lista los tópicos existentes en Kafka usando Docker y el contenedor de Kafka.

### 2. Simulación de eventos
- **send_events_bulk.py**: Envía lotes de eventos simulados de diferentes tipos (botón de pánico, sensor LPR, velocidad, acústico, reporte ciudadano) al endpoint `/events/bulk`. Los eventos cumplen el esquema oficial y simulan zonas céntricas y periféricas.
- **send_events_individual.py**: Envía eventos simulados uno por uno al endpoint `/events`. Útil para pruebas unitarias y debugging.

### 3. Simulación de alertas forzadas
- **send_events_forced_alerts.py**: Genera y envía secuencias de eventos que cumplen las condiciones mínimas para activar reglas de correlación (ejemplo: posible robo, accidente, congestión). Permite probar el correlador y la generación de alertas.
- **send_events_forced_alerts_individual.py**: Similar al anterior, pero envía los eventos de forma individual para observar el procesamiento paso a paso.

## Uso rápido
1. Asegúrate de que los servicios estén levantados (`make -f platform/Makefile up`).
2. Ejecuta los scripts desde esta carpeta:
    ```bash
    # Crear tópicos
    ./create-topics.sh

    # Verificar tópicos
    ./verify-topics.sh

    # Simular eventos en lote
    python send_events_bulk.py

    # Simular eventos individuales
    python send_events_individual.py

    # Forzar alertas (lote)
    python send_events_forced_alerts.py

    # Forzar alertas (individual)
    python send_events_forced_alerts_individual.py
    ```

## Notas
- Todos los scripts usan el esquema de eventos oficial y pueden adaptarse para nuevas reglas o sensores.
- Revisa los endpoints y credenciales en los scripts antes de ejecutar.

Para más ejemplos y detalles de los eventos, consulta `docs/EVENTOS-PRUEBA.md`.
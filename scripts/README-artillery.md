# Prueba de carga con Artillery (Docker)

Este proyecto incluye un escenario de prueba de carga para el endpoint `/events` del ingestor, usando Artillery y Docker.

## Requisitos
- Docker Desktop instalado
- El archivo `scripts/load-test-events.yml` presente en el proyecto

## Ejecución de la prueba

1. **Ubícate en la raíz del proyecto**

  Abre una terminal y navega a la carpeta raíz donde está la carpeta `scripts`:
  ```bash
  cd /ruta/a/tu/proyecto/Ciudad_Inteligente
  ```

2. **Verifica que el ingestor esté corriendo y accesible**
  - El servicio debe estar escuchando en el puerto 8000.
  - El nombre del servicio debe ser `ingestor` (por Docker Compose).

3. **Ejecuta la prueba de carga con Artillery**

  - El target en el YAML debe ser `http://host.docker.internal:8000`.
  - Ejecuta:
    ```bash
    docker run --rm -v "$PWD:/src" artilleryio/artillery run /src/scripts/load-test-events.yml
    ```

  - El resultado se mostrará en la terminal.

4. **Opcional: Exportar el reporte a JSON**

  Si quieres guardar el reporte para análisis posterior:
  ```bash
  docker run --rm -v "$PWD:/src" artilleryio/artillery run /src/scripts/load-test-events.yml -o /src/scripts/artillery-report.json
  ```

  El archivo `artillery-report.json` quedará en la carpeta `scripts`.

## Notas
- Si el endpoint no responde, revisa la red y el nombre del servicio en Docker Compose.
- Puedes modificar la tasa y duración de la prueba editando el archivo YAML.
- Para más información sobre Artillery: https://www.artillery.io/docs/

---

**Contacto:** [Tu nombre o equipo]

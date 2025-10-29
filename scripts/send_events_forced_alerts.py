import requests
import uuid
import random
import time
from datetime import datetime, timedelta
from collections import OrderedDict

# Utilidad para generar timestamps cercanos

def iso_now(offset_sec=0):
    return (datetime.utcnow() + timedelta(seconds=offset_sec)).isoformat() + "Z"


URL = "http://localhost:8000/events/bulk"
ZONES = [f"zone_{i}" for i in range(1, 11)]  # zonas del 1 al 10

# --- Generadores de eventos para cada alerta ---

# possible_robbery: Genera los eventos mínimos para activar la alerta de posible robo.
# Requiere: 1 panic.button y 1 sensor.lpr (velocidad_estimada > 80) en la misma zona y ventana de 2 minutos.
def events_for_possible_robbery(zone):
    ts_base = datetime.utcnow()
    return [
        # Evento de pánico
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "panic.button"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base).isoformat() + "Z"),
            ("partition_key", "panic.button"),
            ("geo", {"zone": zone, "lat": 14.63, "lon": -90.53}),
            ("severity", "critical"),
            ("payload", {"tipo_de_alerta": "panico", "identificador_dispositivo": "BTN-001", "user_context": "movil"})
        ]),
        # Evento de lectura de placa rápida (LPR)
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=30)).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.63, "lon": -90.53}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ123", "velocidad_estimada": 100, "modelo_vehiculo": "sedan", "color_vehiculo": "negro", "ubicacion_sensor": "sensor_5"})
        ])
    ]

# fire: Genera los eventos mínimos para activar la alerta de incendio.
# Requiere: 1 citizen.report (tipo_evento = "incendio") y 1 sensor.acoustic (explosion o decibeles > 100) en la misma zona y ventana de 5 minutos.
def events_for_fire(zone):
    ts_base = datetime.utcnow()
    return [
        # Reporte ciudadano de incendio
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "citizen.report"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base).isoformat() + "Z"),
            ("partition_key", "citizen.report"),
            ("geo", {"zone": zone, "lat": 14.62, "lon": -90.52}),
            ("severity", "critical"),
            ("payload", {"tipo_evento": "incendio", "mensaje_descriptivo": "fuego en edificio", "ubicacion_aproximada": zone, "origen": "usuario"})
        ]),
        # Sensor acústico detecta explosión
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.acoustic"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=10)).isoformat() + "Z"),
            ("partition_key", "sensor.acoustic"),
            ("geo", {"zone": zone, "lat": 14.62, "lon": -90.52}),
            ("severity", "critical"),
            ("payload", {"tipo_sonido_detectado": "explosion", "nivel_decibeles": 120, "probabilidad_evento_critico": 0.95})
        ])
    ]

# accident: Genera los eventos mínimos para activar la alerta de accidente.
# Requiere: 1 citizen.report (tipo_evento = "accidente"), 1 sensor.acoustic (explosion o vidrio roto), y caída de velocidad en LPR en la misma zona y ventana de 5 minutos.
def events_for_accident(zone):
    ts_base = datetime.utcnow()
    return [
        # Reporte ciudadano de accidente
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "citizen.report"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base).isoformat() + "Z"),
            ("partition_key", "citizen.report"),
            ("geo", {"zone": zone, "lat": 14.64, "lon": -90.54}),
            ("severity", "critical"),
            ("payload", {"tipo_evento": "accidente", "mensaje_descriptivo": "vehiculo volcado", "ubicacion_aproximada": zone, "origen": "usuario"})
        ]),
        # Sensor acústico detecta explosión o vidrio roto
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.acoustic"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=20)).isoformat() + "Z"),
            ("partition_key", "sensor.acoustic"),
            ("geo", {"zone": zone, "lat": 14.64, "lon": -90.54}),
            ("severity", "critical"),
            ("payload", {"tipo_sonido_detectado": "vidrio_roto", "nivel_decibeles": 110, "probabilidad_evento_critico": 0.90})
        ]),
        # Sensor LPR con velocidad alta
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=30)).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.64, "lon": -90.54}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ456", "velocidad_estimada": 90, "modelo_vehiculo": "camioneta", "color_vehiculo": "rojo", "ubicacion_sensor": "sensor_7"})
        ]),
        # Sensor LPR con velocidad baja
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=40)).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.64, "lon": -90.54}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ456", "velocidad_estimada": 30, "modelo_vehiculo": "camioneta", "color_vehiculo": "rojo", "ubicacion_sensor": "sensor_7"})
        ])
    ]

# traffic_speed_violation: Genera los eventos mínimos para activar la alerta de exceso de velocidad.
# Requiere: 3 eventos sensor.lpr (velocidad_estimada > 80) en la misma zona y ventana de 2 minutos.
def events_for_traffic_speed_violation(zone):
    ts_base = datetime.utcnow()
    return [
        # Primer evento LPR
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.65, "lon": -90.55}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ789", "velocidad_estimada": 90, "modelo_vehiculo": "moto", "color_vehiculo": "blanco", "ubicacion_sensor": "sensor_9"})
        ]),
        # Segundo evento LPR
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=10)).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.65, "lon": -90.55}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ790", "velocidad_estimada": 95, "modelo_vehiculo": "moto", "color_vehiculo": "negro", "ubicacion_sensor": "sensor_10"})
        ]),
        # Tercer evento LPR
        OrderedDict([
            ("event_version", "1.0"),
            ("event_type", "sensor.lpr"),
            ("event_id", str(uuid.uuid4())),
            ("producer", "python-sim"),
            ("source", "simulated"),
            ("correlation_id", str(uuid.uuid4())),
            ("trace_id", str(uuid.uuid4())),
            ("timestamp", (ts_base + timedelta(seconds=20)).isoformat() + "Z"),
            ("partition_key", "sensor.lpr"),
            ("geo", {"zone": zone, "lat": 14.65, "lon": -90.55}),
            ("severity", "critical"),
            ("payload", {"placa_vehicular": "XYZ791", "velocidad_estimada": 100, "modelo_vehiculo": "moto", "color_vehiculo": "rojo", "ubicacion_sensor": "sensor_11"})
        ])
    ]

# --- Simulación continua ---

def send_events(events):
    resp = requests.post(URL, json=events)
    print(f"Status: {resp.status_code} | Sent {len(events)} events")

if __name__ == "__main__":
    print("Simulando eventos para activar las 4 alertas principales en bloques separados. Ctrl+C para detener.")
    try:
        zone_idx = 0
        while True:
            # Asigna zonas diferentes para cada tipo de alerta en cada iteración
            zone_robbery = ZONES[(zone_idx + 0) % len(ZONES)]
            zone_accident = ZONES[(zone_idx + 1) % len(ZONES)]
            zone_fire = ZONES[(zone_idx + 2) % len(ZONES)]
            zone_speed = ZONES[(zone_idx + 3) % len(ZONES)]

            print(f"\n--- Enviando eventos para posible robo en {zone_robbery} ---")
            send_events(events_for_possible_robbery(zone_robbery))
            time.sleep(10)

            print(f"\n--- Enviando eventos para accidente en {zone_accident} ---")
            send_events(events_for_accident(zone_accident))
            time.sleep(20)

            print(f"\n--- Enviando eventos para incendio en {zone_fire} ---")
            send_events(events_for_fire(zone_fire))
            time.sleep(20)

            print(f"\n--- Enviando eventos para exceso de velocidad en {zone_speed} ---")
            send_events(events_for_traffic_speed_violation(zone_speed))
            time.sleep(10)

            zone_idx += 4  # Avanza 4 zonas por iteración
    except KeyboardInterrupt:
        print("Detenido por el usuario.")

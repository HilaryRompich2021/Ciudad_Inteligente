import requests
import uuid
import time
from datetime import datetime, timedelta
from collections import OrderedDict

# Utilidad para generar timestamps cercanos
def iso_now(offset_sec=0):
    return (datetime.utcnow() + timedelta(seconds=offset_sec)).isoformat() + "Z"

URL = "http://localhost:8000/events"
ZONES = [f"zone_{i}" for i in range(1, 11)]  # zonas del 1 al 10

def send_event(event):
    resp = requests.post(URL, json=event)
    print(f"Status: {resp.status_code} | Event ID: {event['event_id']} | Type: {event['event_type']}")

# --- Generadores de eventos para cada alerta ---

def events_for_possible_robbery(zone):
    ts_base = datetime.utcnow()
    return [
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

def events_for_fire(zone):
    ts_base = datetime.utcnow()
    return [
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

def events_for_accident(zone):
    ts_base = datetime.utcnow()
    return [
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

def events_for_traffic_speed_violation(zone):
    ts_base = datetime.utcnow()
    return [
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

if __name__ == "__main__":
    print("Simulando eventos individuales para forzar alertas principales en bloques separados. Ctrl+C para detener.")
    try:
        zone_idx = 0
        while True:
            zone_robbery = ZONES[(zone_idx + 0) % len(ZONES)]
            zone_accident = ZONES[(zone_idx + 1) % len(ZONES)]
            zone_fire = ZONES[(zone_idx + 2) % len(ZONES)]
            zone_speed = ZONES[(zone_idx + 3) % len(ZONES)]

            print(f"\n--- Enviando eventos individuales para posible robo en {zone_robbery} ---")
            for event in events_for_possible_robbery(zone_robbery):
                send_event(event)
                time.sleep(1)

            print(f"\n--- Enviando eventos individuales para accidente en {zone_accident} ---")
            for event in events_for_accident(zone_accident):
                send_event(event)
                time.sleep(1)

            print(f"\n--- Enviando eventos individuales para incendio en {zone_fire} ---")
            for event in events_for_fire(zone_fire):
                send_event(event)
                time.sleep(1)

            print(f"\n--- Enviando eventos individuales para exceso de velocidad en {zone_speed} ---")
            for event in events_for_traffic_speed_violation(zone_speed):
                send_event(event)
                time.sleep(1)

            zone_idx += 4
    except KeyboardInterrupt:
        print("Detenido por el usuario.")

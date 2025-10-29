import requests
import uuid
import random
import time
from datetime import datetime

URL = "http://localhost:8000/events/bulk"
EVENT_TYPES = [
    "panic.button", "sensor.lpr", "sensor.speed", "sensor.acoustic", "citizen.report"
]

def random_event():
    event_type = random.choice(EVENT_TYPES)
    # Zonas céntricas: zona 10, 11, 1, 7
    central_zones = [10, 11, 1, 7]
    all_zones = list(range(1, 16))
    # 60% probabilidad de zona céntrica, 40% de cualquier otra zona
    if random.random() < 0.6:
        zone = f"zone_{random.choice(central_zones)}"
    else:
        zone = f"zone_{random.choice(all_zones)}"
    event = {
        "event_version": "1.0",
        "event_type": event_type,
        "event_id": str(uuid.uuid4()),
        "producer": "python-sim",
        "source": "simulated",
        "correlation_id": str(uuid.uuid4()),
        "trace_id": str(uuid.uuid4()),
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "partition_key": event_type,
        "geo": {
            "zone": zone,
            "lat": round(random.uniform(14.60, 14.65), 5),
            "lon": round(random.uniform(-90.55, -90.50), 5)
        },
        "severity": random.choice(["info", "warning", "critical"]),
        "payload": {}
    }
    if event_type == "panic.button":
        event["payload"] = {
            "tipo_de_alerta": random.choice(["panico", "emergencia", "incendio"]),
            "identificador_dispositivo": f"BTN-{random.randint(1,99):03d}",
            "user_context": random.choice(["movil", "quiosco", "web"])
        }
    elif event_type == "sensor.lpr":
        event["payload"] = {
            "placa_vehicular": f"XYZ{random.randint(100,999)}",
            "velocidad_estimada": random.randint(60,120),
            "modelo_vehiculo": random.choice(["sedan", "camioneta", "moto"]),
            "color_vehiculo": random.choice(["rojo", "negro", "blanco"]),
            "ubicacion_sensor": f"sensor_{random.randint(1,20)}"
        }
    elif event_type == "sensor.speed":
        event["payload"] = {
            "velocidad_detectada": random.randint(40,120),
            "sensor_id": f"SPD-{random.randint(1,99):03d}",
            "direccion": random.choice(["NORTE", "SUR", "ESTE", "OESTE"])
        }
    elif event_type == "sensor.acoustic":
        event["payload"] = {
            "tipo_sonido_detectado": random.choice(["disparo", "explosion", "vidrio_roto"]),
            "nivel_decibeles": random.randint(80,130),
            "probabilidad_evento_critico": round(random.uniform(0.7, 1.0), 2)
        }
    elif event_type == "citizen.report":
        event["payload"] = {
            "tipo_evento": random.choice(["accidente", "incendio", "altercado"]),
            "mensaje_descriptivo": random.choice(["vehiculo volcado", "incendio en edificio", "pelea en calle"]),
            "ubicacion_aproximada": f"zona_{random.randint(1,10)}",
            "origen": random.choice(["usuario", "app", "punto_fisico"])
        }
    return event

if __name__ == "__main__":
    print("Enviando lotes de eventos (bulk). Ctrl+C para detener.")
    try:
        while True:
            batch = [random_event() for _ in range(10)]
            resp = requests.post(URL, json=batch)
            print(f"Status: {resp.status_code} | Batch size: {len(batch)}")
            time.sleep(1)
    except KeyboardInterrupt:
        print("Detenido por el usuario.")

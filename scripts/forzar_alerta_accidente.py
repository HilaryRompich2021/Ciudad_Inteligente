
import requests
import uuid
from datetime import datetime, timedelta

URL = "http://localhost:8000/events"

zone = "zone_1"
placa = "XYZ123"
correlation_id = str(uuid.uuid4())
trace_id = str(uuid.uuid4())
now = datetime.utcnow()

# Evento citizen.report (accidente)
event_citizen = {
    "event_version": "1.0",
    "event_type": "citizen.report",
    "event_id": str(uuid.uuid4()),
    "producer": "python-sim",
    "source": "simulated",
    "correlation_id": correlation_id,
    "trace_id": trace_id,
    "timestamp": now.isoformat() + "Z",
    "partition_key": "citizen.report",
    "geo": {"zone": zone, "lat": 14.63, "lon": -90.53},
    "severity": "critical",
    "payload": {
        "tipo_evento": "accidente",
        "descripcion": "Colisión entre vehículos"
    }
}

# Evento sensor.acoustic (explosion)
event_acoustic = {
    "event_version": "1.0",
    "event_type": "sensor.acoustic",
    "event_id": str(uuid.uuid4()),
    "producer": "python-sim",
    "source": "simulated",
    "correlation_id": correlation_id,
    "trace_id": trace_id,
    "timestamp": (now + timedelta(seconds=10)).isoformat() + "Z",
    "partition_key": "sensor.acoustic",
    "geo": {"zone": zone, "lat": 14.63, "lon": -90.53},
    "severity": "critical",
    "payload": {
        "tipo_sonido_detectado": "explosion",
        "nivel_decibeles": 120
    }
}

# Evento LPR con velocidad alta
event_lpr_high = {
    "event_version": "1.0",
    "event_type": "sensor.lpr",
    "event_id": str(uuid.uuid4()),
    "producer": "python-sim",
    "source": "simulated",
    "correlation_id": correlation_id,
    "trace_id": trace_id,
    "timestamp": (now + timedelta(seconds=20)).isoformat() + "Z",
    "partition_key": "sensor.lpr",
    "geo": {"zone": zone, "lat": 14.63, "lon": -90.53},
    "severity": "critical",
    "payload": {
        "placa_vehicular": placa,
        "velocidad_estimada": 100,
        "modelo_vehiculo": "sedan",
        "color_vehiculo": "negro",
        "ubicacion_sensor": "sensor_5"
    }
}

# Evento LPR con velocidad baja
event_lpr_low = {
    "event_version": "1.0",
    "event_type": "sensor.lpr",
    "event_id": str(uuid.uuid4()),
    "producer": "python-sim",
    "source": "simulated",
    "correlation_id": correlation_id,
    "trace_id": trace_id,
    "timestamp": (now + timedelta(seconds=30)).isoformat() + "Z",
    "partition_key": "sensor.lpr",
    "geo": {"zone": zone, "lat": 14.63, "lon": -90.53},
    "severity": "critical",
    "payload": {
        "placa_vehicular": placa,
        "velocidad_estimada": 30,
        "modelo_vehiculo": "sedan",
        "color_vehiculo": "negro",
        "ubicacion_sensor": "sensor_5"
    }
}

for event in [event_citizen, event_acoustic, event_lpr_high, event_lpr_low]:
    resp = requests.post(URL, json=event)
    print(f"Status: {resp.status_code} | Event ID: {event['event_id']} | Type: {event['event_type']}")
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.hooks.postgres_hook import PostgresHook
from elasticsearch import Elasticsearch, helpers
import os

# Configuración de conexiones
POSTGRES_CONN_ID = 'supabase_postgres' 
ES_HOST = os.getenv('ELASTICSEARCH_HOST', 'http://elasticsearch:9200')
ES_INDEX_EVENTS = 'events-001'
ES_INDEX_ALERTS = 'alerts-001'

def pg_to_es_etl():
    pg_hook = PostgresHook(postgres_conn_id=POSTGRES_CONN_ID)
    es = Elasticsearch(ES_HOST)

    # Eventos
    events = pg_hook.get_records("SELECT event_id, event_type, ts_utc, geo_lat, geo_lon, severity, payload FROM events WHERE ts_utc > NOW() - INTERVAL '1 HOUR'")
    actions_events = []
    for e in events:
        doc = {
            '_index': ES_INDEX_EVENTS,
            '_id': e[0],
            'event_id': e[0],
            'event_type': e[1],
            'timestamp': e[2].isoformat() if hasattr(e[2], 'isoformat') else str(e[2]),
            'geo': {'lat': e[3], 'lon': e[4]},
            'severity': e[5],
            'payload': e[6]
        }
        actions_events.append(doc)
    if actions_events:
        helpers.bulk(es, actions_events)

    # Alertas
    alerts = pg_hook.get_records("SELECT alert_id, correlation_id, type, score, zone, window_start, window_end, evidence, created_at FROM alerts WHERE created_at > NOW() - INTERVAL '1 HOUR'")
    actions_alerts = []
    for a in alerts:
        doc = {
            '_index': ES_INDEX_ALERTS,
            '_id': a[0],
            'alert_id': a[0],
            'correlation_id': a[1],
            'type': a[2],
            'score': a[3],
            'zone': a[4],
            'window': {
                'start': a[5].isoformat() if hasattr(a[5], 'isoformat') and a[5] else str(a[5]) if a[5] else None,
                'end': a[6].isoformat() if hasattr(a[6], 'isoformat') and a[6] else str(a[6]) if a[6] else None
            },
            'evidence': a[7],
            'created_at': a[8].isoformat() if hasattr(a[8], 'isoformat') else str(a[8])
        }
        actions_alerts.append(doc)
    if actions_alerts:
        helpers.bulk(es, actions_alerts)

# --- Airflow DAG definition ---
default_args = {
    'owner': 'admin',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    dag_id='pg_to_es_etl_dag',
    default_args=default_args,
    description='ETL de eventos y alertas desde PostgreSQL a Elasticsearch',
    schedule_interval='@hourly',
    start_date=datetime(2025, 10, 21),
    catchup=False,
    tags=['etl', 'elasticsearch', 'postgresql'],
) as dag:
    etl_task = PythonOperator(
        task_id='pg_to_es_etl',
        python_callable=pg_to_es_etl,
    )

from datetime import datetime, timedelta
from airflow import DAG
from airflow.providers.postgres.operators.postgres import PostgresOperator

default_args = {
    'owner': 'admin',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=2),
}

with DAG(
    dag_id='etl_analytics_dag',
    default_args=default_args,
    description='Lectura y conteo de alertas por zona desde Azure PostgreSQL',
    schedule_interval='*/15 * * * *',  # cada 15 min
    start_date=datetime(2025, 10, 21),
    catchup=False,
    tags=['etl', 'analytics', 'read-only'],
) as dag:

    read_alerts = PostgresOperator(
        task_id='read_alerts',
        postgres_conn_id='postgres_default',
        sql="""
            SELECT type AS event_type, zone, COUNT(*) AS alert_count
            FROM alerts
            WHERE created_at > NOW() - INTERVAL '1 HOUR'
            GROUP BY type, zone;
        """,
    )

    read_alerts

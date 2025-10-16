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
    description='ETL programado: genera métricas de alertas por zona y tipo de evento',
    schedule_interval='*/10 * * * *',  # cada 10 minutos
    start_date=datetime(2025, 10, 10),
    catchup=False,
    tags=['etl', 'analytics', 'ciudad-inteligente'],
) as dag:

    # Limpia registros antiguos (opcional)
    clear_old_data = PostgresOperator(
        task_id='clear_old_data',
        postgres_conn_id='postgres_default',
        sql="""
            DELETE FROM analytics_results
            WHERE generated_at < NOW() - INTERVAL '1 day';
        """,
    )

    # Inserta nuevas métricas agrupadas
    aggregate_alerts = PostgresOperator(
        task_id='aggregate_alerts',
        postgres_conn_id='postgres_default',
        sql="""
            INSERT INTO analytics_results (event_type, zone, alert_count)
            SELECT type AS event_type, zone, COUNT(*) AS alert_count
            FROM alerts
            GROUP BY type, zone;
        """,
    )

    clear_old_data >> aggregate_alerts

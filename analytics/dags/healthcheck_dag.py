from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.bash import BashOperator

default_args = {
    'owner': 'admin',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=1),
}

with DAG(
    'healthcheck_dag',
    default_args=default_args,
    description='Verifica la disponibilidad de los microservicios de Ciudad Inteligente',
    schedule_interval='*/2 * * * *',  # cada 2 minutos
    start_date=datetime(2025, 10, 10),
    catchup=False,
    tags=['monitoring', 'ciudad-inteligente'],
) as dag:

    check_correlator = BashOperator(
        task_id='ping_correlator',
        bash_command='curl -s -o /dev/null -w "%{http_code}" http://correlator-correlator:8080 || echo "error"',
    )

    check_kafka = BashOperator(
        task_id='ping_kafka',
        bash_command='nc -zv platform-kafka-1 9092 || echo "Kafka no responde"',
    )

    check_correlator >> check_kafka

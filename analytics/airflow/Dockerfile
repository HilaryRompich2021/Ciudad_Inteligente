FROM apache/airflow:2.8.1
# Copy requirements as root (file system op)
USER root
COPY requirements.txt /tmp/requirements.txt
# Install Python packages as the airflow user using --user (recommended for official image)
USER airflow
# Install using Airflow constraints for 2.8.1 / Python 3.8 to avoid provider/version mismatches
RUN pip install --no-cache-dir --user -r /tmp/requirements.txt -c "https://raw.githubusercontent.com/apache/airflow/constraints-2.8.1/constraints-3.8.txt"


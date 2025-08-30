
#!/usr/bin/env bash
# Script para crear los topics requeridos en Kafka

# Configuración
KAFKA_CONTAINER=$(docker ps --filter ancestor=bitnami/kafka:3.7 --format "{{.Names}}" | head -n 1)
KAFKA_BROKER="kafka:9092"

# Crear topics 
docker exec -i $KAFKA_CONTAINER kafka-topics.sh --create --if-not-exists --topic t01.events.standardized --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=259200000
docker exec -i $KAFKA_CONTAINER kafka-topics.sh --create --if-not-exists --topic t01.correlated.alerts --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=604800000

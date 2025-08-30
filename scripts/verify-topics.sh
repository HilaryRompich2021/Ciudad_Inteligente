
#!/usr/bin/env bash
# Script para listar los topics existentes en Kafka

KAFKA_CONTAINER=$(docker ps --filter ancestor=bitnami/kafka:3.7 --format "{{.Names}}" | head -n 1)
KAFKA_BROKER="kafka:9092"

docker exec -i $KAFKA_CONTAINER kafka-topics.sh --list --bootstrap-server $KAFKA_BROKER

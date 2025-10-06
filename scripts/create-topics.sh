#!/bin/bash
# Script para crear los topics requeridos en Kafka 

KAFKA_BROKER="kafka:9092"

# Topics según especificación de la guía (sección 4)
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic events.standardized --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=259200000
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic correlated.alerts --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=604800000

# Topic opcional para DLQ (Dead Letter Queue)
#/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic events.dlq --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=259200000
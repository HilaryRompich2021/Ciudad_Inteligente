#!/bin/bash
# Script para crear los topics requeridos en Kafka

KAFKA_BROKER="kafka:9092"

/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic t01.events.standardized --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=259200000
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic t01.correlated.alerts --bootstrap-server $KAFKA_BROKER --partitions 3 --replication-factor 1 --config retention.ms=604800000

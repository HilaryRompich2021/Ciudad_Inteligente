#!/bin/sh

BROKER="kafka:9092"
TRIES=30
SLEEP=3
i=0

echo "Waiting for Kafka at $BROKER..."
while ! kafka-topics --bootstrap-server "$BROKER" --list >/dev/null 2>&1; do
  i=$((i+1))
  if [ "$i" -ge "$TRIES" ]; then
    echo "Kafka did not respond after $((TRIES * SLEEP)) seconds" >&2
    exit 1
  fi
  sleep $SLEEP
done

echo "Kafka available — creating topics"

kafka-topics --bootstrap-server "$BROKER" --create --if-not-exists --topic events.standardized --partitions 3 --replication-factor 1 --config retention.ms=259200000
kafka-topics --bootstrap-server "$BROKER" --create --if-not-exists --topic correlated.alerts --partitions 3 --replication-factor 1 --config retention.ms=604800000

exit 0
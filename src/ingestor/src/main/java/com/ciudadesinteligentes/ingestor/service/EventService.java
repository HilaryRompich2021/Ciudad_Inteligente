package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;

import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class EventService {
    private final KafkaTemplate<String, CanonicalEvent> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TOPIC = "t01.events.standardized";
    private final CanonicalEventValidator validator;

    public EventService(KafkaTemplate<String, CanonicalEvent> kafkaTemplate) throws IOException {
        this.kafkaTemplate = kafkaTemplate;
        // Cargar el esquema JSON desde recursos
        ClassPathResource resource = new ClassPathResource("canonical-event-schema.json");
        String schemaJson = Files.readString(Paths.get(resource.getURI()));
        this.validator = new CanonicalEventValidator(schemaJson);
    }

    public void processAndPublish(CanonicalEvent event) throws Exception {
        // Validar el evento
        String eventJson = objectMapper.writeValueAsString(event);
        validator.validate(eventJson); // ACTIVADO: validación estricta según el esquema
        
        enrichEvent(event);
        
        // Log para diagnosticar
        System.out.println("Publishing event to Kafka topic: " + TOPIC);
        System.out.println("Event ID: " + event.getEventId());
        System.out.println("Event Type: " + event.getEventType());
        
        // Usar eventId como clave si partitionKey es null
        String key = event.getPartitionKey() != null ? event.getPartitionKey() : event.getEventId();
        
        kafkaTemplate.send(TOPIC, key, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("Successfully published event to Kafka: " + result.getRecordMetadata());
                } else {
                    System.err.println("Failed to publish event to Kafka: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
    }

    private void enrichEvent(CanonicalEvent event) {
        if (event.getTimestamp() == null || event.getTimestamp().isEmpty()) {
            event.setTimestamp(Instant.now().toString());
        }
        if (event.getTraceId() == null || event.getTraceId().isEmpty()) {
            event.setTraceId(UUID.randomUUID().toString());
        }
        if (event.getCorrelationId() == null || event.getCorrelationId().isEmpty()) {
            event.setCorrelationId(UUID.randomUUID().toString());
        }
        
    }
}

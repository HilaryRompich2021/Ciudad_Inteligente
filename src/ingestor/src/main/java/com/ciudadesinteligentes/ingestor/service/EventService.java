package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.ciudadesinteligentes.ingestor.model.BulkProcessResult;
import com.ciudadesinteligentes.ingestor.model.EventEntity;
import com.ciudadesinteligentes.ingestor.repository.EventRepository;
import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.time.OffsetDateTime;

@Service
public class EventService {
    private final KafkaTemplate<String, CanonicalEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CanonicalEventValidator validator;
    private final EventEnricher eventEnricher;
    private final EventRepository eventRepository;
    private static final String TOPIC = "events.standardized";

    @Autowired
    public EventService(KafkaTemplate<String, CanonicalEvent> kafkaTemplate,
                       ObjectMapper objectMapper,
                       CanonicalEventValidator validator,
                       EventEnricher eventEnricher,
                       EventRepository eventRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.eventEnricher = eventEnricher;
        this.eventRepository = eventRepository;
    }

    // lógica para procesar, publicar y guardar un evento
    public void processAndPublish(CanonicalEvent event) throws Exception {
        // Enriquecer campos automáticos que faltan
        eventEnricher.enrichEventIfNeeded(event);

        // Validar el evento
        String eventJson = objectMapper.writeValueAsString(event);
        validator.validate(eventJson); // Validación estricta según el esquema

        // VALIDACIÓN DE DUPLICADOS: verificar si el event_id ya existe
        UUID eventId = UUID.fromString(event.getEventId());
        if (eventRepository.existsById(eventId)) {
            throw new IllegalArgumentException("Duplicate event_id rejected: " + eventId + " - Event already processed");
        }

        // Persistir el evento en la base de datos ANTES de publicar a Kafka
        // (Evita inconsistencias: si falla BD, no publicamos a Kafka)
        EventEntity entity = mapToEntity(event);
        eventRepository.save(entity);

        // Logs informativos
        System.out.println("Event persisted to database: " + event.getEventId());
        System.out.println("Publishing event to Kafka topic: " + TOPIC);
        System.out.println("Event Type: " + event.getEventType());

        // Usar eventId como clave si partitionKey es null
        String key = event.getPartitionKey() != null ? event.getPartitionKey() : event.getEventId();

        // Publicar a Kafka SOLO si la persistencia en BD fue exitosa
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

    // Mapper manual de CanonicalEvent a EventEntity
    private EventEntity mapToEntity(CanonicalEvent event) {
        EventEntity entity = new EventEntity();
        // Convertir String a UUID
        entity.setEventId(event.getEventId() != null ? java.util.UUID.fromString(event.getEventId()) : null);
        entity.setEventType(event.getEventType());
        entity.setEventVersion(event.getEventVersion());
        entity.setProducer(event.getProducer());
        entity.setSource(event.getSource());
        entity.setCorrelationId(event.getCorrelationId() != null ? java.util.UUID.fromString(event.getCorrelationId()) : null);
        entity.setTraceId(event.getTraceId() != null ? java.util.UUID.fromString(event.getTraceId()) : null);
        entity.setPartitionKey(event.getPartitionKey());
        // Convertir String a OffsetDateTime
        entity.setTsUtc(event.getTimestamp() != null ? java.time.OffsetDateTime.parse(event.getTimestamp()) : java.time.OffsetDateTime.now());
        entity.setZone(event.getGeo() != null ? event.getGeo().getZone() : null);
        entity.setGeoLat(event.getGeo() != null ? event.getGeo().getLat() : null);
        entity.setGeoLon(event.getGeo() != null ? event.getGeo().getLon() : null);
        entity.setSeverity(event.getSeverity());
        // Asigna el payload como JsonNode para jsonb
        try {
            entity.setPayload(objectMapper.valueToTree(event.getData()));
        } catch (Exception e) {
            entity.setPayload(objectMapper.createObjectNode());
        }
        return entity;
    }

    // logica para procesar eventos en bulk
    public BulkProcessResult processAndPublishBulk(List<CanonicalEvent> events) throws Exception {
        List<String> successfulEvents = new ArrayList<>();
        List<BulkProcessResult.ProcessingError> failedEvents = new ArrayList<>();
        
        for (int i = 0; i < events.size(); i++) {  //iteramos sobre cada evento
            try {
                CanonicalEvent event = events.get(i); //obtenemos el evento actual
                processAndPublish(event); //procesamos y publicamos el evento

                successfulEvents.add(event.getEventId()); // Agregamos el event_id directamente, ya que es obligatorio

            } catch (Exception e) {
                String eventId = events.get(i).getEventId() != null ? events.get(i).getEventId() : "unknown"; 
                failedEvents.add(new BulkProcessResult.ProcessingError(i, eventId, e.getMessage())); //registramos el error con índice, event_id y mensaje
            }
        }
        
        System.out.println("Bulk processing completed: " + successfulEvents.size() + "/" + events.size() + " successful");
        
        return new BulkProcessResult(events.size(), successfulEvents, failedEvents); //retornamos el resultado del procesamiento
    }
}

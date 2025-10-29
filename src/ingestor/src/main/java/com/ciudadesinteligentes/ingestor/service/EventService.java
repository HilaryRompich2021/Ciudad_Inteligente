package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.ciudadesinteligentes.ingestor.model.BulkProcessResult;
import com.ciudadesinteligentes.ingestor.model.EventEntity;
import com.ciudadesinteligentes.ingestor.repository.EventRepository;
import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
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

           // Configurar ObjectMapper para respetar el orden de los campos
           this.objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
           this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);
    }

    /**
     * Procesa, valida, persiste y publica un evento individual.
     */
    public void processAndPublish(CanonicalEvent event) throws Exception {
        // Enriquecer campos automáticos que faltan
        eventEnricher.enrichEventIfNeeded(event);

        // Validar el evento contra el esquema
        String eventJson = objectMapper.writeValueAsString(event);
        validator.validate(eventJson);

        // VALIDACIÓN DE DUPLICADOS: verificar si el event_id ya existe
        UUID eventId = event.getEventId();
        if (eventRepository.existsById(eventId)) {
            throw new IllegalArgumentException("Duplicate event_id rejected: " + eventId + " - Event already processed");
        }

        // Mapear y guardar en base de datos
        EventEntity entity = mapToEntity(event);
        eventRepository.save(entity);

           

        System.out.println(" Event persisted to database: " + event.getEventId());
        System.out.println(" Publishing event to Kafka topic: " + TOPIC);
        System.out.println(" Event Type: " + event.getEventType());

        // Usar partitionKey como clave para Kafka
         String key = event.getPartitionKey();

        // Publicar a Kafka SOLO si la persistencia fue exitosa
        kafkaTemplate.send(TOPIC, key, event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    System.out.println("Successfully published event to Kafka: " + result.getRecordMetadata());
                } else {
                    System.err.println(" Failed to publish event to Kafka: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
    }

    /**
     * Mapea un CanonicalEvent a EventEntity (para persistencia).
     */
    private EventEntity mapToEntity(CanonicalEvent event) {
        EventEntity entity = new EventEntity();
    entity.setEventId(event.getEventId()); // Usar UUID directamente
        entity.setEventType(event.getEventType());
        entity.setEventVersion(event.getEventVersion());
        entity.setProducer(event.getProducer());
        entity.setSource(event.getSource());
        entity.setCorrelationId(event.getCorrelationId() != null ? UUID.fromString(event.getCorrelationId()) : null);
        entity.setTraceId(event.getTraceId() != null ? UUID.fromString(event.getTraceId()) : null);
        entity.setPartitionKey(event.getPartitionKey());
        entity.setTsUtc(event.getTimestamp() != null ?
                OffsetDateTime.parse(event.getTimestamp()) : OffsetDateTime.now());
        if (event.getGeo() != null) {
            entity.setZone(event.getGeo().getZone());
            entity.setGeoLat(event.getGeo().getLat());
            entity.setGeoLon(event.getGeo().getLon());
        }
        entity.setSeverity(event.getSeverity());

        // Convertir payload (objeto JSON dinámico) a JsonNode
        try {
             entity.setPayload(objectMapper.valueToTree(event.getData()));
        } catch (Exception e) {
            entity.setPayload(objectMapper.createObjectNode());
        }

        return entity;
    }

    /**
     * Procesa múltiples eventos en batch.
     */
    public BulkProcessResult processAndPublishBulk(List<CanonicalEvent> events) {
    List<UUID> successfulEvents = new ArrayList<>();
        List<BulkProcessResult.ProcessingError> failedEvents = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            try {
                CanonicalEvent event = events.get(i);
                processAndPublish(event);
                UUID eventId = event.getEventId();
                successfulEvents.add(eventId);
            } catch (Exception e) {
                UUID eventId = events.get(i).getEventId();
                failedEvents.add(new BulkProcessResult.ProcessingError(i, eventId, e.getMessage()));
            }
        }

        System.out.println("Bulk processing completed: " + successfulEvents.size() + "/" + events.size() + " successful");

        return new BulkProcessResult(events.size(), successfulEvents, failedEvents);
    }
}

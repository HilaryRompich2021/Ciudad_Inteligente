package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.ciudadesinteligentes.ingestor.model.BulkProcessResult;
import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class EventService {
    private final KafkaTemplate<String, CanonicalEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CanonicalEventValidator validator;
    private final EventEnricher eventEnricher;
    private static final String TOPIC = "t01.events.standardized";

    public EventService(KafkaTemplate<String, CanonicalEvent> kafkaTemplate, 
                       ObjectMapper objectMapper,
                       CanonicalEventValidator validator,
                       EventEnricher eventEnricher) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.eventEnricher = eventEnricher;
    }

    // logica para procesar y publicar un evento
    public void processAndPublish(CanonicalEvent event) throws Exception {

        // Primero enriquecemos solo los campos automáticos que faltan
        eventEnricher.enrichEventIfNeeded(event);
        
        // Luego validamos el evento 
        String eventJson = objectMapper.writeValueAsString(event);
        validator.validate(eventJson); // ACTIVADO: validación estricta según el esquema
        
        // Logs informativos
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
                    ex.printStackTrace(); // Imprime la traza completa del error
                }
            });
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

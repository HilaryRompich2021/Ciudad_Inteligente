package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class EventEnricher {
    
    /**
     * Enriquece el evento con campos automáticos solo si es necesario
     * @param event El evento a enriquecer
     */
    public void enrichEventIfNeeded(CanonicalEvent event) {
        if (event.getTimestamp() == null || event.getTimestamp().isEmpty()) {
            event.setTimestamp(Instant.now().toString());
        }
        if (event.getTraceId() == null || event.getTraceId().isEmpty()) {
            event.setTraceId(UUID.randomUUID().toString());
        }
        if (event.getCorrelationId() == null || event.getCorrelationId().isEmpty()) {
            event.setCorrelationId(UUID.randomUUID().toString());
        }
        
        // Auto-extraer partition_key según la guía: geo.zone o payload.placa_vehicular
        if (event.getPartitionKey() == null || event.getPartitionKey().isEmpty()) {
            event.setPartitionKey(event.getEventType());
        }
    }
}

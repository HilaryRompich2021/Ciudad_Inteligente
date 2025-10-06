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
            String extractedKey = extractPartitionKey(event);
            if (extractedKey != null && !extractedKey.isEmpty()) {
                event.setPartitionKey(extractedKey);
            }
        }
    }
    
    /**
     * Extrae partition_key automáticamente según Final-Project-Guide.md:
     * Prioridad: geo.zone > payload.placa_vehicular > null
     */
    private String extractPartitionKey(CanonicalEvent event) {
        // Prioridad 1: geo.zone
        if (event.getGeo() != null && event.getGeo().getZone() != null && !event.getGeo().getZone().isEmpty()) {
            return event.getGeo().getZone();
        }
        
        // Prioridad 2: payload.placa_vehicular (para eventos LPR)
        if (event.getData() != null) {
            try {
                JsonNode payload = (JsonNode) event.getData();
                if (payload.has("placa_vehicular")) {
                    String placa = payload.get("placa_vehicular").asText();
                    if (placa != null && !placa.isEmpty()) {
                        return placa;
                    }
                }
            } catch (Exception e) {
                // Si no se puede extraer de payload, continuar
            }
        }
        
        return null; // No se pudo extraer partition_key
    }
}

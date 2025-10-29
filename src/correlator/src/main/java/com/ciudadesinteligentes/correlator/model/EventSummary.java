package com.ciudadesinteligentes.correlator.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de evento para almacenamiento temporal en Redis
 * Usado para correlación en ventanas de tiempo
 */
@Data
@NoArgsConstructor
public class EventSummary {
    private String event_type;
    private String timestamp;
    private Map<String, Object> payload;
    private UUID event_id;

    public EventSummary(String event_type, String timestamp, Map<String, Object> payload, UUID event_id) {
        this.event_type = event_type;
        this.timestamp = timestamp;
        this.payload = payload;
        this.event_id = event_id;
    }
}
package com.ciudadesinteligentes.ingestor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
public class CanonicalEvent {
    // Campos obligatorios mínimos
    @NotNull
    @JsonProperty("event_id")
    private String eventId;

    @NotNull
    @JsonProperty("event_type")
    private String eventType;

    @NotNull
    @JsonProperty("source")
    private String source;

    // Campos opcionales que serán enriquecidos automáticamente si no vienen
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @JsonProperty("trace_id")
    private String traceId;
    
    // Campo opcional para datos específicos del evento
    @JsonProperty("payload")
    private Map<String, Object> data;
    
    // Campos opcionales adicionales
    @JsonProperty("partition_key")
    private String partitionKey;
    
    @JsonProperty("geo")
    private Geo geo;
    
    @JsonProperty("severity")
    private String severity;
    
    @JsonProperty("producer")
    private String producer;
    
    @JsonProperty("event_version")
    private String eventVersion;

    @Data
    public static class Geo {
        @JsonProperty("lat")
        private Double lat;
        
        @JsonProperty("lon")
        private Double lon;
        
        @JsonProperty("zone")
        private String zone;
    }
}

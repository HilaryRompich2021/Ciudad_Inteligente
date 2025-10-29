
package com.ciudadesinteligentes.ingestor.model;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
public class CanonicalEvent {
    // Campos obligatorios mínimos
    @NotNull
    @JsonProperty("event_id")
    private UUID eventId;

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
    
    // Campo obligatorio para datos específicos del evento
    @NotNull
    @JsonProperty("payload")
    private Map<String, Object> data;
    
    // Campos opcionales adicionales
    @JsonProperty("partition_key")
    private String partitionKey;
    
    @NotNull
    @JsonProperty("geo")
    private Geo geo;
    
    @NotNull
    @JsonProperty("severity")
    private String severity;
    
    @NotNull
    @JsonProperty("producer")
    private String producer;
    
    @NotNull
    @JsonProperty("event_version")
    private String eventVersion;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Geo {
        @JsonProperty("lat")
        private Double lat;
        
        @JsonProperty("lon")
        private Double lon;
        
        @NotNull
        @JsonProperty("zone")
        private String zone;
    }
}


package com.ciudadesinteligentes.ingestor.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "events")
@Data
public class EventEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private String eventVersion;

    @Column(name = "producer", nullable = false)
    private String producer;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "trace_id")
    private UUID traceId;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "ts_utc", nullable = false)
    private OffsetDateTime tsUtc;

    @Column(name = "zone")
    private String zone;

    @Column(name = "geo_lat")
    private Double geoLat;

    @Column(name = "geo_lon")
    private Double geoLon;

    @Column(name = "severity")
    private String severity;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;
}

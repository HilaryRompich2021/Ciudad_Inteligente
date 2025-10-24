package com.ciudadesinteligentes.correlator.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "alerts")
@Data
public class AlertEntity {
    @Id
    @Column(name = "alert_id", nullable = false)
    private String alertId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "score")
    private Integer score;

    @Column(name = "zone")
    private String zone;

    @Column(name = "window_start")
    private OffsetDateTime windowStart;

    @Column(name = "window_end")
    private OffsetDateTime windowEnd;

    /*@Column(name = "evidence", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;*/
    @Column(name = "evidence", columnDefinition = "jsonb")
    private String evidence;


    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

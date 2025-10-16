package com.ciudadesinteligentes.correlator.service;

import com.ciudadesinteligentes.correlator.model.AlertEntity;
import com.ciudadesinteligentes.correlator.model.CorrelatedAlert;
import com.ciudadesinteligentes.correlator.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

@Service
public class AlertService {

    
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlertService(AlertRepository alertRepository, ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    public AlertEntity saveAlert(CorrelatedAlert alert) {
        AlertEntity entity = mapToEntity(alert);
        return alertRepository.save(entity);
    }

    // Mapper manual de CorrelatedAlert a AlertEntity
    private AlertEntity mapToEntity(CorrelatedAlert alert) {
        AlertEntity entity = new AlertEntity();
        // Convertir String a UUID
        entity.setAlertId(alert.alert_id != null ? java.util.UUID.fromString(alert.alert_id) : null);
        entity.setCorrelationId(alert.correlation_id != null ? java.util.UUID.fromString(alert.correlation_id) : null);
        entity.setType(alert.type);
        entity.setScore(alert.score);
        entity.setZone(alert.zone);
        // Convertir String a OffsetDateTime para window
        entity.setWindowStart(alert.window != null && alert.window.containsKey("start") && alert.window.get("start") != null ? java.time.OffsetDateTime.parse(alert.window.get("start")) : null);
        entity.setWindowEnd(alert.window != null && alert.window.containsKey("end") && alert.window.get("end") != null ? java.time.OffsetDateTime.parse(alert.window.get("end")) : null);
        try {
            entity.setEvidence(objectMapper.writeValueAsString(alert.evidence));
        } catch (Exception e) {
            entity.setEvidence("[]");
        }
        // Convertir String a OffsetDateTime para created_at
        entity.setCreatedAt(alert.created_at != null ? java.time.OffsetDateTime.parse(alert.created_at) : java.time.OffsetDateTime.now());
        return entity;
    }
}

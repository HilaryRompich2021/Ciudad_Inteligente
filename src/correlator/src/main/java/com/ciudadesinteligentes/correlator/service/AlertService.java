package com.ciudadesinteligentes.correlator.service;

import com.ciudadesinteligentes.correlator.model.AlertEntity;
import com.ciudadesinteligentes.correlator.model.CorrelatedAlert;
import com.ciudadesinteligentes.correlator.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  
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

    @Transactional  //  fuerza commit en cada inserción
    public AlertEntity saveAlert(CorrelatedAlert alert) {
        System.out.println(">>> [DEBUG] Intentando guardar alerta con ID: " + alert.alert_id);
        AlertEntity entity = mapToEntity(alert);
        System.out.println(">>> [DEBUG] AlertEntity construida: " + entity);
        return alertRepository.save(entity);
    }

    private AlertEntity mapToEntity(CorrelatedAlert alert) {
        AlertEntity entity = new AlertEntity();

        //  Mejor usar String, no UUID (para coincidir con DB)
        entity.setAlertId(alert.alert_id);
        entity.setCorrelationId(alert.correlation_id);

        entity.setType(alert.type);
        entity.setScore((int) Math.round(alert.score));
        entity.setZone(alert.zone);

        entity.setWindowStart(
            alert.window != null && alert.window.get("start") != null
                ? OffsetDateTime.parse(alert.window.get("start"))
                : null
        );
        entity.setWindowEnd(
            alert.window != null && alert.window.get("end") != null
                ? OffsetDateTime.parse(alert.window.get("end"))
                : null
        );

        try {
            entity.setEvidence(objectMapper.writeValueAsString(alert.evidence));
        } catch (Exception e) {
            entity.setEvidence("[]");
        }

        entity.setCreatedAt(
            alert.created_at != null
                ? OffsetDateTime.parse(alert.created_at)
                : OffsetDateTime.now()
        );

        return entity;
    }
}

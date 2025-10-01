package com.ciudadesinteligentes.correlator.controller;

import com.ciudadesinteligentes.correlator.model.CorrelatedAlert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// import com.ciudadesinteligentes.correlator.model.AlertEntity;
// import com.ciudadesinteligentes.correlator.repository.AlertRepository;

import java.util.ArrayList;
import java.util.List;

@RestController
public class ManagementController {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // @Autowired
    // private AlertRepository alertRepository;

    // Endpoint para consultar alertas activas por zona
    @GetMapping("/alerts/active")
    public List<CorrelatedAlert> getActiveAlerts(@RequestParam String zone) {
        String key = "corr:zone:" + zone;
        List<Object> summaries = redisTemplate.opsForList().range(key, 0, -1);
        List<CorrelatedAlert> alerts = new ArrayList<>();
        for (Object obj : summaries) {
            if (obj instanceof CorrelatedAlert) {
                alerts.add((CorrelatedAlert) obj);
            }
        }
        return alerts;
    }

    // Endpoint para consultar alertas persistidas en la base de datos por zona
    /*
    @GetMapping("/alerts/db")
    public List<AlertEntity> getAlertsFromDb(@RequestParam(required = false) String zone) {
        if (zone != null) {
            return alertRepository.findByZone(zone);
        }
        return alertRepository.findAll();
    }
    */

    // Endpoint de health check
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    // Endpoint de métricas básicas (ejemplo)
    @GetMapping("/metrics")
    public String metrics() {
        // Aquí podrías agregar lógica real de métricas
        return "{\"alerts\":0,\"events\":0}";
    }
}

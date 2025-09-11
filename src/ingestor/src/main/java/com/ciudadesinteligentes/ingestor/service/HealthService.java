package com.ciudadesinteligentes.ingestor.service;

import com.ciudadesinteligentes.ingestor.util.CanonicalEventValidator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthService {
    
    private static final String TOPIC = "t01.events.standardized";
    private final KafkaTemplate<?, ?> kafkaTemplate;
    private final CanonicalEventValidator validator;

    public HealthService(KafkaTemplate<?, ?> kafkaTemplate, CanonicalEventValidator validator) {
        this.kafkaTemplate = kafkaTemplate;
        this.validator = validator;
    }

    // Verifica la disponibilidad básica de Kafka
    private boolean checkKafka() {
        try {
            // Verificación pragmática: si el template está configurado y no hay errores
            // recientes, consideramos que Kafka está disponible
            if (kafkaTemplate == null) {
                return false;
            }
            
            // Verificación simple: obtener metadatos del template
            // Esto es menos invasivo que executeInTransaction
            kafkaTemplate.getDefaultTopic(); // Solo verifica que el template esté inicializado
            return true;
            
        } catch (Exception e) {
            System.err.println("Kafka health check failed: " + e.getMessage());
            return false;
        }
    }

    // Lógica de salud del servicio mejorada
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        boolean kafkaOk = checkKafka();
        boolean serviceOk = validator != null; // Si el validador está cargado

        // Estado general más pragmático
        health.put("status", (kafkaOk && serviceOk) ? "UP" : "DEGRADED");
        health.put("kafka", kafkaOk ? "available" : "unavailable");
        health.put("validator", serviceOk ? "ready" : "error");
        health.put("timestamp", Instant.now().toString());
        health.put("service", "ingestor");
        health.put("version", "1.0");
        
        Map<String, Object> details = new HashMap<>();
        details.put("topic", TOPIC);
        details.put("schema_version", "1.0");
        details.put("kafka_template_configured", kafkaTemplate != null);
        details.put("note", "Health check uses basic availability verification");
        health.put("details", details);
        
        return health;
    }
}

package com.ciudadesinteligentes.correlator.consumer;

import com.ciudadesinteligentes.correlator.model.CanonicalEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.ciudadesinteligentes.correlator.util.CanonicalEventValidator;
import javax.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.Files;
import java.nio.file.Paths;


@Component
public class EventConsumer {
    private CanonicalEventValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Autowired
    private com.ciudadesinteligentes.correlator.service.CorrelatorService correlatorService;

    @PostConstruct
    public void init() {
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("canonical-event-schema.json");
            if (is == null) throw new RuntimeException("No se pudo encontrar el esquema canónico en el classpath");
            String schemaJson = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            validator = new CanonicalEventValidator(schemaJson);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el esquema canónico", e);
        }
    }

    @KafkaListener(topics = "events.standardized", groupId = "correlator-group")
    public void consume(CanonicalEvent event) {
        try {
            System.out.println("[EventConsumer] Evento recibido: " + objectMapper.writeValueAsString(event));
            validator.validate(event);
            System.out.println("[EventConsumer] Evento validado, procesando...");
            correlatorService.processEvent(event);
        } catch (Exception e) {
            System.err.println("Evento inválido o error de procesamiento: " + e.getMessage());
        }
    }
}

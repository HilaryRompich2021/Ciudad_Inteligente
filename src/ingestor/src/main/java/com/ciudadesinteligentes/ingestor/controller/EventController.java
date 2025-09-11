package com.ciudadesinteligentes.ingestor.controller;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.ciudadesinteligentes.ingestor.model.BulkProcessResult;
import com.ciudadesinteligentes.ingestor.service.EventService;
import com.ciudadesinteligentes.ingestor.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.time.Instant;

@RestController
@RequestMapping("/events")
@Validated
public class EventController {

    private final EventService eventService;
    private final HealthService healthService;
    private final String canonicalEventSchema;

    public EventController(EventService eventService, HealthService healthService, String canonicalEventSchema) {
        this.eventService = eventService;
        this.healthService = healthService;
        this.canonicalEventSchema = canonicalEventSchema;
    }

    @PostMapping
    public ResponseEntity<?> ingestEvent(@Valid @RequestBody CanonicalEvent event) {
        try {
            eventService.processAndPublish(event);
            
            // Crear respuesta exitosa detallada
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Event processed and published successfully");
            response.put("event_id", event.getEventId());
            response.put("event_type", event.getEventType());
            response.put("partition_key", event.getPartitionKey());
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            // Crear respuesta de error más detallada
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process event");
            errorResponse.put("error_details", e.getMessage());
            errorResponse.put("event_id", event != null ? event.getEventId() : "unknown");
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> ingestBulkEvents(@RequestBody List<CanonicalEvent> events) {
        try {
            BulkProcessResult result = eventService.processAndPublishBulk(events);
            
            // Construir respuesta HTTP detallada en el controlador
            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("message", "Bulk processing completed");
            response.put("total", result.getTotal());
            response.put("successful", result.getSuccessfulCount());
            response.put("failed", result.getFailedCount());
            response.put("successful_events", result.getSuccessfulEvents());
            
            // Formatear errores para la respuesta HTTP
            List<Map<String, Object>> formattedErrors = new ArrayList<>();
            for (BulkProcessResult.ProcessingError error : result.getFailedEvents()) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("index", error.getIndex());
                errorMap.put("event_id", error.getEventId());
                errorMap.put("error", error.getError());
                formattedErrors.add(errorMap);
            }
            response.put("failed_events", formattedErrors);
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            // Respuesta de error consistente con /events
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process bulk events");
            errorResponse.put("error_details", e.getMessage());
            errorResponse.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            Map<String, Object> healthStatus = healthService.getHealthStatus();
            return ResponseEntity.ok(healthStatus);
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Service unavailable: " + e.getMessage());
        }
    }

    @GetMapping("/schema")
    public ResponseEntity<?> getSchema() {
        try {
            return ResponseEntity.ok().header("Content-Type", "application/json").body(canonicalEventSchema);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Schema unavailable: " + e.getMessage());
        }
    }
}

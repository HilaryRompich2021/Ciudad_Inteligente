package com.ciudadesinteligentes.ingestor.controller;

import com.ciudadesinteligentes.ingestor.model.CanonicalEvent;
import com.ciudadesinteligentes.ingestor.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/events")
@Validated
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<?> ingestEvent(@Valid @RequestBody CanonicalEvent event) {
        try {
            eventService.processAndPublish(event);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Validation error: " + e.getMessage());
        }
    }
}

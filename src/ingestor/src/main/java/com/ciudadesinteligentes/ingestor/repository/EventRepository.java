package com.ciudadesinteligentes.ingestor.repository;

import com.ciudadesinteligentes.ingestor.model.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, String> {
    // Método para verificar si existe un event_id (deduplicación)
    boolean existsById(String eventId);
}

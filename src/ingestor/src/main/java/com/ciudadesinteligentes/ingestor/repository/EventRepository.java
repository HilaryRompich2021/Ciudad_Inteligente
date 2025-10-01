package com.ciudadesinteligentes.ingestor.repository;

import com.ciudadesinteligentes.ingestor.model.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {
    //  Agregar métodos personalizados en caso de ser necesario
}

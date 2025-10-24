package com.ciudadesinteligentes.correlator.repository;

import com.ciudadesinteligentes.correlator.model.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    // Método para verificar si existe un alert_id (deduplicación)
    boolean existsById(String alertId);
    
    // Método para consultar alertas por zona 
    List<AlertEntity> findByZone(String zone); 
}

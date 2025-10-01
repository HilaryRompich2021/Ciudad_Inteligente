package com.ciudadesinteligentes.correlator.repository;

import com.ciudadesinteligentes.correlator.model.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {
    // Métodos personalizados si los necesitas
    // List<AlertEntity> findByZone(String zone); // Descomentar si usas el endpoint de consulta por zona
}

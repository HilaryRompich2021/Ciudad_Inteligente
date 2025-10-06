package com.ciudadesinteligentes.ingestor.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice // Indica que esta clase maneja excepciones globalmente
public class GlobalValidationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "Validation failed for event");
        errorResponse.put("timestamp", Instant.now().toString());
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> {
                Map<String, String> err = new HashMap<>();
                err.put("field", fieldError.getField());
                err.put("error", fieldError.getDefaultMessage());
                return err;
            })
            .collect(Collectors.toList());
        errorResponse.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // Manejo de violaciones de integridad de BD (event_id duplicado, constraints, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseBody
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("timestamp", Instant.now().toString());
        
        // Detectar si es un error de clave duplicada
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (message.contains("duplicate key") || message.contains("unique constraint") || message.contains("event_id")) {
            errorResponse.put("message", "Duplicate event_id: Event with this ID already exists");
            errorResponse.put("error_type", "duplicate_event_id");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
        
        // Otros errores de integridad
        errorResponse.put("message", "Database constraint violation");
        errorResponse.put("error_type", "data_integrity_violation");
        errorResponse.put("details", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // Manejo de IllegalArgumentException (validaciones custom, incluida deduplicación)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("timestamp", Instant.now().toString());
        
        // Detectar si es rechazo de duplicado
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate event_id")) {
            errorResponse.put("message", message);
            errorResponse.put("error_type", "duplicate_event_id");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
        
        // Otras validaciones
        errorResponse.put("message", message != null ? message : "Invalid argument");
        errorResponse.put("error_type", "validation_error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}

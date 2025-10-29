
package com.ciudadesinteligentes.ingestor.model;
import java.util.UUID;

import java.util.List;

public class BulkProcessResult {
    private int total;
    private List<UUID> successfulEvents;
    private List<ProcessingError> failedEvents;

    public BulkProcessResult(int total, List<UUID> successfulEvents, List<ProcessingError> failedEvents) {
        this.total = total;
        this.successfulEvents = successfulEvents;
        this.failedEvents = failedEvents;
    }

    // Getters
    public int getTotal() {
        return total;
    }

    public List<UUID> getSuccessfulEvents() {
        return successfulEvents;
    }

    public List<ProcessingError> getFailedEvents() {
        return failedEvents;
    }

    public int getSuccessfulCount() {
        return successfulEvents.size();
    }

    public int getFailedCount() {
        return failedEvents.size();
    }

    // Clase interna para errores de procesamiento
    public static class ProcessingError {
        private int index;
    private UUID eventId;
        private String error;

    public ProcessingError(int index, UUID eventId, String error) {
            this.index = index;
            this.eventId = eventId;
            this.error = error;
        }

        public int getIndex() {
            return index;
        }

        public UUID getEventId() {
            return eventId;
        }

        public String getError() {
            return error;
        }
    }
}

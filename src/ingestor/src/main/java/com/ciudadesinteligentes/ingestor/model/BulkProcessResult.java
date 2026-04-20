package com.ciudadesinteligentes.ingestor.model;

import java.util.List;

public class BulkProcessResult {
    private int total;
    private List<String> successfulEvents;
    private List<ProcessingError> failedEvents;

    public BulkProcessResult(int total, List<String> successfulEvents, List<ProcessingError> failedEvents) {
        this.total = total;
        this.successfulEvents = successfulEvents;
        this.failedEvents = failedEvents;
    }

    // Getters
    public int getTotal() {
        return total;
    }

    public List<String> getSuccessfulEvents() {
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
        private String eventId;
        private String error;

        public ProcessingError(int index, String eventId, String error) {
            this.index = index;
            this.eventId = eventId;
            this.error = error;
        }

        public int getIndex() {
            return index;
        }

        public String getEventId() {
            return eventId;
        }

        public String getError() {
            return error;
        }
    }
}

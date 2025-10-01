package com.ciudadesinteligentes.correlator.model;

import java.util.Map;

public class CanonicalEvent {
    public String event_version;
    public String event_type;
    public String event_id;
    public String producer;
    public String source;
    public String correlation_id;
    public String trace_id;
    public String timestamp;
    public String partition_key;
    public Map<String, Object> geo;
    public String severity;
    public Map<String, Object> payload;
}

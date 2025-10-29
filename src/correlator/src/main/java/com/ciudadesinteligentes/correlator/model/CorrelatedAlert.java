package com.ciudadesinteligentes.correlator.model;

import java.util.List;
import java.util.UUID;
import java.util.Map;

public class CorrelatedAlert {
    public UUID alert_id;
    public UUID correlation_id;
    public String type;
    public double score;
    public String zone;
    public Map<String, String> window;
    public List<String> evidence;
    public String created_at;
}

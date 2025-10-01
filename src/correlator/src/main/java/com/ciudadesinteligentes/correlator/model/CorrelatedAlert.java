package com.ciudadesinteligentes.correlator.model;

import java.util.List;
import java.util.Map;

public class CorrelatedAlert {
    public String alert_id;
    public String correlation_id;
    public String type;
    public double score;
    public String zone;
    public Map<String, String> window;
    public List<String> evidence;
    public String created_at;
}

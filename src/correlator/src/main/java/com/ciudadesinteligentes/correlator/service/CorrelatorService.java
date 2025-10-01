
package com.ciudadesinteligentes.correlator.service;

import com.ciudadesinteligentes.correlator.model.CanonicalEvent;
import com.ciudadesinteligentes.correlator.model.CorrelatedAlert;
import com.ciudadesinteligentes.correlator.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.*;
import java.time.*;

@Service
public class CorrelatorService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AlertService alertService;

    // Lógica base: solo ejemplo para "possible_robbery"
    public void processEvent(CanonicalEvent event) {
        // Idempotencia: evitar procesar dos veces el mismo event_id
        String seenKey = "corr:seen:" + event.event_id;
        Boolean alreadySeen = redisTemplate.hasKey(seenKey);
        if (Boolean.TRUE.equals(alreadySeen)) return;
        redisTemplate.opsForValue().set(seenKey, "1", Duration.ofMinutes(10));

        // Guardar resumen de evento en Redis por zona
        String zone = event.geo.get("zone").toString();
        String zoneKey = "corr:zone:" + zone;
        EventSummary summary = new EventSummary(event.event_type, event.timestamp, event.payload, event.event_id);
        redisTemplate.opsForList().rightPush(zoneKey, summary);
        redisTemplate.expire(zoneKey, Duration.ofMinutes(10));

        // Buscar eventos relevantes en la ventana
        List<Object> recentEvents = redisTemplate.opsForList().range(zoneKey, 0, -1);
        List<EventSummary> panicEvents = new ArrayList<>();
        List<EventSummary> lprEvents = new ArrayList<>();
        List<EventSummary> citizenEvents = new ArrayList<>();
        List<EventSummary> acousticEvents = new ArrayList<>();
        Instant now = Instant.parse(event.timestamp);

        for (Object obj : recentEvents) {
            if (obj instanceof EventSummary) {
                EventSummary e = (EventSummary) obj;
                Instant ts = Instant.parse(e.timestamp);
                long diffSec = Math.abs(Duration.between(ts, now).getSeconds());
                // Regla posible robo: ±2 min
                if ("panic.button".equals(e.event_type) && diffSec <= 120) panicEvents.add(e);
                if ("sensor.lpr".equals(e.event_type) && e.payload != null && e.payload.containsKey("velocidad_estimada")) {
                    double v = Double.parseDouble(e.payload.get("velocidad_estimada").toString());
                    if (v > 80 && diffSec <= 120) lprEvents.add(e);
                }
                // Regla accidente: 5 min
                if ("citizen.report".equals(e.event_type) && e.payload != null && "accidente".equals(e.payload.get("tipo_evento")) && diffSec <= 300) citizenEvents.add(e);
                if ("sensor.acoustic".equals(e.event_type) && e.payload != null && ("explosion".equals(e.payload.get("tipo_sonido_detectado")) || "vidrio_roto".equals(e.payload.get("tipo_sonido_detectado"))) && diffSec <= 300) acousticEvents.add(e);
            }
        }

        // Regla posible robo
        if (!panicEvents.isEmpty() && !lprEvents.isEmpty()) {
            CorrelatedAlert alert = new CorrelatedAlert();
            alert.alert_id = UUID.randomUUID().toString();
            alert.correlation_id = event.correlation_id != null ? event.correlation_id : UUID.randomUUID().toString();
            alert.type = "possible_robbery";
            alert.score = 0.85;
            alert.zone = zone;
            alert.window = Map.of("start", panicEvents.get(0).timestamp, "end", event.timestamp);
            List<String> evidence = new ArrayList<>();
            for (EventSummary e : panicEvents) evidence.add(e.event_id);
            for (EventSummary e : lprEvents) evidence.add(e.event_id);
            alert.evidence = evidence;
            alert.created_at = Instant.now().toString();
            kafkaTemplate.send("t01.correlated.alerts", alert.zone, alert);
            alertService.saveAlert(alert);
        }

        // Regla accidente
        if (!citizenEvents.isEmpty() && !acousticEvents.isEmpty()) {
            CorrelatedAlert alert = new CorrelatedAlert();
            alert.alert_id = UUID.randomUUID().toString();
            alert.correlation_id = event.correlation_id != null ? event.correlation_id : UUID.randomUUID().toString();
            alert.type = "accident";
            alert.score = 0.85;
            alert.zone = zone;
            alert.window = Map.of("start", citizenEvents.get(0).timestamp, "end", event.timestamp);
            List<String> evidence = new ArrayList<>();
            for (EventSummary e : citizenEvents) evidence.add(e.event_id);
            for (EventSummary e : acousticEvents) evidence.add(e.event_id);
            alert.evidence = evidence;
            alert.created_at = Instant.now().toString();
            kafkaTemplate.send("t01.correlated.alerts", alert.zone, alert);
            alertService.saveAlert(alert);
        }
    }

    // Resumen de evento para Redis
    public static class EventSummary implements java.io.Serializable {
        public String event_type;
        public String timestamp;
        public Map<String, Object> payload;
        public String event_id;
        public EventSummary(String event_type, String timestamp, Map<String, Object> payload, String event_id) {
            this.event_type = event_type;
            this.timestamp = timestamp;
            this.payload = payload;
            this.event_id = event_id;
        }
    }
}

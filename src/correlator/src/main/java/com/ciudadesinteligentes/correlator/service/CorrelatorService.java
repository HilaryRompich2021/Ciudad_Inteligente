
package com.ciudadesinteligentes.correlator.service;

import com.ciudadesinteligentes.correlator.model.CanonicalEvent;
import com.ciudadesinteligentes.correlator.model.CorrelatedAlert;
import com.ciudadesinteligentes.correlator.model.EventSummary;
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
    EventSummary summary = new EventSummary(event.getEvent_type(), event.getTimestamp(), event.getPayload(), event.getEvent_id());
        redisTemplate.opsForList().rightPush(zoneKey, summary);
        redisTemplate.expire(zoneKey, Duration.ofMinutes(10));
        
        // Guardar también por placa vehicular si existe (para rastreo multi-zona)
        if (event.payload != null && event.payload.containsKey("placa_vehicular")) {
            String plate = event.payload.get("placa_vehicular").toString();
            String plateKey = "corr:plate:" + plate;
            redisTemplate.opsForList().rightPush(plateKey, summary);
            redisTemplate.expire(plateKey, Duration.ofMinutes(10));
        }

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
                Instant ts = Instant.parse(e.getTimestamp());
                long diffSec = Math.abs(Duration.between(ts, now).getSeconds());
                // Regla posible robo: ±2 min
                if ("panic.button".equals(e.getEvent_type()) && diffSec <= 120) panicEvents.add(e);
                if ("sensor.lpr".equals(e.getEvent_type()) && e.getPayload() != null && e.getPayload().containsKey("velocidad_estimada")) {
                    double v = Double.parseDouble(e.getPayload().get("velocidad_estimada").toString());
                    if (v > 80 && diffSec <= 120) lprEvents.add(e);
                }
                // Regla accidente: 5 min
                if ("citizen.report".equals(e.getEvent_type()) && e.getPayload() != null && "accidente".equals(e.getPayload().get("tipo_evento")) && diffSec <= 300) citizenEvents.add(e);
                if ("sensor.acoustic".equals(e.getEvent_type()) && e.getPayload() != null && ("explosion".equals(e.getPayload().get("tipo_sonido_detectado")) || "vidrio_roto".equals(e.getPayload().get("tipo_sonido_detectado"))) && diffSec <= 300) acousticEvents.add(e);
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
            alert.window = Map.of("start", panicEvents.get(0).getTimestamp(), "end", event.getTimestamp());
            List<String> evidence = new ArrayList<>();
            for (EventSummary e : panicEvents) evidence.add(e.getEvent_id());
            for (EventSummary e : lprEvents) evidence.add(e.getEvent_id());
            alert.evidence = evidence;
            alert.created_at = Instant.now().toString();
            
            // Persistir en BD PRIMERO, luego publicar a Kafka
            alertService.saveAlert(alert);
            kafkaTemplate.send("correlated.alerts", alert.zone, alert);
            
            // Guardar alerta en Redis para endpoint /alerts/active (TTL 10 min)
            String alertActiveKey = "alerts:active:" + zone;
            redisTemplate.opsForList().rightPush(alertActiveKey, alert);
            redisTemplate.expire(alertActiveKey, Duration.ofMinutes(10));
        }

        // Regla accidente
        if (!citizenEvents.isEmpty() && !acousticEvents.isEmpty()) {
            CorrelatedAlert alert = new CorrelatedAlert();
            alert.alert_id = UUID.randomUUID().toString();
            alert.correlation_id = event.correlation_id != null ? event.correlation_id : UUID.randomUUID().toString();
            alert.type = "accident";
            alert.score = 0.85;
            alert.zone = zone;
            alert.window = Map.of("start", citizenEvents.get(0).getTimestamp(), "end", event.getTimestamp());
            List<String> evidence = new ArrayList<>();
            for (EventSummary e : citizenEvents) evidence.add(e.getEvent_id());
            for (EventSummary e : acousticEvents) evidence.add(e.getEvent_id());
            alert.evidence = evidence;
            alert.created_at = Instant.now().toString();
            
           
            alertService.saveAlert(alert);
            kafkaTemplate.send("correlated.alerts", alert.zone, alert);
            
            // Guardar alerta en Redis para endpoint /alerts/active (TTL 10 min)
            String alertActiveKey = "alerts:active:" + zone;
            redisTemplate.opsForList().rightPush(alertActiveKey, alert);
            redisTemplate.expire(alertActiveKey, Duration.ofMinutes(10));
        }
    }
}

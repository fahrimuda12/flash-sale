package com.fahri.product.queue;

import com.fahri.product.config.KafkaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void logActivity(String eventType, String action, String entityName, String entityId,
                            String status, String errorMessage, Map<String, Object> payload) {
        try {
            String eventId = UUID.randomUUID().toString();
            String traceId = UUID.randomUUID().toString();

            Map<String, Object> auditMap = new HashMap<>();
            auditMap.put("eventId", eventId);
            auditMap.put("traceId", traceId);
            auditMap.put("serviceName", "product-service");
            auditMap.put("eventType", eventType);
            auditMap.put("action", action);
            auditMap.put("entityName", entityName);
            auditMap.put("entityId", entityId);
            auditMap.put("status", status != null ? status : "SUCCESS");
            auditMap.put("errorMessage", errorMessage);
            auditMap.put("payload", payload != null ? payload : new HashMap<>());
            auditMap.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(auditMap);

            kafkaTemplate.send(KafkaConfig.AUDIT_EVENTS_TOPIC, eventId, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Published audit event {} to {}", eventType, KafkaConfig.AUDIT_EVENTS_TOPIC);
                        } else {
                            log.warn("Failed to publish audit event {}: {}", eventType, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not serialize audit event: {}", e.getMessage());
        }
    }
}

package com.fahri.order.queue;

import com.fahri.order.config.KafkaConfig;
import com.fahri.order.queue.dto.OrderPlacedEvent;
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
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public static final String AUDIT_EVENTS_TOPIC = "flashsale.audit.events";

    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent to Kafka topic: {} with orderNumber: {}",
                KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderNumber());

        try {
            // Serialize to JSON String to ensure seamless compatibility with StringSerializer
            String jsonPayload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderNumber(), jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Successfully published OrderPlacedEvent for order: {} to partition: {} with offset: {}",
                                    event.getOrderNumber(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to publish OrderPlacedEvent for order: {}", event.getOrderNumber(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize OrderPlacedEvent for order: {}", event.getOrderNumber(), ex);
            throw new RuntimeException("Failed to serialize order event to JSON", ex);
        }
    }

    public void publishAuditEvent(String eventType, String action, String entityId, String status, String errorMessage, Map<String, Object> payload) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> auditMap = new HashMap<>();
            auditMap.put("eventId", eventId);
            auditMap.put("traceId", entityId != null ? entityId : UUID.randomUUID().toString());
            auditMap.put("serviceName", "order-service");
            auditMap.put("eventType", eventType);
            auditMap.put("action", action);
            auditMap.put("entityName", "Order");
            auditMap.put("entityId", entityId);
            auditMap.put("status", status != null ? status : "SUCCESS");
            auditMap.put("errorMessage", errorMessage);
            auditMap.put("payload", payload != null ? payload : new HashMap<>());
            auditMap.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(auditMap);
            kafkaTemplate.send(AUDIT_EVENTS_TOPIC, eventId, jsonPayload);
            log.debug("Published audit event {} for order: {}", eventType, entityId);
        } catch (Exception ex) {
            log.warn("Could not publish order audit event: {}", ex.getMessage());
        }
    }
}

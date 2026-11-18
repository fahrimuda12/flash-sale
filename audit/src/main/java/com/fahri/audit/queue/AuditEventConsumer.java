package com.fahri.audit.queue;

import com.fahri.audit.dto.AuditEventDto;
import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.service.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.audit-events:flashsale.audit.events}",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void consumeAuditEvent(@Payload String payload) {
        log.info("Received message on audit-events topic: {}", payload);
        try {
            AuditEventDto eventDto = objectMapper.readValue(payload, AuditEventDto.class);
            log.info("Processing AuditEvent: service={}, eventType={}, action={}, entityId={}",
                    eventDto.getServiceName(), eventDto.getEventType(), eventDto.getAction(), eventDto.getEntityId());
            auditService.recordAuditEvent(eventDto);
        } catch (Exception e) {
            log.error("Failed to process incoming Kafka audit event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-events:flashsale-order-events}",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void consumeOrderEvents(@Payload String payload) {
        log.info("Received raw domain event on order-events topic: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);

            String orderNumber = root.has("orderNumber") ? root.get("orderNumber").asText() : UUID.randomUUID().toString();
            String customerId = root.has("customerId") ? root.get("customerId").asText() : "ANONYMOUS";
            String customerEmail = root.has("customerEmail") ? root.get("customerEmail").asText() : "";

            Map<String, Object> payloadMap = new HashMap<>();
            if (root.has("totalAmount")) payloadMap.put("totalAmount", root.get("totalAmount").asDouble());
            if (root.has("discountAmount")) payloadMap.put("discountAmount", root.get("discountAmount").asDouble());
            if (root.has("finalAmount")) payloadMap.put("finalAmount", root.get("finalAmount").asDouble());
            if (root.has("voucherCode")) payloadMap.put("voucherCode", root.get("voucherCode").asText());
            if (root.has("paymentMethod")) payloadMap.put("paymentMethod", root.get("paymentMethod").asText());
            if (root.has("items")) payloadMap.put("items", root.get("items"));

            Map<String, String> metadata = new HashMap<>();
            if (root.has("clientIp")) metadata.put("clientIp", root.get("clientIp").asText());
            if (root.has("userAgent")) metadata.put("userAgent", root.get("userAgent").asText());
            metadata.put("customerEmail", customerEmail);

            AuditEventDto auditEvent = AuditEventDto.builder()
                    .eventId(UUID.randomUUID().toString())
                    .traceId(orderNumber)
                    .serviceName("order-service")
                    .eventType(AuditEventType.ORDER_PLACED)
                    .action("ORDER_CHECKOUT_COMPLETED")
                    .entityName("Order")
                    .entityId(orderNumber)
                    .userId(customerId)
                    .status(AuditStatus.SUCCESS)
                    .payload(payloadMap)
                    .metadata(metadata)
                    .timestamp(Instant.now())
                    .build();

            auditService.recordAuditEvent(auditEvent);
            log.info("Successfully audited order-service event for orderNumber: {}", orderNumber);
        } catch (Exception e) {
            log.error("Failed to parse and audit order event: {}", e.getMessage(), e);
        }
    }
}

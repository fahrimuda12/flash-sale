package com.fahri.product.queue;

import com.fahri.product.queue.dto.OrderPlacedEvent;
import com.fahri.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events:flashsale-order-events}",
            groupId = "${spring.kafka.consumer.group-id:product-stock-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(Object payload) {
        log.info("Received message from Kafka on order-events: {}", payload);

        try {
            OrderPlacedEvent event = null;

            if (payload instanceof OrderPlacedEvent placedEvent) {
                event = placedEvent;
            } else if (payload instanceof ConsumerRecord<?, ?> record) {
                Object recordValue = record.value();
                if (recordValue instanceof String json) {
                    event = objectMapper.readValue(json, OrderPlacedEvent.class);
                } else if (recordValue instanceof OrderPlacedEvent placedEvent) {
                    event = placedEvent;
                } else if (recordValue != null) {
                    event = objectMapper.convertValue(recordValue, OrderPlacedEvent.class);
                }
            } else if (payload instanceof String json) {
                event = objectMapper.readValue(json, OrderPlacedEvent.class);
            } else if (payload != null) {
                event = objectMapper.convertValue(payload, OrderPlacedEvent.class);
            }

            if (event == null) {
                log.warn("Payload could not be resolved to an OrderPlacedEvent: {}", payload);
                return;
            }

            log.info("Processing OrderPlacedEvent for orderNumber: {}, items count: {}",
                    event.getOrderNumber(), event.getItems() != null ? event.getItems().size() : 0);

            productService.decreaseStockForOrder(event.getOrderNumber(), event.getItems());
            log.info("Stock successfully deducted for orderNumber: {}", event.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to deduct stock for order event: {}", payload, e);
        }
    }
}

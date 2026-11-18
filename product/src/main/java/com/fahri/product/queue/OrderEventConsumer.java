package com.fahri.product.queue;

import com.fahri.product.queue.dto.OrderPlacedEvent;
import com.fahri.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ProductService productService;

    @KafkaListener(
            topics = "${app.kafka.topics.order-events:flashsale-order-events}",
            groupId = "${spring.kafka.consumer.group-id:product-stock-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPlaced(@Payload OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent from Kafka for orderNumber: {}, items count: {}",
                event.getOrderNumber(), event.getItems() != null ? event.getItems().size() : 0);

        try {
            productService.decreaseStockForOrder(event.getOrderNumber(), event.getItems());
            log.info("Stock successfully deducted for orderNumber: {}", event.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to deduct stock for orderNumber: {} due to error: {}",
                    event.getOrderNumber(), e.getMessage(), e);
            throw e;
        }
    }
}

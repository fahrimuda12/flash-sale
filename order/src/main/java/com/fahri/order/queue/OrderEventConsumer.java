package com.fahri.order.queue;

import com.fahri.order.config.KafkaConfig;
import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.queue.dto.ItemDetailEvent;
import com.fahri.order.queue.dto.OrderPlacedEvent;
import com.fahri.order.repository.OrderSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderSearchRepository orderSearchRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.ORDER_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id:flashsale-order-group}")
    public void handleOrderPlaced(String message) {
        log.info("Received OrderPlacedEvent from Kafka: {}", message);

        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);
            indexToElasticsearch(event);
        } catch (Exception e) {
            log.error("Error processing OrderPlacedEvent asynchronously: {}", e.getMessage(), e);
        }
    }

    private void indexToElasticsearch(OrderPlacedEvent event) {
        if (event == null || event.getItems() == null) {
            return;
        }

        // Java Stream: map product names and distinct categories
        String productNames = event.getItems().stream()
                .map(ItemDetailEvent::getProductName)
                .collect(Collectors.joining(", "));

        List<String> categories = event.getItems().stream()
                .map(ItemDetailEvent::getCategory)
                .distinct()
                .collect(Collectors.toList());

        OrderSearchDocument searchDoc = OrderSearchDocument.builder()
                .id(event.getOrderNumber())
                .orderNumber(event.getOrderNumber())
                .customerId(event.getCustomerId())
                .customerEmail(event.getCustomerEmail())
                .status(event.getStatus())
                .totalAmount(event.getTotalAmount())
                .finalAmount(event.getFinalAmount())
                .voucherCode(event.getVoucherCode())
                .productNames(productNames)
                .categories(categories)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp().toString() : LocalDateTime.now().toString())
                .build();

        orderSearchRepository.save(searchDoc);
        log.info("Indexed order {} into Elasticsearch successfully", event.getOrderNumber());
    }
}

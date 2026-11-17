package com.fahri.order.queue;

import com.fahri.order.config.KafkaConfig;
import com.fahri.order.queue.dto.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing OrderPlacedEvent to Kafka topic: {} with orderNumber: {}",
                KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderNumber());

        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, event.getOrderNumber(), event)
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
    }
}

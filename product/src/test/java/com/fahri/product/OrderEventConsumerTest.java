package com.fahri.product;

import com.fahri.product.queue.OrderEventConsumer;
import com.fahri.product.queue.dto.OrderItemEvent;
import com.fahri.product.queue.dto.OrderPlacedEvent;
import com.fahri.product.service.ProductService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Event Kafka Consumer Unit Tests")
class OrderEventConsumerTest {

    @Mock
    private ProductService productService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    @Test
    @DisplayName("handleOrderPlaced should delegate order items to productService.decreaseStockForOrder when event object passed")
    void testHandleOrderPlaced_Success() {
        OrderItemEvent item = OrderItemEvent.builder()
                .productId(1L)
                .productName("Flash Sale Product")
                .quantity(3)
                .build();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderNumber("ORD-99999")
                .customerId(100L)
                .status("COMPLETED")
                .items(List.of(item))
                .timestamp(LocalDateTime.now())
                .build();

        orderEventConsumer.handleOrderPlaced(event);

        verify(productService).decreaseStockForOrder("ORD-99999", List.of(item));
    }

    @Test
    @DisplayName("handleOrderPlaced should unpack ConsumerRecord value and deserialize event")
    void testHandleOrderPlaced_WithConsumerRecord() throws Exception {
        OrderItemEvent item = OrderItemEvent.builder()
                .productId(2L)
                .productName("Flash Sale Item")
                .quantity(1)
                .build();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderNumber("ORD-12345")
                .customerId(101L)
                .status("CONFIRMED")
                .items(List.of(item))
                .timestamp(LocalDateTime.now())
                .build();

        String json = "{\"orderNumber\":\"ORD-12345\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("flashsale-order-events", 0, 0L, "ORD-12345", json);

        when(objectMapper.readValue(anyString(), eq(OrderPlacedEvent.class))).thenReturn(event);

        orderEventConsumer.handleOrderPlaced(record);

        verify(productService).decreaseStockForOrder("ORD-12345", List.of(item));
    }
}

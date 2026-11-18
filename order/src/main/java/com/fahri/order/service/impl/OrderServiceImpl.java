package com.fahri.order.service.impl;

import com.fahri.order.dto.*;
import com.fahri.order.entity.Order;
import com.fahri.order.entity.OrderItem;
import com.fahri.order.entity.OrderStatus;
import com.fahri.order.queue.dto.ItemDetailEvent;
import com.fahri.order.queue.dto.OrderPlacedEvent;
import com.fahri.order.queue.OrderEventProducer;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.service.OrderService;
import com.fahri.order.service.StockService;
import com.fahri.order.strategy.DiscountStrategy;
import com.fahri.order.strategy.DiscountStrategyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final DiscountStrategyProvider discountStrategyProvider;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        log.info("Processing checkout for customerId: {}, email: {}", request.getCustomerId(), request.getCustomerEmail());

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            stockService.acquireIdempotencyLock(request.getIdempotencyKey(), Duration.ofMinutes(5));
        }

        BigDecimal totalSubtotal = request.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Integer> categoryDistribution = request.getItems().stream()
                .collect(Collectors.groupingBy(
                        OrderItemRequest::getCategory,
                        Collectors.summingInt(OrderItemRequest::getQuantity)
                ));
        log.info("Cart category distribution: {}", categoryDistribution);

        DiscountStrategy discountStrategy = discountStrategyProvider.resolveStrategy(request.getVoucherCode());
        log.info("Applied Discount Strategy: {}", discountStrategy.getStrategyName());

        BigDecimal discountAmount = BigDecimal.ZERO;
        boolean voucherApplied = false;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            voucherApplied = stockService.reserveVoucherQuota(request.getVoucherCode());
            if (voucherApplied) {
                discountAmount = discountStrategy.calculateDiscount(totalSubtotal);
            }
        }
        BigDecimal finalAmount = totalSubtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        List<OrderItemRequest> reservedItems = new ArrayList<>();
        try {
            for (OrderItemRequest item : request.getItems()) {
                stockService.reserveProductStock(item.getProductId(), item.getQuantity());
                reservedItems.add(item);
            }
        } catch (Exception ex) {
            log.error("Stock reservation failed. Executing compensating rollback in Redis grid: {}", ex.getMessage());
            for (OrderItemRequest item : reservedItems) {
                stockService.releaseProductStock(item.getProductId(), item.getQuantity());
            }
            orderEventProducer.publishAuditEvent("ORDER_FAILED", "RESERVE_STOCK_FAILED",
                    "CUSTOMER-" + request.getCustomerId(), "FAILURE", ex.getMessage(),
                    Map.of("customerId", request.getCustomerId(), "itemCount", request.getItems() != null ? request.getItems().size() : 0));
            throw ex;
        }

        Order order = Order.builder()
                .orderNumber(setOrderNumber())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .totalAmount(totalSubtotal)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .voucherCode(voucherApplied ? request.getVoucherCode() : null)
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(request.getPaymentMethod())
                .build();

        List<OrderItem> entityItems = request.getItems().stream()
                .map(reqItem -> OrderItem.builder()
                        .productId(reqItem.getProductId())
                        .productName(reqItem.getProductName())
                        .category(reqItem.getCategory())
                        .price(reqItem.getPrice())
                        .quantity(reqItem.getQuantity())
                        .subtotal(reqItem.getPrice().multiply(BigDecimal.valueOf(reqItem.getQuantity())))
                        .build())
                .toList();

        entityItems.forEach(order::addItem);

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved to PostgreSQL with id: {}, orderNumber: {}", savedOrder.getId(), savedOrder.getOrderNumber());

        publishEventCheckout(request, savedOrder);

        return OrderResponse.fromEntity(savedOrder);
    }

    private void publishEventCheckout(CheckoutRequest request, Order savedOrder) {
        List<ItemDetailEvent> eventItems = savedOrder.getItems().stream()
                .map(item -> ItemDetailEvent.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .category(item.getCategory())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderId(savedOrder.getId())
                .orderNumber(savedOrder.getOrderNumber())
                .customerId(savedOrder.getCustomerId())
                .customerEmail(savedOrder.getCustomerEmail())
                .totalAmount(savedOrder.getTotalAmount())
                .discountAmount(savedOrder.getDiscountAmount())
                .finalAmount(savedOrder.getFinalAmount())
                .voucherCode(savedOrder.getVoucherCode())
                .status(savedOrder.getStatus().name())
                .paymentMethod(savedOrder.getPaymentMethod())
                .items(eventItems)
                .clientIp(request.getClientIp())
                .userAgent(request.getUserAgent())
                .timestamp(LocalDateTime.now())
                .build();

        orderEventProducer.publishOrderPlaced(event);
    }

    private static @NonNull String setOrderNumber() {
       return "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

package com.fahri.order.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent implements Serializable {

    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String voucherCode;
    private String status;
    private String paymentMethod;
    private List<ItemDetailEvent> items;
    private String clientIp;
    private String userAgent;
    private LocalDateTime timestamp;
}

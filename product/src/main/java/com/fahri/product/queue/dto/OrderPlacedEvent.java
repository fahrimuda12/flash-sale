package com.fahri.product.queue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPlacedEvent implements Serializable {

    private String orderNumber;
    private Long customerId;
    private String customerEmail;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String voucherCode;
    private String paymentMethod;
    private String clientIp;
    private String userAgent;

    @Builder.Default
    private List<OrderItemEvent> items = new ArrayList<>();

    private LocalDateTime timestamp;
}

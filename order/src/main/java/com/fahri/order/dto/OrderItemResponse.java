package com.fahri.order.dto;

import com.fahri.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse implements Serializable {

    private Long id;
    private Long productId;
    private String productName;
    private String category;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private LocalDateTime createdAt;

    public static OrderItemResponse fromEntity(OrderItem entity) {
        if (entity == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .category(entity.getCategory())
                .price(entity.getPrice())
                .quantity(entity.getQuantity())
                .subtotal(entity.getSubtotal())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

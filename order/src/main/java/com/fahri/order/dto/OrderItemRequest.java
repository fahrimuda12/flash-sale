package com.fahri.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest implements Serializable {

    @NotNull(message = "Product ID is required, please fill product id")
    private Long productId;

    private String productName;

    private String category;

    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Quantity is required, please fill product quantity")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}

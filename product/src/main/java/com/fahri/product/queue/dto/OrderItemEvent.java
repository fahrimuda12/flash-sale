package com.fahri.product.queue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderItemEvent implements Serializable {

    private Long productId;
    private String productName;
    private String category;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}

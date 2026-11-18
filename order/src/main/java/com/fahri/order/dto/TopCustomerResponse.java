package com.fahri.order.dto;

import com.fahri.order.repository.projection.CustomerSpendingProjection;
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
public class TopCustomerResponse implements Serializable {

    private Long customerId;
    private String customerEmail;
    private BigDecimal totalSpent;
    private Long orderCount;
    private Integer customerRank;

    public static TopCustomerResponse fromProjection(CustomerSpendingProjection projection) {
        return TopCustomerResponse.builder()
                .customerId(projection.getCustomerId())
                .customerEmail(projection.getCustomerEmail())
                .totalSpent(projection.getTotalSpent())
                .orderCount(projection.getOrderCount())
                .customerRank(projection.getCustomerRank())
                .build();
    }
}

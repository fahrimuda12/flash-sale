package com.fahri.order.dto;

import com.fahri.order.repository.projection.CategoryTopProductProjection;
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
public class CategoryTopProductResponse implements Serializable {

    private String category;
    private Long productId;
    private String productName;
    private Long totalQuantitySold;
    private BigDecimal totalRevenue;
    private Integer categoryRank;

    public static CategoryTopProductResponse fromProjection(CategoryTopProductProjection projection) {
        return CategoryTopProductResponse.builder()
                .category(projection.getCategory())
                .productId(projection.getProductId())
                .productName(projection.getProductName())
                .totalQuantitySold(projection.getTotalQuantitySold())
                .totalRevenue(projection.getTotalRevenue())
                .categoryRank(projection.getCategoryRank())
                .build();
    }
}

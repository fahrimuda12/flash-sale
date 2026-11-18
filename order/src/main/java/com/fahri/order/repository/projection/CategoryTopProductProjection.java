package com.fahri.order.repository.projection;

import java.math.BigDecimal;

public interface CategoryTopProductProjection {
    String getCategory();
    Long getProductId();
    String getProductName();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
    Integer getCategoryRank();
}

package com.fahri.order.repository.projection;

import java.math.BigDecimal;

public interface CustomerSpendingProjection {
    Long getCustomerId();
    String getCustomerEmail();
    BigDecimal getTotalSpent();
    Long getOrderCount();
    Integer getCustomerRank();
}

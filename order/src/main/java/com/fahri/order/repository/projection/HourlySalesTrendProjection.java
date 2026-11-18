package com.fahri.order.repository.projection;

import java.math.BigDecimal;

public interface HourlySalesTrendProjection {
    String getHourBucket();
    Long getOrderCount();
    BigDecimal getHourlySales();
    BigDecimal getCumulativeSales();
}

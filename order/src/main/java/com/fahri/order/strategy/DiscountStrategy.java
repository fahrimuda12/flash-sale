package com.fahri.order.strategy;

import java.math.BigDecimal;

public interface DiscountStrategy {

    /**
     * Determines whether this strategy applies to the given voucher code.
     */
    boolean isApplicable(String voucherCode);

    /**
     * Calculates the discount amount given the total cart subtotal.
     */
    BigDecimal calculateDiscount(BigDecimal subtotal);

    /**
     * Identifier for the strategy for logging and auditing.
     */
    String getStrategyName();
}

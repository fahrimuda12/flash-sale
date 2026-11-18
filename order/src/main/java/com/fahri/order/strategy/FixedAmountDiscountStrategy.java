package com.fahri.order.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedAmountDiscountStrategy implements DiscountStrategy {

    private static final String VOUCHER_CODE = "DISCOUNT50K";
    private static final BigDecimal FIXED_DISCOUNT = new BigDecimal("50000.00");
    private static final BigDecimal MIN_SUBTOTAL = new BigDecimal("100000.00");

    @Override
    public boolean isApplicable(String voucherCode) {
        return VOUCHER_CODE.equalsIgnoreCase(voucherCode);
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(MIN_SUBTOTAL) < 0) {
            return BigDecimal.ZERO;
        }
        return FIXED_DISCOUNT.min(subtotal);
    }

    @Override
    public String getStrategyName() {
        return "Flat 50,000 Discount";
    }
}

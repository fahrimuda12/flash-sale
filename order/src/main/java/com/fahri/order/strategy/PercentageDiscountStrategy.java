package com.fahri.order.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PercentageDiscountStrategy implements DiscountStrategy {

    private static final String VOUCHER_CODE = "FLASHSALE10";
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");
    private static final BigDecimal MAX_DISCOUNT = new BigDecimal("5000000.00");

    @Override
    public boolean isApplicable(String voucherCode) {
        return VOUCHER_CODE.equalsIgnoreCase(voucherCode);
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal calculated = subtotal.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
        return calculated.min(MAX_DISCOUNT);
    }

    @Override
    public String getStrategyName() {
        return "10% Flash Sale Percentage Discount (Capped at 5,000,000)";
    }
}

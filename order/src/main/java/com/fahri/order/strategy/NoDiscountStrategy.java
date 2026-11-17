package com.fahri.order.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class NoDiscountStrategy implements DiscountStrategy {

    @Override
    public boolean isApplicable(String voucherCode) {
        return voucherCode == null || voucherCode.trim().isEmpty();
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        return BigDecimal.ZERO;
    }

    @Override
    public String getStrategyName() {
        return "No Discount Strategy (Default)";
    }
}

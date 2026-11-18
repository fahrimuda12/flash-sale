package com.fahri.order.strategy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TieredVipDiscountStrategy implements DiscountStrategy {

    private static final String VOUCHER_CODE = "VIPCASHBACK";
    private static final BigDecimal TIER_3_THRESHOLD = new BigDecimal("40000000.00");
    private static final BigDecimal TIER_2_THRESHOLD = new BigDecimal("20000000.00");
    private static final BigDecimal TIER_1_THRESHOLD = new BigDecimal("10000000.00");

    @Override
    public boolean isApplicable(String voucherCode) {
        return VOUCHER_CODE.equalsIgnoreCase(voucherCode);
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(TIER_1_THRESHOLD) < 0) {
            return BigDecimal.ZERO;
        }

        if (subtotal.compareTo(TIER_3_THRESHOLD) >= 0) {
            BigDecimal disc = subtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            return disc.min(new BigDecimal("10000000.00"));
        } else if (subtotal.compareTo(TIER_2_THRESHOLD) >= 0) {
            BigDecimal disc = subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            return disc.min(new BigDecimal("3000000.00"));
        } else {
            BigDecimal disc = subtotal.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            return disc.min(new BigDecimal("1000000.00"));
        }
    }

    @Override
    public String getStrategyName() {
        return "VIP Tiered Cash-Back Discount (Up to 15%)";
    }
}

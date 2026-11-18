package com.fahri.order;

import com.fahri.order.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Discount Strategy (Spring IoC) Tests")
class DiscountStrategyTest {

    private DiscountStrategyProvider provider;
    private PercentageDiscountStrategy percentageStrategy;
    private FixedAmountDiscountStrategy fixedStrategy;
    private TieredVipDiscountStrategy vipStrategy;
    private NoDiscountStrategy defaultStrategy;

    @BeforeEach
    void setUp() {
        percentageStrategy = new PercentageDiscountStrategy();
        fixedStrategy = new FixedAmountDiscountStrategy();
        vipStrategy = new TieredVipDiscountStrategy();
        defaultStrategy = new NoDiscountStrategy();

        List<DiscountStrategy> strategies = List.of(percentageStrategy, fixedStrategy, vipStrategy, defaultStrategy);
        provider = new DiscountStrategyProvider(strategies, defaultStrategy);
    }

    @Test
    @DisplayName("Should resolve and calculate PercentageDiscountStrategy (10% capped at 5M)")
    void testPercentageDiscount() {
        DiscountStrategy strategy = provider.resolveStrategy("FLASHSALE10");
        assertNotNull(strategy);
        assertTrue(strategy instanceof PercentageDiscountStrategy);

        BigDecimal subtotal = new BigDecimal("20000000.00");
        BigDecimal discount = strategy.calculateDiscount(subtotal);
        // 10% of 20M = 2M
        assertEquals(new BigDecimal("2000000.00"), discount);

        // Subtotal exceeding cap: 60M -> 10% is 6M, but capped at 5M
        BigDecimal largeSubtotal = new BigDecimal("60000000.00");
        BigDecimal cappedDiscount = strategy.calculateDiscount(largeSubtotal);
        assertEquals(new BigDecimal("5000000.00"), cappedDiscount);
    }

    @Test
    @DisplayName("Should resolve and calculate FixedAmountDiscountStrategy (50K)")
    void testFixedAmountDiscount() {
        DiscountStrategy strategy = provider.resolveStrategy("DISCOUNT50K");
        assertNotNull(strategy);
        assertTrue(strategy instanceof FixedAmountDiscountStrategy);

        BigDecimal subtotal = new BigDecimal("250000.00");
        BigDecimal discount = strategy.calculateDiscount(subtotal);
        assertEquals(new BigDecimal("50000.00"), discount);

        // Below minimum threshold of 100K
        BigDecimal belowMin = new BigDecimal("80000.00");
        assertEquals(BigDecimal.ZERO, strategy.calculateDiscount(belowMin));
    }

    @Test
    @DisplayName("Should resolve and calculate TieredVipDiscountStrategy")
    void testTieredVipDiscount() {
        DiscountStrategy strategy = provider.resolveStrategy("VIPCASHBACK");
        assertNotNull(strategy);
        assertTrue(strategy instanceof TieredVipDiscountStrategy);

        // Tier 3: >= 40M (15% discount)
        BigDecimal subtotal45M = new BigDecimal("45000000.00");
        BigDecimal discTier3 = strategy.calculateDiscount(subtotal45M);
        assertEquals(new BigDecimal("6750000.00"), discTier3);

        // Tier 2: >= 20M (10% discount)
        BigDecimal subtotal25M = new BigDecimal("25000000.00");
        BigDecimal discTier2 = strategy.calculateDiscount(subtotal25M);
        assertEquals(new BigDecimal("2500000.00"), discTier2);

        // Tier 1: >= 10M (5% discount)
        BigDecimal subtotal12M = new BigDecimal("12000000.00");
        BigDecimal discTier1 = strategy.calculateDiscount(subtotal12M);
        assertEquals(new BigDecimal("600000.00"), discTier1);
    }

    @Test
    @DisplayName("Should fallback to NoDiscountStrategy when voucher is null, empty, or unknown")
    void testFallbackDiscount() {
        DiscountStrategy nullVoucher = provider.resolveStrategy(null);
        assertTrue(nullVoucher instanceof NoDiscountStrategy);
        assertEquals(BigDecimal.ZERO, nullVoucher.calculateDiscount(new BigDecimal("5000000.00")));

        DiscountStrategy unknownVoucher = provider.resolveStrategy("RANDOM_INVALID");
        assertTrue(unknownVoucher instanceof NoDiscountStrategy);
        assertEquals(BigDecimal.ZERO, unknownVoucher.calculateDiscount(new BigDecimal("5000000.00")));
    }
}

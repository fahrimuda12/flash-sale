package com.fahri.order.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring IoC demonstration:
 * Automatically collects and injects all DiscountStrategy beans configured in the Spring ApplicationContext.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountStrategyProvider {

    private final List<DiscountStrategy> discountStrategies;
    private final NoDiscountStrategy defaultStrategy;

    public DiscountStrategy resolveStrategy(String voucherCode) {
        if (voucherCode == null || voucherCode.trim().isEmpty()) {
            return defaultStrategy;
        }

        return discountStrategies.stream()
                .filter(strategy -> !(strategy instanceof NoDiscountStrategy))
                .filter(strategy -> strategy.isApplicable(voucherCode))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No active discount strategy found for voucher: {}, applying default", voucherCode);
                    return defaultStrategy;
                });
    }
}

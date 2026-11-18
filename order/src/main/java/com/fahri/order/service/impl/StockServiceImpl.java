package com.fahri.order.service.impl;

import com.fahri.order.exception.DuplicateOrderException;
import com.fahri.order.exception.OutOfStockException;
import com.fahri.order.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis In-Memory Data Grid Service:
 * Handles atomic inventory reservations, campaign voucher quotas, and distributed idempotency locking.
 * This guarantees zero overselling during extreme flash sale concurrency spikes without DB lock contention.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STOCK_KEY_PREFIX = "product:stock:";
    private static final String VOUCHER_QUOTA_PREFIX = "voucher:quota:";
    private static final String IDEMPOTENCY_PREFIX = "order:idempotency:";

    @Override
    public void acquireIdempotencyLock(String idempotencyKey, Duration timeout) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", timeout);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Duplicate checkout attempt detected for idempotency key: {}", idempotencyKey);
            throw new DuplicateOrderException("Transaction already in progress or completed for idempotency key: " + idempotencyKey);
        }
    }

    @Override
    public void reserveProductStock(Long productId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;

        redisTemplate.opsForValue().setIfAbsent(stockKey, 100);

        Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, quantity);

        if (remainingStock == null || remainingStock < 0) {
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.warn("Flash sale out of stock for productId: {}. Attempted: {}, Remaining: {}", productId, quantity, remainingStock);
            throw new OutOfStockException("Product " + productId + " is out of stock in flash sale campaign.");
        }

        log.info("Reserved {} units for productId: {}. Remaining in Redis Data Grid: {}", quantity, productId, remainingStock);
    }

    @Override
    public void releaseProductStock(Long productId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        Long restoredStock = redisTemplate.opsForValue().increment(stockKey, quantity);
        log.info("Compensated/Restored {} units for productId: {}. Current grid stock: {}", quantity, productId, restoredStock);
    }

    @Override
    public boolean reserveVoucherQuota(String voucherCode) {
        if (voucherCode == null || voucherCode.trim().isEmpty()) {
            return true;
        }

        String voucherKey = VOUCHER_QUOTA_PREFIX + voucherCode.toUpperCase();
        redisTemplate.opsForValue().setIfAbsent(voucherKey, 50);

        Long remainingQuota = redisTemplate.opsForValue().decrement(voucherKey, 1);
        if (remainingQuota == null || remainingQuota < 0) {
            redisTemplate.opsForValue().increment(voucherKey, 1);
            log.warn("Voucher quota exhausted for code: {}", voucherCode);
            return false;
        }

        log.info("Voucher quota claimed for {}. Remaining quota: {}", voucherCode, remainingQuota);
        return true;
    }
}

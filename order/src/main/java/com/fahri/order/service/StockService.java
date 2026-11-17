package com.fahri.order.service;

import java.time.Duration;

public interface StockService {

    void acquireIdempotencyLock(String idempotencyKey, Duration timeout);
    void reserveProductStock(Long productId, int quantity);
    void releaseProductStock(Long productId, int quantity);
    boolean reserveVoucherQuota(String voucherCode);
}

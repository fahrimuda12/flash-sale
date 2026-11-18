package com.fahri.order.seeder;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for dynamic order data seeding.
 * Enables on-demand seeding of flash sale orders to test and verify:
 * - Step 5 Native SQL Analytics (DENSE_RANK(), CTE + ROW_NUMBER(), Window SUM())
 * - Performance benchmarks and high concurrency scenarios
 */
@RestController
@RequestMapping("/api/orders/seed")
@RequiredArgsConstructor
public class OrderSeederController {

    private final OrderDataSeeder orderDataSeeder;

    @PostMapping
    public ResponseEntity<Map<String, Object>> seedOrders(
            @RequestParam(defaultValue = "50") int count,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> summary = orderDataSeeder.seedDynamic(count, force);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/performance")
    public ResponseEntity<Map<String, Object>> seedPerformanceOrders(
            @RequestParam(defaultValue = "50") int count,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> summary = orderDataSeeder.seedDynamic(count, force);
        return ResponseEntity.ok(summary);
    }
}

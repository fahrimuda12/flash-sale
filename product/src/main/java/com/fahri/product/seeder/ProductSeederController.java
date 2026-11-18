package com.fahri.product.seeder;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller to trigger dynamic product data seeding for performance testing.
 * Supports flexible input count bounded between 1 and 500 items.
 */
@RestController
@RequestMapping("/api/products/seed")
@RequiredArgsConstructor
public class ProductSeederController {

    private final ProductDataSeeder productDataSeeder;

    /**
     * Dynamically seed products into the database and warm Redis stock cache.
     *
     * @param count The number of products to seed (allowed range: 1 to 500, default: 100).
     * @param force If true, overrides the existing database count check.
     * @return Execution analytics (seeded count, category distribution, price metrics, execution time).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> seedProducts(
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> summary = productDataSeeder.seedDynamic(count, force);
        return ResponseEntity.ok(summary);
    }

    /**
     * Dedicated performance testing alias endpoint.
     */
    @PostMapping("/performance")
    public ResponseEntity<Map<String, Object>> seedPerformanceProducts(
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> summary = productDataSeeder.seedDynamic(count, force);
        return ResponseEntity.ok(summary);
    }
}

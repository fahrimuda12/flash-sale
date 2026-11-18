package com.fahri.audit.seeder;

import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditLog;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditDataSeeder implements CommandLineRunner {

    public static final int MIN_SEEDER_COUNT = 1;
    public static final int MAX_SEEDER_COUNT = 500;

    private final AuditLogRepository auditLogRepository;

    @Value("${app.seeder.audit.enabled:true}")
    private boolean seederEnabled;

    @Value("${app.seeder.audit.count:100}")
    private int defaultStartupCount;

    public record AuditEventTemplate(
            String serviceName,
            AuditEventType eventType,
            String action,
            String entityName,
            AuditStatus status,
            String defaultErrorMessage,
            String description
    ) {}

    @Override
    public void run(String... args) {
        if (!seederEnabled) {
            log.info("AuditDataSeeder is disabled via application configuration.");
            return;
        }

        int targetStartupCount = Math.min(Math.max(defaultStartupCount, MIN_SEEDER_COUNT), MAX_SEEDER_COUNT);
        long existingCount = auditLogRepository.count();

        if (existingCount < targetStartupCount) {
            log.info("Current audit log count is {} (< {}). Executing dynamic Java audit seeder...", existingCount, targetStartupCount);
            seedDynamic(targetStartupCount, false);
        } else {
            log.info("MongoDB audit collection already contains {} records. Skipping automatic seeder execution.", existingCount);
        }
    }

    /**
     * Seeds dynamic audit logs into MongoDB NoSQL.
     *
     * @param count Number of audit logs to generate (1 to 500).
     * @param force Whether to force re-seeding even if collection already has >= count records.
     * @return Execution summary map with analytics and metrics.
     * @throws IllegalArgumentException if count is outside [1, 500].
     */
    public Map<String, Object> seedDynamic(int count, boolean force) {
        validateSeederCount(count);

        long startTime = System.currentTimeMillis();
        long existingCount = auditLogRepository.count();

        if (existingCount >= count && !force) {
            log.info("Audit seeder skipped. Database already contains {} records (>= requested {}). Set force=true to override.",
                    existingCount, count);
            return Map.of(
                    "status", "SKIPPED",
                    "message", "MongoDB audit collection already contains sufficient records for testing (current: " + existingCount + ")",
                    "requestedCount", count,
                    "currentDatabaseCount", existingCount,
                    "maxAllowedCount", MAX_SEEDER_COUNT
            );
        }

        log.info("Dynamically generating {} audit logs for MongoDB telemetry store (Max limit: {})...", count, MAX_SEEDER_COUNT);

        List<AuditEventTemplate> templates = getEventTemplates();
        Instant now = Instant.now();

        // Java Stream: Procedurally generate `count` realistic distributed audit events
        List<AuditLog> logsToSave = IntStream.range(0, count)
                .mapToObj(index -> {
                    AuditEventTemplate template = templates.get(index % templates.size());
                    int sessionGroup = index / 3; // Every 3 events share a trace context
                    String traceId = "TRC-FS-" + String.format("%04d", (1000 + sessionGroup));
                    String eventId = UUID.randomUUID().toString();
                    String userId = "USR_" + (100 + (index % 50));
                    String entityId = template.entityName().equalsIgnoreCase("Order")
                            ? "ORD-" + (202611180000L + (index % 100))
                            : "PRD-" + (1 + (index % 50));

                    // Execution time between 15ms and 320ms
                    long executionTimeMs = 15L + ((index * 17L + 23L) % 305L);

                    // Distributed timestamps spanning recent hours
                    Instant eventTimestamp = now.minus(Duration.ofMinutes((count - index) * 2L));

                    // Contextual telemetry payload
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("description", template.description());
                    payload.put("sequenceIndex", index + 1);
                    payload.put("sourceHost", template.serviceName() + "-pod-" + (1 + (index % 3)));
                    if (template.entityName().equalsIgnoreCase("Order")) {
                        payload.put("orderNumber", entityId);
                        payload.put("amount", 2500000.0 + ((index % 20) * 500000.0));
                        payload.put("currency", "IDR");
                    } else {
                        payload.put("productId", entityId);
                        payload.put("quantityDeducted", 1 + (index % 5));
                    }

                    // Metadata attributes
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("environment", "production");
                    metadata.put("ipAddress", "192.168.1." + (10 + (index % 80)));
                    metadata.put("userAgent", "FlashSaleMobileApp/3.2 (Android 14; Build/UP1A)");
                    metadata.put("correlationId", "CORR-" + UUID.randomUUID().toString().substring(0, 8));

                    return AuditLog.builder()
                            .eventId(eventId)
                            .traceId(traceId)
                            .serviceName(template.serviceName())
                            .eventType(template.eventType())
                            .action(template.action())
                            .userId(userId)
                            .entityId(entityId)
                            .entityName(template.entityName())
                            .status(template.status())
                            .executionTimeMs(executionTimeMs)
                            .errorMessage(template.status() == AuditStatus.FAILURE ? template.defaultErrorMessage() : null)
                            .payload(payload)
                            .metadata(metadata)
                            .timestamp(eventTimestamp)
                            .build();
                })
                .collect(Collectors.toList());

        // Batch persist into MongoDB collection
        List<AuditLog> savedLogs = auditLogRepository.saveAll(logsToSave);

        // Java Stream analytics & aggregations
        Map<String, Long> serviceDistribution = savedLogs.stream()
                .collect(Collectors.groupingBy(AuditLog::getServiceName, Collectors.counting()));

        Map<String, Long> eventTypeDistribution = savedLogs.stream()
                .collect(Collectors.groupingBy(logItem -> logItem.getEventType().name(), Collectors.counting()));

        Map<String, Long> statusDistribution = savedLogs.stream()
                .collect(Collectors.groupingBy(logItem -> logItem.getStatus().name(), Collectors.counting()));

        double averageExecutionTimeMs = savedLogs.stream()
                .mapToLong(AuditLog::getExecutionTimeMs)
                .average()
                .orElse(0.0);

        long minExecutionTimeMs = savedLogs.stream()
                .mapToLong(AuditLog::getExecutionTimeMs)
                .min()
                .orElse(0L);

        long maxExecutionTimeMs = savedLogs.stream()
                .mapToLong(AuditLog::getExecutionTimeMs)
                .max()
                .orElse(0L);

        long executionTimeMs = System.currentTimeMillis() - startTime;
        long totalCountAfter = auditLogRepository.count();

        log.info("==========================================================================");
        log.info("Dynamic Audit Seeder finished! Persisted {} logs to MongoDB in {} ms", savedLogs.size(), executionTimeMs);
        log.info("Total audit logs in MongoDB: {}", totalCountAfter);
        log.info("Average execution time: {} ms (Min: {} ms, Max: {} ms)",
                String.format("%.2f", averageExecutionTimeMs), minExecutionTimeMs, maxExecutionTimeMs);
        log.info("Distribution by Service: {}", serviceDistribution);
        log.info("Distribution by Status: {}", statusDistribution);
        log.info("==========================================================================");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "SUCCESS");
        summary.put("seededCount", savedLogs.size());
        summary.put("requestedCount", count);
        summary.put("maxAllowedCount", MAX_SEEDER_COUNT);
        summary.put("totalAuditLogsInDatabase", totalCountAfter);
        summary.put("averageExecutionTimeMs", Math.round(averageExecutionTimeMs * 100.0) / 100.0);
        summary.put("minExecutionTimeMs", minExecutionTimeMs);
        summary.put("maxExecutionTimeMs", maxExecutionTimeMs);
        summary.put("serviceDistribution", serviceDistribution);
        summary.put("statusDistribution", statusDistribution);
        summary.put("eventTypeDistribution", eventTypeDistribution);
        summary.put("executionTimeMs", executionTimeMs);

        return summary;
    }

    private void validateSeederCount(int count) {
        if (count < MIN_SEEDER_COUNT || count > MAX_SEEDER_COUNT) {
            log.error("Invalid audit seeder count requested: {}. Must be between {} and {}.",
                    count, MIN_SEEDER_COUNT, MAX_SEEDER_COUNT);
            throw new IllegalArgumentException(String.format(
                    "Seeder count must be between %d and %d. Requested count: %d",
                    MIN_SEEDER_COUNT, MAX_SEEDER_COUNT, count));
        }
    }

    private List<AuditEventTemplate> getEventTemplates() {
        return List.of(
                // 1. Order Placed
                new AuditEventTemplate("order-service", AuditEventType.ORDER_PLACED, "ORDER_CHECKOUT", "Order",
                        AuditStatus.SUCCESS, null, "Flash sale checkout successfully placed by customer"),

                // 2. Stock Reserved
                new AuditEventTemplate("order-service", AuditEventType.STOCK_RESERVED, "RESERVE_STOCK", "ProductStock",
                        AuditStatus.SUCCESS, null, "Distributed stock lock and decrement reserved in Redis grid"),

                // 3. Stock Deducted
                new AuditEventTemplate("product-service", AuditEventType.STOCK_DEDUCTED, "DECREASE_STOCK", "ProductStock",
                        AuditStatus.SUCCESS, null, "Inventory permanently deducted in product database"),

                // 4. Product Catalog Viewed
                new AuditEventTemplate("product-service", AuditEventType.FLASH_SALE_CLICK, "VIEW_PRODUCT", "Product",
                        AuditStatus.SUCCESS, null, "User tapped high-priority flash sale item banner"),

                // 5. Payment Completed
                new AuditEventTemplate("order-service", AuditEventType.ORDER_PAID, "PROCESS_PAYMENT", "Order",
                        AuditStatus.SUCCESS, null, "Payment gateway callback verified and order marked as PAID"),

                // 6. Insufficient Stock Warning/Failure
                new AuditEventTemplate("product-service", AuditEventType.INSUFFICIENT_STOCK, "DECREASE_STOCK_FAILED", "ProductStock",
                        AuditStatus.FAILURE, "Stock depleted: Requested quantity exceeds available inventory",
                        "High concurrency conflict: Flash sale product out of stock"),

                // 7. Order Cancelled
                new AuditEventTemplate("order-service", AuditEventType.ORDER_CANCELLED, "CANCEL_ORDER", "Order",
                        AuditStatus.WARNING, "Payment window expired after 15 minutes",
                        "Order auto-cancelled due to payment timeout"),

                // 8. Product Created
                new AuditEventTemplate("product-service", AuditEventType.PRODUCT_CREATED, "CREATE_PRODUCT", "Product",
                        AuditStatus.SUCCESS, null, "Admin catalog upload: New flash sale gadget created"),

                // 9. Gateway Security Rate-Limit
                new AuditEventTemplate("flashsale-gateway", AuditEventType.SECURITY_ALERT, "RATE_LIMIT_EXCEEDED", "Security",
                        AuditStatus.WARNING, "Token bucket rate limit reached: 100 req/s from single IP",
                        "API Gateway throttled excessive burst requests during flash sale opening"),

                // 10. System Cache Synced
                new AuditEventTemplate("system-monitor", AuditEventType.SYSTEM_EVENT, "CACHE_GRID_WARMED", "System",
                        AuditStatus.SUCCESS, null, "Redis in-memory cluster synchronized with product inventory")
        );
    }
}

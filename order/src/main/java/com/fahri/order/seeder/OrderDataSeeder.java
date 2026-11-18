package com.fahri.order.seeder;

import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.entity.Order;
import com.fahri.order.entity.OrderItem;
import com.fahri.order.entity.OrderStatus;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.repository.OrderSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Dynamic Java Order Seeder for Order Service.
 * Generates realistic flash sale order transactions across multiple customers,
 * product categories, and hourly buckets to test advanced native SQL analytics:
 * 1. DENSE_RANK() Top Spenders
 * 2. CTE + ROW_NUMBER() Top Products per Category
 * 3. Cumulative Running Total Window SUM() Hourly Trends
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDataSeeder implements CommandLineRunner {

    public static final int MIN_SEEDER_COUNT = 1;
    public static final int MAX_SEEDER_COUNT = 500;

    private final OrderRepository orderRepository;
    private final OrderSearchRepository orderSearchRepository;

    @Value("${app.seeder.order.enabled:false}")
    private boolean seederEnabled;

    @Value("${app.seeder.order.count:50}")
    private int defaultStartupCount;

    public record CustomerProfile(Long id, String email) {}

    public record CatalogItem(Long productId, String name, String category, BigDecimal price) {}

    private static final List<CustomerProfile> CUSTOMERS = List.of(
            new CustomerProfile(101L, "alex.tan@example.com"),
            new CustomerProfile(102L, "sarah.connor@example.com"),
            new CustomerProfile(103L, "bruce.wayne@example.com"),
            new CustomerProfile(104L, "diana.prince@example.com"),
            new CustomerProfile(105L, "clark.kent@example.com"),
            new CustomerProfile(106L, "peter.parker@example.com"),
            new CustomerProfile(107L, "tony.stark@example.com"),
            new CustomerProfile(108L, "natasha.romanoff@example.com")
    );

    private static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem(1L, "Flagship Smartphone Pro 256GB", "Electronics", new BigDecimal("15000000.00")),
            new CatalogItem(2L, "Ultra Thin Laptop 14 inch", "Electronics", new BigDecimal("18500000.00")),
            new CatalogItem(3L, "Wireless Noise Cancelling Headphones", "Accessories", new BigDecimal("5000000.00")),
            new CatalogItem(4L, "Mechanical Gaming Keyboard", "Gaming", new BigDecimal("1750000.00")),
            new CatalogItem(5L, "Gaming Console Next Gen", "Gaming", new BigDecimal("9500000.00")),
            new CatalogItem(6L, "OLED 4K 144Hz Gaming Monitor 32 inch", "Electronics", new BigDecimal("14500000.00")),
            new CatalogItem(7L, "Smartwatch Titanium Edition", "Wearables", new BigDecimal("8900000.00")),
            new CatalogItem(8L, "Rog Alloy Gaming Pad", "Gaming", new BigDecimal("10899000.00")),
            new CatalogItem(9L, "Apple iPhone 15 Pro 128GB", "Smartphone", new BigDecimal("18499000.00")),
            new CatalogItem(10L, "USB-C GaN 100W Fast Charger", "Accessories", new BigDecimal("450000.00"))
    );

    private static final List<String> PAYMENT_METHODS = List.of(
            "VIRTUAL_ACCOUNT", "CREDIT_CARD", "QRIS", "DIRECT_DEBIT"
    );

    @Override
    public void run(String... args) {
        if (!seederEnabled) {
            log.info("OrderDataSeeder startup execution is disabled by configuration.");
            return;
        }

        int targetStartupCount = Math.min(Math.max(defaultStartupCount, MIN_SEEDER_COUNT), MAX_SEEDER_COUNT);
        long existingCount = orderRepository.count();

        if (existingCount < targetStartupCount) {
            log.info("Current order count is {} (< {}). Executing OrderDataSeeder...", existingCount, targetStartupCount);
            seedDynamic(targetStartupCount, false);
        } else {
            log.info("Database already contains {} orders. Skipping automatic startup seeder.", existingCount);
        }
    }

    /**
     * Seeds a dynamic number of orders into PostgreSQL and indexes them into Elasticsearch.
     *
     * @param count The number of orders to generate (bounded 1..500).
     * @param force Whether to force seeding even if count already exists.
     * @return Execution summary map with analytics.
     */
    @Transactional
    public Map<String, Object> seedDynamic(int count, boolean force) {
        validateSeederCount(count);

        long startTime = System.currentTimeMillis();
        long existingCount = orderRepository.count();

        if (existingCount >= count && !force) {
            log.info("Seeder skipped. Database already has {} orders (>= requested {}). Set force=true to override.",
                    existingCount, count);
            return Map.of(
                    "status", "SKIPPED",
                    "message", "Database already contains sufficient orders for testing (current: " + existingCount + ")",
                    "requestedCount", count,
                    "currentDatabaseCount", existingCount,
                    "maxAllowedCount", MAX_SEEDER_COUNT
            );
        }

        log.info("Generating {} orders for analytics testing...", count);
        LocalDateTime baseTime = LocalDateTime.now();

        List<Order> ordersToSave = IntStream.range(0, count)
                .mapToObj(index -> {
                    CustomerProfile customer = CUSTOMERS.get(index % CUSTOMERS.size());
                    String orderNumber = String.format("ORD-%s-%04d",
                            baseTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                            existingCount + index + 1);

                    // Spread orders across the past 24 hours to create distinct hourly buckets
                    int hoursAgo = (index % 24);
                    int minutesOffset = (index * 7) % 60;
                    LocalDateTime orderTime = baseTime.minusHours(hoursAgo).minusMinutes(minutesOffset);

                    // Pick 1 to 3 items
                    int itemCount = 1 + (index % 3);
                    List<OrderItem> items = new ArrayList<>();
                    BigDecimal totalAmount = BigDecimal.ZERO;

                    for (int i = 0; i < itemCount; i++) {
                        CatalogItem catalogItem = CATALOG.get((index + i) % CATALOG.size());
                        int qty = 1 + ((index + i) % 2);
                        BigDecimal subtotal = catalogItem.price().multiply(BigDecimal.valueOf(qty));
                        totalAmount = totalAmount.add(subtotal);

                        OrderItem item = OrderItem.builder()
                                .productId(catalogItem.productId())
                                .productName(catalogItem.name())
                                .category(catalogItem.category())
                                .price(catalogItem.price())
                                .quantity(qty)
                                .subtotal(subtotal)
                                .createdAt(orderTime)
                                .build();
                        items.add(item);
                    }

                    // Apply discount based on voucher
                    BigDecimal discountAmount = BigDecimal.ZERO;
                    String voucherCode = null;
                    if (index % 3 == 0) {
                        voucherCode = "FLASHSALE10";
                        discountAmount = totalAmount.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
                    } else if (index % 5 == 0) {
                        voucherCode = "DISCOUNT50K";
                        discountAmount = new BigDecimal("50000.00").min(totalAmount);
                    }

                    BigDecimal finalAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
                    OrderStatus status = (index % 10 == 0) ? OrderStatus.CONFIRMED : OrderStatus.COMPLETED;
                    String paymentMethod = PAYMENT_METHODS.get(index % PAYMENT_METHODS.size());

                    Order order = Order.builder()
                            .orderNumber(orderNumber)
                            .customerId(customer.id())
                            .customerEmail(customer.email())
                            .totalAmount(totalAmount)
                            .discountAmount(discountAmount)
                            .finalAmount(finalAmount)
                            .voucherCode(voucherCode)
                            .status(status)
                            .paymentMethod(paymentMethod)
                            .createdAt(orderTime)
                            .updatedAt(orderTime)
                            .build();

                    items.forEach(order::addItem);
                    return order;
                })
                .collect(Collectors.toList());

        List<Order> savedOrders = orderRepository.saveAll(ordersToSave);

        // Synchronize into Elasticsearch if repository is accessible
        long indexedCount = 0;
        try {
            List<OrderSearchDocument> searchDocuments = savedOrders.stream()
                    .map(order -> {
                        String productNames = order.getItems().stream()
                                .map(OrderItem::getProductName)
                                .collect(Collectors.joining(", "));
                        List<String> categories = order.getItems().stream()
                                .map(OrderItem::getCategory)
                                .distinct()
                                .collect(Collectors.toList());

                        return OrderSearchDocument.builder()
                                .id(order.getId() != null ? String.valueOf(order.getId()) : UUID.randomUUID().toString())
                                .orderNumber(order.getOrderNumber())
                                .customerId(order.getCustomerId())
                                .customerEmail(order.getCustomerEmail())
                                .status(order.getStatus().name())
                                .totalAmount(order.getTotalAmount())
                                .finalAmount(order.getFinalAmount())
                                .voucherCode(order.getVoucherCode())
                                .productNames(productNames)
                                .categories(categories)
                                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : LocalDateTime.now().toString())
                                .build();
                    })
                    .collect(Collectors.toList());

            orderSearchRepository.saveAll(searchDocuments);
            indexedCount = searchDocuments.size();
        } catch (Exception ex) {
            log.warn("Elasticsearch indexing skipped during order seeder: {}", ex.getMessage());
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        BigDecimal totalRevenue = savedOrders.stream()
                .map(Order::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> categoryBreakdown = savedOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .collect(Collectors.groupingBy(OrderItem::getCategory, Collectors.counting()));

        log.info("OrderDataSeeder completed: {} orders saved in {}ms. Total revenue: {}",
                savedOrders.size(), executionTimeMs, totalRevenue);

        return Map.of(
                "status", "SUCCESS",
                "seededCount", savedOrders.size(),
                "requestedCount", count,
                "totalRevenue", totalRevenue,
                "elasticsearchIndexedCount", indexedCount,
                "categoryBreakdown", categoryBreakdown,
                "executionTimeMs", executionTimeMs
        );
    }

    private void validateSeederCount(int count) {
        if (count < MIN_SEEDER_COUNT || count > MAX_SEEDER_COUNT) {
            log.error("Invalid order seeder count: {}. Must be between {} and {}.",
                    count, MIN_SEEDER_COUNT, MAX_SEEDER_COUNT);
            throw new IllegalArgumentException(String.format(
                    "Seeder count must be between %d and %d. Requested count: %d",
                    MIN_SEEDER_COUNT, MAX_SEEDER_COUNT, count));
        }
    }
}

package com.fahri.product.service.impl;

import com.fahri.product.dto.CreateProductRequest;
import com.fahri.product.dto.ProductResponse;
import com.fahri.product.entity.Product;
import com.fahri.product.exception.InsufficientStockException;
import com.fahri.product.exception.NotFoundException;
import com.fahri.product.queue.AuditProducer;
import com.fahri.product.queue.dto.OrderItemEvent;
import com.fahri.product.repository.ProductRepository;
import com.fahri.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditProducer auditProducer;
    private final com.fahri.product.service.ProductSearchService productSearchService;

    private static final String REDIS_STOCK_PREFIX = "product:stock:";

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::mapAndSyncWithRedis)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsPaged(Pageable pageable) {
        return productRepository.findAvailableProducts(pageable)
                .map(this::mapAndSyncWithRedis);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + id));

        return mapAndSyncWithRedis(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating new product: {}", request.getName());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .category(request.getCategory())
                .build();

        Product savedProduct = productRepository.save(product);

        if (savedProduct.getId() != null && savedProduct.getStock() != null) {
            redisTemplate.opsForValue().set(REDIS_STOCK_PREFIX + savedProduct.getId(), savedProduct.getStock());
        }

        productSearchService.indexProduct(savedProduct);

        publishAuditCreate(savedProduct);

        return ProductResponse.fromEntity(savedProduct);
    }

    private void publishAuditCreate(Product savedProduct) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", savedProduct.getId());
        payload.put("name", savedProduct.getName());
        payload.put("price", savedProduct.getPrice());
        payload.put("stock", savedProduct.getStock());
        payload.put("category", savedProduct.getCategory());
        auditProducer.logActivity("PRODUCT_CREATED", "CREATE_PRODUCT", "Product",
                String.valueOf(savedProduct.getId()), "SUCCESS", null, payload);
    }

    private ProductResponse mapAndSyncWithRedis(Product product) {
        ProductResponse response = ProductResponse.fromEntity(product);
        if (product.getId() == null) {
            return response;
        }

        String redisKey = REDIS_STOCK_PREFIX + product.getId();
        try {
            Object cachedStock = redisTemplate.opsForValue().get(redisKey);
            if (cachedStock == null || (cachedStock instanceof Number num && num.intValue() < 0)) {
                redisTemplate.opsForValue().set(redisKey, product.getStock());
                response.setStock(product.getStock());
                log.debug("Warmed Redis stock key {} with DB value {}", redisKey, product.getStock());
            } else if (cachedStock instanceof Number num) {
                response.setStock(num.intValue());
            }
        } catch (Exception ex) {
            log.warn("Non-critical Redis sync error for product {}: {}", product.getId(), ex.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public void decreaseStockForOrder(String orderNumber, List<OrderItemEvent> items) {
        if (items == null || items.isEmpty()) {
            log.warn("Received empty item list to decrease stock for orderNumber: {}", orderNumber);
            return;
        }

        log.info("Processing stock deduction for orderNumber: {} with {} item entries", orderNumber, items.size());

        Map<Long, Integer> productQuantities = items.stream()
                .filter(item -> item.getProductId() != null && item.getQuantity() != null && item.getQuantity() > 0)
                .collect(Collectors.groupingBy(
                        OrderItemEvent::getProductId,
                        Collectors.summingInt(OrderItemEvent::getQuantity)
                ));

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();

            int updatedRows = productRepository.decreaseStockAtomic(productId, quantity);

            if (updatedRows == 0) {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new NotFoundException("Product not found with id: " + productId));

                log.error("Stock deduction failed for product [id={}, name={}]. Requested: {}, Available: {}",
                        productId, product.getName(), quantity, product.getStock());

                String errorMsg = "Insufficient stock for product id " + productId + " (" + product.getName() + "). Available: " + product.getStock() + ", Requested: " + quantity;

                // Publish Audit Event for Stock Failure
                Map<String, Object> errorPayload = new HashMap<>();
                errorPayload.put("orderNumber", orderNumber);
                errorPayload.put("productId", productId);
                errorPayload.put("productName", product.getName());
                errorPayload.put("requestedQuantity", quantity);
                errorPayload.put("availableStock", product.getStock());
                auditProducer.logActivity("INSUFFICIENT_STOCK", "DECREASE_STOCK_FAILED", "ProductStock",
                        String.valueOf(productId), "FAILURE", errorMsg, errorPayload);

                throw new InsufficientStockException(errorMsg);
            }

            try {
                redisTemplate.opsForValue().decrement(REDIS_STOCK_PREFIX + productId, quantity);
                // Evict Spring Cache so subsequent getProductById fetches updated stock
                redisTemplate.delete("products::" + productId);
            } catch (Exception ex) {
                log.debug("Redis stock decrement sync skipped: {}", ex.getMessage());
            }

            // Sync updated stock to Elasticsearch
            productRepository.findById(productId).ifPresent(productSearchService::indexProduct);

            log.info("Successfully decreased stock by {} for product id: {} (order: {})", quantity, productId, orderNumber);
        }

        // Publish Audit Event for Successful Stock Deduction
        Map<String, Object> successPayload = new HashMap<>();
        successPayload.put("orderNumber", orderNumber);
        successPayload.put("itemCount", items.size());
        successPayload.put("deductedQuantities", productQuantities);
        auditProducer.logActivity("STOCK_DEDUCTED", "DECREASE_STOCK_SUCCESS", "ProductStock",
                orderNumber, "SUCCESS", null, successPayload);
    }
}

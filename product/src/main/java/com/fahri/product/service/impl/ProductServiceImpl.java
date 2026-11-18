package com.fahri.product.service.impl;

import com.fahri.product.dto.CreateProductRequest;
import com.fahri.product.dto.ProductResponse;
import com.fahri.product.entity.Product;
import com.fahri.product.exception.InsufficientStockException;
import com.fahri.product.exception.NotFoundException;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

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

        // Sync initial stock into Redis Data Grid
        if (savedProduct.getId() != null && savedProduct.getStock() != null) {
            redisTemplate.opsForValue().set(REDIS_STOCK_PREFIX + savedProduct.getId(), savedProduct.getStock());
        }

        return ProductResponse.fromEntity(savedProduct);
    }

    private ProductResponse mapAndSyncWithRedis(Product product) {
        ProductResponse response = ProductResponse.fromEntity(product);
        if (product.getId() == null) {
            return response;
        }

        String redisKey = REDIS_STOCK_PREFIX + product.getId();
        try {
            Object cachedStock = redisTemplate.opsForValue().get(redisKey);
            if (cachedStock == null) {
                redisTemplate.opsForValue().set(redisKey, product.getStock());
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

                throw new InsufficientStockException("Insufficient stock for product id " + productId +
                        " (" + product.getName() + "). Available: " + product.getStock() + ", Requested: " + quantity);
            }

            try {
                redisTemplate.opsForValue().decrement(REDIS_STOCK_PREFIX + productId, quantity);
            } catch (Exception ex) {
                log.debug("Redis stock decrement sync skipped: {}", ex.getMessage());
            }

            log.info("Successfully decreased stock by {} for product id: {} (order: {})", quantity, productId, orderNumber);
        }
    }
}

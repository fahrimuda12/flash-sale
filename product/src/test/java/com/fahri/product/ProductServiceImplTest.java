package com.fahri.product;

import com.fahri.product.dto.CreateProductRequest;
import com.fahri.product.dto.ProductResponse;
import com.fahri.product.entity.Product;
import com.fahri.product.exception.InsufficientStockException;
import com.fahri.product.exception.NotFoundException;
import com.fahri.product.repository.ProductRepository;
import com.fahri.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Service Implementation Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        sampleProduct = Product.builder()
                .id(1L)
                .name("Flash Sale Smartphone")
                .description("Flagship device")
                .price(BigDecimal.valueOf(10000000))
                .stock(50)
                .category("Electronics")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createProduct should save product and sync stock to Redis Data Grid")
    void testCreateProduct_Success() {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("Flash Sale Smartphone")
                .description("Flagship device")
                .price(BigDecimal.valueOf(10000000))
                .stock(50)
                .category("Electronics")
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Flash Sale Smartphone", response.getName());
        assertEquals(50, response.getStock());

        verify(productRepository).save(any(Product.class));
        verify(valueOperations).set("product:stock:1", 50);
    }

    @Test
    @DisplayName("getProductById should return product and warm up Redis stock on cache miss")
    void testGetProductById_CacheMiss_WarmsRedis() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(valueOperations.get("product:stock:1")).thenReturn(null);

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Flash Sale Smartphone", response.getName());
        assertEquals(50, response.getStock());

        verify(valueOperations).set("product:stock:1", 50);
    }

    @Test
    @DisplayName("getProductById should reflect real-time Redis stock on cache hit")
    void testGetProductById_CacheHit_UsesRedisStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(valueOperations.get("product:stock:1")).thenReturn(42);

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals(42, response.getStock());
        verify(valueOperations, never()).set(eq("product:stock:1"), any());
    }

    @Test
    @DisplayName("getProductById should throw ProductNotFoundException when missing")
    void testGetProductById_NotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> productService.getProductById(99L));
    }

    @Test
    @DisplayName("getAllProducts should transform all entities and sync with Redis Data Grid")
    void testGetAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));
        when(valueOperations.get("product:stock:1")).thenReturn(50);

        List<ProductResponse> responses = productService.getAllProducts();

        assertEquals(1, responses.size());
        assertEquals(1L, responses.getFirst().getId());
        assertEquals(50, responses.getFirst().getStock());
    }

    @Test
    @DisplayName("decreaseStockForOrder should atomically update stock and decrement Redis count")
    void testDecreaseStockForOrder_Success() {
        com.fahri.product.queue.dto.OrderItemEvent item1 = com.fahri.product.queue.dto.OrderItemEvent.builder()
                .productId(1L)
                .productName("Flash Sale Smartphone")
                .quantity(2)
                .build();

        when(productRepository.decreaseStockAtomic(1L, 2)).thenReturn(1);

        productService.decreaseStockForOrder("ORD-12345", List.of(item1));

        verify(productRepository).decreaseStockAtomic(1L, 2);
        verify(valueOperations).decrement("product:stock:1", 2);
    }

    @Test
    @DisplayName("decreaseStockForOrder should throw InsufficientStockException when atomic update fails")
    void testDecreaseStockForOrder_InsufficientStock() {
        com.fahri.product.queue.dto.OrderItemEvent item1 = com.fahri.product.queue.dto.OrderItemEvent.builder()
                .productId(1L)
                .productName("Flash Sale Smartphone")
                .quantity(99)
                .build();

        when(productRepository.decreaseStockAtomic(1L, 99)).thenReturn(0);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(InsufficientStockException.class, () ->
                productService.decreaseStockForOrder("ORD-12345", List.of(item1))
        );

        verify(productRepository).decreaseStockAtomic(1L, 99);
        verify(valueOperations, never()).decrement(anyString(), anyLong());
    }
}

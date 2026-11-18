package com.fahri.product;

import com.fahri.product.entity.Product;
import com.fahri.product.repository.ProductRepository;
import com.fahri.product.seeder.ProductDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic Product Data Seeder Unit Tests")
class ProductDataSeederTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private com.fahri.product.service.ProductSearchService productSearchService;

    @InjectMocks
    private ProductDataSeeder productDataSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productDataSeeder, "seederEnabled", true);
        ReflectionTestUtils.setField(productDataSeeder, "defaultStartupCount", 100);
    }

    @Test
    @DisplayName("Specs generation should return 100 base template products")
    void testGetPerformanceProductSpecs_Returns100BaseItems() {
        List<ProductDataSeeder.ProductSeedSpec> specs = productDataSeeder.getPerformanceProductSpecs();

        assertNotNull(specs);
        assertEquals(100, specs.size(), "Should have exactly 100 base product specifications");

        specs.forEach(spec -> {
            assertNotNull(spec.name());
            assertFalse(spec.name().isBlank());
            assertNotNull(spec.category());
            assertNotNull(spec.price());
            assertTrue(spec.price().doubleValue() > 0);
            assertTrue(spec.stock() > 0);
            assertNotNull(spec.description());
        });
    }

    @Test
    @DisplayName("seedDynamic should accurately generate 50 products when requested")
    void testSeedDynamic_50Products() {
        when(productRepository.count())
                .thenReturn(0L)
                .thenReturn(50L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AtomicLong idGenerator = new AtomicLong(1L);
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Product> products = invocation.getArgument(0);
            return products.stream()
                    .map(p -> {
                        p.setId(idGenerator.getAndIncrement());
                        return p;
                    })
                    .collect(Collectors.toList());
        });

        Map<String, Object> result = productDataSeeder.seedDynamic(50, false);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(50, result.get("seededCount"));
        assertEquals(50, result.get("requestedCount"));
        assertEquals(500, result.get("maxAllowedCount"));
        assertEquals(50L, result.get("totalProductsInDatabase"));
        assertTrue((int) result.get("totalInventoryStock") > 0);

        verify(productRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should support maximum boundary of exactly 500 products")
    void testSeedDynamic_Max500Products() {
        when(productRepository.count())
                .thenReturn(0L)
                .thenReturn(500L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AtomicLong idGenerator = new AtomicLong(1L);
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Product> products = invocation.getArgument(0);
            return products.stream()
                    .map(p -> {
                        p.setId(idGenerator.getAndIncrement());
                        return p;
                    })
                    .collect(Collectors.toList());
        });

        Map<String, Object> result = productDataSeeder.seedDynamic(500, false);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(500, result.get("seededCount"));
        assertEquals(500, result.get("requestedCount"));
        assertEquals(500L, result.get("totalProductsInDatabase"));

        verify(productRepository, times(1)).saveAll(argThat(list -> ((List<?>) list).size() == 500));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -50, 501, 502, 1000})
    @DisplayName("seedDynamic should throw IllegalArgumentException when count is outside [1, 500]")
    void testSeedDynamic_ThrowsExceptionOnInvalidBoundaries(int invalidCount) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                productDataSeeder.seedDynamic(invalidCount, false));

        assertTrue(ex.getMessage().contains("1 and 500"));
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should skip when database count >= requested count and force is false")
    void testSeedDynamic_SkippedWhenAlreadyPopulated() {
        when(productRepository.count()).thenReturn(150L);

        Map<String, Object> result = productDataSeeder.seedDynamic(100, false);

        assertNotNull(result);
        assertEquals("SKIPPED", result.get("status"));
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should re-seed when force is true even if database has records")
    void testSeedDynamic_ForcesWhenRequested() {
        when(productRepository.count())
                .thenReturn(150L)  // initial check
                .thenReturn(300L); // after re-seed

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AtomicLong idGen = new AtomicLong(151L);
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Product> products = invocation.getArgument(0);
            return products.stream()
                    .map(p -> {
                        p.setId(idGen.getAndIncrement());
                        return p;
                    })
                    .collect(Collectors.toList());
        });

        Map<String, Object> result = productDataSeeder.seedDynamic(150, true);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(150, result.get("seededCount"));
        verify(productRepository, times(1)).saveAll(anyList());
    }
}

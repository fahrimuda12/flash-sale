package com.fahri.order;

import com.fahri.order.entity.Order;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.repository.OrderSearchRepository;
import com.fahri.order.seeder.OrderDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic Order Data Seeder Unit Tests")
class OrderDataSeederTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderSearchRepository orderSearchRepository;

    @InjectMocks
    private OrderDataSeeder orderDataSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderDataSeeder, "seederEnabled", true);
        ReflectionTestUtils.setField(orderDataSeeder, "defaultStartupCount", 50);
    }

    @Test
    @DisplayName("seedDynamic should generate and save requested orders")
    void testSeedDynamic_Success() {
        when(orderRepository.count()).thenReturn(0L);
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = orderDataSeeder.seedDynamic(25, false);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(25, result.get("seededCount"));
        assertEquals(25, result.get("requestedCount"));
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should skip if database already has enough orders without force")
    void testSeedDynamic_SkippedWhenAlreadyPopulated() {
        when(orderRepository.count()).thenReturn(100L);

        Map<String, Object> result = orderDataSeeder.seedDynamic(50, false);

        assertNotNull(result);
        assertEquals("SKIPPED", result.get("status"));
        assertEquals(50, result.get("requestedCount"));
        verify(orderRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should throw IllegalArgumentException when count exceeds boundary")
    void testSeedDynamic_InvalidCount() {
        assertThrows(IllegalArgumentException.class, () -> orderDataSeeder.seedDynamic(0, false));
        assertThrows(IllegalArgumentException.class, () -> orderDataSeeder.seedDynamic(501, false));
    }
}

package com.fahri.order;

import com.fahri.order.exception.GlobalExceptionHandler;
import com.fahri.order.seeder.OrderDataSeeder;
import com.fahri.order.seeder.OrderSeederController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Seeder Controller Unit Tests")
class OrderSeederControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderDataSeeder orderDataSeeder;

    @InjectMocks
    private OrderSeederController orderSeederController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderSeederController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/orders/seed with valid count returns 200 OK")
    void testSeedOrders_Success() throws Exception {
        when(orderDataSeeder.seedDynamic(50, false))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 50,
                        "requestedCount", 50,
                        "maxAllowedCount", 500
                ));

        mockMvc.perform(post("/api/orders/seed")
                        .param("count", "50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(50));
    }

    @Test
    @DisplayName("POST /api/orders/seed/performance alias returns 200 OK")
    void testSeedPerformanceOrders_Success() throws Exception {
        when(orderDataSeeder.seedDynamic(100, true))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 100,
                        "requestedCount", 100
                ));

        mockMvc.perform(post("/api/orders/seed/performance")
                        .param("count", "100")
                        .param("force", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(100));
    }
}

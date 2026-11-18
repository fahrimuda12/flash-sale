package com.fahri.product;

import com.fahri.product.exception.GlobalExceptionHandler;
import com.fahri.product.seeder.ProductDataSeeder;
import com.fahri.product.seeder.ProductSeederController;
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Seeder Controller Unit Tests")
class ProductSeederControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductDataSeeder productDataSeeder;

    @InjectMocks
    private ProductSeederController productSeederController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productSeederController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/products/seed with valid count returns 200 OK")
    void testSeedProducts_Success() throws Exception {
        when(productDataSeeder.seedDynamic(150, false))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 150,
                        "requestedCount", 150,
                        "maxAllowedCount", 500
                ));

        mockMvc.perform(post("/api/products/seed")
                        .param("count", "150")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(150))
                .andExpect(jsonPath("$.requestedCount").value(150))
                .andExpect(jsonPath("$.maxAllowedCount").value(500));
    }

    @Test
    @DisplayName("POST /api/products/seed/performance alias returns 200 OK")
    void testSeedPerformanceProducts_Success() throws Exception {
        when(productDataSeeder.seedDynamic(250, true))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 250,
                        "requestedCount", 250
                ));

        mockMvc.perform(post("/api/products/seed/performance")
                        .param("count", "250")
                        .param("force", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(250));
    }

    @Test
    @DisplayName("POST /api/products/seed with count > 500 returns 400 Bad Request")
    void testSeedProducts_ExceedsMaxLimit_Returns400() throws Exception {
        when(productDataSeeder.seedDynamic(anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Seeder count must be between 1 and 500. Requested count: 600"));

        mockMvc.perform(post("/api/products/seed")
                        .param("count", "600")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Seeder count must be between 1 and 500. Requested count: 600"));
    }

    @Test
    @DisplayName("POST /api/products/seed with count < 1 returns 400 Bad Request")
    void testSeedProducts_BelowMinLimit_Returns400() throws Exception {
        when(productDataSeeder.seedDynamic(anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Seeder count must be between 1 and 500. Requested count: 0"));

        mockMvc.perform(post("/api/products/seed")
                        .param("count", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Seeder count must be between 1 and 500. Requested count: 0"));
    }
}

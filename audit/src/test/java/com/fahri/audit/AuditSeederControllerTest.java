package com.fahri.audit;

import com.fahri.audit.exception.GlobalExceptionHandler;
import com.fahri.audit.seeder.AuditDataSeeder;
import com.fahri.audit.seeder.AuditSeederController;
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
@DisplayName("Audit Seeder Controller Unit Tests")
class AuditSeederControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditDataSeeder auditDataSeeder;

    @InjectMocks
    private AuditSeederController auditSeederController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditSeederController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/audit/seed with valid count returns 200 OK")
    void testSeedAuditLogs_Success() throws Exception {
        when(auditDataSeeder.seedDynamic(120, false))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 120,
                        "requestedCount", 120,
                        "maxAllowedCount", 500
                ));

        mockMvc.perform(post("/api/audit/seed")
                        .param("count", "120")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(120))
                .andExpect(jsonPath("$.requestedCount").value(120))
                .andExpect(jsonPath("$.maxAllowedCount").value(500));
    }

    @Test
    @DisplayName("POST /api/audit/seed/performance alias returns 200 OK")
    void testSeedPerformanceLogs_Success() throws Exception {
        when(auditDataSeeder.seedDynamic(200, true))
                .thenReturn(Map.of(
                        "status", "SUCCESS",
                        "seededCount", 200,
                        "requestedCount", 200
                ));

        mockMvc.perform(post("/api/audit/seed/performance")
                        .param("count", "200")
                        .param("force", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.seededCount").value(200));
    }

    @Test
    @DisplayName("POST /api/audit/seed with count > 500 returns 400 Bad Request")
    void testSeedAuditLogs_ExceedsMaxLimit_Returns400() throws Exception {
        when(auditDataSeeder.seedDynamic(anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Seeder count must be between 1 and 500. Requested count: 600"));

        mockMvc.perform(post("/api/audit/seed")
                        .param("count", "600")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Seeder count must be between 1 and 500. Requested count: 600"));
    }

    @Test
    @DisplayName("POST /api/audit/seed with count < 1 returns 400 Bad Request")
    void testSeedAuditLogs_BelowMinLimit_Returns400() throws Exception {
        when(auditDataSeeder.seedDynamic(anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Seeder count must be between 1 and 500. Requested count: 0"));

        mockMvc.perform(post("/api/audit/seed")
                        .param("count", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Seeder count must be between 1 and 500. Requested count: 0"));
    }
}

package com.fahri.audit;

import com.fahri.audit.model.AuditLog;
import com.fahri.audit.repository.AuditLogRepository;
import com.fahri.audit.seeder.AuditDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dynamic Audit Data Seeder Unit Tests")
class AuditDataSeederTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditDataSeeder auditDataSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditDataSeeder, "seederEnabled", true);
        ReflectionTestUtils.setField(auditDataSeeder, "defaultStartupCount", 100);
    }

    @Test
    @DisplayName("seedDynamic should generate and save exactly 50 audit logs to MongoDB")
    void testSeedDynamic_50Logs() {
        when(auditLogRepository.count())
                .thenReturn(0L)
                .thenReturn(50L);

        when(auditLogRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<AuditLog> logs = invocation.getArgument(0);
            return logs.stream()
                    .map(l -> {
                        l.setId("mongo-id-" + l.getEventId());
                        return l;
                    })
                    .collect(Collectors.toList());
        });

        Map<String, Object> result = auditDataSeeder.seedDynamic(50, false);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(50, result.get("seededCount"));
        assertEquals(50, result.get("requestedCount"));
        assertEquals(500, result.get("maxAllowedCount"));
        assertEquals(50L, result.get("totalAuditLogsInDatabase"));
        assertNotNull(result.get("serviceDistribution"));
        assertNotNull(result.get("statusDistribution"));
        assertNotNull(result.get("eventTypeDistribution"));

        verify(auditLogRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should generate maximum boundary of 500 audit logs")
    void testSeedDynamic_Max500Logs() {
        when(auditLogRepository.count())
                .thenReturn(0L)
                .thenReturn(500L);

        when(auditLogRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<AuditLog> logs = invocation.getArgument(0);
            return logs.stream()
                    .map(l -> {
                        l.setId("mongo-id-" + l.getEventId());
                        return l;
                    })
                    .collect(Collectors.toList());
        });

        Map<String, Object> result = auditDataSeeder.seedDynamic(500, false);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(500, result.get("seededCount"));
        assertEquals(500, result.get("requestedCount"));
        assertEquals(500L, result.get("totalAuditLogsInDatabase"));

        verify(auditLogRepository, times(1)).saveAll(argThat(list -> ((List<?>) list).size() == 500));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -50, 501, 502, 1000})
    @DisplayName("seedDynamic should throw IllegalArgumentException when count is outside [1, 500]")
    void testSeedDynamic_ThrowsExceptionOnInvalidBoundaries(int invalidCount) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                auditDataSeeder.seedDynamic(invalidCount, false));

        assertTrue(ex.getMessage().contains("1 and 500"));
        verify(auditLogRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should skip when collection already has records and force is false")
    void testSeedDynamic_SkippedWhenAlreadyPopulated() {
        when(auditLogRepository.count()).thenReturn(150L);

        Map<String, Object> result = auditDataSeeder.seedDynamic(100, false);

        assertNotNull(result);
        assertEquals("SKIPPED", result.get("status"));
        verify(auditLogRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("seedDynamic should re-seed when force is true even if collection already has records")
    void testSeedDynamic_ForcesWhenRequested() {
        when(auditLogRepository.count())
                .thenReturn(150L)  // initial check
                .thenReturn(250L); // after re-seed

        when(auditLogRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = auditDataSeeder.seedDynamic(100, true);

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(100, result.get("seededCount"));
        verify(auditLogRepository, times(1)).saveAll(anyList());
    }
}

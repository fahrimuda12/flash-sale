package com.fahri.audit.service.impl;

import com.fahri.audit.dto.AuditAnalyticsSummary;
import com.fahri.audit.dto.AuditResponse;
import com.fahri.audit.model.AuditLog;
import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.repository.AuditLogRepository;
import com.fahri.audit.service.AuditAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service demonstrating advanced Java Stream operations:
 * - Complex groupingBy collectors
 * - Stream partitioning
 * - LongSummaryStatistics on primitive stream
 * - Custom sorting, mapping, and limiting pipelines
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditAnalyticsServiceImpl implements AuditAnalyticsService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public AuditAnalyticsSummary computeAnalyticsSummary(Instant fromDate, Instant toDate) {
        Instant start = fromDate != null ? fromDate : Instant.now().minus(24, ChronoUnit.HOURS);
        Instant end = toDate != null ? toDate : Instant.now();

        List<AuditLog> logs = auditLogRepository.findLogsBetween(start, end);

        if (logs.isEmpty()) {
            return AuditAnalyticsSummary.builder()
                    .totalEvents(0)
                    .overallSuccessRate(100.0)
                    .latencyStats(new AuditAnalyticsSummary.LatencyStats(0.0, 0, 0, 0))
                    .eventsByService(Collections.emptyMap())
                    .eventsByStatus(Collections.emptyMap())
                    .eventsByType(Collections.emptyMap())
                    .topActiveUsers(Collections.emptyMap())
                    .recentSlowOperations(Collections.emptyList())
                    .highPriorityAlerts(Collections.emptyList())
                    .build();
        }

        Map<String, Long> eventsByService = logs.stream()
                .filter(l -> l.getServiceName() != null)
                .collect(Collectors.groupingBy(AuditLog::getServiceName, Collectors.counting()));

        Map<AuditStatus, Long> eventsByStatus = logs.stream()
                .filter(l -> l.getStatus() != null)
                .collect(Collectors.groupingBy(AuditLog::getStatus, Collectors.counting()));

        Map<AuditEventType, Long> eventsByType = logs.stream()
                .filter(l -> l.getEventType() != null)
                .collect(Collectors.groupingBy(AuditLog::getEventType, Collectors.counting()));

        Map<Boolean, List<AuditLog>> partitionedBySuccess = logs.stream()
                .collect(Collectors.partitioningBy(l -> l.getStatus() == AuditStatus.SUCCESS));

        long successCount = partitionedBySuccess.getOrDefault(true, Collections.emptyList()).size();
        double successRate = logs.isEmpty() ? 100.0 : ((double) successCount / logs.size()) * 100.0;

        LongSummaryStatistics latencyStats = logs.stream()
                .filter(l -> l.getExecutionTimeMs() != null)
                .mapToLong(AuditLog::getExecutionTimeMs)
                .summaryStatistics();

        AuditAnalyticsSummary.LatencyStats latencyDto = AuditAnalyticsSummary.LatencyStats.builder()
                .averageExecutionTimeMs(latencyStats.getCount() > 0 ? latencyStats.getAverage() : 0.0)
                .minExecutionTimeMs(latencyStats.getCount() > 0 ? latencyStats.getMin() : 0)
                .maxExecutionTimeMs(latencyStats.getCount() > 0 ? latencyStats.getMax() : 0)
                .sampleSize(latencyStats.getCount())
                .build();

        Map<String, Long> topActiveUsers = logs.stream()
                .filter(l -> l.getUserId() != null && !l.getUserId().isBlank())
                .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        List<AuditResponse> slowOperations = logs.stream()
                .filter(l -> l.getExecutionTimeMs() != null && l.getExecutionTimeMs() >= 300L)
                .sorted(Comparator.comparing(AuditLog::getExecutionTimeMs).reversed())
                .limit(5)
                .map(AuditResponse::fromEntity)
                .collect(Collectors.toList());

        List<AuditResponse> highPriorityAlerts = logs.stream()
                .filter(l -> l.getStatus() == AuditStatus.FAILURE ||
                        "HIGH".equals(l.getMetadata().get("security_severity")))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .limit(5)
                .map(AuditResponse::fromEntity)
                .collect(Collectors.toList());

        return AuditAnalyticsSummary.builder()
                .totalEvents(logs.size())
                .overallSuccessRate(Math.round(successRate * 100.0) / 100.0)
                .latencyStats(latencyDto)
                .eventsByService(eventsByService)
                .eventsByStatus(eventsByStatus)
                .eventsByType(eventsByType)
                .topActiveUsers(topActiveUsers)
                .recentSlowOperations(slowOperations)
                .highPriorityAlerts(highPriorityAlerts)
                .build();
    }

    @Override
    public Map<String, Double> computeAverageLatencyPerService() {
        List<AuditLog> allLogs = auditLogRepository.findAll();

        return allLogs.stream()
                .filter(l -> l.getServiceName() != null && l.getExecutionTimeMs() != null)
                .collect(Collectors.groupingBy(
                        AuditLog::getServiceName,
                        Collectors.averagingLong(AuditLog::getExecutionTimeMs)
                ));
    }

    @Override
    public List<AuditResponse> getHighPrioritySecurityAlerts() {
        return auditLogRepository.findFailedEvents(PageRequest.of(0, 20))
                .stream()
                .filter(l -> l.getMetadata().containsKey("security_severity") || l.getStatus() == AuditStatus.FAILURE)
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .map(AuditResponse::fromEntity)
                .collect(Collectors.toList());
    }
}

package com.fahri.audit.dto;

import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditAnalyticsSummary implements Serializable {

    private long totalEvents;
    private double overallSuccessRate;
    private LatencyStats latencyStats;
    private Map<String, Long> eventsByService;
    private Map<AuditStatus, Long> eventsByStatus;
    private Map<AuditEventType, Long> eventsByType;
    private Map<String, Long> topActiveUsers;
    private List<AuditResponse> recentSlowOperations;
    private List<AuditResponse> highPriorityAlerts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LatencyStats implements Serializable {
        private double averageExecutionTimeMs;
        private long minExecutionTimeMs;
        private long maxExecutionTimeMs;
        private long sampleSize;
    }
}

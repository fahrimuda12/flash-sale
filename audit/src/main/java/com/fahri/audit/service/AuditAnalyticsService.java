package com.fahri.audit.service;

import com.fahri.audit.dto.AuditAnalyticsSummary;
import com.fahri.audit.dto.AuditResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AuditAnalyticsService {

    AuditAnalyticsSummary computeAnalyticsSummary(Instant fromDate, Instant toDate);

    Map<String, Double> computeAverageLatencyPerService();

    List<AuditResponse> getHighPrioritySecurityAlerts();
}

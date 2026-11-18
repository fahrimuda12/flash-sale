package com.fahri.audit.controller;

import com.fahri.audit.dto.AuditAnalyticsSummary;
import com.fahri.audit.dto.AuditEventDto;
import com.fahri.audit.dto.AuditResponse;
import com.fahri.audit.dto.AuditSearchRequest;
import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.service.AuditAnalyticsService;
import com.fahri.audit.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final AuditAnalyticsService auditAnalyticsService;

    @PostMapping("/events")
    public ResponseEntity<AuditResponse> getAuditEvents(@Valid @RequestBody AuditEventDto eventDto) {
        AuditResponse created = auditService.recordAuditEvent(eventDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/logs/{id}")
    public ResponseEntity<AuditResponse> getLogById(@PathVariable String id) {
        return ResponseEntity.ok(auditService.getAuditLogById(id));
    }

    @GetMapping("/logs/trace/{traceId}")
    public ResponseEntity<List<AuditResponse>> getTraceTimeline(@PathVariable String traceId) {
        return ResponseEntity.ok(auditService.getTraceTimeline(traceId));
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<AuditResponse>> searchLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) Long minExecutionTimeMs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        AuditSearchRequest request = AuditSearchRequest.builder()
                .serviceName(serviceName)
                .eventType(eventType)
                .status(status)
                .userId(userId)
                .traceId(traceId)
                .fromDate(fromDate)
                .toDate(toDate)
                .minExecutionTimeMs(minExecutionTimeMs)
                .build();

        Sort sort = sortDirection.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(auditService.searchAuditLogs(request, pageRequest));
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<AuditAnalyticsSummary> getAnalyticsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        return ResponseEntity.ok(auditAnalyticsService.computeAnalyticsSummary(fromDate, toDate));
    }

    @GetMapping("/analytics/latency")
    public ResponseEntity<Map<String, Double>> getAverageLatencyPerService() {
        return ResponseEntity.ok(auditAnalyticsService.computeAverageLatencyPerService());
    }

    @GetMapping("/analytics/alerts")
    public ResponseEntity<List<AuditResponse>> getSecurityAlerts() {
        return ResponseEntity.ok(auditAnalyticsService.getHighPrioritySecurityAlerts());
    }
}

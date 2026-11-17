package com.fahri.audit.service;

import com.fahri.audit.dto.AuditEventDto;
import com.fahri.audit.dto.AuditResponse;
import com.fahri.audit.dto.AuditSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditService {

    AuditResponse recordAuditEvent(AuditEventDto eventDto);

    AuditResponse getAuditLogById(String id);

    List<AuditResponse> getTraceTimeline(String traceId);

    Page<AuditResponse> searchAuditLogs(AuditSearchRequest request, Pageable pageable);
}

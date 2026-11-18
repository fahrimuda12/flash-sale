package com.fahri.audit.dto;

import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditSearchRequest {

    private String serviceName;
    private AuditEventType eventType;
    private AuditStatus status;
    private String userId;
    private String traceId;
    private Instant fromDate;
    private Instant toDate;
    private Long minExecutionTimeMs;
}

package com.fahri.audit.dto;

import com.fahri.audit.model.AuditLog;
import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditResponse implements Serializable {

    private String id;
    private String eventId;
    private String traceId;
    private String serviceName;
    private AuditEventType eventType;
    private String action;
    private String userId;
    private String entityId;
    private String entityName;
    private AuditStatus status;
    private Long executionTimeMs;
    private String errorMessage;
    private Map<String, Object> payload;
    private Map<String, String> metadata;
    private Instant timestamp;

    public static AuditResponse fromEntity(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        return AuditResponse.builder()
                .id(entity.getId())
                .eventId(entity.getEventId())
                .traceId(entity.getTraceId())
                .serviceName(entity.getServiceName())
                .eventType(entity.getEventType())
                .action(entity.getAction())
                .userId(entity.getUserId())
                .entityId(entity.getEntityId())
                .entityName(entity.getEntityName())
                .status(entity.getStatus())
                .executionTimeMs(entity.getExecutionTimeMs())
                .errorMessage(entity.getErrorMessage())
                .payload(entity.getPayload())
                .metadata(entity.getMetadata())
                .timestamp(entity.getTimestamp())
                .build();
    }
}

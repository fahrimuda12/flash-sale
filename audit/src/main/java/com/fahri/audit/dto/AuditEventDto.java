package com.fahri.audit.dto;

import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditEventDto implements Serializable {

    private String eventId;

    private String traceId;

    @NotBlank(message = "Service name must not be blank, you must fill service name")
    private String serviceName;

    @NotNull(message = "Event type is required, you must fill event type")
    private AuditEventType eventType;

    @NotBlank(message = "Action must not be blank, you must fill action")
    private String action;

    private String userId;

    private String entityId;

    private String entityName;

    @Builder.Default
    private AuditStatus status = AuditStatus.SUCCESS;

    private Long executionTimeMs;

    private String clientIp;

    private String errorMessage;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    private Instant timestamp;
}

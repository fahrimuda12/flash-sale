package com.fahri.audit.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "audit_logs")
@CompoundIndexes({
    @CompoundIndex(name = "idx_service_timestamp", def = "{'serviceName': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_user_timestamp", def = "{'userId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_event_status", def = "{'eventType': 1, 'status': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLog implements Serializable {

    @Id
    private String id;

    @Indexed
    @Field("event_id")
    private String eventId;

    @Indexed
    @Field("trace_id")
    private String traceId;

    @Indexed
    @Field("service_name")
    private String serviceName;

    @Indexed
    @Field("event_type")
    private AuditEventType eventType;

    @Field("action")
    private String action;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("entity_id")
    private String entityId;

    @Field("entity_name")
    private String entityName;

    @Field("status")
    private AuditStatus status;

    @Field("execution_time_ms")
    private Long executionTimeMs;

    @Field("error_message")
    private String errorMessage;

    @Builder.Default
    @Field("payload")
    private Map<String, Object> payload = new HashMap<>();

    @Builder.Default
    @Field("metadata")
    private Map<String, String> metadata = new HashMap<>();

    @CreatedDate
    @Indexed
    @Field("timestamp")
    private Instant timestamp;
}

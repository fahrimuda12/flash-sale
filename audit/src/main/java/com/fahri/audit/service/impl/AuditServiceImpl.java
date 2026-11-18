package com.fahri.audit.service.impl;

import com.fahri.audit.dto.AuditEventDto;
import com.fahri.audit.dto.AuditResponse;
import com.fahri.audit.dto.AuditSearchRequest;
import com.fahri.audit.model.AuditLog;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.repository.AuditLogRepository;
import com.fahri.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public AuditResponse recordAuditEvent(AuditEventDto eventDto) {
        String eventId = eventDto.getEventId() != null ? eventDto.getEventId() : UUID.randomUUID().toString();
        String traceId = eventDto.getTraceId() != null ? eventDto.getTraceId() : UUID.randomUUID().toString();
        Instant timestamp = eventDto.getTimestamp() != null ? eventDto.getTimestamp() : Instant.now();

        Map<String, String> metadata = eventDto.getMetadata() != null
                ? new HashMap<>(eventDto.getMetadata())
                : new HashMap<>();


        AuditLog auditLog = AuditLog.builder()
                .eventId(eventId)
                .traceId(traceId)
                .serviceName(eventDto.getServiceName())
                .eventType(eventDto.getEventType())
                .action(eventDto.getAction())
                .userId(eventDto.getUserId())
                .entityId(eventDto.getEntityId())
                .entityName(eventDto.getEntityName())
                .status(eventDto.getStatus() != null ? eventDto.getStatus() : AuditStatus.SUCCESS)
                .executionTimeMs(eventDto.getExecutionTimeMs())
                .errorMessage(eventDto.getErrorMessage())
                .payload(eventDto.getPayload() != null ? eventDto.getPayload() : new HashMap<>())
                .metadata(metadata)
                .timestamp(timestamp)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        return AuditResponse.fromEntity(saved);
    }

//    private void enrichMetadata(AuditEventDto eventDto, Map<String, String> metadata) {
//        if (eventDto.getStatus() == AuditStatus.FAILURE || eventDto.getEventType() == AuditEventType.SECURITY_ALERT) {
//            metadata.put("security_severity", "HIGH");
//        }
//
//        if (eventDto.getExecutionTimeMs() != null) {
//            if (eventDto.getExecutionTimeMs() >= 1000L) {
//                metadata.put("sla_status", "BREACHED");
//            } else if (eventDto.getExecutionTimeMs() >= 300L) {
//                metadata.put("sla_status", "WARNING");
//            }
//        }
//    }

    @Override
    public AuditResponse getAuditLogById(String id) {
        return auditLogRepository.findById(id)
                .map(AuditResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Audit log not found with ID: " + id));
    }

    @Override
    public List<AuditResponse> getTraceTimeline(String traceId) {
        return auditLogRepository.findByTraceIdOrderByTimestampAsc(traceId)
                .stream()
                .map(AuditResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<AuditResponse> searchAuditLogs(AuditSearchRequest request, Pageable pageable) {
        Query query = new Query().with(pageable);
        List<Criteria> criteriaList = new ArrayList<>();

        if (request.getServiceName() != null && !request.getServiceName().isBlank()) {
            criteriaList.add(Criteria.where("serviceName").is(request.getServiceName()));
        }
        if (request.getEventType() != null) {
            criteriaList.add(Criteria.where("eventType").is(request.getEventType()));
        }
        if (request.getStatus() != null) {
            criteriaList.add(Criteria.where("status").is(request.getStatus()));
        }
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            criteriaList.add(Criteria.where("userId").is(request.getUserId()));
        }
        if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
            criteriaList.add(Criteria.where("traceId").is(request.getTraceId()));
        }
        if (request.getMinExecutionTimeMs() != null) {
            criteriaList.add(Criteria.where("executionTimeMs").gte(request.getMinExecutionTimeMs()));
        }
        if (request.getFromDate() != null && request.getToDate() != null) {
            criteriaList.add(Criteria.where("timestamp").gte(request.getFromDate()).lte(request.getToDate()));
        } else if (request.getFromDate() != null) {
            criteriaList.add(Criteria.where("timestamp").gte(request.getFromDate()));
        } else if (request.getToDate() != null) {
            criteriaList.add(Criteria.where("timestamp").lte(request.getToDate()));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query.skip(-1).limit(-1), AuditLog.class);
        List<AuditLog> results = mongoTemplate.find(query, AuditLog.class);

        List<AuditResponse> dtos = results.stream()
                .map(AuditResponse::fromEntity)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, total);
    }
}

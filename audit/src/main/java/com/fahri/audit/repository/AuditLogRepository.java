package com.fahri.audit.repository;

import com.fahri.audit.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findByTraceIdOrderByTimestampAsc(String traceId);

    @Query("{ 'timestamp': { $gte: ?0, $lte: ?1 } }")
    List<AuditLog> findLogsBetween(Instant fromDate, Instant toDate);

    @Query("{ 'status': 'FAILURE' }")
    List<AuditLog> findFailedEvents(Pageable pageable);
}

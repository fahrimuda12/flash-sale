package com.fahri.audit;

import com.fahri.audit.queue.AuditEventConsumer;
import com.fahri.audit.dto.AuditEventDto;
import com.fahri.audit.dto.AuditResponse;
import com.fahri.audit.model.AuditLog;
import com.fahri.audit.model.AuditEventType;
import com.fahri.audit.model.AuditStatus;
import com.fahri.audit.repository.AuditLogRepository;
import com.fahri.audit.service.AuditService;
import com.fahri.audit.service.impl.AuditServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Service Unit Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AuditServiceImpl auditService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully record audit event from product service into MongoDB")
    void testRecordProductServiceAuditEvent() {
        AuditEventDto dto = AuditEventDto.builder()
                .serviceName("product-service")
                .eventType(AuditEventType.PRODUCT_CREATED)
                .action("CREATE_PRODUCT")
                .entityName("Product")
                .entityId("1")
                .status(AuditStatus.SUCCESS)
                .payload(Map.of("name", "Flash Sale TV", "price", 10000000))
                .build();

        AuditLog savedLog = AuditLog.builder()
                .id("mongo-id-123")
                .serviceName("product-service")
                .eventType(AuditEventType.PRODUCT_CREATED)
                .action("CREATE_PRODUCT")
                .entityName("Product")
                .entityId("1")
                .status(AuditStatus.SUCCESS)
                .timestamp(Instant.now())
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditResponse response = auditService.recordAuditEvent(dto);

        assertNotNull(response);
        assertEquals("mongo-id-123", response.getId());
        assertEquals("product-service", response.getServiceName());
        assertEquals(AuditEventType.PRODUCT_CREATED, response.getEventType());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("AuditKafkaConsumer should consume raw order event and persist ORDER_PLACED audit")
    void testConsumeOrderEventFromOrderService() {
        AuditService mockAuditService = mock(AuditService.class);
        AuditEventConsumer consumer = new AuditEventConsumer(mockAuditService, objectMapper);

        String orderJson = """
                {
                    "orderNumber": "ORD-20261118-0001",
                    "customerId": 101,
                    "customerEmail": "alex.tan@example.com",
                    "totalAmount": 15000000.0,
                    "finalAmount": 13500000.0,
                    "paymentMethod": "VIRTUAL_ACCOUNT"
                }
                """;

        consumer.consumeOrderEvents(orderJson);

        verify(mockAuditService).recordAuditEvent(argThat(event ->
                "order-service".equals(event.getServiceName()) &&
                AuditEventType.ORDER_PLACED.equals(event.getEventType()) &&
                "ORD-20261118-0001".equals(event.getEntityId())
        ));
    }

    @Test
    @DisplayName("AuditKafkaConsumer should consume generic audit event from product service")
    void testConsumeProductAuditEvent() throws Exception {
        AuditService mockAuditService = mock(AuditService.class);
        AuditEventConsumer consumer = new AuditEventConsumer(mockAuditService, objectMapper);

        AuditEventDto dto = AuditEventDto.builder()
                .serviceName("product-service")
                .eventType(AuditEventType.STOCK_DEDUCTED)
                .action("DECREASE_STOCK_SUCCESS")
                .entityName("ProductStock")
                .entityId("ORD-20261118-0001")
                .status(AuditStatus.SUCCESS)
                .build();

        consumer.consumeAuditEvent(objectMapper.writeValueAsString(dto));

        verify(mockAuditService).recordAuditEvent(argThat(event ->
                "product-service".equals(event.getServiceName()) &&
                AuditEventType.STOCK_DEDUCTED.equals(event.getEventType())
        ));
    }
}

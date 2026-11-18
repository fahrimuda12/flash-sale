package com.fahri.order;

import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.entity.Order;
import com.fahri.order.entity.OrderItem;
import com.fahri.order.entity.OrderStatus;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.repository.OrderSearchRepository;
import com.fahri.order.service.impl.OrderSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Search Service Implementation Unit Tests")
class OrderSearchServiceImplTest {

    @Mock
    private OrderSearchRepository orderSearchRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private OrderSearchServiceImpl orderSearchService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .productId(101L)
                .productName("Samsung Galaxy S24 Ultra")
                .category("Smartphone")
                .price(new BigDecimal("19999000.00"))
                .quantity(1)
                .subtotal(new BigDecimal("19999000.00"))
                .build();

        sampleOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20261118-TEST1234")
                .customerId(99L)
                .customerEmail("buyer@example.com")
                .totalAmount(new BigDecimal("19999000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("19999000.00"))
                .status(OrderStatus.CONFIRMED)
                .paymentMethod("VIRTUAL_ACCOUNT")
                .items(List.of(item1))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("indexOrder should map Order entity to OrderSearchDocument and save to Elasticsearch")
    void testIndexOrder_Success() {
        orderSearchService.indexOrder(sampleOrder);

        verify(orderSearchRepository, times(1)).save(argThat(doc ->
                doc.getOrderNumber().equals("ORD-20261118-TEST1234") &&
                doc.getCustomerEmail().equals("buyer@example.com") &&
                doc.getProductNames().contains("Samsung Galaxy S24 Ultra") &&
                doc.getCategories().contains("Smartphone")
        ));
    }

    @Test
    @DisplayName("indexAllOrders should batch index orders into Elasticsearch")
    void testIndexAllOrders_Success() {
        orderSearchService.indexAllOrders(List.of(sampleOrder));

        verify(orderSearchRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("syncAllFromDatabase should fetch all orders from PostgreSQL and index to Elasticsearch")
    void testSyncAllFromDatabase_Success() {
        when(orderRepository.findAll()).thenReturn(List.of(sampleOrder));

        Map<String, Object> result = orderSearchService.syncAllFromDatabase();

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("totalSynced"));
        verify(orderSearchRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("searchOrders should execute Elasticsearch query and return paged results")
    void testSearchOrders_ElasticsearchSuccess() {
        OrderSearchDocument doc = OrderSearchDocument.fromEntity(sampleOrder);
        SearchHit<OrderSearchDocument> hit = new SearchHit<>(
                "orders_search", doc.getId(), null, 1.0f, null, null, null, null, null, null, doc
        );
        SearchHits<OrderSearchDocument> hits = new SearchHitsImpl<>(
                1, TotalHitsRelation.EQUAL_TO, 1.0f, null, null, null, List.of(hit), null, null, null
        );

        when(elasticsearchOperations.search(any(Query.class), eq(OrderSearchDocument.class))).thenReturn(hits);

        Page<OrderSearchDocument> result = orderSearchService.searchOrders(
                "Samsung", "buyer@example.com", "CONFIRMED", "Smartphone", PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("ORD-20261118-TEST1234", result.getContent().getFirst().getOrderNumber());
    }

    @Test
    @DisplayName("searchOrders should gracefully fallback to PostgreSQL database when Elasticsearch fails")
    void testSearchOrders_DatabaseFallback() {
        when(elasticsearchOperations.search(any(Query.class), eq(OrderSearchDocument.class)))
                .thenThrow(new RuntimeException("Elasticsearch cluster unavailable"));
        when(orderRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(sampleOrder), PageRequest.of(0, 10), 1));

        Page<OrderSearchDocument> result = orderSearchService.searchOrders(
                "Samsung", null, null, null, PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("ORD-20261118-TEST1234", result.getContent().getFirst().getOrderNumber());
        verify(orderRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    @DisplayName("getByOrderNumber should return document from Elasticsearch repository")
    void testGetByOrderNumber_Found() {
        OrderSearchDocument doc = OrderSearchDocument.fromEntity(sampleOrder);
        when(orderSearchRepository.findByOrderNumber("ORD-20261118-TEST1234")).thenReturn(List.of(doc));

        OrderSearchDocument result = orderSearchService.getByOrderNumber("ORD-20261118-TEST1234");

        assertNotNull(result);
        assertEquals("ORD-20261118-TEST1234", result.getOrderNumber());
    }
}

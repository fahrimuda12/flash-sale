package com.fahri.order.service.impl;

import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.entity.Order;
import com.fahri.order.repository.OrderRepository;
import com.fahri.order.repository.OrderSearchRepository;
import com.fahri.order.service.OrderSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSearchServiceImpl implements OrderSearchService {

    private final OrderSearchRepository orderSearchRepository;
    private final OrderRepository orderRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void indexOrder(Order order) {
        if (order == null || order.getOrderNumber() == null) {
            return;
        }

        try {
            OrderSearchDocument doc = OrderSearchDocument.fromEntity(order);
            orderSearchRepository.save(doc);
            log.debug("Indexed order {} into Elasticsearch successfully", order.getOrderNumber());
        } catch (Exception ex) {
            log.warn("Non-critical: Elasticsearch indexing skipped for order {}: {}", order.getOrderNumber(), ex.getMessage());
        }
    }

    @Override
    public void indexAllOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        try {
            List<OrderSearchDocument> docs = orders.stream()
                    .filter(o -> o != null && o.getOrderNumber() != null)
                    .map(OrderSearchDocument::fromEntity)
                    .collect(Collectors.toList());

            orderSearchRepository.saveAll(docs);
            log.info("Batch indexed {} orders into Elasticsearch index 'orders_search'", docs.size());
        } catch (Exception ex) {
            log.warn("Non-critical: Elasticsearch batch indexing skipped for orders: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncAllFromDatabase() {
        long startTime = System.currentTimeMillis();
        log.info("Starting full order database synchronization from PostgreSQL to Elasticsearch...");

        List<Order> allOrders = orderRepository.findAll();
        List<OrderSearchDocument> docs = allOrders.stream()
                .map(OrderSearchDocument::fromEntity)
                .collect(Collectors.toList());

        orderSearchRepository.saveAll(docs);
        long executionTimeMs = System.currentTimeMillis() - startTime;

        log.info("Successfully synchronized {} orders into Elasticsearch in {} ms", docs.size(), executionTimeMs);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "SUCCESS");
        summary.put("message", "Successfully synchronized all orders from database to Elasticsearch");
        summary.put("totalSynced", docs.size());
        summary.put("executionTimeMs", executionTimeMs);

        return summary;
    }

    @Override
    public Page<OrderSearchDocument> searchOrders(
            String keyword,
            String customerEmail,
            String status,
            String category,
            Pageable pageable) {

        log.debug("Executing Elasticsearch order search - keyword: '{}', customerEmail: '{}', status: '{}', category: '{}'",
                keyword, customerEmail, status, category);

        try {
            Criteria criteria = new Criteria();
            boolean hasCriteria = false;

            if (keyword != null && !keyword.isBlank()) {
                String cleanKeyword = keyword.trim();
                Criteria keywordCriteria = new Criteria("orderNumber").contains(cleanKeyword)
                        .or(new Criteria("customerEmail").contains(cleanKeyword))
                        .or(new Criteria("productNames").contains(cleanKeyword));
                criteria = criteria.subCriteria(keywordCriteria);
                hasCriteria = true;
            }

            if (customerEmail != null && !customerEmail.isBlank()) {
                Criteria emailCriteria = new Criteria("customerEmail").is(customerEmail.trim());
                criteria = hasCriteria ? criteria.and(emailCriteria) : emailCriteria;
                hasCriteria = true;
            }

            if (status != null && !status.isBlank()) {
                Criteria statusCriteria = new Criteria("status").is(status.trim().toUpperCase());
                criteria = hasCriteria ? criteria.and(statusCriteria) : statusCriteria;
                hasCriteria = true;
            }

            if (category != null && !category.isBlank()) {
                Criteria categoryCriteria = new Criteria("categories").is(category.trim());
                criteria = hasCriteria ? criteria.and(categoryCriteria) : categoryCriteria;
                hasCriteria = true;
            }

            Query query = hasCriteria ? new CriteriaQuery(criteria, pageable) : new CriteriaQuery(new Criteria(), pageable);
            SearchHits<OrderSearchDocument> searchHits = elasticsearchOperations.search(query, OrderSearchDocument.class);

            List<OrderSearchDocument> content = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, searchHits.getTotalHits());

        } catch (Exception ex) {
            log.warn("Elasticsearch order search query failed ({}), gracefully falling back to database query", ex.getMessage());

            Page<Order> dbOrders = orderRepository.findAll(pageable);
            List<OrderSearchDocument> fallbackDocs = dbOrders.getContent().stream()
                    .filter(o -> {
                        boolean match = true;
                        if (keyword != null && !keyword.isBlank()) {
                            String term = keyword.toLowerCase();
                            boolean orderNumMatch = o.getOrderNumber() != null && o.getOrderNumber().toLowerCase().contains(term);
                            boolean emailMatch = o.getCustomerEmail() != null && o.getCustomerEmail().toLowerCase().contains(term);
                            match = orderNumMatch || emailMatch;
                        }
                        if (match && customerEmail != null && !customerEmail.isBlank()) {
                            match = o.getCustomerEmail() != null && o.getCustomerEmail().equalsIgnoreCase(customerEmail.trim());
                        }
                        if (match && status != null && !status.isBlank()) {
                            match = o.getStatus() != null && o.getStatus().name().equalsIgnoreCase(status.trim());
                        }
                        return match;
                    })
                    .map(OrderSearchDocument::fromEntity)
                    .collect(Collectors.toList());

            return new PageImpl<>(fallbackDocs, pageable, dbOrders.getTotalElements());
        }
    }

    @Override
    public OrderSearchDocument getByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }

        try {
            List<OrderSearchDocument> docs = orderSearchRepository.findByOrderNumber(orderNumber.trim());
            return docs.isEmpty() ? null : docs.getFirst();
        } catch (Exception ex) {
            log.warn("Elasticsearch lookup failed for order {}, querying PostgreSQL database: {}", orderNumber, ex.getMessage());
            return orderRepository.findAll().stream()
                    .filter(o -> orderNumber.equalsIgnoreCase(o.getOrderNumber()))
                    .findFirst()
                    .map(OrderSearchDocument::fromEntity)
                    .orElse(null);
        }
    }
}

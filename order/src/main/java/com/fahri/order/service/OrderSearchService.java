package com.fahri.order.service;

import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface OrderSearchService {

    void indexOrder(Order order);

    void indexAllOrders(List<Order> orders);

    Map<String, Object> syncAllFromDatabase();

    /**
     * Performs multi-criteria full-text search across order number, customer email, product names,
     * status, and categories with pagination and resilient database fallback.
     *
     * @param keyword       Search term matching orderNumber, customerEmail, or productNames.
     * @param customerEmail Filter by specific customer email (optional).
     * @param status        Filter by order status (optional).
     * @param category      Filter by product category (optional).
     * @param pageable      Pagination and sorting specifications.
     * @return Paged Elasticsearch search documents.
     */
    Page<OrderSearchDocument> searchOrders(
            String keyword,
            String customerEmail,
            String status,
            String category,
            Pageable pageable
    );

    OrderSearchDocument getByOrderNumber(String orderNumber);
}

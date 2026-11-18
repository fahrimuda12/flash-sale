package com.fahri.order.controller;

import com.fahri.order.document.OrderSearchDocument;
import com.fahri.order.dto.*;
import com.fahri.order.service.OrderSearchService;
import com.fahri.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderSearchService orderSearchService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest servletRequest) {

        if (request.getClientIp() == null || request.getClientIp().isBlank()) {
            request.setClientIp(servletRequest.getRemoteAddr());
        }
        if (request.getUserAgent() == null || request.getUserAgent().isBlank()) {
            request.setUserAgent(servletRequest.getHeader("User-Agent"));
        }

        OrderResponse response = orderService.checkout(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByOrderNumber(orderNumber));
    }

    @GetMapping("/analytics/top-customers")
    public ResponseEntity<List<TopCustomerResponse>> getTopCustomers(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(orderService.getTopSpenders(limit));
    }

    @GetMapping({"/analytics/hourly-trend", "/analytics/hourly-trends"})
    public ResponseEntity<List<HourlySalesTrendResponse>> getHourlyTrends() {
        return ResponseEntity.ok(orderService.getHourlySalesTrends());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<OrderSearchDocument>> searchOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<OrderSearchDocument> result = orderSearchService.searchOrders(
                keyword, customerEmail, status, category, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search/{orderNumber}")
    public ResponseEntity<OrderSearchDocument> getOrderByNumberFromSearch(@PathVariable String orderNumber) {
        OrderSearchDocument doc = orderSearchService.getByOrderNumber(orderNumber);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/search/sync")
    public ResponseEntity<Map<String, Object>> syncOrdersToElasticsearch() {
        Map<String, Object> summary = orderSearchService.syncAllFromDatabase();
        return ResponseEntity.ok(summary);
    }
}

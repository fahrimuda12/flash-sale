package com.fahri.order;

import com.fahri.order.controller.OrderController;
import com.fahri.order.dto.*;
import com.fahri.order.exception.GlobalExceptionHandler;
import com.fahri.order.exception.OrderNotFoundException;
import com.fahri.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Controller Unit Tests")
class OrderControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/orders/analytics/top-customers?limit=5 should return top customers")
    void testGetTopCustomers() throws Exception {
        TopCustomerResponse customer = TopCustomerResponse.builder()
                .customerId(101L)
                .customerEmail("alex.tan@example.com")
                .totalSpent(new BigDecimal("25000000.00"))
                .orderCount(2L)
                .customerRank(1)
                .build();

        when(orderService.getTopSpenders(5)).thenReturn(List.of(customer));

        mockMvc.perform(get("/api/orders/analytics/top-customers")
                        .param("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(101))
                .andExpect(jsonPath("$[0].customerEmail").value("alex.tan@example.com"))
                .andExpect(jsonPath("$[0].customerRank").value(1));
    }

    @Test
    @DisplayName("GET /api/orders/analytics/hourly-trend (singular) should return hourly sales trend")
    void testGetHourlySalesTrend_Singular() throws Exception {
        HourlySalesTrendResponse trend = HourlySalesTrendResponse.builder()
                .hourBucket("2026-11-18 10:00")
                .orderCount(10L)
                .hourlySales(new BigDecimal("35000000.00"))
                .cumulativeSales(new BigDecimal("35000000.00"))
                .build();

        when(orderService.getHourlySalesTrends()).thenReturn(List.of(trend));

        mockMvc.perform(get("/api/orders/analytics/hourly-trend")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hourBucket").value("2026-11-18 10:00"))
                .andExpect(jsonPath("$[0].orderCount").value(10))
                .andExpect(jsonPath("$[0].cumulativeSales").value(35000000.00));
    }

    @Test
    @DisplayName("GET /api/orders/analytics/hourly-trends (plural alias) should also return hourly sales trend")
    void testGetHourlySalesTrend_PluralAlias() throws Exception {
        HourlySalesTrendResponse trend = HourlySalesTrendResponse.builder()
                .hourBucket("2026-11-18 11:00")
                .orderCount(5L)
                .hourlySales(new BigDecimal("15000000.00"))
                .cumulativeSales(new BigDecimal("50000000.00"))
                .build();

        when(orderService.getHourlySalesTrends()).thenReturn(List.of(trend));

        mockMvc.perform(get("/api/orders/analytics/hourly-trends")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hourBucket").value("2026-11-18 11:00"))
                .andExpect(jsonPath("$[0].cumulativeSales").value(50000000.00));
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return 200 OK when order exists")
    void testGetOrderById_Success() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-20261118-0001")
                .customerEmail("alex.tan@example.com")
                .finalAmount(new BigDecimal("22500000.00"))
                .status("COMPLETED")
                .build();

        when(orderService.getOrderById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20261118-0001"))
                .andExpect(jsonPath("$.customerEmail").value("alex.tan@example.com"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return 404 NOT FOUND when order does not exist")
    void testGetOrderById_NotFound() throws Exception {
        when(orderService.getOrderById(999L)).thenThrow(new OrderNotFoundException("Order not found with id: 999"));

        mockMvc.perform(get("/api/orders/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found with id: 999"));
    }

    @Test
    @DisplayName("GET /api/orders should return all orders list")
    void testGetAllOrders() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-20261118-0001")
                .status("COMPLETED")
                .build();

        when(orderService.getAllOrders()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/orders")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-20261118-0001"));
    }

    @Test
    @DisplayName("POST /api/orders/checkout should return 201 CREATED")
    void testCheckout() throws Exception {
        OrderItemRequest item = OrderItemRequest.builder()
                .productId(1L)
                .quantity(1)
                .build();

        CheckoutRequest request = CheckoutRequest.builder()
                .customerId(101L)
                .customerEmail("alex.tan@example.com")
                .paymentMethod("VIRTUAL_ACCOUNT")
                .items(List.of(item))
                .build();

        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderNumber("ORD-20261118-0001")
                .customerId(101L)
                .customerEmail("alex.tan@example.com")
                .finalAmount(new BigDecimal("15000000.00"))
                .status("CONFIRMED")
                .build();

        when(orderService.checkout(any(CheckoutRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20261118-0001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}

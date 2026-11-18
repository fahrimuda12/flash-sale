package com.fahri.order.service;

import com.fahri.order.dto.*;

import java.util.List;

public interface OrderService {

    OrderResponse checkout(CheckoutRequest request);

    OrderResponse getOrderById(Long id);

    OrderResponse getOrderByOrderNumber(String orderNumber);

    List<OrderResponse> getAllOrders();
}

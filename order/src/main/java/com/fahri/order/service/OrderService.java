package com.fahri.order.service;

import com.fahri.order.dto.*;

public interface OrderService {

    OrderResponse checkout(CheckoutRequest request);
}

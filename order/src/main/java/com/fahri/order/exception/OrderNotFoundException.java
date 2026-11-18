package com.fahri.order.exception;

public class OrderNotFoundException extends ProductNotFoundException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}

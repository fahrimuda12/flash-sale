package com.fahri.order.exception;

public class OutOfStockException extends ProductNotFoundException {
    public OutOfStockException(String message) {
        super(message);
    }
}

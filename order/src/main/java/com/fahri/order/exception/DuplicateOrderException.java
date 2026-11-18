package com.fahri.order.exception;

public class DuplicateOrderException extends ProductNotFoundException {
    public DuplicateOrderException(String message) {
        super(message);
    }
}

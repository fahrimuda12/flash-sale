package com.fahri.order.exception;

public class OrderNotFoundException extends FlashSaleException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}

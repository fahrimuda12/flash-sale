package com.fahri.order.exception;

public class DuplicateOrderException extends FlashSaleException {
    public DuplicateOrderException(String message) {
        super(message);
    }
}

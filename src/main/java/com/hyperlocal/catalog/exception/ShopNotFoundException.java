package com.hyperlocal.catalog.exception;

public class ShopNotFoundException extends RuntimeException {
    public ShopNotFoundException(String message) {
        super(message);
    }
}

package com.hyperlocal.catalog.exception;

public class InvalidProductException extends RuntimeException {
    public InvalidProductException(String message) {
        super(message);
    }
}

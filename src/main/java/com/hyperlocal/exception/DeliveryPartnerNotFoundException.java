package com.hyperlocal.exception;

public class DeliveryPartnerNotFoundException extends RuntimeException {
    public DeliveryPartnerNotFoundException(String message) {
        super(message);
    }
}

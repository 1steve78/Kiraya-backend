package com.hyperlocal.dispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidAssignmentStateException extends RuntimeException {

    private final String code;

    public InvalidAssignmentStateException(String message) {
        super(message);
        this.code = "DELIVERY_ALREADY_ASSIGNED";
    }

    public InvalidAssignmentStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public InvalidAssignmentStateException(String message, Throwable cause) {
        super(message, cause);
        this.code = "DELIVERY_ALREADY_ASSIGNED";
    }

    public String getCode() {
        return code;
    }
}

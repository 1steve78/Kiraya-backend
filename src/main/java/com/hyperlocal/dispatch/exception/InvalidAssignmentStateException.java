package com.hyperlocal.dispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidAssignmentStateException extends RuntimeException {

    public InvalidAssignmentStateException(String message) {
        super(message);
    }

    public InvalidAssignmentStateException(String message, Throwable cause) {
        super(message, cause);
    }
}

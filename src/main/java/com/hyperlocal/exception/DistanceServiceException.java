package com.hyperlocal.exception;

public class DistanceServiceException extends RuntimeException{

    public DistanceServiceException(String message){
        super(message);
    }

    public DistanceServiceException(String message,Throwable cause){
        super(message,cause);
    }
}

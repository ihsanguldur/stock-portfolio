package com.serphenix.portfolio.portfolio.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientHoldingException extends RuntimeException {
    public InsufficientHoldingException(String message) {
        super(message);
    }
}

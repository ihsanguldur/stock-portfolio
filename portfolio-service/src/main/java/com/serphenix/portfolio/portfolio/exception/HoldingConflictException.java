package com.serphenix.portfolio.portfolio.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class HoldingConflictException extends RuntimeException {
    public HoldingConflictException(String message) {
        super(message);
    }
}

package com.serphenix.portfolio.stock.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class ExternalApiTimeoutException extends RuntimeException {
    public ExternalApiTimeoutException(String message) {
        super(message);
    }
}

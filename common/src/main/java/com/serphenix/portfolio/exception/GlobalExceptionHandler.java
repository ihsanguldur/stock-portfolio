package com.serphenix.portfolio.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ProblemDetail> handleRestClientResponseException(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();

        ProblemDetail upstreamProblem = ex.getResponseBodyAs(ProblemDetail.class);
        String message = upstreamProblem != null ? upstreamProblem.getDetail() : ex.getMessage();

        if (status.is5xxServerError()) {
            message = "Internal Server Error";
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        log.warn(message, ex);
        return ResponseEntity.status(status).body(problemDetail);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(RuntimeException ex) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);

        HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;

        String message = ex.getMessage();
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            message = "Internal Server Error";
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        log.warn(message, ex);
        return ResponseEntity.status(status).body(problemDetail);
    }
}

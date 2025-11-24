package com.flightapp.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalErrorHandler {
	private static final String ERROR_KEY = "error";
  @ExceptionHandler(IllegalArgumentException.class)
  public Mono<ResponseEntity<Object>> handleBadRequest(IllegalArgumentException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(ERROR_KEY, ex.getMessage())));
  }

  @ExceptionHandler(IllegalStateException.class)
  public Mono<ResponseEntity<Object>> handleConflict(IllegalStateException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(ERROR_KEY, ex.getMessage())));
  }

  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<Object>> handleOther(Exception ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of(ERROR_KEY, "Internal server error")));
  }
}
package com.flightapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalErrorHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public Mono<ResponseEntity<String>> handleNotFound(IllegalArgumentException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public Mono<ResponseEntity<String>> handleBadRequest(IllegalStateException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<String>> handleOther(Exception ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error"));
  }
}
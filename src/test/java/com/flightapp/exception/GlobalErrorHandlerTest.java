package com.flightapp.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalErrorHandlerTest {

    private GlobalErrorHandler handler;

    @BeforeEach
    void setup() {
        handler = new GlobalErrorHandler();
    }

    @Test
    void handleBadRequest_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad request");

        Mono<ResponseEntity<Object>> result = handler.handleBadRequest(ex);
        ResponseEntity<Object> response = result.block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("error"))
                .isEqualTo("bad request");
    }

    @Test
    void handleConflict_returns400() {
        IllegalStateException ex = new IllegalStateException("state error");

        Mono<ResponseEntity<Object>> result = handler.handleConflict(ex);
        ResponseEntity<Object> response = result.block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<?, ?>) response.getBody()).get("error"))
                .isEqualTo("state error");
    }

    @Test
    void handleOther_returns500() {
        Exception ex = new Exception("random");

        Mono<ResponseEntity<Object>> result = handler.handleOther(ex);
        ResponseEntity<Object> response = result.block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((Map<?, ?>) response.getBody()).get("error"))
                .isEqualTo("Internal server error");
    }
}
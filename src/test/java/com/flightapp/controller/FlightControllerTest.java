package com.flightapp.controller;

import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.service.FlightService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class FlightControllerTest {

    FlightService flightService;
    WebTestClient webClient;
    FlightController controller;

    @BeforeEach
    void setUp() {
        flightService = mock(FlightService.class);
        controller = new FlightController(flightService);
        webClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void addInventory_returns201() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("id-1");
        inv.setAirline("Indigo");
        when(flightService.addInventory(any(AirlineInventory.class))).thenReturn(Mono.just(inv));

        webClient.post().uri("/api/flight/airline/inventory/add")
                .bodyValue(inv)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.airline").isEqualTo("Indigo");
    }

    @Test
    void ticket_notFound_returns404() {
        when(flightService.findByPnr("NOPE")).thenReturn(Mono.empty());

        webClient.get().uri("/api/flight/ticket/NOPE")
                .exchange()
                .expectStatus().isNotFound();
    }
}
package com.flightapp.controller;

import com.flightapp.dto.InventoryRequest;
import com.flightapp.model.AirlineInventory;
import com.flightapp.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
        InventoryRequest req = new InventoryRequest();
        req.setAirline("Indigo");
        req.setAirlineLogoUrl("logo.png");
        req.setFlightNumber("IN123");
        req.setOrigin("HYD");
        req.setDestination("BLR");
        req.setDeparture(LocalDateTime.now().plusDays(2));
        req.setArrival(LocalDateTime.now().plusDays(2).plusHours(2));
        req.setTotalSeats(30);
        req.setPrice(4500.0);

        AirlineInventory saved = new AirlineInventory();
        saved.setId("id-1");
        saved.setAirline("Indigo");

        when(flightService.addInventory(any())).thenReturn(Mono.just(saved));

        webClient.post().uri("/api/flight/airline/inventory/add")
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.airline").isEqualTo("Indigo");
    }
}
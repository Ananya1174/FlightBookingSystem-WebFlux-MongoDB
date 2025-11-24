package com.flightapp.controller;

import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.BookingUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FlightControllerTest {

    FlightService flightService;
    WebTestClient webClient;
    FlightController controller;

    @BeforeEach
    public void setup() {
        flightService = mock(FlightService.class);
        controller = new FlightController(flightService);
        webClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    public void addInventory_returns201() {
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
    public void ticket_notFound_returns404() {
        when(flightService.findByPnr("NOPE")).thenReturn(Mono.empty());

        webClient.get().uri("/api/flight/ticket/NOPE")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    public void book_returns201() {
        Booking booking = new Booking();
        booking.setPnr("PNR1");
        when(flightService.book(eq("flight-1"), any(BookingRequest.class))).thenReturn(Mono.just(booking));

        webClient.post().uri("/api/flight/booking/flight-1")
            .bodyValue(new BookingRequest())
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.pnr").isEqualTo("PNR1");
    }

    @Test
    public void cancel_ownerMismatch_returns401() {
        when(flightService.cancelByPnrAndEmail(eq("PNR1"), eq("wrong@example.com")))
            .thenReturn(Mono.error(new IllegalStateException("Only owner can cancel the booking")));

        webClient.delete().uri("/api/flight/booking/cancel/PNR1")
            .header("X-User-Email", "wrong@example.com")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").exists();
    }

    @Test
    public void updateBooking_success_returns200() {
        Booking updated = new Booking();
        updated.setPnr("PNR2");
        when(flightService.updateBooking(eq("PNR2"), any(BookingUpdateRequest.class)))
            .thenReturn(Mono.just(updated));

        BookingUpdateRequest req = new BookingUpdateRequest();
        req.setEmail("owner@example.com");

        webClient.put().uri("/api/flight/booking/PNR2")
            .header("X-User-Email", "owner@example.com")
            .bodyValue(req)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Booking updated successfully")
            .jsonPath("$.pnr").isEqualTo("PNR2");
    }
}
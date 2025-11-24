package com.flightapp.controller;

import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.model.Passenger;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.BookingUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

 class FlightControllerTest {

    FlightService flightService;
    WebTestClient webClient;
    FlightController controller;

    @BeforeEach
     void setup() {
        flightService = mock(FlightService.class);
        controller = new FlightController(flightService);
        webClient = WebTestClient.bindToController(controller).build();
    }

    @Test
     void addInventory_returns201() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("id-1");
        inv.setAirline("Indigo");
        inv.setFlightNumber("IN1");
        inv.setOrigin("HYD");
        inv.setDestination("BLR");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(2));
        inv.setTotalSeats(30);
        inv.setPrice(4500.0);

        when(flightService.addInventory(any(AirlineInventory.class))).thenReturn(Mono.just(inv));

        webClient.post().uri("/api/flight/airline/inventory/add")
            .contentType(MediaType.APPLICATION_JSON)
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

    @Test
    void book_returns201() {
        Booking booking = new Booking();
        booking.setPnr("PNR1");
        when(flightService.book(eq("flight-1"), any(BookingRequest.class))).thenReturn(Mono.just(booking));

        BookingRequest req = new BookingRequest();
        req.setName("Test");
        req.setEmail("test@example.com");
        Passenger p = new Passenger();
        p.setName("P1");
        p.setGender("F");
        p.setAge(25);
        req.setPassengers(List.of(p));
        req.setSeatNumbers(List.of("S1"));
        req.setMealVeg(true);

        webClient.post().uri("/api/flight/booking/flight-1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(req)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.pnr").isEqualTo("PNR1");
    }

    @Test
     void cancel_ownerMismatch_returns401() {
    	when(flightService.cancelByPnrAndEmail("PNR1", "wrong@example.com"))
    	.thenReturn(Mono.error(new IllegalStateException("Only owner can cancel the booking")));

        webClient.method(HttpMethod.DELETE)
            .uri("/api/flight/booking/cancel/PNR1")
            .header("X-User-Email", "wrong@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(Collections.singletonMap("email", "wrong@example.com")))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").exists();
    }

    @Test
     void updateBooking_success_returns200() {
        Booking updated = new Booking();
        updated.setPnr("PNR2");
        when(flightService.updateBooking(eq("PNR2"), any(BookingUpdateRequest.class)))
            .thenReturn(Mono.just(updated));

        BookingUpdateRequest req = new BookingUpdateRequest();
        req.setEmail("owner@example.com");

        webClient.put().uri("/api/flight/booking/PNR2")
            .header("X-User-Email", "owner@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(req)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Booking updated successfully")
            .jsonPath("$.pnr").isEqualTo("PNR2");
    }
}
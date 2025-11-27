package com.flightapp.controller;

import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.SearchRequest;
import com.flightapp.model.Passenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;


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

    private AirlineInventory sampleInventory() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("id-1");
        inv.setAirline("Indigo");
        inv.setFlightNumber("IN1");
        inv.setOrigin("HYD");
        inv.setDestination("BLR");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(2));
        inv.setTotalSeats(5);
        inv.setAvailableSeats(List.of("S1","S2","S3","S4","S5"));
        inv.setPrice(3000.0);
        return inv;
    }

    @Test
    void addInventory_returns201() {
        AirlineInventory inv = sampleInventory();
        when(flightService.addInventory(any(AirlineInventory.class))).thenReturn(Mono.just(inv));

        webClient.post().uri("/api/flight/airline/inventory/add")
                .bodyValue(inv)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.airline").isEqualTo("Indigo");
    }

    @Test
    void search_returnsResults() {
        AirlineInventory inv = sampleInventory();
        when(flightService.search(eq("HYD"), eq("BLR"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(inv));

        SearchRequest req = new SearchRequest();
        req.setOrigin("HYD");
        req.setDestination("BLR");
        req.setFrom(LocalDateTime.now().toString());
        req.setTo(LocalDateTime.now().plusDays(3).toString());

        webClient.post().uri("/api/flight/search")
                .bodyValue(req)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(AirlineInventory.class)
                .hasSize(1);
    }

    @Test
    void book_returns201() {
        Booking booking = new Booking();
        booking.setPnr("PNR1");
        when(flightService.book(eq("flight-1"), any(BookingRequest.class))).thenReturn(Mono.just(booking));

        BookingRequest req = new BookingRequest();
        req.setEmail("u@example.com");
        req.setName("User");
        req.setPassengers(List.of(new Passenger()));
        req.setSeatNumbers(List.of("S1"));
        req.setMealVeg(true);

        webClient.post().uri("/api/flight/booking/flight-1")
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.pnr").isEqualTo("PNR1");
    }

    @Test
    void ticket_notFound_returns404() {
        when(flightService.findByPnr("NOPE")).thenReturn(Mono.empty());

        webClient.get().uri("/api/flight/ticket/NOPE")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void history_returnsBookings_withStatusField() {
        Booking b = new Booking();
        b.setPnr("PNR10");
        b.setEmail("e@example.com");
        b.setCanceled(true);

        when(flightService.findByEmail("e@example.com")).thenReturn(Flux.just(b));

        webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/flight/booking/history")
                        .queryParam("email", "e@example.com")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .hasSize(1)
                .consumeWith(res -> {
                    Map<String, Object> first = res.getResponseBody().get(0);
                    // clearer assertions using AssertJ containsEntry
                    assertThat(first).containsEntry("status", "CANCELLED");
                    assertThat(first).containsEntry("email", "e@example.com");
                    assertThat(first).containsEntry("pnr", "PNR10");
                });
    }

    @Test
    void cancel_requiresHeaderAndBody_and_ownerMatch() {
        // success flow
        when(flightService.cancelByPnrAndEmail("PNR1", "owner@example.com")).thenReturn(Mono.empty());

        webClient.method(HttpMethod.DELETE)
                .uri("/api/flight/booking/cancel/PNR1?email=owner@example.com")
                .header("X-User-Email", "owner@example.com")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Booking cancelled successfully")
                .jsonPath("$.pnr").isEqualTo("PNR1");
    }
    @Test
    void cancel_ownerMismatch_returnsBadRequest() {
        when(flightService.cancelByPnrAndEmail("PNR1", "wrong@example.com"))
                .thenReturn(Mono.error(new IllegalStateException("Only owner can cancel the booking")));

        webClient.method(HttpMethod.DELETE)
                .uri("/api/flight/booking/cancel/PNR1?email=wrong@example.com")
                .header("X-User-Email", "wrong@example.com")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    void cancel_missingBody_returns400() {
        webClient.method(HttpMethod.DELETE)
                .uri("/api/flight/booking/cancel/PNR1") // no ?email=
                .header("X-User-Email", "owner@example.com")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Email required in request parameter");
    }
}

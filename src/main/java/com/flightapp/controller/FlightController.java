package com.flightapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.BookingUpdateRequest;
import com.flightapp.dto.SearchRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/flight")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }
    private static final String ERROR_KEY = "error";
    // ADD INVENTORY (admin)
    @PostMapping("/airline/inventory/add")
    public Mono<ResponseEntity<com.flightapp.model.AirlineInventory>> addInventory(
            @RequestBody @Valid com.flightapp.dto.InventoryRequest req,
            UriComponentsBuilder uriBuilder) {

        // Map DTO -> entity (or delegate mapping to service)
        com.flightapp.model.AirlineInventory inv = new com.flightapp.model.AirlineInventory();
        inv.setAirline(req.getAirline());
        inv.setAirlineLogoUrl(req.getAirlineLogoUrl());
        inv.setFlightNumber(req.getFlightNumber());
        inv.setOrigin(req.getOrigin());
        inv.setDestination(req.getDestination());
        inv.setDeparture(req.getDeparture());
        inv.setArrival(req.getArrival());
        inv.setTotalSeats(req.getTotalSeats());
        inv.setPrice(req.getPrice());
        // If client passed seats use them, otherwise service may create default seat map
        if (req.getAvailableSeats() != null && !req.getAvailableSeats().isEmpty()) {
            inv.setAvailableSeats(new java.util.ArrayList<>(req.getAvailableSeats()));
        }

        // delegate to service to finalize/save
        return flightService.addInventory(inv)
            .map(saved -> {
                var location = uriBuilder.path("/api/v1.0/flight/airline/inventory/{id}")
                        .buildAndExpand(saved.getId()).toUri();
                return ResponseEntity.created(location).body(saved);
            });
    }

    // SEARCH
    @PostMapping("/search")
    public Flux<AirlineInventory> search(@RequestBody @Valid SearchRequest req) {

        LocalDateTime fromDt = LocalDateTime.parse(req.getFrom());
        LocalDateTime toDt = LocalDateTime.parse(req.getTo());

        return flightService.search(
            req.getOrigin(),
            req.getDestination(),
            fromDt,
            toDt
        );
    }

    // BOOK
    @PostMapping("/booking/{flightId}")
    public Mono<ResponseEntity<Booking>> book(@PathVariable String flightId,
                                              @RequestBody @Valid BookingRequest req,
                                              UriComponentsBuilder uriBuilder) {

        return flightService.book(flightId, req)
                .map(booking -> {
                    var location = uriBuilder.path("/api/flight/ticket/{pnr}")
                            .buildAndExpand(booking.getPnr()).toUri();
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .location(location)
                            .body(booking);
                });
    }

    // GET TICKET BY PNR
    @GetMapping("/ticket/{pnr}")
    public Mono<ResponseEntity<Booking>> ticket(@PathVariable String pnr) {
        return flightService.findByPnr(pnr)
        		.map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.<Booking>notFound().build());
    }

    // GET HISTORY
    @GetMapping("/booking/history/{emailId}")
    public Flux<Map<String,Object>> history(@PathVariable String emailId) {
        return flightService.findByEmail(emailId)
            .map(b -> {
                Map<String,Object> m = new HashMap<>();
                m.put("pnr", b.getPnr());
                m.put("name", b.getName());
                m.put("email", b.getEmail());
                m.put("flightId", b.getFlightId());
                m.put("journeyDate", b.getJourneyDate());
                m.put("seatNumbers", b.getSeatNumbers());
                m.put("status", Boolean.TRUE.equals(b.isCanceled()) ? "CANCELLED" : "ACTIVE");
                // optionally include canceledAt, cancelReason etc.
                if (b.getCanceledAt() != null) m.put("canceledAt", b.getCanceledAt());
                return m;
            });
    }

    // CANCEL: Owner-only. Email must be present in header X-User-Email and in request body (email).
    // Returns 200 with message on success; returns JSON error messages on failure.
    @DeleteMapping("/booking/cancel/{pnr}")
    public Mono<ResponseEntity<Map<String, Object>>> cancel(
            @PathVariable String pnr,
            @RequestHeader(name = "X-User-Email") String headerEmail) {

        return flightService.cancelByPnrAndEmail(pnr, headerEmail)
                .then(Mono.fromSupplier(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Booking cancelled successfully");
                    response.put("pnr", pnr);
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(err -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put(ERROR_KEY, err.getMessage());

                    if (err instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
                    }
                    if (err instanceof IllegalStateException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });
    }
    // UPDATE Booking: Owner-only. Email must be present in header and body.
    // This updates passenger details, meal preference, name and optionally seatNumbers (if seats available).
    @PutMapping("/booking/{pnr}")
    public Mono<ResponseEntity<Map<String, Object>>> updateBooking(
            @PathVariable String pnr,
            @RequestHeader(name = "X-User-Email", required = true) String headerEmail,
            @RequestBody @Valid BookingUpdateRequest updateReq) {

        if (!headerEmail.equalsIgnoreCase(updateReq.getEmail())) {
            Map<String, Object> err = new HashMap<>();
            err.put(ERROR_KEY, "Header email and body email must match");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err));
        }

        return flightService.updateBooking(pnr, updateReq)
                .map(updated -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("message", "Booking updated successfully");
                    resp.put("pnr", updated.getPnr());
                    resp.put("booking", updated);
                    return ResponseEntity.ok(resp);
                })
                .onErrorResume(err -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put(ERROR_KEY, err.getMessage());

                    if (err instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
                    }
                    if (err instanceof IllegalStateException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });
    }
}
package com.flightapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/flight")
public class FlightController {

  private final FlightService flightService;

  public FlightController(FlightService flightService) {
    this.flightService = flightService;
  }

  @PostMapping("/airline/inventory/add")
  public Mono<ResponseEntity<AirlineInventory>> addInventory(@RequestBody @Valid AirlineInventory inventory,
      UriComponentsBuilder uriBuilder) {
    return flightService.addInventory(inventory)
      .map(saved -> {
        var location = uriBuilder.path("/api/flight/airline/inventory/{id}")
            .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(saved); // 201
      });
  }

  @PostMapping("/search")
  public Flux<AirlineInventory> search(
      @RequestParam String origin,
      @RequestParam String destination,
      @RequestParam String from,      // ISO date-time string
      @RequestParam String to) {
    LocalDateTime f = LocalDateTime.parse(from);
    LocalDateTime t = LocalDateTime.parse(to);
    return flightService.search(origin, destination, f, t);
  }

  @PostMapping("/booking/{flightId}")
  public Mono<ResponseEntity<Booking>> book(@PathVariable String flightId,
                                            @RequestBody @Valid BookingRequest req,
                                            UriComponentsBuilder uriBuilder) {
    return flightService.book(flightId, req)
      .map(booking -> {
        var location = uriBuilder.path("/api/flight/ticket/{pnr}")
            .buildAndExpand(booking.getPnr()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(booking);
      });
  }

  @GetMapping("/ticket/{pnr}")
  public Mono<ResponseEntity<Booking>> ticket(@PathVariable String pnr) {
    return flightService.findByPnr(pnr)
      .map(b -> ResponseEntity.ok(b))
      .defaultIfEmpty(ResponseEntity.<Booking>notFound().build());
  }

  @GetMapping("/booking/history/{emailId}")
  public Flux<Booking> history(@PathVariable String emailId,
                               @RequestParam(name = "includeCancelled", defaultValue = "false") boolean includeCancelled) {
    return flightService.findByEmail(emailId)
        .filter(b -> includeCancelled || !Boolean.TRUE.equals(b.isCanceled()));
    // returns 200 automatically; empty list if none
  }

  @DeleteMapping("/booking/cancel/{pnr}")
  public Mono<ResponseEntity<Map<String, Object>>> cancel(@PathVariable String pnr) {
      return flightService.cancelByPnr(pnr)
          .then(Mono.fromSupplier(() -> {
              Map<String, Object> response = new HashMap<>();
              response.put("message", "Booking cancelled successfully");
              response.put("pnr", pnr);
              return ResponseEntity.ok(response);  // 200 OK with JSON message
          }))
          .onErrorResume(err -> {

              Map<String, Object> error = new HashMap<>();
              error.put("error", err.getMessage());

              if (err instanceof IllegalArgumentException) {
                  // PNR not found
                  return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
              }

              if (err instanceof IllegalStateException) {
                  // Cancellation rule violation
                  return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
              }

              // unexpected server errors
              return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
          });
  }
}
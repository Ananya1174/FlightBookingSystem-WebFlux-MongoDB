package com.flightapp.controller;

import org.springframework.web.bind.annotation.*;
import com.flightapp.service.FlightService;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1.0/flight")
public class FlightController {

  private final FlightService flightService;

  public FlightController(FlightService flightService) {
    this.flightService = flightService;
  }

  @PostMapping("/airline/inventory/add")
  public Mono<AirlineInventory> addInventory(@RequestBody @Valid AirlineInventory inventory) {
    return flightService.addInventory(inventory);
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
  public Mono<Booking> book(@PathVariable String flightId, @RequestBody @Valid BookingRequest req) {
    return flightService.book(flightId, req);
  }

  @GetMapping("/ticket/{pnr}")
  public Mono<Booking> ticket(@PathVariable String pnr) {
    return flightService.findByPnr(pnr);
  }

  @GetMapping("/booking/history/{emailId}")
  public Flux<Booking> history(@PathVariable String emailId) {
    return flightService.findByEmail(emailId);
  }

  @DeleteMapping("/booking/cancel/{pnr}")
  public Mono<Boolean> cancel(@PathVariable String pnr) {
    return flightService.cancelByPnr(pnr);
  }
}
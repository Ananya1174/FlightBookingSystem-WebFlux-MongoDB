package com.flightapp.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import java.time.LocalDateTime;

public interface FlightService {
  Mono<AirlineInventory> addInventory(AirlineInventory inventory);
  Flux<AirlineInventory> search(String origin, String destination, LocalDateTime from, LocalDateTime to);
  Mono<Booking> book(String flightId, BookingRequest req);
  Mono<Booking> findByPnr(String pnr);
  Flux<Booking> findByEmail(String email);
  Mono<Void> cancelByPnr(String pnr);
  }
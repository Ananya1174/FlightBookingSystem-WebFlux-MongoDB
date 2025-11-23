package com.flightapp.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import com.flightapp.model.Booking;

public interface BookingRepository extends ReactiveCrudRepository<Booking, String> {
  Mono<Booking> findByPnr(String pnr);
  Flux<Booking> findByEmail(String email);
}
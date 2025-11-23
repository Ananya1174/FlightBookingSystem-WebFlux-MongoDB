package com.flightapp.repository;

import reactor.core.publisher.Flux;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import com.flightapp.model.AirlineInventory;

public interface InventoryRepository extends ReactiveCrudRepository<AirlineInventory, String> {
  Flux<AirlineInventory> findByOriginAndDestinationAndDepartureBetween(
      String origin, String destination, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
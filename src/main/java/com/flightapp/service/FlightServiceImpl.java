package com.flightapp.service;

import org.springframework.stereotype.Service;
import com.flightapp.repository.InventoryRepository;
import com.flightapp.repository.BookingRepository;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.util.PnrGenerator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class FlightServiceImpl implements FlightService {
  private final InventoryRepository inventoryRepository;
  private final BookingRepository bookingRepository;

  public FlightServiceImpl(InventoryRepository inventoryRepository, BookingRepository bookingRepository) {
    this.inventoryRepository = inventoryRepository;
    this.bookingRepository = bookingRepository;
  }

  @Override
  public Mono<AirlineInventory> addInventory(AirlineInventory inventory) {
    // very simple: ensure availableSeats is filled if null and persist
    if (inventory.getAvailableSeats() == null) {
      inventory.setAvailableSeats(
          java.util.stream.IntStream.rangeClosed(1, inventory.getTotalSeats())
            .mapToObj(i -> "S" + i).collect(Collectors.toList())
      );
    }
    return inventoryRepository.save(inventory);
  }

  @Override
  public Flux<AirlineInventory> search(String origin, String destination, LocalDateTime from, LocalDateTime to) {
    return inventoryRepository.findByOriginAndDestinationAndDepartureBetween(origin, destination, from, to);
  }

  @Override
  public Mono<Booking> book(String flightId, BookingRequest req) {
    return inventoryRepository.findById(flightId)
      .switchIfEmpty(Mono.error(new IllegalArgumentException("Flight not found")))
      .flatMap(inv -> {
        // naive seat selection: check requested seats exist in availableSeats
        if (req.getSeatNumbers() == null || req.getSeatNumbers().isEmpty()) {
          return Mono.error(new IllegalArgumentException("At least one seat must be selected"));
        }
        if (!inv.getAvailableSeats().containsAll(req.getSeatNumbers())) {
          return Mono.error(new IllegalArgumentException("Requested seats not available"));
        }
        // remove seats from inventory
        inv.getAvailableSeats().removeAll(req.getSeatNumbers());
        Booking b = new Booking();
        b.setPnr(PnrGenerator.generate());
        b.setFlightId(inv.getId());
        b.setEmail(req.getEmail());
        b.setName(req.getName());
        b.setPassengers(req.getPassengers());
        b.setSeatNumbers(req.getSeatNumbers());
        b.setMealVeg(req.isMealVeg());
        b.setBookedAt(LocalDateTime.now());
        b.setJourneyDate(inv.getDeparture().toLocalDate().atStartOfDay());
        // persist both booking and inventory
        return inventoryRepository.save(inv).then(bookingRepository.save(b));
      });
  }

  @Override
  public Mono<Booking> findByPnr(String pnr) {
    return bookingRepository.findByPnr(pnr);
  }

  @Override
  public Flux<Booking> findByEmail(String email) {
    return bookingRepository.findByEmail(email);
  }

  @Override
  public Mono<Void> cancelByPnr(String pnr) {
    return bookingRepository.findByPnr(pnr)
      .switchIfEmpty(Mono.error(new IllegalArgumentException("PNR not found")))
      .flatMap(b -> {
        if (b.isCanceled()) {
          return Mono.error(new IllegalStateException("Booking already cancelled"));
        }
        LocalDateTime now = LocalDateTime.now();
        // allow cancellation only if more than 24 hrs prior to journey
        if (!b.getJourneyDate().minusHours(24).isAfter(now)) {
          return Mono.error(new IllegalStateException("Cancellation allowed only 24 hrs prior to journey"));
        }
        b.setCanceled(true);
        b.setCanceledAt(now);
        return bookingRepository.save(b).then(); // return Mono<Void>
      });
  }
}
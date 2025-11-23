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

    // -------------------- ADD INVENTORY -----------------------
    @Override
    public Mono<AirlineInventory> addInventory(AirlineInventory inventory) {

        // Business rules
        if (inventory.getOrigin().equalsIgnoreCase(inventory.getDestination())) {
            return Mono.error(new IllegalStateException("Origin and destination cannot be the same"));
        }

        if (!inventory.getArrival().isAfter(inventory.getDeparture())) {
            return Mono.error(new IllegalStateException("Arrival must be after departure"));
        }

        if (inventory.getTotalSeats() <= 0) {
            return Mono.error(new IllegalStateException("Total seats must be > 0"));
        }

        // Generate seat list S1, S2, ...
        inventory.setAvailableSeats(
                java.util.stream.IntStream.rangeClosed(1, inventory.getTotalSeats())
                        .mapToObj(i -> "S" + i)
                        .collect(Collectors.toList())
        );

        return inventoryRepository.save(inventory);
    }

    // -------------------- SEARCH -----------------------
    @Override
    public Flux<AirlineInventory> search(String origin, String destination,
                                         LocalDateTime from, LocalDateTime to) {
        return inventoryRepository
                .findByOriginAndDestinationAndDepartureBetween(origin, destination, from, to);
    }

    // -------------------- BOOK -----------------------
    @Override
    public Mono<Booking> book(String flightId, BookingRequest req) {

        return inventoryRepository.findById(flightId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Flight not found")))
                .flatMap(inv -> {

                    // flight cannot be booked after departure
                    if (!inv.getDeparture().isAfter(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException("Cannot book a flight that already departed"));
                    }

                    // seat selection check
                    if (!inv.getAvailableSeats().containsAll(req.getSeatNumbers())) {
                        return Mono.error(new IllegalStateException("Some selected seats are unavailable"));
                    }

                    // passenger count = seat count
                    if (req.getPassengers().size() != req.getSeatNumbers().size()) {
                        return Mono.error(new IllegalArgumentException(
                                "Passenger count must match selected seats"));
                    }

                    // reserve the seats
                    inv.getAvailableSeats().removeAll(req.getSeatNumbers());

                    Booking booking = new Booking();
                    booking.setPnr(PnrGenerator.generate());
                    booking.setFlightId(inv.getId());
                    booking.setEmail(req.getEmail());
                    booking.setName(req.getName());
                    booking.setPassengers(req.getPassengers());
                    booking.setSeatNumbers(req.getSeatNumbers());
                    booking.setMealVeg(req.isMealVeg());
                    booking.setBookedAt(LocalDateTime.now());
                    booking.setJourneyDate(inv.getDeparture());
                    booking.setCanceled(false);

                    // save inventory first then booking
                    return inventoryRepository.save(inv)
                            .then(bookingRepository.save(booking));
                });
    }

    // -------------------- FIND BY PNR -----------------------
    @Override
    public Mono<Booking> findByPnr(String pnr) {
        return bookingRepository.findByPnr(pnr);
    }

    // -------------------- FIND BY EMAIL -----------------------
    @Override
    public Flux<Booking> findByEmail(String email) {
        return bookingRepository.findByEmail(email);
    }

    // -------------------- CANCEL -----------------------
    @Override
    public Mono<Void> cancelByPnr(String pnr) {

        return bookingRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("PNR not found")))
                .flatMap(booking -> {

                    if (booking.isCanceled()) {
                        return Mono.error(new IllegalStateException("Booking already cancelled"));
                    }

                    LocalDateTime now = LocalDateTime.now();

                    if (!booking.getJourneyDate().minusHours(24).isAfter(now)) {
                        return Mono.error(new IllegalStateException(
                                "Cancellation allowed only 24 hrs before journey"));
                    }

                    booking.setCanceled(true);
                    booking.setCanceledAt(now);

                    return bookingRepository.save(booking).then();
                });
    }
}
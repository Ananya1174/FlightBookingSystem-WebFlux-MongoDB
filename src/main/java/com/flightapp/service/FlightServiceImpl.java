package com.flightapp.service;

import org.springframework.stereotype.Service;

import com.flightapp.repository.InventoryRepository;
import com.flightapp.repository.BookingRepository;
import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.BookingUpdateRequest;
import com.flightapp.util.PnrGenerator;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlightServiceImpl implements FlightService {

    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;

    public FlightServiceImpl(InventoryRepository inventoryRepository, BookingRepository bookingRepository) {
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
    }

    // addInventory, search, book, findByPnr, findByEmail left unchanged (keep existing implementations)
    // below we show addInventory and book (if you already have them, keep them),
    // and add the new methods: cancelByPnrAndEmail and updateBooking.

    @Override
    public Mono<AirlineInventory> addInventory(AirlineInventory inventory) {
        if (inventory.getOrigin().equalsIgnoreCase(inventory.getDestination())) {
            return Mono.error(new IllegalStateException("Origin and destination cannot be the same"));
        }

        if (!inventory.getArrival().isAfter(inventory.getDeparture())) {
            return Mono.error(new IllegalStateException("Arrival must be after departure"));
        }

        if (inventory.getTotalSeats() <= 0) {
            return Mono.error(new IllegalStateException("Total seats must be > 0"));
        }

        inventory.setAvailableSeats(
                java.util.stream.IntStream.rangeClosed(1, inventory.getTotalSeats())
                        .mapToObj(i -> "S" + i)
                        .collect(Collectors.toList())
        );

        return inventoryRepository.save(inventory);
    }

    @Override
    public Flux<AirlineInventory> search(String origin, String destination,
                                         LocalDateTime from, LocalDateTime to) {
        return inventoryRepository.findByOriginAndDestinationAndDepartureBetween(origin, destination, from, to);
    }

    @Override
    public Mono<Booking> book(String flightId, BookingRequest req) {
        return inventoryRepository.findById(flightId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Flight not found")))
                .flatMap(inv -> {

                    if (!inv.getDeparture().isAfter(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException("Cannot book a flight that already departed"));
                    }

                    if (req.getSeatNumbers() == null || req.getSeatNumbers().isEmpty()) {
                        return Mono.error(new IllegalArgumentException("At least one seat must be selected"));
                    }

                    if (!inv.getAvailableSeats().containsAll(req.getSeatNumbers())) {
                        return Mono.error(new IllegalStateException("Some selected seats are unavailable"));
                    }

                    if (req.getPassengers().size() != req.getSeatNumbers().size()) {
                        return Mono.error(new IllegalArgumentException("Passenger count must match selected seats"));
                    }

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

                    return inventoryRepository.save(inv)
                            .then(bookingRepository.save(booking));
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

    // NEW: cancel by pnr + email (owner-only)
    @Override
    public Mono<Void> cancelByPnrAndEmail(String pnr, String email) {
        return bookingRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("PNR not found")))
                .flatMap(b -> {
                    if (!b.getEmail().equalsIgnoreCase(email)) {
                        return Mono.error(new IllegalStateException("Only owner can cancel the booking"));
                    }
                    if (b.isCanceled()) {
                        return Mono.error(new IllegalStateException("Booking already cancelled"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    if (!b.getJourneyDate().minusHours(24).isAfter(now)) {
                        return Mono.error(new IllegalStateException("Cancellation allowed only 24 hrs before journey"));
                    }
                    b.setCanceled(true);
                    b.setCanceledAt(now);
                    return bookingRepository.save(b).then();
                });
    }

    // NEW: update booking (owner-only).
    // Supports: update passengers, name, mealVeg, and seatNumbers (if provided and available).
    @Override
    public Mono<Booking> updateBooking(String pnr, BookingUpdateRequest req) {
        return bookingRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("PNR not found")))
                .flatMap(existingBooking -> {
                    if (!existingBooking.getEmail().equalsIgnoreCase(req.getEmail())) {
                        return Mono.error(new IllegalStateException("Only owner can update the booking"));
                    }
                    if (existingBooking.isCanceled()) {
                        return Mono.error(new IllegalStateException("Cannot update a cancelled booking"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    if (!existingBooking.getJourneyDate().minusHours(24).isAfter(now)) {
                        return Mono.error(new IllegalStateException("Updates not allowed within 24 hrs of journey"));
                    }

                    // If seatNumbers provided -> handle seat change
                    List<String> newSeats = req.getSeatNumbers();
                    if (newSeats != null && !newSeats.isEmpty()) {
                        // passenger count must match seat count
                        if (req.getPassengers() != null && req.getPassengers().size() != newSeats.size()) {
                            return Mono.error(new IllegalArgumentException("Passengers count must match new seats"));
                        }
                        // fetch inventory to check seat availability and swap seats
                        return inventoryRepository.findById(existingBooking.getFlightId())
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Flight not found for booking")))
                                .flatMap(inv -> {
                                    // create an availability set that treats currently booked seats as available (we're swapping)
                                    // release existing seats back temporarily
                                    List<String> tempAvailable = new ArrayList<>(inv.getAvailableSeats());
                                    // add back existing booking seats
                                    tempAvailable.addAll(existingBooking.getSeatNumbers());

                                    // check all requested seats are in tempAvailable
                                    if (!tempAvailable.containsAll(newSeats)) {
                                        return Mono.error(new IllegalStateException("Requested seats not available"));
                                    }

                                    // effect seats change: add old seats back to inventory and remove new seats
                                    // remove duplicates carefully
                                    List<String> invSeats = new ArrayList<>(inv.getAvailableSeats());
                                    // add old seats (if not already present)
                                    for (String s : existingBooking.getSeatNumbers()) {
                                        if (!invSeats.contains(s)) invSeats.add(s);
                                    }
                                    // now remove newSeats from invSeats (reserve them)
                                    invSeats.removeAll(newSeats);

                                    // save inventory and update booking fields
                                    inv.setAvailableSeats(invSeats);
                                    existingBooking.setSeatNumbers(newSeats);
                                    if (req.getPassengers() != null) existingBooking.setPassengers(req.getPassengers());
                                    if (req.getName() != null) existingBooking.setName(req.getName());
                                    if (req.getMealVeg() != null) existingBooking.setMealVeg(req.getMealVeg());

                                    return inventoryRepository.save(inv)
                                            .then(bookingRepository.save(existingBooking));
                                });
                    }

                    // No seat change: update passengers/name/meal only
                    if (req.getPassengers() != null) existingBooking.setPassengers(req.getPassengers());
                    if (req.getName() != null) existingBooking.setName(req.getName());
                    if (req.getMealVeg() != null) existingBooking.setMealVeg(req.getMealVeg());

                    return bookingRepository.save(existingBooking);
                });
    }
}
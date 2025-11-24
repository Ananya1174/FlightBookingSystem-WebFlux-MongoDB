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
                        .toList()
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
                .flatMap(existingBooking ->
                        validateUpdatePreconditions(existingBooking, req)
                                .flatMap(v -> {
                                    List<String> newSeats = req.getSeatNumbers();
                                    if (newSeats != null && !newSeats.isEmpty()) {
                                        // delegate the entire seat-change flow to a helper
                                        return handleSeatChange(existingBooking, req);
                                    }

                                    // No seat change: update passengers/name/meal only
                                    if (req.getPassengers() != null) existingBooking.setPassengers(req.getPassengers());
                                    if (req.getName() != null) existingBooking.setName(req.getName());
                                    if (req.getMealVeg() != null) existingBooking.setMealVeg(req.getMealVeg());

                                    return bookingRepository.save(existingBooking);
                                })
                );
    }

    // --- helpers extracted to reduce nesting / cognitive complexity ---
    private Mono<Void> validateUpdatePreconditions(Booking existingBooking, BookingUpdateRequest req) {
        if (!existingBooking.getEmail().equalsIgnoreCase(req.getEmail())) {
            return Mono.error(new IllegalStateException("Only the booking owner can perform this update"));
        }
        if (existingBooking.isCanceled()) {
            return Mono.error(new IllegalStateException("Cannot update a booking that is already cancelled"));
        }
        LocalDateTime now = LocalDateTime.now();
        if (!existingBooking.getJourneyDate().minusHours(24).isAfter(now)) {
            return Mono.error(new IllegalStateException("Updates are not allowed within 24 hours of the journey"));
        }
        return Mono.empty();
    }

    private List<String> buildTempAvailability(AirlineInventory inv, Booking existingBooking) {
        List<String> tempAvailable = new ArrayList<>();
        if (inv.getAvailableSeats() != null) tempAvailable.addAll(inv.getAvailableSeats());
        if (existingBooking.getSeatNumbers() != null) tempAvailable.addAll(existingBooking.getSeatNumbers());
        return tempAvailable;
    }

    private List<String> mergeInventoryWithOldSeats(List<String> invSeats, List<String> oldSeats) {
        if (invSeats == null) invSeats = new ArrayList<>();
        if (oldSeats == null || oldSeats.isEmpty()) return invSeats;
        for (String s : oldSeats) {
            if (!invSeats.contains(s)) {
                invSeats.add(s);
            }
        }
        return invSeats;
    }

    // helper that encapsulates the entire seat-change logic (previously inline)
    private Mono<Booking> handleSeatChange(Booking existingBooking, BookingUpdateRequest req) {
        // passenger count check (same as before)
        List<String> newSeats = req.getSeatNumbers();
        if (req.getPassengers() != null && req.getPassengers().size() != newSeats.size()) {
            return Mono.error(new IllegalArgumentException("Passenger count must match the number of requested seats"));
        }

        return inventoryRepository.findById(existingBooking.getFlightId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Flight not found for this booking")))
                .flatMap(inv -> {
                    // create an availability set that treats currently booked seats as available (we're swapping)
                    List<String> tempAvailable = buildTempAvailability(inv, existingBooking);

                    // check all requested seats are in tempAvailable
                    if (!tempAvailable.containsAll(newSeats)) {
                        return Mono.error(new IllegalStateException("One or more requested seats are not available"));
                    }

                    // effect seats change: add old seats back to inventory and remove new seats
                    List<String> invSeats = new ArrayList<>(inv.getAvailableSeats() == null ? List.of() : inv.getAvailableSeats());
                    invSeats = mergeInventoryWithOldSeats(invSeats, existingBooking.getSeatNumbers());

                    // now remove newSeats from invSeats (reserve them)
                    invSeats.removeAll(newSeats);

                    // save inventory and update booking fields
                    inv.setAvailableSeats(invSeats);
                    existingBooking.setSeatNumbers(new ArrayList<>(newSeats)); // defensive copy
                    if (req.getPassengers() != null) existingBooking.setPassengers(req.getPassengers());
                    if (req.getName() != null) existingBooking.setName(req.getName());
                    if (req.getMealVeg() != null) existingBooking.setMealVeg(req.getMealVeg());

                    return inventoryRepository.save(inv)
                            .then(bookingRepository.save(existingBooking));
                });
    }
}
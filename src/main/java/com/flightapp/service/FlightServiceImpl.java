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
import java.util.Objects;

@Service
public class FlightServiceImpl implements FlightService {

    private final InventoryRepository inventoryRepository;
    private final BookingRepository bookingRepository;

    private static final String ERR_ORIGIN_DEST_SAME = "Origin and destination cannot be the same";
    private static final String ERR_ARRIVAL_BEFORE_DEPARTURE = "Arrival must be after departure";
    private static final String ERR_TOTAL_SEATS_POSITIVE = "Total seats must be > 0";
    private static final String ERR_FLIGHT_NOT_FOUND = "Flight not found";
    private static final String ERR_SELECT_SEAT = "At least one seat must be selected";
    private static final String ERR_SEAT_UNAVAILABLE = "Some selected seats are unavailable";
    private static final String ERR_PASSENGER_SEAT_MISMATCH = "Passenger count must match selected seats";
    private static final String ERR_PNR_NOT_FOUND = "PNR not found";
    private static final String ERR_ONLY_OWNER = "Only owner can cancel the booking";
    private static final String ERR_ALREADY_CANCELLED = "Booking already cancelled";
    private static final String ERR_CANCEL_WINDOW = "Cancellation allowed only 24 hrs before journey";
    private static final String ERR_UPDATE_ONLY_OWNER = "Only the booking owner can perform this update";
    private static final String ERR_UPDATE_CANCELLED = "Cannot update a booking that is already cancelled";
    private static final String ERR_UPDATE_WINDOW = "Updates are not allowed within 24 hours of the journey";
    private static final String ERR_REQUESTED_SEATS_UNAVAILABLE = "One or more requested seats are not available";
    private static final String ERR_PASSENGER_COUNT_NEWSEATS = "Passenger count must match the number of requested seats";
    private static final String ERR_FLIGHT_NOT_FOUND_FOR_BOOKING = "Flight not found for this booking";

    public FlightServiceImpl(InventoryRepository inventoryRepository, BookingRepository bookingRepository) {
        this.inventoryRepository = inventoryRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Mono<AirlineInventory> addInventory(AirlineInventory inventory) {
        if (inventory.getOrigin().equalsIgnoreCase(inventory.getDestination())) {
            return Mono.error(new IllegalStateException(ERR_ORIGIN_DEST_SAME));
        }

        if (!inventory.getArrival().isAfter(inventory.getDeparture())) {
            return Mono.error(new IllegalStateException(ERR_ARRIVAL_BEFORE_DEPARTURE));
        }

        if (inventory.getTotalSeats() == null || inventory.getTotalSeats() <= 0) {
            return Mono.error(new IllegalStateException(ERR_TOTAL_SEATS_POSITIVE));
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
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ERR_FLIGHT_NOT_FOUND)))
                .flatMap(inv -> {

                    if (!inv.getDeparture().isAfter(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException("Cannot book a flight that already departed"));
                    }

                    if (req.getSeatNumbers() == null || req.getSeatNumbers().isEmpty()) {
                        return Mono.error(new IllegalArgumentException(ERR_SELECT_SEAT));
                    }

                    if (inv.getAvailableSeats() == null || !inv.getAvailableSeats().containsAll(req.getSeatNumbers())) {
                        return Mono.error(new IllegalStateException(ERR_SEAT_UNAVAILABLE));
                    }

                    if (req.getPassengers() == null || req.getPassengers().size() != req.getSeatNumbers().size()) {
                        return Mono.error(new IllegalArgumentException(ERR_PASSENGER_SEAT_MISMATCH));
                    }

                    // reserve seats
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

    @Override
    public Mono<Void> cancelByPnrAndEmail(String pnr, String email) {
        return bookingRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ERR_PNR_NOT_FOUND)))
                .flatMap(b -> {
                    if (!b.getEmail().equalsIgnoreCase(email)) {
                        return Mono.error(new IllegalStateException(ERR_ONLY_OWNER));
                    }
                    if (b.isCanceled()) {
                        return Mono.error(new IllegalStateException(ERR_ALREADY_CANCELLED));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    if (!b.getJourneyDate().minusHours(24).isAfter(now)) {
                        return Mono.error(new IllegalStateException(ERR_CANCEL_WINDOW));
                    }
                    b.setCanceled(true);
                    b.setCanceledAt(now);
                    return bookingRepository.save(b).then();
                });
    }

    @Override
    public Mono<Booking> updateBooking(String pnr, BookingUpdateRequest req) {
        return bookingRepository.findByPnr(pnr)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ERR_PNR_NOT_FOUND)))
                .flatMap(existingBooking ->
                        validateUpdatePreconditions(existingBooking, req)
                                .then(Mono.defer(() -> processUpdate(existingBooking, req)))
                );
    }

    // --- helpers ---

    private Mono<Void> validateUpdatePreconditions(Booking existingBooking, BookingUpdateRequest req) {
        if (!existingBooking.getEmail().equalsIgnoreCase(req.getEmail())) {
            return Mono.error(new IllegalStateException(ERR_UPDATE_ONLY_OWNER));
        }
        if (existingBooking.isCanceled()) {
            return Mono.error(new IllegalStateException(ERR_UPDATE_CANCELLED));
        }
        if (!existingBooking.getJourneyDate().minusHours(24).isAfter(LocalDateTime.now())) {
            return Mono.error(new IllegalStateException(ERR_UPDATE_WINDOW));
        }
        return Mono.empty();
    }

    /**
     * Centralized update processing extracted to reduce cognitive complexity in the main flow.
     * Preserves identical logic to previous inline implementation.
     */
    private Mono<Booking> processUpdate(Booking existingBooking, BookingUpdateRequest req) {
        final List<String> newSeats = req.getSeatNumbers();

        if (newSeats != null && !newSeats.isEmpty()) {
            if (req.getPassengers() != null && req.getPassengers().size() != newSeats.size()) {
                return Mono.error(new IllegalArgumentException(ERR_PASSENGER_COUNT_NEWSEATS));
            }
            return handleSeatChange(existingBooking, req, newSeats);
        }

        // No seat change: update simple fields and save
        if (req.getPassengers() != null) {
            existingBooking.setPassengers(req.getPassengers());
        }
        if (req.getName() != null) {
            existingBooking.setName(req.getName());
        }
        if (req.getMealVeg() != null) {
            existingBooking.setMealVeg(req.getMealVeg());
        }
        return bookingRepository.save(existingBooking);
    }

    /**
     * Handles the seat-change flow:
     *  - loads inventory
     *  - treats currently booked seats as available (swap)
     *  - validates requested seats
     *  - updates inventory.availableSeats and booking.seatNumbers
     */
    private Mono<Booking> handleSeatChange(Booking existingBooking, BookingUpdateRequest req, List<String> newSeats) {
        // guard flightId presence
        if (Objects.isNull(existingBooking.getFlightId())) {
            return Mono.error(new IllegalArgumentException(ERR_FLIGHT_NOT_FOUND_FOR_BOOKING));
        }

        return inventoryRepository.findById(existingBooking.getFlightId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(ERR_FLIGHT_NOT_FOUND_FOR_BOOKING)))
                .flatMap(inv -> {
                    List<String> tempAvailable = buildTempAvailability(inv, existingBooking);

                    if (!tempAvailable.containsAll(newSeats)) {
                        return Mono.error(new IllegalStateException(ERR_REQUESTED_SEATS_UNAVAILABLE));
                    }

                    List<String> invSeats = new ArrayList<>(inv.getAvailableSeats() != null ? inv.getAvailableSeats() : List.of());
                    invSeats = mergeInventoryWithOldSeats(invSeats, existingBooking.getSeatNumbers());

                    invSeats.removeAll(newSeats);

                    inv.setAvailableSeats(invSeats);
                    existingBooking.setSeatNumbers(newSeats);
                    if (req.getPassengers() != null) existingBooking.setPassengers(req.getPassengers());
                    if (req.getName() != null) existingBooking.setName(req.getName());
                    if (req.getMealVeg() != null) existingBooking.setMealVeg(req.getMealVeg());

                    return inventoryRepository.save(inv).then(bookingRepository.save(existingBooking));
                });
    }

    private List<String> buildTempAvailability(AirlineInventory inv, Booking existingBooking) {
        List<String> temp = new ArrayList<>();
        if (inv.getAvailableSeats() != null) temp.addAll(inv.getAvailableSeats());
        if (existingBooking.getSeatNumbers() != null) temp.addAll(existingBooking.getSeatNumbers());
        return temp;
    }

    private List<String> mergeInventoryWithOldSeats(List<String> invSeats, List<String> oldSeats) {
        if (invSeats == null) invSeats = new ArrayList<>();
        if (oldSeats == null || oldSeats.isEmpty()) return invSeats;
        for (String s : oldSeats) {
            if (!invSeats.contains(s)) invSeats.add(s);
        }
        return invSeats;
    }
}
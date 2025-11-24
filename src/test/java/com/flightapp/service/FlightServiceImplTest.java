package com.flightapp.service;

import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.model.Passenger;
import com.flightapp.dto.BookingRequest;
import com.flightapp.dto.BookingUpdateRequest;
import com.flightapp.repository.InventoryRepository;
import com.flightapp.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FlightServiceImplTest {

    InventoryRepository inventoryRepo;
    BookingRepository bookingRepo;
    FlightServiceImpl svc;

    @BeforeEach
    void setup() {
        inventoryRepo = mock(InventoryRepository.class);
        bookingRepo = mock(BookingRepository.class);
        svc = new FlightServiceImpl(inventoryRepo, bookingRepo);
    }

    private AirlineInventory sampleInventory() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("f-1");
        inv.setFlightNumber("IN1");
        inv.setOrigin("HYD");
        inv.setDestination("BLR");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(2));
        inv.setTotalSeats(3);
        // MUTABLE list to allow removeAll() in service
        inv.setAvailableSeats(new ArrayList<>(List.of("S1","S2","S3")));
        inv.setPrice(1000.0);
        return inv;
    }

    @Test
    void book_success_reservesSeatsAndSavesBooking() {
        AirlineInventory inv = sampleInventory();

        when(inventoryRepo.findById("f-1")).thenReturn(Mono.just(inv));
        when(inventoryRepo.save(any())).thenReturn(Mono.just(inv));
        when(bookingRepo.save(any())).thenAnswer(a -> Mono.just(a.getArgument(0)));

        BookingRequest req = new BookingRequest();
        req.setEmail("u@example.com");
        req.setName("User");
        req.setPassengers(List.of(new Passenger()));
        req.setSeatNumbers(List.of("S1"));
        req.setMealVeg(true);

        StepVerifier.create(svc.book("f-1", req))
                .assertNext(b -> {
                    assertNotNull(b.getPnr());
                    assertEquals("u@example.com", b.getEmail());
                    assertTrue(b.getSeatNumbers().contains("S1"));
                })
                .verifyComplete();

        verify(inventoryRepo).save(any());
        verify(bookingRepo).save(any());
    }

    @Test
    void book_seatUnavailable_throws() {
        AirlineInventory inv = sampleInventory();
        when(inventoryRepo.findById("f-1")).thenReturn(Mono.just(inv));

        BookingRequest req = new BookingRequest();
        req.setEmail("u@example.com");
        req.setName("User");
        req.setPassengers(List.of(new Passenger()));
        req.setSeatNumbers(List.of("S9")); // not available

        StepVerifier.create(svc.book("f-1", req))
                .expectErrorMatches(err -> err instanceof IllegalStateException &&
                        err.getMessage().contains("Some selected seats are unavailable"))
                .verify();

        verify(bookingRepo, never()).save(any());
    }

    @Test
    void cancel_success_marksCanceled() {
        Booking b = new Booking();
        b.setPnr("PNR1");
        b.setEmail("u@example.com");
        b.setCanceled(false);
        b.setJourneyDate(LocalDateTime.now().plusDays(3));

        when(bookingRepo.findByPnr("PNR1")).thenReturn(Mono.just(b));
        when(bookingRepo.save(any())).thenAnswer(a -> Mono.just(a.getArgument(0)));

        StepVerifier.create(svc.cancelByPnrAndEmail("PNR1", "u@example.com"))
                .verifyComplete();

        ArgumentCaptor<Booking> cap = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepo).save(cap.capture());
        assertTrue(cap.getValue().isCanceled());
    }

    @Test
    void cancel_alreadyCancelled_errors() {
        Booking b = new Booking();
        b.setPnr("PNR2");
        b.setEmail("u@example.com");
        b.setCanceled(true);
        b.setJourneyDate(LocalDateTime.now().plusDays(3));

        when(bookingRepo.findByPnr("PNR2")).thenReturn(Mono.just(b));

        StepVerifier.create(svc.cancelByPnrAndEmail("PNR2", "u@example.com"))
                .expectErrorMatches(err -> err instanceof IllegalStateException &&
                        err.getMessage().contains("Booking already cancelled"))
                .verify();
    }

    @Test
    void update_noSeatChange_updatesFields() {
        Booking existing = new Booking();
        existing.setPnr("PNR3");
        existing.setEmail("owner@example.com");
        existing.setCanceled(false);
        existing.setJourneyDate(LocalDateTime.now().plusDays(5));
        existing.setSeatNumbers(new ArrayList<>(List.of("S1")));
        existing.setPassengers(List.of(new Passenger()));
        existing.setFlightId("f-1"); // ensure flightId exists

        when(bookingRepo.findByPnr("PNR3")).thenReturn(Mono.just(existing));
        when(bookingRepo.save(any())).thenAnswer(a -> Mono.just(a.getArgument(0)));

        BookingUpdateRequest req = new BookingUpdateRequest();
        req.setEmail("owner@example.com");
        req.setName("New Name");
        req.setPassengers(List.of(new Passenger()));

        StepVerifier.create(svc.updateBooking("PNR3", req))
                .assertNext(b -> assertEquals("New Name", b.getName()))
                .verifyComplete();

        verify(bookingRepo).save(any());
    }

    @Test
    void update_seatChange_success() {
        Booking existing = new Booking();
        existing.setPnr("PNR4");
        existing.setEmail("owner@example.com");
        existing.setCanceled(false);
        existing.setJourneyDate(LocalDateTime.now().plusDays(5));
        existing.setSeatNumbers(new ArrayList<>(List.of("S1")));
        existing.setPassengers(List.of(new Passenger()));
        existing.setFlightId("f-1");

        when(bookingRepo.findByPnr("PNR4")).thenReturn(Mono.just(existing));

        AirlineInventory inv = sampleInventory();
        inv.setId("f-1");
        inv.setAvailableSeats(new ArrayList<>(List.of("S2","S3"))); // S1 currently booked
        when(inventoryRepo.findById("f-1")).thenReturn(Mono.just(inv));
        when(inventoryRepo.save(any())).thenAnswer(a -> Mono.just(a.getArgument(0)));
        when(bookingRepo.save(any())).thenAnswer(a -> Mono.just(a.getArgument(0)));

        BookingUpdateRequest req = new BookingUpdateRequest();
        req.setEmail("owner@example.com");
        req.setSeatNumbers(List.of("S2"));
        req.setPassengers(List.of(new Passenger()));

        StepVerifier.create(svc.updateBooking("PNR4", req))
                .assertNext(b -> assertTrue(b.getSeatNumbers().contains("S2")))
                .verifyComplete();

        verify(inventoryRepo).save(any());
        verify(bookingRepo).save(any());
    }
}
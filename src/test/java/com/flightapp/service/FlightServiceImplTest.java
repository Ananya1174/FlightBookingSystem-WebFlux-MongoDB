package com.flightapp.service;

import com.flightapp.model.AirlineInventory;
import com.flightapp.model.Booking;
import com.flightapp.dto.BookingRequest;
import com.flightapp.model.Passenger;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlightServiceImplTest {

    InventoryRepository inventoryRepo;
    BookingRepository bookingRepo;
    FlightServiceImpl service;

    @BeforeEach
     void setup() {
        inventoryRepo = mock(InventoryRepository.class);
        bookingRepo = mock(BookingRepository.class);
        service = new FlightServiceImpl(inventoryRepo, bookingRepo);
    }

    @Test
     void addInventory_validInventory_saves() {
        AirlineInventory inv = new AirlineInventory();
        inv.setAirline("TestAir");
        inv.setFlightNumber("T100");
        inv.setOrigin("AAA");
        inv.setDestination("BBB");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(1));
        inv.setTotalSeats(5);
        inv.setPrice(1000.0);

        when(inventoryRepo.save(any(AirlineInventory.class))).thenReturn(Mono.just(inv));

        StepVerifier.create(service.addInventory(inv))
            .expectNextMatches(saved -> "TestAir".equals(saved.getAirline()))
            .verifyComplete();

        verify(inventoryRepo, times(1)).save(any(AirlineInventory.class));
    }

    @Test
     void addInventory_sameOriginDestination_fails() {
        AirlineInventory inv = new AirlineInventory();
        inv.setOrigin("AAA");
        inv.setDestination("AAA");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(1));
        inv.setTotalSeats(5);
        inv.setPrice(1000.0);

        StepVerifier.create(service.addInventory(inv))
            .expectErrorMatches(err -> err instanceof IllegalStateException
                && err.getMessage().contains("Origin and destination cannot be the same"))
            .verify();
    }

    @Test
     void book_successfulBooking_reservesSeatsAndSavesBooking() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("flight-1");
        inv.setFlightNumber("F1");
        inv.setOrigin("O");
        inv.setDestination("D");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(2));
        inv.setTotalSeats(5);
        inv.setAvailableSeats(new ArrayList<>(List.of("S1", "S2", "S3", "S4", "S5")));
        inv.setPrice(200.0);

        BookingRequest req = new BookingRequest();
        req.setName("Tester");
        req.setEmail("tester@example.com");
        Passenger p1 = new Passenger();
        p1.setName("P1"); p1.setGender("M"); p1.setAge(30);
        req.setPassengers(List.of(p1));
        req.setSeatNumbers(List.of("S1"));
        req.setMealVeg(true);

        when(inventoryRepo.findById("flight-1")).thenReturn(Mono.just(inv));
        when(inventoryRepo.save(any(AirlineInventory.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(bookingRepo.save(any(Booking.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.book("flight-1", req))
            .assertNext(booking -> {
                assert booking.getPnr() != null;
                assert booking.getSeatNumbers().contains("S1");
                assert booking.getEmail().equals("tester@example.com");
            })
            .verifyComplete();

        verify(inventoryRepo, times(1)).save(any(AirlineInventory.class));
        verify(bookingRepo, times(1)).save(any(Booking.class));
    }

    @Test
     void book_seatUnavailable_fails() {
        AirlineInventory inv = new AirlineInventory();
        inv.setId("flight-1");
        inv.setDeparture(LocalDateTime.now().plusDays(2));
        inv.setArrival(inv.getDeparture().plusHours(2));
        inv.setAvailableSeats(new ArrayList<>(List.of("S2", "S3")));

        BookingRequest req = new BookingRequest();
        req.setName("Tester");
        req.setEmail("tester@example.com");
        Passenger p1 = new Passenger();
        p1.setName("P1"); p1.setGender("M"); p1.setAge(30);
        req.setPassengers(List.of(p1));
        req.setSeatNumbers(List.of("S1")); // unavailable

        when(inventoryRepo.findById("flight-1")).thenReturn(Mono.just(inv));

        StepVerifier.create(service.book("flight-1", req))
            .expectErrorMatches(err -> err instanceof IllegalStateException
                && err.getMessage().toLowerCase().contains("unavailable"))
            .verify();
    }

    @Test
     void cancel_ownerMismatch_fails() {
        Booking existing = new Booking();
        existing.setPnr("PNR1");
        existing.setEmail("owner@example.com");
        existing.setCanceled(false);
        existing.setJourneyDate(LocalDateTime.now().plusDays(2));

        when(bookingRepo.findByPnr("PNR1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.cancelByPnrAndEmail("PNR1", "nobody@example.com"))
            .expectErrorMatches(err -> err instanceof IllegalStateException
                && err.getMessage().toLowerCase().contains("only owner"))
            .verify();
    }

    @Test
    public void cancel_within24hrs_fails() {
        Booking existing = new Booking();
        existing.setPnr("PNR1");
        existing.setEmail("owner@example.com");
        existing.setCanceled(false);
        existing.setJourneyDate(LocalDateTime.now().plusHours(10)); // less than 24h

        when(bookingRepo.findByPnr("PNR1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.cancelByPnrAndEmail("PNR1", "owner@example.com"))
            .expectErrorMatches(err -> err instanceof IllegalStateException
                && err.getMessage().toLowerCase().contains("24"))
            .verify();
    }

    @Test
     void cancel_successful_setsCanceledTrue() {
        Booking existing = new Booking();
        existing.setPnr("PNR1");
        existing.setEmail("owner@example.com");
        existing.setCanceled(false);
        existing.setJourneyDate(LocalDateTime.now().plusDays(2));

        when(bookingRepo.findByPnr("PNR1")).thenReturn(Mono.just(existing));
        when(bookingRepo.save(any(Booking.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.cancelByPnrAndEmail("PNR1", "owner@example.com"))
            .verifyComplete();

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepo, times(1)).save(captor.capture());
        Booking saved = captor.getValue();
        assert saved.isCanceled();
    }
}
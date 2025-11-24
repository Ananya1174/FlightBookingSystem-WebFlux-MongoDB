package com.flightapp.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryRequestTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private InventoryRequest valid() {
        InventoryRequest r = new InventoryRequest();
        r.setAirline("Indigo");
        r.setAirlineLogoUrl("logo.png");
        r.setFlightNumber("IN123");
        r.setOrigin("HYD");
        r.setDestination("BLR");
        r.setDeparture(LocalDateTime.now().plusDays(1));
        r.setArrival(LocalDateTime.now().plusDays(1).plusHours(2));
        r.setTotalSeats(50);
        r.setPrice(5000.0);
        r.setAvailableSeats(List.of("S1", "S2"));
        return r;
    }

    @Test
    void validRequest_passesValidation() {
        InventoryRequest r = valid();

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isEmpty();
    }

    @Test
    void missingAirline_failsValidation() {
        InventoryRequest r = valid();
        r.setAirline("");

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("airline");
    }

    @Test
    void missingFlightNumber_failsValidation() {
        InventoryRequest r = valid();
        r.setFlightNumber(null);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("flightNumber");
    }

    @Test
    void missingOrigin_failsValidation() {
        InventoryRequest r = valid();
        r.setOrigin(" ");

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("origin");
    }

    @Test
    void missingDestination_failsValidation() {
        InventoryRequest r = valid();
        r.setDestination(null);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("destination");
    }

    @Test
    void nullDeparture_failsValidation() {
        InventoryRequest r = valid();
        r.setDeparture(null);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("departure");
    }

    @Test
    void nullArrival_failsValidation() {
        InventoryRequest r = valid();
        r.setArrival(null);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("arrival");
    }

    @Test
    void totalSeats_zero_failsMin() {
        InventoryRequest r = valid();
        r.setTotalSeats(0);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("totalSeats");
    }

    @Test
    void negativePrice_failsPositiveOrZero() {
        InventoryRequest r = valid();
        r.setPrice(-10.0);

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(r);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath())
                .hasToString("price");
    }
}
package com.flightapp.dto;

import java.util.List;

import com.flightapp.model.Passenger;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BookingUpdateRequest {
  @Email(message = "Email must be valid") @NotEmpty(message = "Email required") private String email;
  // optional: user can update their name
  private String name;

  // replace passenger details (optional but when provided should be non-empty)
  private List<Passenger> passengers;

  // optional: request to change seats; if provided, must match passengers size in service
  private List<String> seatNumbers;

  // optional: change meal preference
  private Boolean mealVeg;
}